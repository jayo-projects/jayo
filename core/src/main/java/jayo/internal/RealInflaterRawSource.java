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
import org.jspecify.annotations.Nullable;

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
    private @Nullable Segment currentSegment = null;
    private boolean closed = false;

    public RealInflaterRawSource(final @NonNull RawSource source, final @NonNull Inflater inflater) {
        this(new RealSource(Objects.requireNonNull(source), false), inflater);
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

        final var bytesInflated = new Wrapper.Int();
        // Prepare the destination that we'll write into.
        _sink.segmentQueue.withWritableTail(1, tail -> {
            final var toRead = (int) Math.min(byteCount, Segment.SIZE - tail.limit);

            // Prepare the source that we'll read from.
            refill();
            try {
                // Decompress the inflater's compressed data into the sink.
                bytesInflated.value = inflater.inflate(tail.data, tail.limit, toRead);
            } catch (DataFormatException e) {
                throw new JayoException(new IOException(e));
            }
            // Release consumed bytes from the source.
            releaseBytesAfterInflate();

            // Track produced bytes in the destination.
            if (bytesInflated.value > 0) {
                tail.limit += bytesInflated.value;
            } else {
                bytesInflated.value = 0;
            }
            return true;
        });

        return bytesInflated.value;
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

        unlockCurrentSegmentIfPresent();
        // Assign buffer bytes to the inflater.
        final var head = source.buffer.segmentQueue.lockedReadableHead();
        currentSegment = head;
        bufferBytesHeldByInflater = head.limit - head.pos;
        inflater.setInput(head.data, head.pos, bufferBytesHeldByInflater);
        return false;
    }

    @Override
    public void close() {
        unlockCurrentSegmentIfPresent();
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
        try {
            if (bufferBytesHeldByInflater == 0) {
                return;
            }

            final var toRelease = bufferBytesHeldByInflater - inflater.getRemaining();
            if (currentSegment != null) {
                currentSegment.pos += toRelease;
                if (currentSegment.pos < 0) {
                    throw new RuntimeException();
                }
                source.buffer.segmentQueue.decrementSize(toRelease);

                if (currentSegment.pos == currentSegment.limit) {
                    source.buffer.segmentQueue.removeLockedHead(currentSegment, false);
                    SegmentPool.recycle(currentSegment);
                    currentSegment = null;
                }
            } else {
                source.skip(toRelease);
            }
            bufferBytesHeldByInflater -= toRelease;
        } finally {
            unlockCurrentSegmentIfPresent();
        }
    }

    private void unlockCurrentSegmentIfPresent() {
        if (currentSegment != null) {
            currentSegment.unlock();
            currentSegment = null;
        }
    }
}
