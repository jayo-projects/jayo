/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package jayo.internal;

import jayo.*;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class RealInflaterRawReader implements InflaterRawReader {
    private final @NonNull RealReader reader;
    private final @NonNull Inflater inflater;
    /**
     * When we call Inflater.setInput(), the inflater keeps our byte array until it needs input again.
     * This tracks how many bytes the inflater is currently holding on to.
     */
    private int bufferBytesHeldByInflater = 0;
    private boolean closed = false;

    public RealInflaterRawReader(final @NonNull RawReader rawReader, final @NonNull Inflater inflater) {
        this(new RealReader(rawReader), inflater);
    }

    /**
     * This internal constructor shares a buffer with its trusted caller. In general, we can't share a {@code Reader}
     * because the inflater holds input bytes until they are inflated.
     */
    RealInflaterRawReader(final @NonNull RealReader reader, final @NonNull Inflater inflater) {
        assert reader != null;
        assert inflater != null;

        this.reader = reader;
        this.inflater = inflater;
    }

    @Override
    public long readAtMostTo(final @NonNull Buffer destination, final long byteCount) {
        Objects.requireNonNull(destination);

        while (true) {
            final var bytesInflated = readOrInflateAtMostTo(destination, byteCount); // Read or inflate data
            if (bytesInflated > 0) {
                return bytesInflated; // Return if bytes inflated
            }
            if (inflater.finished() || inflater.needsDictionary()) {
                return -1L; // Check if finished or needs dictionary
            }
            if (reader.exhausted()) {
                throw new JayoEOFException("reader exhausted prematurely"); // Handle exhausted reader
            }
        }
    }

    @Override
    public long readOrInflateAtMostTo(final @NonNull Buffer destination, final long byteCount) {
        Objects.requireNonNull(destination);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        if (closed) {
            throw new IllegalStateException("closed");
        }

        if (byteCount == 0L) {
            return 0L;
        }

        final var dst = (RealBuffer) destination;

        // Prepare the destination that we'll write into.
        final var dstTail = dst.writableTail(1);
        final var toRead = (int) Math.min(byteCount, Segment.SIZE - dstTail.limit);

        // Prepare the reader that we'll read from.
        refill();

        final int bytesInflated;
        try {
            // Decompress the inflater's compressed data into the writer.
            bytesInflated = inflater.inflate(dstTail.data, dstTail.limit, toRead);
        } catch (DataFormatException e) {
            throw new JayoException(new IOException(e));
        }

        // Release consumed bytes from the reader.
        releaseBytesAfterInflate();

        // Track produced bytes in the destination.
        if (bytesInflated > 0) {
            dstTail.limit += bytesInflated;
            dst.byteSize += bytesInflated;
            return bytesInflated;
        }

        if (dstTail.pos == dstTail.limit) {
            // We allocated a tail segment, but didn't end up needing it. Recycle!
            dst.head = dstTail.pop();
            SegmentPool.recycle(dstTail);
        }

        return 0L;
    }

    @Override
    public boolean refill() {
        if (!inflater.needsInput()) {
            return false;
        }

        // If there are no further bytes in the reader, we cannot refill.
        if (reader.exhausted()) {
            return true;
        }

        // Assign buffer bytes to the inflater.
        final var head = reader.buffer.head;
        assert head != null;
        bufferBytesHeldByInflater = head.limit - head.pos;
        inflater.setInput(head.data, head.pos, bufferBytesHeldByInflater);

        return false;
    }

    /**
     * When the inflater has processed compressed data, remove it from the buffer.
     */
    private void releaseBytesAfterInflate() {
        if (bufferBytesHeldByInflater == 0) {
            return;
        }
        final var toRelease = bufferBytesHeldByInflater - inflater.getRemaining();
        bufferBytesHeldByInflater -= toRelease;
        reader.skip(toRelease);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        inflater.end();
        closed = true;
        reader.close();
    }

    @Override
    public String toString() {
        return "InflaterRawReader(" + reader + ")";
    }
}
