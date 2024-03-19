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
import jayo.RawSink;
import jayo.exceptions.JayoException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.Objects;
import java.util.zip.Deflater;

import static jayo.internal.Utils.checkOffsetAndCount;

public final class DeflaterRawSink implements RawSink {
    private final @NonNull RealSink sink;
    private final @NonNull Deflater deflater;
    private boolean closed = false;

    public DeflaterRawSink(final @NonNull RawSink sink, final @NonNull Deflater deflater) {
        this(new RealSink(Objects.requireNonNull(sink)), deflater);
    }

    /**
     * This internal constructor shares a buffer with its trusted caller.
     */
    DeflaterRawSink(final @NonNull RealSink sink, final @NonNull Deflater deflater) {
        this.sink = Objects.requireNonNull(sink);
        this.deflater = Objects.requireNonNull(deflater);
    }

    @Override
    public void write(final @NonNull Buffer source, final @NonNegative long byteCount) {
        Objects.requireNonNull(source);
        checkOffsetAndCount(source.getSize(), 0, byteCount);
        if (!(source instanceof RealBuffer _source)) {
            throw new IllegalArgumentException("source must be an instance of RealBuffer");
        }

        var remaining = byteCount;
        while (remaining > 0) {
            // Share bytes from the head segment of 'source' with the deflater.
            final var head = _source.segmentQueue.head();
            assert head != null;
            var pos = head.pos;
            final var currentLimit = head.limit;
            final var toDeflate = (int) Math.min(remaining, currentLimit - pos);
            deflater.setInput(head.data, pos, toDeflate);

            // Deflate those bytes into sink.
            deflate(false);

            // Mark those bytes as read.
            _source.segmentQueue.decrementSize(toDeflate);
            pos += toDeflate;
            head.pos = pos;
            if (pos == currentLimit) {
                SegmentPool.recycle(_source.segmentQueue.removeHead());
            }

            remaining -= toDeflate;
        }
    }

    @Override
    public void flush() {
        deflate(true);
        sink.flush();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        // Emit deflated data to the underlying sink. If this fails, we still need to close the deflater and the sink;
        // otherwise we risk leaking resources.
        Throwable thrown = null;
        try {
            finishDeflate();
        } catch (Throwable e) {
            thrown = e;
        }

        try {
            deflater.end();
        } catch (Throwable e) {
            if (thrown == null) {
                thrown = e;
            }
        }

        try {
            sink.close();
        } catch (Throwable e) {
            if (thrown == null) {
                thrown = e;
            }
        }

        closed = true;

        if (thrown != null) {
            if (thrown instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw (Error) thrown;
        }
    }

    @Override
    public String toString() {
        return "DeflaterRawSink(" + sink + ")";
    }

    void finishDeflate() {
        deflater.finish();
        deflate(false);
    }

    private void deflate(final boolean syncFlush) {
        final var buffer = sink.buffer;
        while (true) {
            final var tail = buffer.segmentQueue.writableSegment(1);
            final var currentLimit = tail.limit;
            final var isNewTail = currentLimit == 0;

            final int deflated;
            try {
                if (syncFlush) {
                    deflated = deflater.deflate(tail.data, currentLimit, Segment.SIZE - currentLimit,
                            Deflater.SYNC_FLUSH);
                } else {
                    deflated = deflater.deflate(tail.data, currentLimit, Segment.SIZE - currentLimit);
                }
            } catch (NullPointerException npe) {
                throw new JayoException("Deflater already closed", new IOException(npe));
            }

            if (deflated > 0) {
                tail.limit = currentLimit + deflated;
                if (isNewTail) {
                    buffer.segmentQueue.addTail(tail);
                }
                buffer.segmentQueue.incrementSize(deflated);
                sink.emitCompleteSegments();
            } else if (deflater.needsInput()) {
                // We allocated a tail segment, but didn't end up needing it. Recycle!
                if (isNewTail) {
                    SegmentPool.recycle(tail);
                }
                return;
            }
        }
    }
}
