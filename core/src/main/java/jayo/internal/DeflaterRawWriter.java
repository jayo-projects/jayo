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
import jayo.RawWriter;
import jayo.exceptions.JayoException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.Objects;
import java.util.zip.Deflater;

import static jayo.external.JayoUtils.checkOffsetAndCount;
import static jayo.internal.WriterSegmentQueue.newWriterSegmentQueue;

public final class DeflaterRawWriter implements RawWriter {
    private final @NonNull WriterSegmentQueue segmentQueue;
    private final @NonNull Deflater deflater;
    private boolean closed = false;

    public DeflaterRawWriter(final @NonNull RawWriter writer, final @NonNull Deflater deflater) {
        Objects.requireNonNull(writer);
        this.segmentQueue = newWriterSegmentQueue(writer, false);
        this.deflater = Objects.requireNonNull(deflater);
    }

    @Override
    public void write(final @NonNull Buffer reader, final @NonNegative long byteCount) {
        Objects.requireNonNull(reader);
        checkOffsetAndCount(reader.byteSize(), 0, byteCount);
        if (!(reader instanceof RealBuffer _reader)) {
            throw new IllegalArgumentException("reader must be an instance of RealBuffer");
        }

        var remaining = byteCount;
        var head = _reader.segmentQueue.head();
        assert head != null;
        while (remaining > 0L) {
            var headLimit = head.limitVolatile();
            if (head.pos == headLimit) {
                if (!head.tryRemove()) {
                    throw new IllegalStateException("Non tail segment must be removable");
                }
                final var oldHead = head;
                head = _reader.segmentQueue.removeHead(head);
                assert head != null;
                headLimit = head.limitVolatile();
                SegmentPool.recycle(oldHead);
            }

            // Share bytes from the head segment of 'reader' with the deflater.
            final var toDeflate = (int) Math.min(remaining, headLimit - head.pos);
            deflater.setInput(head.data, head.pos, toDeflate);

            // Deflate those bytes into writer.
            deflate(false);

            // Mark those bytes as read.
            head.pos += toDeflate;
            _reader.segmentQueue.decrementSize(toDeflate);
            remaining -= toDeflate;
        }
        if (head.pos == head.limitVolatile() && head.tryRemove() && head.validateRemove()) {
            _reader.segmentQueue.removeHead(head);
            SegmentPool.recycle(head);
        }
    }

    @Override
    public void flush() {
        deflate(true);
        segmentQueue.emit(true);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        // Emit deflated data to the underlying writer. If this fails, we still need to close the deflater and the
        // segment queue; otherwise we risk leaking resources.
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
            segmentQueue.close();
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
        return "DeflaterRawWriter(" + segmentQueue + ")";
    }

    void finishDeflate() {
        deflater.finish();
        deflate(false);
    }

    private void deflate(final boolean syncFlush) {
        final var segmentQueue = this.segmentQueue.buffer.segmentQueue;

        var continueLoop = true;
        while (continueLoop) {
            continueLoop = segmentQueue.withWritableTail(1, tail -> {
                final int deflated;
                try {
                    deflated = deflater.deflate(tail.data, tail.limit(), Segment.SIZE - tail.limit(),
                            syncFlush ? Deflater.SYNC_FLUSH : Deflater.NO_FLUSH);
                } catch (NullPointerException npe) {
                    throw new JayoException("Deflater already closed", new IOException(npe));
                }

                if (deflated > 0) {
                    tail.incrementLimitVolatile(deflated);
                    this.segmentQueue.emitCompleteSegments();
                    return true;
                } else {
                    return !deflater.needsInput();
                }
            });
        }
    }
}
