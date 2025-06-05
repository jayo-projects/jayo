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
 * Each segment in a {@linkplain jayo.Buffer Buffer} is a doubly linked queue node referencing the following and
 * preceding segments in the buffer.
 * <p>
 * Each segment in the {@link SegmentPool} is a singly linked queue node referencing the rest of segments in the pool.
 * <p>
 * The underlying byte array of segments may be shared between buffers and byte strings. When a segment's byte array
 * is shared, the segment may not be recycled, nor may its byte data be changed.
 * The lone exception is that the {@link #owner} segment is allowed to append to the segment, meaning writing data at
 * {@code limit} and beyond. There is a single owning segment for each byte array. Positions, limits, prev, and next
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
     * A segment will be shared if the data size exceeds this threshold to avoid having to copy this many bytes.
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
     * The first byte of available data ready to be written to. This field will be exclusively modified by the writer
     * and will be read when needed by the reader.
     * <p>
     * Note: <i>In the segment pool</i>, if the segment is free and linked, the field contains the total byte count of
     * this and all next segments.
     */
    int limit;

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
     * A reference to the next segment in the singly or circularly linked queue.
     */
    @Nullable
    Segment next = null;

    /**
     * A reference to the previous segment in the circularly linked queue.
     */
    @Nullable
    Segment prev = null;

    /**
     * A lateinit on-heap {@link ByteBuffer} that wraps the underlying byte array that is created on-demand for NIO or
     * TLS operations.
     */
    private @Nullable ByteBuffer byteBuffer = null;

    Segment() {
        this.data = new byte[SIZE];
        this.owner = true;
        this.copyTracker = null;
    }

    Segment(final byte @NonNull [] data,
            final int pos,
            final int limit,
            final @Nullable CopyTracker copyTracker,
            final boolean owner) {
        assert data != null;
        this.data = data;
        this.pos = pos;
        this.limit = limit;
        this.copyTracker = copyTracker;
        this.owner = owner;
    }

    /**
     * True if other buffer segments or byte strings use the same byte array.
     */
    boolean isShared() {
        return copyTracker != null && copyTracker.isShared();
    }

    /**
     * Returns a new segment that shares the underlying byte array with this one. Adjusting pos and limit is safe, but
     * writes are forbidden. This also marks the current segment as shared, which prevents it from being pooled.
     */
    public @NonNull Segment sharedCopy() {
        share();
        return new Segment(
                data,
                pos,
                limit,
                copyTracker,
                false
        );
    }

    void share() {
        if (copyTracker == null) {
            copyTracker = new CopyTracker();
        }
        copyTracker.addCopy();
    }

    /**
     * Returns a new segment with its own private copy of the underlying byte array.
     */
    @NonNull
    Segment unsharedCopy() {
        return new Segment(data.clone(), pos, limit, null, true);
    }

    /**
     * Removes this segment of a circularly linked list and returns its successor.
     * Returns null if the list is now empty.
     */
    Segment pop() {
        Segment result = (next != this) ? next : null;
        assert prev != null;
        prev.next = next;
        assert next != null;
        next.prev = prev;
        // no cleaning, next and prev will be re-affected in recycle or when transferred to another buffer.
        return result;
    }

    /**
     * Appends {@code segment} after this segment in the circularly linked list. Returns the pushed segment.
     */
    @NonNull
    Segment push(final @NonNull Segment segment) {
        assert segment != null;

        segment.prev = this;
        segment.next = next;
        assert next != null;
        next.prev = segment;
        next = segment;
        return segment;
    }

    /**
     * Moves {@code byteCount} bytes from this segment to {@code targetSegment}.
     */
    void writeTo(final @NonNull Segment targetSegment, final int byteCount) {
        assert targetSegment != null;

        if (targetSegment.limit + byteCount > SIZE) {
            // We can't fit byteCount bytes at the writer's current position. Shift writer first.
            assert targetSegment.owner;
            final var targetSize = targetSegment.limit - targetSegment.pos;
            if (targetSize + byteCount > SIZE) {
                throw new IllegalArgumentException("not enough space in writer segment to write " + byteCount + " bytes");
            }
            System.arraycopy(targetSegment.data, targetSegment.pos, targetSegment.data, 0, targetSize);
            targetSegment.limit = targetSize;
            targetSegment.pos = 0;
        }

        System.arraycopy(data, pos, targetSegment.data, targetSegment.limit, byteCount);
        targetSegment.limit += byteCount;
        pos += byteCount;
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
        //  - Avoid copying data. We achieve this by sharing segments.
        //  - Avoid short shared segments. These are bad for performance because they are readonly and may lead to long
        //    chains of short segments.
        // To balance these goals, we only share segments when the copy will be large.
        if (byteCount >= SHARE_MINIMUM) {
            prefix = sharedCopy();
        } else {
            prefix = SegmentPool.take();
            System.arraycopy(data, pos, prefix.data, 0, byteCount);
        }
        prefix.limit = prefix.pos + byteCount;
        pos += byteCount;

        return prefix;
    }

    @NonNull
    ByteBuffer asByteBuffer(final int pos, final int byteCount) {
        if (byteBuffer == null) {
            byteBuffer = ByteBuffer.wrap(data);
        }

        return byteBuffer
                .limit(pos + byteCount)
                .position(pos);
    }

    @Override
    public String toString() {
        final var next = this.next;
        final var prev = this.prev;
        return "Segment#" + hashCode() + " [maxSize=" + data.length + "] {" +
                System.lineSeparator() +
                ", pos=" + pos +
                ", limit=" + limit +
                ", shared=" + isShared() +
                ", owner=" + owner +
                System.lineSeparator() +
                ", next=" + ((next != null) ? "Segment#" + next.hashCode() : "null") +
                ", prev=" + ((prev != null) ? "Segment#" + prev.hashCode() : "null") +
                System.lineSeparator() +
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
         * If a tracker was in an unshared state, this call should not affect an internal state.
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
