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
import java.util.Objects;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;

/**
 * A segment of a buffer.
 * <p>
 * Each segment in a buffer is a singly-linked queue node referencing the following segments in the buffer.
 * <p>
 * Each segment in the pool is a singly-linked queue node referencing the rest of segments in the pool.
 * <p>
 * The underlying byte array of segments may be shared between buffers and byte strings. When a segment's byte array
 * is shared the segment may not be recycled, nor may its byte data be changed.
 * The lone exception is that the owner segment is allowed to append to the segment, meaning writing data at
 * {@code limit} and beyond. There is a single owning segment for each byte array. Positions, limits, and next
 * references are not shared.
 */
final class Segment {
    private static final System.Logger LOGGER = System.getLogger("jayo.Segment");

    /**
     * The default size of all segments in bytes, if no System property is provided.
     */
    private static final int DEFAULT_SIZE = 8 * 1024;

    /**
     * The size of all segments in bytes.
     */
    static final int SIZE;
    /**
     * A segment will be shared if the data size exceeds this threshold, to avoid having to copy this many bytes.
     */
    private static final int SHARE_MINIMUM = 1024;

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
     * and will be read by the reader.
     * <p>
     * <b>In the segment pool:</b> if the segment is free and linked, the field contains total byte count of this and
     * next segments.
     */
    @SuppressWarnings("FieldMayBeFinal")
    private volatile int limit = 0;
    /**
     * True if other buffer segments or byte strings use the same byte array.
     */
    boolean shared;
    /**
     * True if this segment owns the byte array and can append to it, extending `limit`.
     */
    final boolean owner;
    /**
     * Next segment in the queue.
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
    static final VarHandle NEXT;
    static final VarHandle STATUS;

    static {
        try {
            final var l = MethodHandles.lookup();
            LIMIT = l.findVarHandle(Segment.class, "limit", int.class);
            NEXT = l.findVarHandle(Segment.class, "next", Segment.class);
            STATUS = l.findVarHandle(Segment.class, "status", byte.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        // Segment.SIZE System property overriding.
        String systemSegmentSize = null;
        try {
            systemSegmentSize = System.getProperty("jayo.segmentSize");
        } catch (Throwable t) { // whatever happens, recover
            LOGGER.log(ERROR, "Exception when resolving the provided segment size, fallback to default " +
                    "segment's SIZE = {0}", DEFAULT_SIZE);
        } finally {
            var segmentSize = 0;
            if (systemSegmentSize != null && !systemSegmentSize.isBlank()) {
                try {
                    segmentSize = Integer.parseInt(systemSegmentSize);
                } catch (NumberFormatException _unused) {
                    LOGGER.log(ERROR, "{0} is not a valid size, fallback to default segment's SIZE = {1}",
                            systemSegmentSize, DEFAULT_SIZE);
                }
            }
            SIZE = (segmentSize > 0) ? segmentSize : DEFAULT_SIZE;
            LOGGER.log(INFO, "Jayo will use segments of SIZE = {0} bytes", SIZE);
        }
    }

    Segment() {
        this.data = new byte[SIZE];
        this.owner = true;
        this.shared = false;
        this.status = WRITING;
    }

    Segment(final byte @NonNull [] data, final int pos, final int limit, final boolean shared, final boolean owner) {
        this(data, pos, limit, shared, owner, WRITING);
    }

    Segment(final byte @NonNull [] data, final int pos, final int limit, final boolean shared, final boolean owner, final byte status) {
        this.data = Objects.requireNonNull(data);
        this.pos = pos;
        this.limit = limit;
        this.shared = shared;
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
     * Returns a new segment that shares the underlying byte array with this one. Adjusting pos and limit are safe but
     * writes are forbidden. This also marks the current segment as shared, which prevents it from being pooled.
     */
    @NonNull
    Segment sharedCopy() {
        shared = true;
        return new Segment(data, pos, limitVolatile(), true, false);
    }

    /**
     * Returns a new segment with its own private copy of the underlying byte array.
     */
    @NonNull
    Segment unsharedCopy() {
        return new Segment(data.clone(), pos, limitVolatile(), false, true, AVAILABLE);
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
        // stop transferring suffix
        finishTransfer(wasWriting);

        return prefix;
    }

    /**
     * Moves {@code byteCount} bytes from this segment to {@code targetSegment}.
     */
    void writeTo(final @NonNull Segment targetSegment, final @NonNegative int byteCount) {
        Objects.requireNonNull(targetSegment);
        if (!targetSegment.owner) {
            throw new IllegalStateException("only owner can write");
        }
        var writerCurrentLimit = targetSegment.limit();
        if (writerCurrentLimit + byteCount > SIZE) {
            // We can't fit byteCount bytes at the writer's current position. Shift writer first.
            if (targetSegment.shared) {
                throw new IllegalArgumentException("cannot write in a shared Segment");
            }
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

    @Override
    public String toString() {
        final var next = this.next;
        return "Segment#" + hashCode() + "{" + System.lineSeparator() +
                "data=[" + data.length + "]" +
                ", pos=" + pos +
                ", limit=" + limit + System.lineSeparator() +
                ", shared=" + shared +
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
}
