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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
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
    int pos;

    /**
     * The first byte of available data ready to be written to. This field will be exclusively modified by the writer,
     * and will be read when needed by the reader.
     * <p>
     * <b>In the segment pool:</b> if the segment is free and linked, the field contains total byte count of this and
     * next segments.
     */
    int limit;

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
    @Nullable
    CopyTracker copyTracker;

    /**
     * True if this segment owns the byte array and can append to it, extending `limit`.
     */
    boolean owner;

    /**
     * A reference to the next segment in the queue.
     */
    @Nullable
    Segment next = null;

    // status
    static final byte AVAILABLE = 1;
    static final byte WRITING = 2;
    static final byte TRANSFERRING = 3;
    static final byte REMOVING = 4; // final state, cannot go back

    byte status;

    Segment() {
        this(new byte[SIZE], 0, 0, null, true);
    }

    Segment(final byte @NonNull [] data,
            final int pos,
            final int limit,
            final @Nullable CopyTracker copyTracker,
            final boolean owner) {
        this(data, pos, limit, copyTracker, owner, WRITING);
    }

    private Segment(final byte @NonNull [] data,
                    final int pos,
                    final int limit,
                    final @Nullable CopyTracker copyTracker,
                    final boolean owner,
                    final byte status) {
        assert data != null;
        this.data = data;
        this.pos = pos;
        this.limit = limit;
        this.copyTracker = copyTracker;
        this.owner = owner;
        this.status = status;
    }

    boolean tryWrite() {
        if (status == AVAILABLE) {
            status = WRITING;
            return true;
        }
        return false;
    }

    void finishWrite() {
        final var currentStatus = this.status;
        if (currentStatus == WRITING) {
            status = AVAILABLE;
        } else {
            throw new IllegalStateException("Could not finish write operation, status = " + currentStatus);
        }
    }

    boolean tryRemove() {
        return switch (status) {
            case AVAILABLE -> {
                status = REMOVING;
                yield true;
            }
            case REMOVING -> true;
            default -> false;
        };
    }

    boolean tryAndValidateRemove() {
        if (status == AVAILABLE) {
            status = REMOVING;
        } else if (status != REMOVING) {
            return false;
        }

        if (pos == limit) {
            return true;
        }

        status = AVAILABLE;
        return false;
    }

    public boolean startTransfer() {
        return switch (status) {
            case AVAILABLE -> {
                status = TRANSFERRING;
                yield false;
            }
            case WRITING -> true;
            default -> throw new IllegalStateException("Unexpected state " + status + ". The head queue node " +
                    "should be in 'AVAILABLE' or 'WRITING' state before transferring.");
        };
    }

    void finishTransfer() {
        switch (status) {
            case TRANSFERRING -> status = AVAILABLE;
            case AVAILABLE, WRITING -> { /*nop*/ }
            default -> throw new IllegalStateException("Unexpected state " + status + ". The head queue node" +
                    " should be in 'AVAILABLE', 'TRANSFERRING' or 'WRITING' state before ending the transfer.");
        }
    }

    /**
     * True if other buffer segments or byte strings use the same byte array.
     */
    boolean isShared() {
        return copyTracker != null && copyTracker.isShared();
    }

    /**
     * Returns a new segment that shares the underlying byte array with this one. Adjusting pos and limit are safe but
     * writes are forbidden. This also marks the current segment as shared, which prevents it from being pooled.
     */
    @NonNull
    Segment sharedCopy() {
        CopyTracker t = copyTracker;
        if (t == null) {
            t = new CopyTracker();
            copyTracker = t;
        }
        t.addCopy();
        return new Segment(data, pos, limit, t, false);
    }

    /**
     * Returns a new segment with its own private copy of the underlying byte array.
     */
    @NonNull
    Segment unsharedCopy() {
        return new Segment(data.clone(), pos, limit, null, true, AVAILABLE);
    }

    /**
     * Splits this segment into two segments. The first segment contains the data in {@code [pos..pos+byteCount)}.
     * The second segment contains the data in {@code [pos+byteCount..limit)}.
     * This is useful when moving partial segments from one buffer to another.
     *
     * @return the new head of the queue.
     */
    @NonNull
    Segment splitHead(final int byteCount) {
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
        prefix.status = TRANSFERRING;
        prefix.limit = prefix.pos + byteCount;
        pos += byteCount;
        prefix.next = this;

        // stop transferring the current segment = the suffix
        finishTransfer();

        return prefix;
    }

    /**
     * Moves {@code byteCount} bytes from this segment to {@code targetSegment}.
     */
    void writeTo(final @NonNull Segment targetSegment, final int byteCount) {
        assert targetSegment != null;
        assert targetSegment.owner;

        if (targetSegment.limit + byteCount > SIZE) {
            // We can't fit byteCount bytes at the writer's current position. Shift writer first.
            assert !targetSegment.isShared();
            if (targetSegment.limit + byteCount - targetSegment.pos > SIZE) {
                throw new IllegalArgumentException("not enough space in writer segment to write " + byteCount + " bytes");
            }
            final var writerSize = targetSegment.limit - targetSegment.pos;
            System.arraycopy(targetSegment.data, targetSegment.pos, targetSegment.data, 0, writerSize);
            targetSegment.limit = writerSize;
            targetSegment.pos = 0;
        }

        System.arraycopy(data, pos, targetSegment.data, targetSegment.limit, byteCount);
        targetSegment.limit += byteCount;
        pos += byteCount;
    }

    @NonNull
    ByteBuffer asReadByteBuffer(final int byteCount) {
        assert byteCount > 0;

        if (readByteBuffer == null) {
            readByteBuffer = JavaVersionUtils.asReadOnlyBuffer(ByteBuffer.wrap(data));
        }

        // just set position and limit, then return this BytBuffer
        return readByteBuffer
                .limit(pos + byteCount)
                .position(pos);
    }

    @NonNull
    ByteBuffer asWriteByteBuffer(final int byteCount) {
        assert byteCount > 0;

        if (writeByteBuffer == null) {
            writeByteBuffer = ByteBuffer.wrap(data);
        }

        final var currentLimit = limit;
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
