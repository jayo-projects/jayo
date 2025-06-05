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
import jayo.JayoException;
import jayo.RawWriter;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.Objects;
import java.util.zip.Deflater;

import static jayo.tools.JayoUtils.checkOffsetAndCount;

public final class DeflaterRawWriter implements RawWriter {
    private static final byte @NonNull [] EMPTY_BYTE_ARRAY = new byte[0];

    private final @NonNull RealWriter writer;
    private final @NonNull Deflater deflater;
    private boolean closed = false;

    public DeflaterRawWriter(final @NonNull RawWriter rawWriter, final @NonNull Deflater deflater) {
        assert rawWriter != null;
        assert deflater != null;

        this.writer = new RealWriter(rawWriter);
        this.deflater = deflater;
    }

    @Override
    public void write(final @NonNull Buffer source, final long byteCount) {
        Objects.requireNonNull(source);
        checkOffsetAndCount(source.bytesAvailable(), 0, byteCount);

        final var src = (RealBuffer) source;
        var remaining = byteCount;
        while (remaining > 0L) {
            // Share bytes from the head segment of 'reader' with the deflater.
            final var head = src.head;
            assert head != null;
            final var toDeflate = (int) Math.min(remaining, head.limit - head.pos);
            deflater.setInput(head.data, head.pos, toDeflate);

            // Deflate those bytes into writer.
            deflate(false);

            // Mark those bytes as read.
            head.pos += toDeflate;
            src.byteSize -= toDeflate;
            remaining -= toDeflate;

            if (head.pos == head.limit) {
                src.head = head.pop();
                SegmentPool.recycle(head);
            }
        }

        // Deflater still holds a reference to the most recent segment's byte array. That can cause problems in JNI,
        // so clear it now.
        deflater.setInput(EMPTY_BYTE_ARRAY, 0, 0);
    }

    private void deflate(final boolean syncFlush) {
        final var dstBuffer = writer.buffer;
        while (true) {
            final var dstTail = dstBuffer.writableTail(1);
            final int deflated;
            try {
                deflated = deflater.deflate(dstTail.data, dstTail.limit, Segment.SIZE - dstTail.limit,
                        syncFlush ? Deflater.SYNC_FLUSH : Deflater.NO_FLUSH);
            } catch (NullPointerException npe) {
                throw new JayoException("Deflater already closed", new IOException(npe));
            }

            if (deflated > 0) {
                dstTail.limit += deflated;
                dstBuffer.byteSize += deflated;
                writer.emitCompleteSegments();
            } else if (deflater.needsInput()) {
                if (dstTail.pos == dstTail.limit) {
                    // We allocated a tail segment, but didn't end up needing it. Recycle!
                    dstBuffer.head = dstTail.pop();
                    SegmentPool.recycle(dstTail);
                }
                return;
            }
        }
    }

    @Override
    public void flush() {
        deflate(true);
        writer.flush();
    }

    void finishDeflate() {
        deflater.finish();
        deflate(false);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        // Emit deflated data to the underlying writer. If this fails, we still need to close the deflater and the
        // writer; otherwise, we risk leaking resources.
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
            writer.close();
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
        return "DeflaterRawWriter(" + writer + ")";
    }
}
