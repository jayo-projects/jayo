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

import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.Inflater;

/**
 * A raw source that uses <a href="http://www.ietf.org/rfc/rfc1952.txt">GZIP</a> to decompress data read from another
 * source.
 */
public final class GzipRawSource implements RawSource {
    /**
     * The current section. Always progresses forward.
     */
    private byte section = SECTION_HEADER;

    /**
     * Our source should yield a GZIP header (which we consume directly), followed by deflated bytes (which we consume
     * via an InflaterSource), followed by a GZIP trailer (which we also consume directly).
     */
    private final @NonNull RealSource source;

    /**
     * The inflater used to decompress the deflated body.
     */
    private final @NonNull Inflater inflater = new Inflater(true);

    /**
     * The inflater source takes care of moving data between compressed source and decompressed sink buffers.
     */
    private final @NonNull InflaterRawSource inflaterSource;

    /**
     * Checksum used to check both the GZIP header and decompressed body.
     */
    private final @NonNull CRC32 crc = new CRC32();

    public GzipRawSource(final @NonNull RawSource source) {
        Objects.requireNonNull(source);
        this.source = new RealSource(source, false);
        inflaterSource = new RealInflaterRawSource(this.source, inflater);
    }

    @Override
    public long readAtMostTo(final @NonNull Buffer sink, final @NonNegative long byteCount) {
        Objects.requireNonNull(sink);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        if (!(sink instanceof RealBuffer _sink)) {
            throw new IllegalArgumentException("sink must be an instance of RealBuffer");
        }
        if (byteCount == 0L) {
            return 0L;
        }

        // If we haven't consumed the header, we must consume it before anything else.
        if (section == SECTION_HEADER) {
            consumeHeader();
            section = SECTION_BODY;
        }

        // Attempt to read at least a byte of the body. If we do, we're done.
        if (section == SECTION_BODY) {
            final var offset = _sink.byteSize();
            final var result = inflaterSource.readAtMostTo(_sink, byteCount);
            if (result != -1L) {
                updateCrc(_sink, offset, result);
                return result;
            }
            section = SECTION_TRAILER;
        }

        // The body is exhausted; time to read the trailer. We always consume the trailer before returning a -1
        // exhausted result; that way if you read to the end of a GzipSource you guarantee that the CRC has been
        // checked.
        if (section == SECTION_TRAILER) {
            consumeTrailer();
            section = SECTION_DONE;

            // Gzip streams self-terminate: they return -1 before their underlying source returns -1. Here we attempt to
            // force the underlying stream to return -1 which may trigger it to release its resources. If it doesn't
            // return -1, then our Gzip data finished prematurely!
            if (!source.exhausted()) {
                throw new JayoException("gzip finished without exhausting source");
            }
        }

        return -1L;
    }

    @Override
    public void close() {
        inflaterSource.close();
    }

    private void consumeHeader() {
        // Read the 10-byte header. We peek at the flags byte first, so we know if we need to CRC the entire header.
        // Then we read the magic ID1ID2 sequence. We can skip everything else in the first 10 bytes.
        // +---+---+---+---+---+---+---+---+---+---+
        // |ID1|ID2|CM |FLG|     MTIME     |XFL|OS | (more-->)
        // +---+---+---+---+---+---+---+---+---+---+
        source.require(10);
        final var flags = (int) source.buffer.getByte(3);
        final var fhcrc = getBit(flags, FHCRC);
        if (fhcrc) {
            updateCrc(source.buffer, 0, 10);
        }

        final var id1id2 = (int) source.readShort();
        checkEqual("ID1ID2", 0x1f8b, id1id2);
        source.skip(8);

        // Skip optional extra fields.
        // +---+---+=================================+
        // | XLEN  |...XLEN bytes of "extra field"...| (more-->)
        // +---+---+=================================+
        if (getBit(flags, FEXTRA)) {
            source.require(2);
            if (fhcrc) {
                updateCrc(source.buffer, 0, 2);
            }
            final var xlen = (long) (((int) Short.reverseBytes(source.buffer.readShort())) & 0xffff);
            source.require(xlen);
            if (fhcrc) {
                updateCrc(source.buffer, 0, xlen);
            }
            source.skip(xlen);
        }

        // Skip an optional 0-terminated name.
        // +=========================================+
        // |...original file name, zero-terminated...| (more-->)
        // +=========================================+
        if (getBit(flags, FNAME)) {
            final var index = source.indexOf((byte) 0);
            if (index == -1L) {
                throw new JayoEOFException();
            }
            if (fhcrc) {
                updateCrc(source.buffer, 0, index + 1);
            }
            source.skip(index + 1);
        }

        // Skip an optional 0-terminated comment.
        // +===================================+
        // |...file comment, zero-terminated...| (more-->)
        // +===================================+
        if (getBit(flags, FCOMMENT)) {
            final var index = source.indexOf((byte) 0);
            if (index == -1L) {
                throw new JayoEOFException();
            }
            if (fhcrc) {
                updateCrc(source.buffer, 0, index + 1);
            }
            source.skip(index + 1);
        }

        // Confirm the optional header CRC.
        // +---+---+
        // | CRC16 |
        // +---+---+
        if (fhcrc) {
            checkEqual("FHCRC", Short.reverseBytes(source.readShort()), (short) crc.getValue());
            crc.reset();
        }
    }

    private void consumeTrailer() {
        // Read the eight-byte trailer. Confirm the body's CRC and size.
        // +---+---+---+---+---+---+---+---+
        // |     CRC32     |     ISIZE     |
        // +---+---+---+---+---+---+---+---+
        checkEqual("CRC", Integer.reverseBytes(source.readInt()), (int) crc.getValue());
        checkEqual("ISIZE", Integer.reverseBytes(source.readInt()), (int) inflater.getBytesWritten());
    }

    private void updateCrc(final @NonNull RealBuffer buffer,
                           final @NonNegative long offset,
                           final @NonNegative long byteCount) {
        var _offset = offset;
        var _byteCount = byteCount;
        // Skip segments that we aren't checksumming.
        var segment = buffer.segmentQueue.head();
        assert segment != null;
        while (_offset >= segment.limit - segment.pos) {
            _offset -= (segment.limit - segment.pos);
            segment = segment.next();
            assert segment != null;
        }

        // Checksum one segment at a time.
        while (_byteCount > 0) {
            assert segment != null;
            final var pos = (int) (segment.pos + _offset);
            final var toUpdate = (int) Math.min(segment.limit - pos, _byteCount);
            crc.update(segment.data, pos, toUpdate);
            _byteCount -= toUpdate;
            _offset = 0;
            segment = segment.next();
        }
    }

    private void checkEqual(final @NonNull String name, final int expected, final int actual) {
        if (actual != expected) {
            throw new JayoException(
                    name + ": " +
                            "actual 0x" + Utils.toHexString(actual)/*.padStart(8, '0')*/ + " != " +
                            "expected 0x" + Utils.toHexString(expected)/*.padStart(8, '0')*/
            );
        }
    }

    private static boolean getBit(int flags, int bit) {
        return ((flags >> bit) & 1) == 1;
    }

    private static final int FHCRC = 1;
    private static final int FEXTRA = 2;
    private static final int FNAME = 3;
    private static final int FCOMMENT = 4;

    private static final byte SECTION_HEADER = 0;
    private static final byte SECTION_BODY = 1;
    private static final byte SECTION_TRAILER = 2;
    private static final byte SECTION_DONE = 3;
}
