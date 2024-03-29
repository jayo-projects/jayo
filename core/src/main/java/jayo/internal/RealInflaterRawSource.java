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

import jayo.Buffer;
import jayo.InflaterRawSource;
import jayo.RawSource;
import jayo.exceptions.JayoEOFException;
import jayo.exceptions.JayoException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class RealInflaterRawSource implements InflaterRawSource {
    private final @NonNull RealSource source;
    private final @NonNull Inflater inflater;
    /**
     * When we call Inflater.setInput(), the inflater keeps our byte array until it needs input again.
     * This tracks how many bytes the inflater is currently holding on to.
     */
    private int bufferBytesHeldByInflater = 0;
    private boolean closed = false;

    public RealInflaterRawSource(final @NonNull RawSource source, final @NonNull Inflater inflater) {
        this(new RealSource(Objects.requireNonNull(source)), inflater);
    }

    /**
     * This internal constructor shares a buffer with its trusted caller. In general, we can't share a
     * {@code Source} because the inflater holds input bytes until they are inflated.
     */
    RealInflaterRawSource(final @NonNull RealSource source, final @NonNull Inflater inflater) {
        this.source = Objects.requireNonNull(source);
        this.inflater = Objects.requireNonNull(inflater);
    }

    @Override
    public long readAtMostTo(final @NonNull Buffer sink, final @NonNegative long byteCount) {
        while (true) {
            final var bytesInflated = readOrInflateAtMostTo(sink, byteCount);
            if (bytesInflated > 0) {
                return bytesInflated;
            }
            if (inflater.finished() || inflater.needsDictionary()) {
                return -1L;
            }
            if (source.exhausted()) {
                throw new JayoEOFException("source exhausted prematurely");
            }
        }
    }

    @Override
    public long readOrInflateAtMostTo(final @NonNull Buffer sink, final @NonNegative long byteCount) {
        Objects.requireNonNull(sink);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        if (!(sink instanceof RealBuffer _sink)) {
            throw new IllegalArgumentException("sink must be an instance of RealBuffer");
        }
        if (closed) {
            throw new IllegalStateException("closed");
        }
        if (byteCount == 0L) {
            return 0L;
        }

        try {
            // Prepare the destination that we'll write into.
            final var tail = _sink.segmentQueue.writableSegment(1);
            final var currentLimit = tail.limit;
            final var toRead = (int) Math.min(byteCount, Segment.SIZE - currentLimit);
            final var isNewTail = currentLimit == 0;

            // Prepare the source that we'll read from.
            refill();

            // Decompress the inflater's compressed data into the sink.
            final var bytesInflated = inflater.inflate(tail.data, currentLimit, toRead);

            // Release consumed bytes from the source.
            releaseBytesAfterInflate();

            // Track produced bytes in the destination.
            if (bytesInflated > 0) {
                tail.limit = currentLimit + bytesInflated;
                if (isNewTail) {
                    _sink.segmentQueue.addTail(tail);
                }
                _sink.segmentQueue.incrementSize(bytesInflated);
                return bytesInflated;
            }

            // We allocated a tail segment, but didn't end up needing it. Recycle!
            if (isNewTail) {
                SegmentPool.recycle(tail);
            }

            return 0L;
        } catch (DataFormatException e) {
            throw new JayoException(new IOException(e));
        }
    }

    @Override
    public boolean refill() {
        if (!inflater.needsInput()) {
            return false;
        }

        // If there are no further bytes in the source, we cannot refill.
        if (source.exhausted()) {
            return true;
        }

        // Assign buffer bytes to the inflater.
        final var head = source.buffer.segmentQueue.head();
        assert head != null;
        final var currentPos = head.pos;
        bufferBytesHeldByInflater = head.limit - currentPos;
        inflater.setInput(head.data, currentPos, bufferBytesHeldByInflater);
        return false;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        inflater.end();
        closed = true;
        source.close();
    }

    @Override
    public String toString() {
        return "InflaterRawSource(" + source + ")";
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
        source.skip(toRelease);
    }
}
