/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
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
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static java.util.zip.Deflater.DEFAULT_COMPRESSION;

/**
 * A raw writer that uses <a href="http://www.ietf.org/rfc/rfc1952.txt">GZIP</a> to compress written data to another
 * writer.
 * <h3>Sync flush</h3>
 * Aggressive flushing of this stream may result in reduced compression. Each call to {@link #flush()} immediately
 * compresses all currently buffered data; this early compression may be less effective than compression performed
 * without flushing.
 * <p>
 * This is equivalent to using {@link Deflater} with the sync flush option. This class does not offer any partial flush
 * mechanism. For the best performance, only call {@link #flush()} when application behavior requires it.
 */
public final class GzipRawWriter implements RawWriter {
    /**
     * Writer into which the GZIP format is written.
     */
    private final @NonNull RealWriter writer;

    /**
     * The deflater used to compress the body.
     */
    private final @NonNull Deflater deflater = new Deflater(DEFAULT_COMPRESSION, true /* No wrap */);

    /**
     * The deflater writer takes care of moving data between decompressed source and compressed writer buffers.
     */
    private final @NonNull DeflaterRawWriter deflaterWriter;

    private boolean closed = false;

    /**
     * Checksum calculated for the compressed body.
     */
    private final @NonNull CRC32 crc = new CRC32();

    public GzipRawWriter(final @NonNull RawWriter rawWriter) {
        assert rawWriter != null;
        this.writer = new RealWriter(rawWriter);

        // Write the Gzip header directly into the buffer for the sink to avoid handling JayoException.
        final var writerBuffer = writer.buffer;
        writerBuffer.writeShort((short) 0x1f8b); // Two-byte Gzip ID.
        writerBuffer.writeByte((byte) 0x08); // 8 == Deflate compression method.
        writerBuffer.writeByte((byte) 0x00); // No flags.
        writerBuffer.writeInt(0x00); // No modification time.
        writerBuffer.writeByte((byte) 0x00); // No extra flags.
        writerBuffer.writeByte((byte) 0x00); // No OS.

        deflaterWriter = new DeflaterRawWriter(writer, deflater);
    }

    @Override
    public void write(final @NonNull Buffer source, final long byteCount) {
        Objects.requireNonNull(source);
        if (byteCount < 0) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }

        if (byteCount == 0L) {
            return;
        }

        updateCrc((RealBuffer) source, byteCount);
        deflaterWriter.write(source, byteCount);
    }

    /**
     * Updates the CRC with the given bytes.
     */
    private void updateCrc(final @NonNull RealBuffer src, final long byteCount) {
        assert src != null;

        var srcHead = src.head;
        assert srcHead != null;
        var remaining = byteCount;
        while (remaining > 0) {
            final var segmentLength = (int) Math.min(remaining, srcHead.limit - srcHead.pos);
            crc.update(srcHead.data, srcHead.pos, segmentLength);
            remaining -= segmentLength;
            srcHead = srcHead.next;
            assert srcHead != null;
        }
    }

    @Override
    public void flush() {
        deflaterWriter.flush();
    }

    @Override
    public void close() {
        if (closed) return;

        // This method delegates to the DeflaterSink for finishing the deflate process
        // but keeps responsibility for releasing the deflater's resources. This is
        // necessary because writeFooter needs to query the processed byte count which
        // only works when the deflater is still open.
        Throwable thrown = null;
        try {
            deflaterWriter.finishDeflate();
            writeFooter();
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

    private void writeFooter() {
        writer.writeInt(Integer.reverseBytes((int) crc.getValue()));          // CRC of original data.
        writer.writeInt(Integer.reverseBytes((int) deflater.getBytesRead())); // Length of original data.
    }
}
