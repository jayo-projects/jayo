/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
 *
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.internal;

import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * A segment of a buffer.
 * <p>
 * Each segment in a {@linkplain jayo.Buffer Buffer} is a singly-linked queue node referencing the following segments in
 * the buffer.
 * <p>
 * Each segment in the {@link SegmentPool} is a singly-linked queue node referencing the rest of segments in the pool.
 * <p>
 * The underlying byte array of segments may be shared between buffers and byte strings. When a segment's byte array
 * is shared the segment may not be recycled, nor may its byte data be changed.
 * The lone exception is that the owner segment is allowed to append to the segment, meaning writing data at
 * {@code limit} and beyond. There is a single owning segment for each byte array. Positions, limits, and next
 * references are not shared.
 */
final class Segment {
    /**
     * The size of all segments in bytes.
     *
     * @implNote Aligned with TLS max data size = 16_709 bytes
     */
    static final int SIZE = RealTlsEndpoint.MAX_ENCRYPTED_PACKET_BYTE_SIZE;

    /**
     * A segment will be shared if the data size exceeds this threshold, to avoid having to copy this many bytes.
     */
    private static final int SHARE_MINIMUM = 1024; // todo should it be more now that size is 16 KB ?

    /**
     * The binary data.
     */
    final byte @NonNull [] data;

    /**
     * The next byte of application data byte to read in this segment. This field will be exclusively modified and read
     * by the reader.
     */
    int pos = 0;

    /**
     * The first byte of available data ready to be written to. This field will be exclusively modified by the writer,
     * and will be read when needed by the reader.
     * <p>
     * <b>In the segment pool:</b> if the segment is free and linked, the field contains total byte count of this and
     * next segments.
     */
    @SuppressWarnings("FieldMayBeFinal")
    private volatile int limit = 0;

    /**
     * A lateinit on-heap {@link ByteBuffer} associated with the underlying byte array that is created on-demand for
     * write operations.
     */
    private @Nullable ByteBuffer writeByteBuffer = null;

    /**
     * A lateinit on-heap {@link ByteBuffer} associated with the underlying byte array that is created on-demand for
     * read operations.
     */
    private @Nullable ByteBuffer readByteBuffer = null;

    /**
     * Tracks number shared copies.
     */
    @SuppressWarnings("FieldMayBeFinal")
    private volatile @Nullable CopyTracker copyTracker;

    /**
     * True if this segment owns the byte array and can append to it, extending `limit`.
     */
    boolean owner;

    /**
     * A reference to the next segment in the queue.
     */
    @SuppressWarnings("FieldMayBeFinal")
    private volatile @Nullable Segment next = null;

    // status
    static final byte AVAILABLE = 1;
    static final byte WRITING = 2;
    static final byte TRANSFERRING = 3;
    static final byte REMOVING = 4; // final state, cannot go back

    @SuppressWarnings("FieldMayBeFinal")
    private volatile byte status;

    // VarHandle mechanics
    private static final VarHandle LIMIT;
    static final VarHandle COPY_TRACKER;
    static final VarHandle NEXT;
    static final VarHandle STATUS;

    static {
        try {
            final var l = MethodHandles.lookup();
            LIMIT = l.findVarHandle(Segment.class, "limit", int.class);
            COPY_TRACKER = l.findVarHandle(Segment.class, "copyTracker", CopyTracker.class);
            NEXT = l.findVarHandle(Segment.class, "next", Segment.class);
            STATUS = l.findVarHandle(Segment.class, "status", byte.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    Segment() {
        this.data = new byte[SIZE];
        this.owner = true;
        this.copyTracker = null;
        this.status = WRITING;
    }

    Segment(final byte @NonNull [] data,
            final int pos,
            final int limit,
            final @Nullable ByteBuffer writeByteBuffer,
            final @Nullable ByteBuffer readByteBuffer,
            final @Nullable CopyTracker copyTracker,
            final boolean owner) {
        this(data, pos, limit, writeByteBuffer, readByteBuffer, copyTracker, owner, WRITING);
    }

    Segment(final byte @NonNull [] data,
            final int pos,
            final int limit,
            final @Nullable ByteBuffer writeByteBuffer,
            final @Nullable ByteBuffer readByteBuffer,
            final @Nullable CopyTracker copyTracker,
            final boolean owner,
            final byte status) {
        assert data != null;
        this.data = data;
        this.pos = pos;
        this.limit = limit;
        this.writeByteBuffer = writeByteBuffer;
        this.readByteBuffer = readByteBuffer;
        this.copyTracker = copyTracker;
        this.owner = owner;
        this.status = status;
    }

    @Nullable
    Segment nextVolatile() {
        return (@Nullable Segment) NEXT.getVolatile(this);
    }

    @NonNegative
    int limit() {
        return (int) LIMIT.get(this);
    }

    @NonNegative
    int limitVolatile() {
        return (int) LIMIT.getVolatile(this);
    }

    // Use only this non-volatile set from SegmentPool or Buffer.UnsafeCursor !
    void limit(final @NonNegative int limit) {
        LIMIT.set(this, limit);
    }

    void limitVolatile(final @NonNegative int limit) {
        LIMIT.setVolatile(this, limit); // test less strict than volatile
    }

    void incrementLimitVolatile(final @NonNegative int increment) {
        LIMIT.getAndAdd(this, increment); // test less strict than volatile
    }

    boolean tryWrite() {
        return STATUS.compareAndSet(this, AVAILABLE, WRITING);
    }

    void finishWrite() {
        if (!STATUS.compareAndSet(this, WRITING, AVAILABLE)) {
            throw new IllegalStateException("Could not finish write operation");
        }
    }

    boolean tryRemove() {
        final var previousState = (byte) STATUS.compareAndExchange(this, AVAILABLE, REMOVING);
        return switch (previousState) {
            case AVAILABLE, REMOVING -> true;
            default -> false;
        };
    }

    boolean validateRemove() {
        assert (byte) STATUS.get(this) == REMOVING;

        if (pos == limitVolatile()) {
            return true;
        }

        finishRemove();
        return false;
    }

    void finishRemove() {
        if (!STATUS.compareAndSet(this, REMOVING, AVAILABLE)) {
            throw new IllegalStateException("Could not finish remove operation");
        }
    }

    public boolean startTransfer() {
        final var previousState = (byte) STATUS.compareAndExchange(this, AVAILABLE, TRANSFERRING);
        return switch (previousState) {
            case AVAILABLE -> false;
            case WRITING -> true;
            default -> throw new IllegalStateException("Unexpected state " + previousState + ". The head queue " +
                    "node should be in 'AVAILABLE' or 'WRITING' state before transferring.");
        };
    }

    void finishTransfer(final boolean wasWriting) {
        if (!wasWriting) {
            STATUS.compareAndSet(this, TRANSFERRING, AVAILABLE);
        }
    }

    /**
     * True if other buffer segments or byte strings use the same byte array.
     */
    boolean isShared() {
        final var ct = (CopyTracker) COPY_TRACKER.getVolatile(this);
        return ct != null && ct.isShared();
    }

    /**
     * Returns a new segment that shares the underlying byte array with this one. Adjusting pos and limit are safe but
     * writes are forbidden. This also marks the current segment as shared, which prevents it from being pooled.
     */
    @NonNull
    Segment sharedCopy() {
        var ct = (CopyTracker) COPY_TRACKER.getVolatile(this);
        if (ct == null) {
            ct = new CopyTracker();
            final var oldCt = (CopyTracker) COPY_TRACKER.compareAndExchange(this, null, ct);
            if (oldCt != null) {
                ct = oldCt;
            }
        }
        ct.addCopy();
        return new Segment(
                data,
                pos,
                limitVolatile(),
                (writeByteBuffer != null) ? writeByteBuffer.duplicate() : null,
                (readByteBuffer != null) ? readByteBuffer.duplicate() : null,
                ct, false
        );
    }

    /**
     * Returns a new segment with its own private copy of the underlying byte array.
     */
    @NonNull
    Segment unsharedCopy() {
        return new Segment(data.clone(), pos, limitVolatile(), null, null, null, true, AVAILABLE);
    }

    /**
     * Splits this segment into two segments. The first segment contains the data in {@code [pos..pos+byteCount)}.
     * The second segment contains the data in {@code [pos+byteCount..limit)}.
     * This is useful when moving partial segments from one buffer to another.
     *
     * @return the new head of the queue.
     */
    @NonNull
    Segment splitHead(final @NonNegative int byteCount, final boolean wasWriting) {
        final Segment prefix;

        // We have two competing performance goals:
        //  - Avoid copying data. We accomplish this by sharing segments.
        //  - Avoid short shared segments. These are bad for performance because they are readonly and may lead to long
        //    chains of short segments.
        // To balance these goals we only share segments when the copy will be large.
        if (byteCount >= SHARE_MINIMUM) {
            prefix = sharedCopy();
        } else {
            prefix = SegmentPool.take();
            System.arraycopy(data, pos, prefix.data, 0, byteCount);
        }
        STATUS.set(prefix, TRANSFERRING);

        prefix.limitVolatile(prefix.pos + byteCount);
        pos += byteCount;

        if (!NEXT.compareAndSet(prefix, null, this)) {
            throw new IllegalStateException("Could set next segment of prefix");
        }
        // stop transferring the current segment = the suffix
        finishTransfer(wasWriting);

        return prefix;
    }

    /**
     * Moves {@code byteCount} bytes from this segment to {@code targetSegment}.
     */
    void writeTo(final @NonNull Segment targetSegment, final @NonNegative int byteCount) {
        Objects.requireNonNull(targetSegment);
        assert targetSegment.owner;
        var writerCurrentLimit = targetSegment.limit();
        if (writerCurrentLimit + byteCount > SIZE) {
            // We can't fit byteCount bytes at the writer's current position. Shift writer first.
            assert !targetSegment.isShared();
            final var writerCurrentPos = targetSegment.pos;
            if (writerCurrentLimit + byteCount - writerCurrentPos > SIZE) {
                throw new IllegalArgumentException("not enough space in writer segment to write " + byteCount + " bytes");
            }
            final var writerSize = writerCurrentLimit - writerCurrentPos;
            System.arraycopy(targetSegment.data, writerCurrentPos, targetSegment.data, 0, writerSize);
            targetSegment.limitVolatile(writerSize);
            targetSegment.pos = 0;
        }

        writerCurrentLimit = targetSegment.limit();
        final var currentPos = pos;
        System.arraycopy(data, currentPos, targetSegment.data, writerCurrentLimit, byteCount);
        targetSegment.limitVolatile(writerCurrentLimit + byteCount);
        pos = currentPos + byteCount;
    }

    @NonNull
    ByteBuffer asReadByteBuffer(final @NonNegative int byteCount) {
        assert byteCount > 0;

        if (readByteBuffer == null) {
            readByteBuffer = ByteBuffer.wrap(data).asReadOnlyBuffer();
        }

        // just set position and limit, then return this BytBuffer
        return readByteBuffer
                .limit(pos + byteCount)
                .position(pos);
    }

    @NonNull
    ByteBuffer asWriteByteBuffer(final @NonNegative int byteCount) {
        assert byteCount > 0;

        if (writeByteBuffer == null) {
            writeByteBuffer = ByteBuffer.wrap(data);
            writeByteBuffer.limit(Segment.SIZE);
        }

        final var currentLimit = limit();
        // just set position and limit, then return this BytBuffer
        return writeByteBuffer
                .limit(currentLimit + byteCount)
                .position(currentLimit);
    }

    @Override
    public String toString() {
        final var next = this.next;
        return "Segment#" + hashCode() + " [maxSize=" + data.length + "] {" + System.lineSeparator() +
                ", pos=" + pos +
                ", limit=" + limit +
                ", shared=" + isShared() +
                ", owner=" + owner +
                ", status=" +
                switch (status) {
                    case 1 -> "AVAILABLE";
                    case 2 -> "WRITING";
                    case 3 -> "TRANSFERRING";
                    case 4 -> "REMOVING";
                    default -> throw new IllegalStateException("Unexpected status: " + status);
                } + System.lineSeparator() +
                ", next=" + ((next != null) ? "Segment#" + next.hashCode() : "null") + System.lineSeparator() +
                '}';
    }

    /**
     * Reference counting SegmentCopyTracker tracking the number of shared segment copies.
     * Every {@link #addCopy} call increments the counter, every {@link #removeCopy} decrements it.
     * <p>
     * After calling {@link #removeCopy} the same number of time {@link #addCopy} was called, this tracker returns to the
     * unshared state.
     */
    static final class CopyTracker {
        @SuppressWarnings("FieldMayBeFinal")
        private volatile int copyCount = 0;

        // AtomicIntegerFieldUpdater mechanics
        private static final AtomicIntegerFieldUpdater<CopyTracker> COPY_COUNT =
                AtomicIntegerFieldUpdater.newUpdater(CopyTracker.class, "copyCount");

        boolean isShared() {
            return copyCount > 0;
        }

        /**
         * Track a new copy created by sharing an associated segment.
         */
        void addCopy() {
            COPY_COUNT.incrementAndGet(this);
        }

        /**
         * Records reclamation of a shared segment copy associated with this tracker.
         * If a tracker was in unshared state, this call should not affect an internal state.
         *
         * @return {@code true} if the segment was not shared <i>before</i> this call.
         */
        boolean removeCopy() {
            // The value could not be incremented from `0` under the race, so once it zero, it remains zero in the scope of
            // this call.
            if (copyCount == 0) {
                return false;
            }

            final var updatedValue = COPY_COUNT.decrementAndGet(this);
            // If there are several copies, the last decrement will update copyCount from 0 to -1.
            // That would be the last standing copy, and we can recycle it.
            // If, however, the decremented value falls below -1, it's an error as there were more `removeCopy` than
            // `addCopy` calls.
            if (updatedValue >= 0) {
                return true;
            }
            if (updatedValue < -1) {
                throw new IllegalStateException("Shared copies count is negative: " + updatedValue + 1);
            }
            copyCount = 0;
            return false;
        }
    }
}
