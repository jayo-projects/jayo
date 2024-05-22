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

import static jayo.external.JayoUtils.checkOffsetAndCount;

public final class DeflaterRawSink implements RawSink {
    private final @NonNull RealSink sink;
    private final @NonNull Deflater deflater;
    private boolean closed = false;

    public DeflaterRawSink(final @NonNull RawSink sink, final @NonNull Deflater deflater) {
        this(new RealSink(Objects.requireNonNull(sink), false), deflater);
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
        checkOffsetAndCount(source.byteSize(), 0, byteCount);
        if (!(source instanceof RealBuffer _source)) {
            throw new IllegalArgumentException("source must be an instance of RealBuffer");
        }

        var remaining = byteCount;
        var head = _source.segmentQueue.lockedReadableHead();
        try {
            var finished = false;
            while (!finished) {
                assert head != null;
                // Share bytes from the head segment of 'source' with the deflater.
                final var toDeflate = (int) Math.min(remaining, head.limit - head.pos);
                deflater.setInput(head.data, head.pos, toDeflate);

                // Deflate those bytes into sink.
                deflate(false);

                // Mark those bytes as read.
                head.pos += toDeflate;
                _source.segmentQueue.decrementSize(toDeflate);
                remaining -= toDeflate;
                finished = remaining == 0L;
                if (head.pos == head.limit) {
                    final var oldHead = head;
                    if (finished) {
                        _source.segmentQueue.removeLockedHead(head, false);
                        head = null;
                    } else {
                        head = _source.segmentQueue.removeLockedHead(head, true);
                    }
                    SegmentPool.recycle(oldHead);
                }
            }
        } finally {
            if (head != null) {
                head.unlock();
            }
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
        final var segmentQueue = sink.buffer.segmentQueue;

        var tail = segmentQueue.lockedWritableTail(1);
        try {
            var limit = tail.limit;
            while (true) {
                if (limit == Segment.SIZE) {
                    final var writtenInSegment = (limit - tail.limit);
                    tail.limit = limit;
                    final var newTail = SegmentPool.take();
                    newTail.lock.lock();
                    Segment.NEXT.setVolatile(tail, newTail);
                    segmentQueue.incrementSize(writtenInSegment);
                    tail.unlock();
                    tail = newTail;
                    limit = 0;
                }
                final int deflated;
                try {
                    deflated = deflater.deflate(tail.data, tail.limit, Segment.SIZE - tail.limit,
                            syncFlush ? Deflater.SYNC_FLUSH : Deflater.NO_FLUSH);
                } catch (NullPointerException npe) {
                    throw new JayoException("Deflater already closed", new IOException(npe));
                }

                if (deflated > 0) {
                    limit += deflated;
                    sink.emitCompleteSegments();
                } else if (deflater.needsInput()) {
                    break;
                }
            }
            final var writtenInSegment = (limit - tail.limit);
            tail.limit = limit;
            // tail may be null or a removed segment
            SegmentQueue.TAIL.setVolatile(segmentQueue, tail);
            segmentQueue.incrementSize(writtenInSegment);
        } finally {
            tail.unlock();
        }
    }
}
