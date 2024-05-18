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

import java.util.Objects;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;

/**
 * A segment of a buffer.
 * <p>
 * Each segment in a buffer is a circularly-linked list node referencing the following and preceding segments in the
 * buffer.
 * <p>
 * Each segment in the pool is a singly-linked list node referencing the rest of segments in the pool.
 * <p>
 * The underlying byte array of segments may be shared between buffers and byte strings. When a segment's byte array
 * is shared the segment may not be recycled, nor may its byte data be changed.
 * The lone exception is that the owner segment is allowed to append to the segment, meaning writing data at `limit` and
 * beyond. There is a single owning segment for each byte array. Positions, limits, prev, and next references are not
 * shared.
 */
final class Segment extends SafeSegment {
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

    static {
        String systemSegmentSize = null;
        try {
            systemSegmentSize = System.getProperty("jayo.segmentSize");
        } catch (Throwable t) { // whatever happens, recover
            LOGGER.log(ERROR, "Exception when resolving the provided segment size, fallback to default " +
                    "segment's SIZE = {0}", DEFAULT_SIZE);
        } finally {
            if (systemSegmentSize != null && !systemSegmentSize.isBlank()) {
                SIZE = Integer.parseInt(systemSegmentSize);
            } else {
                SIZE = DEFAULT_SIZE;
            }
            LOGGER.log(INFO, "Jayo will use segments of SIZE = {0} bytes", SIZE);
        }
    }

    /**
     * The binary data.
     */
    final byte @NonNull [] data;
    /**
     * The next byte of application data byte to read in this segment.
     */
    int pos = 0;
    /**
     * The first byte of available data ready to be written to.
     */
    int limit = 0;
    /**
     * True if other buffer segments or byte strings use the same byte array.
     */
    boolean shared;
    /**
     * True if this segment owns the byte array and can append to it, extending `limit`.
     */
    final boolean owner;

    Segment() {
        this.data = new byte[SIZE];
        this.owner = true;
        this.shared = false;
    }

    Segment(final byte @NonNull [] data, final int pos, final int limit, final boolean shared, final boolean owner) {
        this.data = Objects.requireNonNull(data);
        this.pos = pos;
        this.limit = limit;
        this.shared = shared;
        this.owner = owner;
    }

    /**
     * Returns a new segment that shares the underlying byte array with this one. Adjusting pos and limit are safe but
     * writes are forbidden. This also marks the current segment as shared, which prevents it from being pooled.
     */
    @NonNull
    Segment sharedCopy() {
        shared = true;
        return new Segment(data, pos, limit, true, false);
    }

    /**
     * Returns a new segment with its own private copy of the underlying byte array.
     */
    @NonNull
    Segment unsharedCopy() {
        return new Segment(data.clone(), pos, limit, false, true);
    }

    /**
     * Splits this segment into two segments. The first segment contains the data in {@code [pos..pos+byteCount)}.
     * The second segment contains the data in {@code [pos+byteCount..limit)}.
     * This can be useful when moving partial segments from one buffer to another.
     *
     * @return the first part of this split segment.
     */
    @NonNull
    Segment split(final @NonNegative int byteCount) {
        final var currentPos = pos;
        if (byteCount <= 0 || byteCount > limit - currentPos) {
            throw new IllegalArgumentException("byteCount out of range: " + byteCount);
        }
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
            System.arraycopy(data, currentPos, prefix.data, 0, byteCount);
        }

        prefix.limit = prefix.pos + byteCount;
        pos = currentPos + byteCount;
        return prefix;
    }

    /**
     * Moves `byteCount` bytes from this segment to `sink`.
     */
    void writeTo(final @NonNull Segment sink, final int byteCount) {
        Objects.requireNonNull(sink);
        if (!sink.owner) {
            throw new IllegalStateException("only owner can write");
        }
        var sinkCurrentLimit = sink.limit;
        if (sinkCurrentLimit + byteCount > SIZE) {
            // We can't fit byteCount bytes at the sink's current position. Shift sink first.
            if (sink.shared) {
                throw new IllegalArgumentException("cannot write in a shared Segment");
            }
            final var sinkCurrentPos = sink.pos;
            if (sinkCurrentLimit + byteCount - sinkCurrentPos > SIZE) {
                throw new IllegalArgumentException("not enough space in sink segment to write " + byteCount + " bytes");
            }
            final var sinkSize = sinkCurrentLimit - sinkCurrentPos;
            System.arraycopy(sink.data, sinkCurrentPos, sink.data, 0, sinkSize);
            sink.limit = sinkSize;
            sink.pos = 0;
        }

        sinkCurrentLimit = sink.limit;
        final var currentPos = pos;
        System.arraycopy(data, currentPos, sink.data, sinkCurrentLimit, byteCount);
        sink.limit = sinkCurrentLimit + byteCount;
        pos = currentPos + byteCount;
    }

    @Override
    byte @NonNull [] data() {
        return data;
    }

    @Override
    int pos() {
        return pos;
    }

    @Override
    int limit() {
        return limit;
    }

    @Override
    boolean shared() {
        return shared;
    }

    @Override
    public String toString() {
        return "Segment#" + hashCode() + "{" +
                "data=[" + data.length + "]" +
                ", pos=" + pos +
                ", limit=" + limit +
                ", shared=" + shared +
                ", owner=" + owner +
                '}';
    }
}
