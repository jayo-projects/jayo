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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import jayo.*;
import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import jayo.exceptions.JayoEOFException;
import jayo.exceptions.JayoException;
import jayo.external.NonNegative;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.function.BiFunction;

import static jayo.internal.UnsafeUtils.*;
import static jayo.internal.Utils.*;

public final class RealBuffer implements Buffer {
    final @NonNull SegmentQueue segmentQueue;

    public RealBuffer() {
        this(new SegmentQueue());
    }

    RealBuffer(final @NonNull SegmentQueue segmentQueue) {
        this.segmentQueue = Objects.requireNonNull(segmentQueue);
    }

    @Override
    public @NonNegative long getSize() {
        return segmentQueue.size();
    }

    @Override
    public @NonNull OutputStream asOutputStream() {
        return new OutputStream() {
            @Override
            public void write(final int b) {
                writeByte((byte) b);
            }

            @Override
            public void write(final byte @NonNull [] data, final int offset, final int byteCount) {
                RealBuffer.this.write(data, offset, byteCount);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }

            @Override
            public String toString() {
                return RealBuffer.this + ".asOutputStream()";
            }
        };
    }

    @Override
    public @NonNull Buffer emitCompleteSegments() {
        return this; // Nowhere to emit to!
    }

    @Override
    public @NonNull Buffer emit() {
        return this; // Nowhere to emit to!
    }

    @Override
    public boolean exhausted() {
        return segmentQueue.size() == 0L;
    }

    @Override
    public void require(final @NonNegative long byteCount) {
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0L: " + byteCount);
        }
        if (segmentQueue.size() < byteCount) {
            throw new JayoEOFException();
        }
    }

    @Override
    public boolean request(final @NonNegative long byteCount) {
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0L: " + byteCount);
        }
        return segmentQueue.size() >= byteCount;
    }

    @Override
    public @NonNull Source peek() {
        return new RealSource(new PeekRawSource(this));
    }

    @Override
    public @NonNull InputStream asInputStream() {
        return new InputStream() {
            @Override
            public int read() {
                if (segmentQueue.size() > 0L) {
                    return readByte() & 0xff;
                }
                return -1;
            }

            @Override
            public int read(final byte @NonNull [] sink, final int offset, final int byteCount) {
                return RealBuffer.this.readAtMostTo(sink, offset, byteCount);
            }

            @Override
            public int available() {
                return (int) Math.min(segmentQueue.size(), Integer.MAX_VALUE);
            }

            @Override
            public void close() {
            }

            @Override
            public String toString() {
                return RealBuffer.this + ".asInputStream()";
            }
        };
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull OutputStream out) {
        return copyTo(out, 0L);
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull OutputStream out, final @NonNegative long offset) {
        return copyTo(out, offset, segmentQueue.size() - offset);
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull OutputStream out,
                                  final @NonNegative long offset,
                                  final @NonNegative long byteCount) {
        Objects.requireNonNull(out);
        checkOffsetAndCount(segmentQueue.size(), offset, byteCount);
        if (byteCount == 0L) {
            return this;
        }

        var _offset = offset;
        var _byteCount = byteCount;

        // Skip segments that we aren't copying from.
        var s = segmentQueue.head();
        assert s != null;
        var segmentSize = s.limit - s.pos;
        while (_offset >= segmentSize) {
            _offset -= segmentSize;
            s = s.next;
            segmentSize = s.limit - s.pos;
        }

        // Copy from one segment at a time.
        while (_byteCount > 0L) {
            final var pos = (int) (s.pos + _offset);
            final var toCopy = (int) Math.min(s.limit - pos, _byteCount);
            try {
                out.write(s.data, pos, toCopy);
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
            _byteCount -= toCopy;
            _offset = 0L;
            s = s.next;
        }
        return this;
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull Buffer out) {
        return copyTo(out, 0L);
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull Buffer out, final @NonNegative long offset) {
        return copyTo(out, offset, segmentQueue.size() - offset);
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull Buffer out,
                                  final @NonNegative long offset,
                                  final @NonNegative long byteCount) {
        checkOffsetAndCount(segmentQueue.size(), offset, byteCount);
        if (!(Objects.requireNonNull(out) instanceof RealBuffer _out)) {
            throw new IllegalArgumentException("out must be an instance of JayoBuffer");
        }
        if (byteCount == 0L) {
            return this;
        }

        var _offset = offset;

        // Skip segments that we aren't copying from.
        var s = segmentQueue.head();
        assert s != null;
        var segmentSize = s.limit - s.pos;
        while (_offset >= segmentSize) {
            _offset -= segmentSize;
            s = s.next;
            segmentSize = s.limit - s.pos;
        }

        var _byteCount = byteCount;
        // Copy from one segment at a time.
        while (_byteCount > 0L) {
            final var sCopy = s.sharedCopy();
            var pos = sCopy.pos;
            pos += (int) _offset;
            sCopy.pos = pos;
            var limit = sCopy.limit;
            limit = Math.min(pos + (int) _byteCount, limit);
            sCopy.limit = limit;
            final var written = limit - pos;
            _out.segmentQueue.addTail(sCopy);
            _out.segmentQueue.incrementSize(written);
            _byteCount -= written;
            _offset = 0L;
            s = s.next;
        }
        return this;
    }

    @Override
    public @NonNull Buffer readTo(final @NonNull OutputStream out) {
        return readTo(out, segmentQueue.size());
    }

    @Override
    public @NonNull Buffer readTo(final @NonNull OutputStream out, final @NonNegative long byteCount) {
        Objects.requireNonNull(out);
        checkOffsetAndCount(segmentQueue.size(), 0L, byteCount);
        if (byteCount == 0L) {
            return this;
        }

        var _byteCount = byteCount;
        var head = segmentQueue.head();

        while (_byteCount > 0L) {
            assert head != null;
            var pos = head.pos;
            final var toCopy = (int) Math.min(_byteCount, head.limit - pos);
            try {
                out.write(head.data, pos, toCopy);
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }

            pos += toCopy;
            segmentQueue.decrementSize(toCopy);
            head.pos = pos;
            if (pos == head.limit) {
                SegmentPool.recycle(segmentQueue.removeHead());
                head = segmentQueue.head();
            }
            _byteCount -= toCopy;
        }

        return this;
    }

    @Override
    public @NonNull Buffer transferFrom(final @NonNull InputStream input) {
        write(input, Long.MAX_VALUE, true);
        return this;
    }

    @Override
    public @NonNull Buffer write(final @NonNull InputStream input, final @NonNegative long byteCount) {
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0 : " + byteCount);
        }
        write(input, byteCount, false);
        return this;
    }

    private void write(final @NonNull InputStream in,
                       final @NonNegative long byteCount,
                       final boolean forever) {
        Objects.requireNonNull(in);
        var _byteCount = byteCount;

        while (_byteCount > 0L || forever) {
            final var tail = segmentQueue.writableSegment(1);
            final var currentLimit = tail.limit;
            final var toRead = (int) Math.min(_byteCount, Segment.SIZE - currentLimit);
            final var isNewTail = currentLimit == 0;
            final int bytesRead;
            try {
                bytesRead = in.read(tail.data, currentLimit, toRead);
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
            if (bytesRead == -1) {
                if (isNewTail) {
                    // We allocated a tail segment, but didn't end up needing it. Recycle!
                    SegmentPool.recycle(tail);
                }
                if (forever) {
                    return;
                }
                throw new JayoEOFException();
            }
            tail.limit = currentLimit + bytesRead;
            if (isNewTail) {
                segmentQueue.addTail(tail);
            }
            segmentQueue.incrementSize(bytesRead);
            _byteCount -= bytesRead;
        }
    }

    @Override
    public @NonNegative long completeSegmentByteCount() {
        var result = segmentQueue.size();
        if (result == 0L) {
            return 0L;
        }

        // Omit the tail if it's still writable.
        final var tail = segmentQueue.tail();
        assert tail != null;
        final var limit = tail.limit;
        if (tail.owner && limit < Segment.SIZE) {
            result -= (limit - tail.pos);
        }

        return result;
    }

    @Override
    public byte get(final @NonNegative long pos) {
        checkOffsetAndCount(segmentQueue.size(), pos, 1L);
        return seek(pos, (s, offset) -> s.data[(int) (s.pos + pos - offset)]);
    }

    @Override
    public byte readByte() {
        final var head = segmentQueue.head();
        if (head == null) {
            throw new JayoEOFException();
        }
        var pos = head.pos;

        final var b = head.data[pos++];
        segmentQueue.decrementSize(1L);
        head.pos = pos;

        if (pos == head.limit) {
            SegmentPool.recycle(segmentQueue.removeHead());
        }

        return b;
    }

    @Override
    public short readShort() {
        if (segmentQueue.size() < 2L) {
            throw new JayoEOFException();
        }

        final var head = segmentQueue.head();
        assert head != null;
        var pos = head.pos;

        // If the short is split across multiple segments, delegate to readByte().
        if (head.limit - pos < 2) {
            final var s = ((readByte() & 0xff) << 8) | (readByte() & 0xff);
            return (short) s;
        }

        final var s = (short) (((head.data[pos++] & 0xff) << 8) | (head.data[pos++] & 0xff));
        segmentQueue.decrementSize(2L);
        head.pos = pos;

        if (pos == head.limit) {
            SegmentPool.recycle(segmentQueue.removeHead());
        }

        return s;
    }

    @Override
    public int readInt() {
        if (segmentQueue.size() < 4L) {
            throw new JayoEOFException();
        }

        final var head = segmentQueue.head();
        assert head != null;
        var pos = head.pos;

        // If the int is split across multiple segments, delegate to readByte().
        if (head.limit - pos < 4L) {
            return (
                    ((readByte() & 0xff) << 24)
                            | ((readByte() & 0xff) << 16)
                            | ((readByte() & 0xff) << 8)
                            | (readByte() & 0xff)
            );
        }

        final var i = (
                ((head.data[pos++] & 0xff) << 24)
                        | ((head.data[pos++] & 0xff) << 16)
                        | ((head.data[pos++] & 0xff) << 8)
                        | (head.data[pos++] & 0xff)
        );
        segmentQueue.decrementSize(4L);
        head.pos = pos;

        if (pos == head.limit) {
            SegmentPool.recycle(segmentQueue.removeHead());
        }

        return i;
    }

    @Override
    public long readLong() {
        if (segmentQueue.size() < 8L) {
            throw new JayoEOFException();
        }

        final var head = segmentQueue.head();
        assert head != null;
        var pos = head.pos;

        // If the long is split across multiple segments, delegate to readByte().
        if (head.limit - pos < 8L) {
            return (((readByte() & 0xffL) << 56)
                    | ((readByte() & 0xffL) << 48)
                    | ((readByte() & 0xffL) << 40)
                    | ((readByte() & 0xffL) << 32)
                    | ((readByte() & 0xffL) << 24)
                    | ((readByte() & 0xffL) << 16)
                    | ((readByte() & 0xffL) << 8)
                    | (readByte() & 0xffL));
        }

        final var l = (((head.data[pos++] & 0xffL) << 56)
                | ((head.data[pos++] & 0xffL) << 48)
                | ((head.data[pos++] & 0xffL) << 40)
                | ((head.data[pos++] & 0xffL) << 32)
                | ((head.data[pos++] & 0xffL) << 24)
                | ((head.data[pos++] & 0xffL) << 16)
                | ((head.data[pos++] & 0xffL) << 8)
                | (head.data[pos++] & 0xffL));
        segmentQueue.decrementSize(8L);
        head.pos = pos;

        if (pos == head.limit) {
            SegmentPool.recycle(segmentQueue.removeHead());
        }

        return l;
    }

    @Override
    public long readDecimalLong() {
        var head = segmentQueue.head();
        if (head == null) {
            throw new JayoEOFException();
        }

        // This value is always built negatively in order to accommodate Long.MIN_VALUE.
        var value = 0L;
        var seen = 0;
        var negative = false;
        var done = false;

        var overflowDigit = OVERFLOW_DIGIT_START;
        do {
            final var data = head.data;
            var pos = head.pos;
            final var limit = head.limit;

            while (pos < limit) {
                final var b = data[pos];
                if (b >= (byte) ((int) '0') && b <= (byte) ((int) '9')) {
                    final var digit = (byte) ((int) '0') - b;

                    // Detect when the digit would cause an overflow.
                    if (value < OVERFLOW_ZONE || value == OVERFLOW_ZONE && digit < overflowDigit) {
                        try (final var buffer = new RealBuffer()) {
                            buffer.writeDecimalLong(value).writeByte(b);
                            if (!negative) {
                                buffer.readByte(); // Skip negative sign.
                            }
                            throw new NumberFormatException("Number too large: " + buffer.readUtf8());
                        }
                    }
                    value *= 10L;
                    value += digit;
                    pos++;
                    segmentQueue.decrementSize(1L);
                    head.pos = pos;
                } else if (b == (byte) ((int) '-') && seen == 0) {
                    negative = true;
                    overflowDigit -= 1;
                    pos++;
                    segmentQueue.decrementSize(1L);
                    head.pos = pos;
                } else {
                    // Set a flag to stop iteration. We still need to run through segment updating below.
                    done = true;
                    break;
                }
                seen++;
            }
            if (pos == head.limit) {
                SegmentPool.recycle(segmentQueue.removeHead());
                head = segmentQueue.head();
            }
        } while (!done && head != null);

        final var minimumSeen = (negative) ? 2 : 1;
        if (seen < minimumSeen) {
            if (segmentQueue.size() == 0L) {
                throw new JayoEOFException();
            }
            final var expected = (negative) ? "Expected a digit" : "Expected a digit or '-'";
            throw new NumberFormatException(expected + " but was 0x" + toHexString(get(0)));
        }

        return (negative) ? value : -value;
    }

    @Override
    public long readHexadecimalUnsignedLong() {
        var head = segmentQueue.head();
        if (head == null) {
            throw new JayoEOFException();
        }

        // This value is always built negatively in order to accommodate Long.MIN_VALUE.
        var value = 0L;
        var seen = 0;
        var done = false;
        do {
            final var data = head.data;
            var pos = head.pos;
            final var limit = head.limit;

            while (pos < limit) {
                final int digit;

                final var b = data[pos];
                if (b >= (byte) ((int) '0') && b <= (byte) ((int) '9')) {
                    digit = b - (byte) ((int) '0');
                } else if (b >= (byte) ((int) 'a') && b <= (byte) ((int) 'f')) {
                    digit = b - (byte) ((int) 'a') + 10;
                } else if (b >= (byte) ((int) 'A') && b <= (byte) ((int) 'F')) {
                    digit = b - (byte) ((int) 'A') + 10; // We never write uppercase, but we support reading it.
                } else {
                    if (seen == 0) {
                        throw new NumberFormatException(
                                "Expected leading [0-9a-fA-F] character but was 0x" + toHexString(b));
                    }
                    // Set a flag to stop iteration. We still need to run through segment updating below.
                    done = true;
                    break;
                }

                // Detect when the shift will overflow.
                if ((value & -0x1000000000000000L) != 0L) {
                    try (final var buffer = new RealBuffer()) {
                        buffer.writeHexadecimalUnsignedLong(value).writeByte(b);
                        throw new NumberFormatException("Number too large: " + buffer.readUtf8());
                    }
                }

                value = value << 4;
                value = value | (long) digit;
                pos++;
                seen++;
            }

            segmentQueue.decrementSize(seen);
            head.pos = pos;
            if (pos == head.limit) {
                SegmentPool.recycle(segmentQueue.removeHead());
                head = segmentQueue.head();
            }
            seen = 0;
        } while (!done && head != null);

        return value;
    }

    @Override
    public @NonNull ByteString readByteString() {
        return readByteString(segmentQueue.size());
    }

    // Threshold determined empirically via ReadByteStringBenchmark
    /**
     * Create SegmentedByteString when size is greater than this many bytes.
     */
    private static final int SEGMENTING_THRESHOLD = 4096;

    @Override
    public @NonNull ByteString readByteString(final @NonNegative long byteCount) {
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        if (segmentQueue.size() < byteCount) {
            throw new JayoEOFException();
        }

        if (byteCount >= SEGMENTING_THRESHOLD) {
            final var snapshot = snapshot((int) byteCount);
            skip(byteCount);
            return snapshot;
        } else {
            return new RealByteString(readByteArray(byteCount));
        }
    }

    @Override
    public int select(final @NonNull Options options) {
        if (!(Objects.requireNonNull(options) instanceof RealOptions _options)) {
            throw new IllegalArgumentException("options must be an instance of JayoOptions");
        }
        final var index = selectPrefix(this, _options);
        if (index == -1) {
            return -1;
        }

        // If the prefix match actually matched a full byte string, consume it and return it.
        final var selectedSize = _options.byteStrings[index].getSize();
        skip(selectedSize);
        return index;
    }

    @Override
    public void readTo(final @NonNull RawSink sink, final @NonNegative long byteCount) {
        Objects.requireNonNull(sink);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0L: " + byteCount);
        }
        if (segmentQueue.size() < byteCount) {
            sink.write(this, segmentQueue.size()); // Exhaust ourselves.
            throw new JayoEOFException(
                    "Buffer exhausted before writing " + byteCount + " bytes. Only " + segmentQueue.size()
                            + " bytes were written.");
        }
        sink.write(this, byteCount);
    }

    @Override
    public @NonNegative long transferTo(final @NonNull RawSink sink) {
        Objects.requireNonNull(sink);
        final var byteCount = segmentQueue.size();
        if (byteCount > 0L) {
            sink.write(this, byteCount);
        }
        return byteCount;
    }

    @Override
    public @NonNull String readUtf8() {
        return readString(segmentQueue.size(), StandardCharsets.UTF_8);
    }

    @Override
    public @NonNull String readUtf8(final @NonNegative long byteCount) {
        return readString(byteCount, StandardCharsets.UTF_8);
    }

    @Override
    public @NonNull String readString(final @NonNull Charset charset) {
        return readString(segmentQueue.size(), charset);
    }

    @Override
    public @NonNull String readString(final @NonNegative long byteCount, final @NonNull Charset charset) {
        Objects.requireNonNull(charset);
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        if (segmentQueue.size() < byteCount) {
            throw new JayoEOFException();
        }
        if (byteCount == 0L) {
            return "";
        }

        final var head = segmentQueue.head();
        assert head != null;
        var pos = head.pos;
        if (pos + byteCount > head.limit) {
            // If the string spans multiple segments, delegate to readByteArray().
            return new String(readByteArray(byteCount), charset);
        }

        // else all bytes of this future String are in head Segment itself
        final var result = new String(head.data, pos, (int) byteCount, charset);
        pos += (int) byteCount;
        segmentQueue.decrementSize(byteCount);
        head.pos = pos;

        if (pos == head.limit) {
            SegmentPool.recycle(segmentQueue.removeHead());
        }

        return result;
    }

    @Override
    public @Nullable String readUtf8Line() {
        final var newline = indexOf((byte) ((int) '\n'));

        if (newline != -1L) {
            return Utils.readUtf8Line(this, newline);
        }
        if (segmentQueue.size() != 0L) {
            return readUtf8(segmentQueue.size());
        }

        return null;
    }

    @Override
    public @NonNull String readUtf8LineStrict() {
        return readUtf8LineStrict(Long.MAX_VALUE);
    }

    @Override
    public @NonNull String readUtf8LineStrict(final @NonNegative long limit) {
        if (limit < 0L) {
            throw new IllegalArgumentException("limit < 0: " + limit);
        }
        final var scanLength = (limit == Long.MAX_VALUE) ? Long.MAX_VALUE : limit + 1L;
        final var newline = indexOf((byte) ((int) '\n'), 0L, scanLength);
        if (newline != -1L) {
            return Utils.readUtf8Line(this, newline);
        }
        if (scanLength < segmentQueue.size() &&
                get(scanLength - 1) == (byte) ((int) '\r') &&
                get(scanLength) == (byte) ((int) '\n')) {
            // The line was 'limit' UTF-8 bytes followed by \r\n.
            return Utils.readUtf8Line(this, scanLength);
        }
        final var data = new RealBuffer();
        copyTo(data, 0, Math.min(32, segmentQueue.size()));
        throw new JayoEOFException(
                "\\n not found: limit=" + Math.min(segmentQueue.size(), limit) + " content="
                        + data.readByteString().hex() + "'…'");
    }

    @Override
    public @NonNegative int readUtf8CodePoint() {
        if (segmentQueue.size() == 0L) {
            throw new JayoEOFException();
        }

        final var b0 = get(0);
        int codePoint;
        final int byteCount;
        final int min;

        if ((b0 & 0x80) == 0) {
            // 0xxxxxxx.
            codePoint = b0 & 0x7f;
            byteCount = 1; // 7 bits (ASCII).
            min = 0x0;
        } else if ((b0 & 0xe0) == 0xc0) {
            // 0x110xxxxx
            codePoint = b0 & 0x1f;
            byteCount = 2; // 11 bits (5 + 6).
            min = 0x80;
        } else if ((b0 & 0xf0) == 0xe0) {
            // 0x1110xxxx
            codePoint = b0 & 0x0f;
            byteCount = 3; // 16 bits (4 + 6 + 6).
            min = 0x800;
        } else if ((b0 & 0xf8) == 0xf0) {
            // 0x11110xxx
            codePoint = b0 & 0x07;
            byteCount = 4; // 21 bits (3 + 6 + 6 + 6).
            min = 0x10000;
        } else {
            // We expected the first byte of a code point but got something else.
            skip(1);
            return UTF8_REPLACEMENT_CODE_POINT;
        }

        if (segmentQueue.size() < byteCount) {
            throw new JayoEOFException(
                    "size < " + byteCount + ": " + segmentQueue.size() + " (to read code point prefixed 0x"
                            + toHexString(b0) + ")");
        }

        // Read the continuation bytes. If we encounter a non-continuation byte, the sequence consumed
        // thus far is truncated and is decoded as the replacement character. That non-continuation byte
        // is left in the stream for processing by the next call to readUtf8CodePoint().
        for (var i = 1; i < byteCount; i++) {
            final var b = get(i);
            if ((b & 0xc0) == 0x80) {
                // 0x10xxxxxx
                codePoint = codePoint << 6;
                codePoint = codePoint | (b & 0x3f);
            } else {
                skip(i);
                return UTF8_REPLACEMENT_CODE_POINT;
            }
        }

        skip(byteCount);

        if (codePoint > 0x10ffff // Reject code points larger than the Unicode maximum.
                || (0xd800 <= codePoint && codePoint <= 0xdfff) // Reject partial surrogates.
                || codePoint < min) { // Reject overlong code points.
            return UTF8_REPLACEMENT_CODE_POINT;
        }
        return codePoint;
    }

    @Override
    public byte @NonNull [] readByteArray() {
        return readByteArray(segmentQueue.size());
    }

    @Override
    public byte @NonNull [] readByteArray(final @NonNegative long byteCount) {
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        if (segmentQueue.size() < byteCount) {
            throw new JayoEOFException();
        }

        final var result = new byte[(int) byteCount];
        readTo(result);
        return result;
    }

    @Override
    public void readTo(final byte @NonNull [] sink) {
        readTo(sink, 0, sink.length);
    }

    @Override
    public void readTo(final byte @NonNull [] sink,
                       final @NonNegative int offset,
                       final @NonNegative int byteCount) {
        checkOffsetAndCount(Objects.requireNonNull(sink).length, offset, byteCount);
        var _offset = offset;
        var remaining = byteCount;
        while (remaining > 0) {
            final var bytesRead = readAtMostTo(sink, _offset, remaining);
            if (bytesRead == -1) {
                throw new JayoEOFException("could not write all the requested bytes to byte array, remaining = " +
                        remaining + "/" + byteCount);
            }
            _offset += bytesRead;
            remaining -= bytesRead;
        }
    }

    @Override
    public int readAtMostTo(final byte @NonNull [] sink) {
        return readAtMostTo(sink, 0, sink.length);
    }

    @Override
    public int readAtMostTo(final byte @NonNull [] sink,
                            final @NonNegative int offset,
                            final @NonNegative int byteCount) {
        checkOffsetAndCount(Objects.requireNonNull(sink).length, offset, byteCount);

        final var head = segmentQueue.head();
        if (head == null) {
            return -1;
        }
        var pos = head.pos;
        final var toCopy = Math.min(byteCount, head.limit - pos);
        System.arraycopy(head.data, pos, sink, offset, toCopy);
        pos += toCopy;
        segmentQueue.decrementSize(toCopy);
        head.pos = pos;

        if (pos == head.limit) {
            SegmentPool.recycle(segmentQueue.removeHead());
        }

        return toCopy;
    }

    @Override
    public int readAtMostTo(final @NonNull ByteBuffer sink) {
        Objects.requireNonNull(sink);
        final var head = segmentQueue.head();
        if (head == null) {
            return -1;
        }

        var pos = head.pos;
        final var toCopy = Math.min(sink.remaining(), head.limit - pos);
        sink.put(head.data, pos, toCopy);
        pos += toCopy;
        segmentQueue.decrementSize(toCopy);
        head.pos = pos;

        if (pos == head.limit) {
            SegmentPool.recycle(segmentQueue.removeHead());
        }

        return toCopy;
    }

    @Override
    public void clear() {
        final var size = segmentQueue.size();
        if (size > 0) {
            skip(size, true);
        }
    }

    @Override
    public void skip(final @NonNegative long byteCount) {
        skip(byteCount, false);
    }

    private void skip(final @NonNegative long byteCount, final boolean forceRemoveSegments) {
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0L: " + byteCount);
        }
        var _byteCount = byteCount;
        var head = segmentQueue.head();
        while (_byteCount > 0) {
            if (head == null) {
                throw new JayoEOFException();
            }
            final var currentLimit = head.limit;
            var pos = head.pos;

            final var toSkip = (int) Math.min(_byteCount, currentLimit - pos);
            pos += toSkip;
            segmentQueue.decrementSize(toSkip);
            head.pos = pos;

            if (pos == currentLimit) {
                final Segment removed;
                if (forceRemoveSegments) {
                    removed = segmentQueue.forceRemoveHead();
                } else {
                    removed = segmentQueue.removeHead();
                }
                SegmentPool.recycle(removed);
                head = segmentQueue.head();
            }
            _byteCount -= toSkip;
        }
    }

    @Override
    public @NonNull Buffer write(final @NonNull ByteString byteString) {
        return write(byteString, 0, byteString.getSize());
    }

    @Override
    public @NonNull Buffer write(final @NonNull ByteString byteString,
                                 final @NonNegative int offset,
                                 final @NonNegative int byteCount) {
        if (!(Objects.requireNonNull(byteString) instanceof RealByteString _byteString)) {
            throw new IllegalArgumentException("byteString must be an instance of RealByteString");
        }
        _byteString.write(this, offset, byteCount);
        return this;
    }

    @Override
    public @NonNull Buffer writeUtf8(final @NonNull CharSequence charSequence) {
        return writeUtf8(charSequence, 0, charSequence.length());
    }

    @Override
    public @NonNull Buffer writeUtf8(final @NonNull CharSequence charSequence,
                                     final @NonNegative int startIndex,
                                     final @NonNegative int endIndex) {
        Objects.requireNonNull(charSequence);
        if (endIndex < startIndex) {
            throw new IllegalArgumentException("endIndex < beginIndex: " + endIndex + " < " + startIndex);
        }
        if (startIndex < 0) {
            throw new IndexOutOfBoundsException("beginIndex < 0: " + startIndex);
        }
        if (endIndex > charSequence.length()) {
            throw new IndexOutOfBoundsException("endIndex > string.length: " + endIndex + " > " + charSequence.length());
        }

        // Transcode a UTF-16 Java String to UTF-8 bytes.
        var i = startIndex;
        while (i < endIndex) {
            int c = charSequence.charAt(i);

            if (c < 0x80) {
                final var tail = segmentQueue.writableSegment(1);
                final var data = tail.data;
                final var currentLimit = tail.limit;
                final var segmentOffset = currentLimit - i;
                final var runLimit = Math.min(endIndex, Segment.SIZE - segmentOffset);

                // Emit a 7-bit character with 1 byte.
                data[segmentOffset + i++] = (byte) c; // 0xxxxxxx
                tail.limit = currentLimit + 1;

                // Fast-path contiguous runs of ASCII characters. This is ugly, but yields a ~4x performance
                // improvement over independent calls to writeByte().
                while (i < runLimit) {
                    c = charSequence.charAt(i);
                    if (c >= 0x80) {
                        break;
                    }
                    data[segmentOffset + i++] = (byte) c; // 0xxxxxxx
                }

                final var runSize = i + segmentOffset - currentLimit; // Equivalent to i - (previous i).
                final var isNewTail = currentLimit == 0;
                tail.limit = currentLimit + runSize;
                if (isNewTail) {
                    segmentQueue.addTail(tail);
                }
                segmentQueue.incrementSize(runSize);
            } else if (c < 0x800) {
                // Emit a 11-bit character with 2 bytes.
                final var tail = segmentQueue.writableSegment(2);
                final var data = tail.data;
                var limit = tail.limit;
                final var isNewTail = limit == 0;
                data[limit++] = (byte) (c >> 6 | 0xc0); // 110xxxxx
                data[limit++] = (byte) (c & 0x3f | 0x80); // 10xxxxxx
                tail.limit = limit;
                if (isNewTail) {
                    segmentQueue.addTail(tail);
                }
                segmentQueue.incrementSize(2L);
                i++;
            } else if ((c < 0xd800) || (c > 0xdfff)) {
                // Emit a 16-bit character with 3 bytes.
                final var tail = segmentQueue.writableSegment(3);
                final var data = tail.data;
                var limit = tail.limit;
                final var isNewTail = limit == 0;
                data[limit++] = (byte) (c >> 12 | 0xe0); // 1110xxxx
                data[limit++] = (byte) (c >> 6 & 0x3f | 0x80); // 10xxxxxx
                data[limit++] = (byte) (c & 0x3f | 0x80); // 10xxxxxx
                tail.limit = limit;
                if (isNewTail) {
                    segmentQueue.addTail(tail);
                }
                segmentQueue.incrementSize(3L);
                i++;
            } else {
                // c is a surrogate. Mac successor is a low
                // surrogate. If not, the UTF-16 is invalid, in which case we emit a replacement
                // character.
                final int low = (i + 1 < endIndex) ? charSequence.charAt(i + 1) : 0;
                if (c > 0xdbff || low < 0xdc00 || low > 0xdfff) {
                    writeByte((byte) ((int) '?'));
                    i++;
                } else {
                    // UTF-16 high surrogate: 110110xxxxxxxxxx (10 bits)
                    // UTF-16 low surrogate:  110111yyyyyyyyyy (10 bits)
                    // Unicode code point:    00010000000000000000 + xxxxxxxxxxyyyyyyyyyy (21 bits)
                    final var codePoint = 0x010000 + ((c & 0x03ff) << 10 | (low & 0x03ff));

                    // Emit a 21-bit character with 4 bytes.
                    final var tail = segmentQueue.writableSegment(4);
                    final var data = tail.data;
                    var limit = tail.limit;
                    final var isNewTail = limit == 0;
                    data[limit++] = (byte) (codePoint >> 18 | 0xf0); // 11110xxx
                    data[limit++] = (byte) (codePoint >> 12 & 0x3f | 0x80); // 10xxxxxx
                    data[limit++] = (byte) (codePoint >> 6 & 0x3f | 0x80); // 10xxyyyy
                    data[limit++] = (byte) (codePoint & 0x3f | 0x80); // 10yyyyyy
                    tail.limit = limit;
                    if (isNewTail) {
                        segmentQueue.addTail(tail);
                    }
                    segmentQueue.incrementSize(4L);
                    i += 2;
                }
            }
        }

        return this;
    }

    @Override
    public @NonNull Buffer writeUtf8CodePoint(final @NonNegative int codePoint) {
        if (codePoint < 0x80) {
            // Emit a 7-bit code point with 1 byte.
            writeByte((byte) codePoint);
        } else if (codePoint < 0x800) {
            // Emit a 11-bit code point with 2 bytes.
            final var tail = segmentQueue.writableSegment(2);
            final var data = tail.data;
            var limit = tail.limit;
            final var isNewTail = limit == 0;
            data[limit++] = (byte) (codePoint >> 6 | 0xc0); // 110xxxxx
            data[limit++] = (byte) (codePoint & 0x3f | 0x80); // 10xxxxxx
            tail.limit = limit;
            if (isNewTail) {
                segmentQueue.addTail(tail);
            }
            segmentQueue.incrementSize(2L);
        } else if (codePoint >= 0xd800 && codePoint <= 0xdfff) {
            // Emit a replacement character for a partial surrogate.
            writeByte((byte) ((int) '?'));
        } else if (codePoint < 0x10000) {
            // Emit a 16-bit code point with 3 bytes.
            final var tail = segmentQueue.writableSegment(3);
            final var data = tail.data;
            var limit = tail.limit;
            final var isNewTail = limit == 0;
            data[limit++] = (byte) (codePoint >> 12 | 0xe0); // 1110xxxx
            data[limit++] = (byte) (codePoint >> 6 & 0x3f | 0x80); // 10xxxxxx
            data[limit++] = (byte) (codePoint & 0x3f | 0x80); // 10xxxxxx
            tail.limit = limit;
            if (isNewTail) {
                segmentQueue.addTail(tail);
            }
            segmentQueue.incrementSize(3L);
        } else if (codePoint <= 0x10ffff) {
            // Emit a 21-bit code point with 4 bytes.
            final var tail = segmentQueue.writableSegment(4);
            final var data = tail.data;
            var limit = tail.limit;
            final var isNewTail = limit == 0;
            data[limit++] = (byte) (codePoint >> 18 | 0xf0); // 11110xxx
            data[limit++] = (byte) (codePoint >> 12 & 0x3f | 0x80); // 10xxxxxx
            data[limit++] = (byte) (codePoint >> 6 & 0x3f | 0x80); // 10xxyyyy
            data[limit++] = (byte) (codePoint & 0x3f | 0x80); // 10yyyyyy
            tail.limit = limit;
            if (isNewTail) {
                segmentQueue.addTail(tail);
            }
            segmentQueue.incrementSize(4L);
        } else {
            throw new IllegalArgumentException("Unexpected code point: 0x" + toHexString(codePoint));
        }

        return this;
    }

    @Override
    public @NonNull Buffer writeString(final @NonNull String string, final @NonNull Charset charset) {
        return writeString(string, 0, string.length(), charset);
    }

    @Override
    public @NonNull Buffer writeString(final @NonNull String string,
                                       final @NonNegative int startIndex,
                                       final @NonNegative int endIndex,
                                       final @NonNull Charset charset) {
        Objects.requireNonNull(string);
        if (startIndex < 0) {
            throw new IllegalArgumentException("beginIndex < 0: " + startIndex);
        }
        if (endIndex < startIndex) {
            throw new IllegalArgumentException("endIndex < beginIndex: " + endIndex + " < " + startIndex);
        }
        if (endIndex > string.length()) {
            throw new IllegalArgumentException("endIndex > string.length: " + endIndex + " > " + string.length());
        }
        // fast-path#1 for UTF-8 encoding
        if (charset == StandardCharsets.UTF_8) {
            return writeUtf8(string, startIndex, endIndex);
        }

        // fast-path#2 for ISO_8859_1 encoding
        if (charset == StandardCharsets.ISO_8859_1 && UNSAFE_AVAILABLE && isLatin1(string)) {
            final var stringBytes = bytes(string);
            return write(stringBytes, startIndex, endIndex - startIndex);
        }

        final var substring = string.substring(startIndex, endIndex);
        final var data = substring.getBytes(charset);
        return write(data, 0, data.length);
    }

    @Override
    public @NonNull Buffer write(final byte @NonNull [] source) {
        return write(source, 0, source.length);
    }

    @Override
    public @NonNull Buffer write(final byte @NonNull [] source,
                                 final @NonNegative int offset,
                                 final @NonNegative int byteCount) {
        checkOffsetAndCount(Objects.requireNonNull(source).length, offset, byteCount);

        final var limit = offset + byteCount;
        var _offset = offset;
        while (_offset < limit) {
            final var tail = segmentQueue.writableSegment(1);
            final var currentLimit = tail.limit;
            final var toCopy = Math.min(limit - _offset, Segment.SIZE - currentLimit);
            System.arraycopy(source, _offset, tail.data, currentLimit, toCopy);
            final var isNewTail = currentLimit == 0;
            tail.limit = currentLimit + toCopy;
            if (isNewTail) {
                segmentQueue.addTail(tail);
            }
            segmentQueue.incrementSize(toCopy);
            _offset += toCopy;
        }

        return this;
    }

    @Override
    public @NonNegative int transferFrom(final @NonNull ByteBuffer source) {
        final var byteCount = Objects.requireNonNull(source).remaining();
        var remaining = byteCount;
        while (remaining > 0) {
            final var tail = segmentQueue.writableSegment(1);
            final var currentLimit = tail.limit;
            final var toCopy = Math.min(remaining, Segment.SIZE - currentLimit);
            source.get(tail.data, currentLimit, toCopy);
            final var isNewTail = currentLimit == 0;
            tail.limit = currentLimit + toCopy;
            if (isNewTail) {
                segmentQueue.addTail(tail);
            }
            segmentQueue.incrementSize(toCopy);
            remaining -= toCopy;
        }

        return byteCount;
    }

    @Override
    public @NonNegative long transferFrom(final @NonNull RawSource source) {
        Objects.requireNonNull(source);
        var totalBytesRead = 0L;
        while (true) {
            final var readCount = source.readAtMostTo(this, Segment.SIZE);
            if (readCount == -1L) {
                break;
            }
            totalBytesRead += readCount;
        }
        return totalBytesRead;
    }

    @Override
    public @NonNull Buffer write(final @NonNull RawSource source, final @NonNegative long byteCount) {
        Objects.requireNonNull(source);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        var _byteCount = byteCount;
        while (_byteCount > 0L) {
            final var read = source.readAtMostTo(this, _byteCount);
            if (read == -1L) {
                throw new JayoEOFException();
            }
            _byteCount -= read;
        }
        return this;
    }

    @Override
    public @NonNull Buffer writeByte(final byte b) {
        final var tail = segmentQueue.writableSegment(1);
        var limit = tail.limit;
        final var isNewTail = limit == 0;
        tail.data[limit++] = b;
        tail.limit = limit;
        if (isNewTail) {
            segmentQueue.addTail(tail);
        }
        segmentQueue.incrementSize(1L);
        return this;
    }

    @Override
    public @NonNull Buffer writeShort(final short s) {
        final var tail = segmentQueue.writableSegment(2);
        var limit = tail.limit;
        final var isNewTail = limit == 0;
        tail.data[limit++] = (byte) (s >>> 8 & 0xff);
        tail.data[limit++] = (byte) (s & 0xff);
        tail.limit = limit;
        if (isNewTail) {
            segmentQueue.addTail(tail);
        }
        segmentQueue.incrementSize(2L);
        return this;
    }

    @Override
    public @NonNull Buffer writeInt(final int i) {
        final var tail = segmentQueue.writableSegment(4);
        var limit = tail.limit;
        final var isNewTail = limit == 0;
        tail.data[limit++] = (byte) (i >>> 24 & 0xff);
        tail.data[limit++] = (byte) (i >>> 16 & 0xff);
        tail.data[limit++] = (byte) (i >>> 8 & 0xff);
        tail.data[limit++] = (byte) (i & 0xff);
        tail.limit = limit;
        if (isNewTail) {
            segmentQueue.addTail(tail);
        }
        segmentQueue.incrementSize(4L);
        return this;
    }

    @Override
    public @NonNull Buffer writeLong(final long l) {
        final var tail = segmentQueue.writableSegment(8);
        var limit = tail.limit;
        final var isNewTail = limit == 0;
        tail.data[limit++] = (byte) (l >>> 56 & 0xffL);
        tail.data[limit++] = (byte) (l >>> 48 & 0xffL);
        tail.data[limit++] = (byte) (l >>> 40 & 0xffL);
        tail.data[limit++] = (byte) (l >>> 32 & 0xffL);
        tail.data[limit++] = (byte) (l >>> 24 & 0xffL);
        tail.data[limit++] = (byte) (l >>> 16 & 0xffL);
        tail.data[limit++] = (byte) (l >>> 8 & 0xffL);
        tail.data[limit++] = (byte) (l & 0xffL);
        tail.limit = limit;
        if (isNewTail) {
            segmentQueue.addTail(tail);
        }
        segmentQueue.incrementSize(8L);
        return this;
    }

    @Override
    public @NonNull Buffer writeDecimalLong(final long l) {
        if (l == 0L) {
            // Both a shortcut and required since the following code can't handle zero.
            return writeByte((byte) ((int) '0'));
        }

        var _l = l;
        var negative = false;
        if (_l < 0L) {
            _l = -_l;
            if (_l < 0L) { // Only true for Long.MIN_VALUE.
                return writeUtf8("-9223372036854775808");
            }
            negative = true;
        }

        // Binary search for character width which favors matching lower numbers.
        int width;
        if (_l < 100000000L) {
            if (_l < 10000L) {
                if (_l < 100L) {
                    if (_l < 10L) {
                        width = 1;
                    } else {
                        width = 2;
                    }
                } else if (_l < 1000L) {
                    width = 3;
                } else {
                    width = 4;
                }
            } else if (_l < 1000000L) {
                if (_l < 100000L) {
                    width = 5;
                } else {
                    width = 6;
                }
            } else if (_l < 10000000L) {
                width = 7;
            } else {
                width = 8;
            }
        } else if (_l < 1000000000000L) {
            if (_l < 10000000000L) {
                if (_l < 1000000000L) {
                    width = 9;
                } else {
                    width = 10;
                }
            } else if (_l < 100000000000L) {
                width = 11;
            } else {
                width = 12;
            }
        } else if (_l < 1000000000000000L) {
            if (_l < 10000000000000L) {
                width = 13;
            } else if (_l < 100000000000000L) {
                width = 14;
            } else {
                width = 15;
            }
        } else if (_l < 100000000000000000L) {
            if (_l < 10000000000000000L) {
                width = 16;
            } else {
                width = 17;
            }
        } else if (_l < 1000000000000000000L) {
            width = 18;
        } else {
            width = 19;
        }

        if (negative) {
            ++width;
        }

        final var tail = segmentQueue.writableSegment(width);
        final var data = tail.data;
        final var currentLimit = tail.limit;
        var pos = currentLimit + width; // We write backwards from right to left.
        while (_l != 0L) {
            final var digit = (int) (_l % 10);
            data[--pos] = HEX_DIGIT_BYTES[digit];
            _l /= 10;
        }
        if (negative) {
            data[--pos] = (byte) ((int) '-');
        }

        final var isNewTail = currentLimit == 0;
        tail.limit = currentLimit + width;
        if (isNewTail) {
            segmentQueue.addTail(tail);
        }
        segmentQueue.incrementSize(width);
        return this;
    }

    @Override
    public @NonNull Buffer writeHexadecimalUnsignedLong(final long l) {
        var _l = l;
        if (_l == 0L) {
            // Both a shortcut and required since the following code can't handle zero.
            return writeByte((byte) ((int) '0'));
        }

        // Mask every bit below the most significant bit to a 1
        // http://aggregate.org/MAGIC/#Most%20Significant%201%20Bit
        final var width = getHexadecimalUnsignedLongWidth(_l);

        final var tail = segmentQueue.writableSegment(width);
        final var data = tail.data;
        final var currentLimit = tail.limit;
        var pos = currentLimit + width - 1;
        while (pos >= currentLimit) {
            data[pos--] = HEX_DIGIT_BYTES[(int) (_l & 0xF)];
            _l = _l >>> 4;
        }
        final var isNewTail = currentLimit == 0;
        tail.limit = currentLimit + width;
        if (isNewTail) {
            segmentQueue.addTail(tail);
        }
        segmentQueue.incrementSize(width);
        return this;
    }

    @Override
    public void write(final @NonNull Buffer source, final @NonNegative long byteCount) {
        // Move bytes from the head of the source buffer to the tail of this buffer
        // while balancing two conflicting goals: don't waste CPU and don't waste
        // memory.
        //
        //
        // Don't waste CPU (i.e. don't copy data around).
        //
        // Copying large amounts of data is expensive. Instead, we prefer to
        // reassign entire segments from one buffer to the other.
        //
        //
        // Don't waste memory.
        //
        // As an invariant, adjacent pairs of segments in a buffer should be at
        // least 50% full, except for the head segment and the tail segment.
        //
        // The head segment cannot maintain the invariant because the application is
        // consuming bytes from this segment, decreasing its level.
        //
        // The tail segment cannot maintain the invariant because the application is
        // producing bytes, which may require new nearly-empty tail segments to be
        // appended.
        //
        //
        // Moving segments between buffers
        //
        // When writing one buffer to another, we prefer to reassign entire segments
        // over copying bytes into their most compact form. Suppose we have a buffer
        // with these segment levels [91%, 61%]. If we append a buffer with a
        // single [72%] segment, that yields [91%, 61%, 72%]. No bytes are copied.
        //
        // Or suppose we have a buffer with these segment levels: [100%, 2%], and we
        // want to append it to a buffer with these segment levels [99%, 3%]. This
        // operation will yield the following segments: [100%, 2%, 99%, 3%]. That
        // is, we do not spend time copying bytes around to achieve more efficient
        // memory use like [100%, 100%, 4%].
        //
        // When combining buffers, we will compact adjacent buffers when their
        // combined level doesn't exceed 100%. For example, when we start with
        // [100%, 40%] and append [30%, 80%], the result is [100%, 70%, 80%].
        //
        //
        // Splitting segments
        //
        // Occasionally we write only part of a source buffer to a sink buffer. For
        // example, given a sink [51%, 91%], we may want to write the first 30% of
        // a source [92%, 82%] to it. To simplify, we first transform the source to
        // an equivalent buffer [30%, 62%, 82%] and then move the head segment,
        // yielding sink [51%, 91%, 30%] and source [62%, 82%].

        if (Objects.requireNonNull(source) == this) {
            throw new IllegalArgumentException("source == this, cannot write in itself");
        }
        checkOffsetAndCount(source.getSize(), 0, byteCount);
        if (!(source instanceof RealBuffer _source)) {
            throw new IllegalArgumentException("source must be an instance of JayoBuffer");
        }

        var _byteCount = byteCount;
        while (_byteCount > 0L) {
            final var sourceHeadForTransferring = _source.segmentQueue.headForTransferring();
            final var tail = segmentQueue.writableSegmentWithState();
            final var currentLimit = tail.limit;
            final var isNewTail = currentLimit == 0;
            final var sourceHeadIsWriting = sourceHeadForTransferring.isWriting();
            var needSplit = sourceHeadIsWriting;
            var sourceHead = sourceHeadForTransferring.head();
            var bytesInSource = sourceHead.limit - sourceHead.pos;
            // Is a prefix of the source's head segment all that we need to move?
            if (_byteCount < bytesInSource) {
                if (tail.owner &&
                        _byteCount + currentLimit
                                - ((tail.shared) ? 0 : tail.pos) <= Segment.SIZE
                ) {
                    try {
                        // Our existing segments are sufficient. Move bytes from source's head to our tail.
                        _source.segmentQueue.decrementSize(_byteCount);
                        sourceHead.writeTo(tail, (int) _byteCount);
                        if (isNewTail) {
                            segmentQueue.addTail(tail);
                        }
                        return;
                    } finally {
                        segmentQueue.finishWrite(tail);
                        segmentQueue.incrementSize(_byteCount);
                        _source.segmentQueue.finishTransfer(sourceHead, sourceHeadIsWriting);
                    }

                }
                needSplit = true;
            }

            Segment newTail = null;
            try {
                if (needSplit) {
                    bytesInSource = (int) Math.min(bytesInSource, _byteCount);
                    // We're going to need another segment. Split the source's head segment in two,
                    // then move the first of those two to this buffer.
                    sourceHead = _source.segmentQueue.splitHead(bytesInSource, sourceHeadIsWriting);
                }

                _source.segmentQueue.decrementSize(bytesInSource);
                // Remove the source's head segment and append it to our tail.
                final var segmentToMove = _source.segmentQueue.forceRemoveHead();
                newTail = newTailIfNeeded(tail, segmentToMove);
                if (newTail != null) {
                    segmentQueue.addTail(newTail);
                } else if (isNewTail) {
                    segmentQueue.addTail(tail);
                }
            } finally {
                segmentQueue.finishWrite(tail);
                segmentQueue.incrementSize(bytesInSource);
                if (newTail != null) {
                    _source.segmentQueue.finishTransfer(sourceHead, sourceHeadIsWriting);
                }
            }
            _byteCount -= bytesInSource;
        }
    }

    /**
     * Call this when the tail and its predecessor may both be less than half full. In this case, we will copy data so
     * that a segment can be recycled.
     */
    private static @Nullable Segment newTailIfNeeded(final @NonNull Segment currentTail,
                                                     final @NonNull Segment newTail) {
        Objects.requireNonNull(currentTail);
        Objects.requireNonNull(newTail);
        if (!currentTail.owner) {
            return newTail; // Cannot compact: current tail isn't writable.
        }
        final var byteCount = newTail.limit - newTail.pos;
        final var availableByteCount = Segment.SIZE - currentTail.limit
                + ((currentTail.shared) ? 0 : currentTail.pos);
        if (byteCount > availableByteCount) {
            return newTail; // Cannot compact: not enough writable space in current tail.
        }

        newTail.writeTo(currentTail, byteCount);
        SegmentPool.recycle(newTail);
        return null;
    }

    @Override
    public long readAtMostTo(final @NonNull Buffer sink, final @NonNegative long byteCount) {
        Objects.requireNonNull(sink);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        if (segmentQueue.size() == 0L) {
            return -1L;
        }
        var _byteCount = byteCount;
        if (byteCount > segmentQueue.size()) {
            _byteCount = segmentQueue.size();
        }
        sink.write(this, _byteCount);
        return _byteCount;
    }

    @Override
    public long indexOf(final byte b) {
        return indexOf(b, 0L, Long.MAX_VALUE);
    }

    @Override
    public long indexOf(final byte b, final @NonNegative long startIndex) {
        return indexOf(b, startIndex, Long.MAX_VALUE);
    }

    @Override
    public long indexOf(final byte b, final @NonNegative long startIndex, final @NonNegative long endIndex) {
        if (startIndex < 0 || startIndex > endIndex) {
            throw new IllegalArgumentException("size=" + segmentQueue.size() + " startIndex=" + startIndex
                    + " endIndex=" + endIndex);
        }

        final long _endIndex;
        if (endIndex > segmentQueue.size()) {
            _endIndex = segmentQueue.size();
        } else {
            _endIndex = endIndex;
        }
        if (startIndex >= _endIndex) {
            return -1L;
        }

        return seek(startIndex, (s, o) -> {
            if (s == null) {
                return -1L;
            }
            var segment = s;
            var offset = o;
            var _startIndex = startIndex;

            // Scan through the segments, searching for b.
            while (offset < _endIndex) {
                final var data = segment.data;
                final var currentPos = segment.pos;
                final var currentLimit = segment.limit;
                final var limit = (int) Math.min(currentLimit, currentPos + _endIndex - offset);
                var pos = (int) (currentPos + _startIndex - offset);
                while (pos < limit) {
                    if (data[pos] == b) {
                        return pos - currentPos + offset;
                    }
                    pos++;
                }

                // Not in this segment. Try the next one.
                offset += (currentLimit - currentPos);
                _startIndex = offset;
                if (segment.next == segmentQueue) {
                    break;
                }
                segment = segment.next;
            }

            return -1L;
        });
    }

    @Override
    public long indexOf(final @NonNull ByteString byteString) {
        return indexOf(byteString, 0L);
    }

    @Override
    public long indexOf(final @NonNull ByteString byteString, final @NonNegative long startIndex) {
        if (Objects.requireNonNull(byteString).isEmpty()) {
            return 0L;
        }
        if (startIndex < 0L) {
            throw new IllegalArgumentException("startIndex < 0: " + startIndex);
        }
        if (!(byteString instanceof RealByteString _bytes)) {
            throw new IllegalArgumentException("bytes must be an instance of RealByteString");
        }

        return seek(startIndex, (s, o) -> {
            if (s == null) {
                return -1L;
            }
            var segment = s;
            var offset = o;
            var _startIndex = startIndex;

            // Scan through the segments, searching for the lead byte. Each time that is found, delegate to
            // rangeEquals() to check for a complete match.
            final var targetByteArray = _bytes.internalArray();
            final var b0 = targetByteArray[0];
            final var bytesSize = byteString.getSize();
            final var resultLimit = segmentQueue.size() - bytesSize + 1L;
            while (offset < resultLimit) {
                // Scan through the current segment.
                final var data = segment.data;
                final var currentPos = segment.pos;
                final var currentLimit = segment.limit;
                final var segmentLimit = (int) Math.min(currentLimit, currentPos + resultLimit - offset);
                for (var pos = (int) (currentPos + _startIndex - offset); pos < segmentLimit; pos++) {
                    if (data[pos] == b0
                            && rangeEquals(segment, pos + 1, targetByteArray, bytesSize)) {
                        return pos - currentPos + offset;
                    }
                }

                // Not in this segment. Try the next one.
                offset += (currentLimit - currentPos);
                _startIndex = offset;
                segment = segment.next;
            }

            return -1L;
        });
    }

    @Override
    public long indexOfElement(final @NonNull ByteString targetBytes) {
        return indexOfElement(targetBytes, 0L);
    }

    @Override
    public long indexOfElement(final @NonNull ByteString targetBytes, final @NonNegative long startIndex) {
        if (startIndex < 0L) {
            throw new IllegalArgumentException("startIndex < 0: " + startIndex);
        }
        if (!(Objects.requireNonNull(targetBytes) instanceof RealByteString _targetBytes)) {
            throw new IllegalArgumentException("targetBytes must be an instance of RealByteString");
        }

        return seek(startIndex, (s, o) -> {
            if (s == null) {
                return -1L;
            }
            var segment = s;
            var offset = o;
            var _startIndex = startIndex;

            // Special case searching for one of two bytes. This is a common case for tools like Moshi,
            // which search for pairs of chars like `\r` and `\n` or {@code `"` and `\`. The impact of this
            // optimization is a ~5x speedup for this case without a substantial cost to other cases.
            if (_targetBytes.getSize() == 2) {
                // Scan through the segments, searching for either of the two bytes.
                final var b0 = _targetBytes.get(0);
                final var b1 = _targetBytes.get(1);
                while (offset < segmentQueue.size()) {
                    final var data = segment.data;
                    final var currentPos = segment.pos;
                    var pos = (int) (currentPos + _startIndex - offset);
                    final var currentLimit = segment.limit;
                    while (pos < currentLimit) {
                        final var b = (int) data[pos];
                        if (b == (int) b0 || b == (int) b1) {
                            return pos - currentPos + offset;
                        }
                        pos++;
                    }

                    // Not in this segment. Try the next one.
                    offset += (currentLimit - currentPos);
                    _startIndex = offset;
                    segment = segment.next;
                }
            } else {
                // Scan through the segments, searching for a byte that's also in the array.
                final var targetByteArray = _targetBytes.internalArray();
                while (offset < segmentQueue.size()) {
                    final var data = segment.data;
                    final var currentPos = segment.pos;
                    var pos = (int) (currentPos + _startIndex - offset);
                    final var currentLimit = segment.limit;
                    while (pos < currentLimit) {
                        final var b = (int) data[pos];
                        for (final var t : targetByteArray) {
                            if (b == (int) t) {
                                return pos - currentPos + offset;
                            }
                        }
                        pos++;
                    }

                    // Not in this segment. Try the next one.
                    offset += (currentLimit - currentPos);
                    _startIndex = offset;
                    segment = segment.next;
                }
            }

            return -1L;
        });
    }

    @Override
    public boolean rangeEquals(final @NonNegative long offset, final @NonNull ByteString byteString) {
        return rangeEquals(offset, byteString, 0, byteString.getSize());
    }

    @Override
    public boolean rangeEquals(final @NonNegative long offset,
                               final @NonNull ByteString byteString,
                               final @NonNegative int bytesOffset,
                               final @NonNegative int byteCount) {
        Objects.requireNonNull(byteString);
        if (offset < 0L ||
                bytesOffset < 0 ||
                byteCount < 0 ||
                segmentQueue.size() - offset < byteCount ||
                byteString.getSize() - bytesOffset < byteCount
        ) {
            return false;
        }
        for (var i = 0; i < byteCount; i++) {
            if (get(offset + i) != byteString.get(bytesOffset + i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        segmentQueue.close();
    }

    @Override
    public @NonNull ByteString hash(final @NonNull Digest digest) {
        final MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(digest.algorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm is not available : " + digest.algorithm(), e);
        }
        segmentQueue.forEach(s -> {
            final var currentPos = s.pos;
            messageDigest.update(s.data, currentPos, s.limit - currentPos);
        });
        return new RealByteString(messageDigest.digest());
    }

    @Override
    public @NonNull ByteString hmac(final @NonNull Hmac hMac, final @NonNull ByteString key) {
        Objects.requireNonNull(key);
        final javax.crypto.Mac javaMac;
        try {
            javaMac = javax.crypto.Mac.getInstance(hMac.algorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm is not available : " + hMac.algorithm(), e);
        }
        if (!(key instanceof RealByteString _key)) {
            throw new IllegalArgumentException("key must be an instance of RealByteString");
        }
        try {
            javaMac.init(new SecretKeySpec(_key.internalArray(), hMac.algorithm()));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("InvalidKeyException was fired with the provided ByteString key", e);
        }
        segmentQueue.forEach(s -> {
            final var currentPos = s.pos;
            javaMac.update(s.data, currentPos, s.limit - currentPos);
        });
        return new RealByteString(javaMac.doFinal());
    }

    @Override
    public @NonNull ReadableByteChannel asReadableByteChannel() {
        return asByteChannel();
    }

    @Override
    public @NonNull WritableByteChannel asWritableByteChannel() {
        return asByteChannel();
    }

    @Override
    public @NonNull ByteChannel asByteChannel() {
        return new ByteChannel() {
            @Override
            public int read(final @NonNull ByteBuffer sink) {
                return RealBuffer.this.readAtMostTo(sink);
            }

            @Override
            public int write(final @NonNull ByteBuffer source) {
                return RealBuffer.this.transferFrom(source);
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public void close() {
            }

            @Override
            public String toString() {
                return RealBuffer.this + ".asByteChannel()";
            }
        };
    }

//    @Override
//    public boolean equals(final @Nullable Object other) {
//        if (other == null) {
//            return false;
//        }
//        if (this == other) {
//            return true;
//        }
//        if (!(other instanceof RealBuffer _other)) {
//            return false;
//        }
//        if (size != _other.size) {
//            return false;
//        }
//        if (size == 0L) return true; // Both buffers are empty.
//
//        assert head != null;
//        var sa = this.head();
//        assert _other.head() != null;
//        var sb = _other.head();
//        var posA = sa.pos;
//        var posB = sb.pos;
//
//        var pos = 0L;
//        long count;
//        while (pos < size) {
//            count = Math.min(sa.limit - posA, sb.limit - posB);
//
//            for (var i = 0L; i < count; i++) {
//                if (sa.data.get(posA++) != sb.data.get(posB++)) {
//                    return false;
//                }
//            }
//
//            if (posA == sa.limit) {
//                assert sa.next != null;
//                sa = sa.next;
//                posA = sa.pos;
//            }
//
//            if (posB == sb.limit) {
//                assert sb.next != null;
//                sb = sb.next;
//                posB = sb.pos;
//            }
//            pos += count;
//        }
//
//        return true;
//    }
//
//    @Override
//    public int hashCode() {
//        if (head == null) {
//            return 0;
//        }
//        var s = head();
//        var result = 1;
//        do {
//            var pos = s.pos;
//            final var limit = s.limit;
//            while (pos < limit) {
//                result = 31 * result + s.data.get(pos);
//                pos++;
//            }
//            assert s.next != null;
//            s = s.next;
//        } while (s != head);
//        return result;
//    }

    @Override
    public @NonNull String toString() {
        if (segmentQueue.size() == 0L) return "Buffer(size=0)";

        final var maxPrintableBytes = 64;
        final var len = (int) Math.min(maxPrintableBytes, segmentQueue.size());

        final var builder = new StringBuilder(len * 2 + ((segmentQueue.size() > maxPrintableBytes) ? 1 : 0));

        var curr = segmentQueue.head();
        assert curr != null;
        var written = 0;
        var pos = curr.pos;
        while (written < len) {
            if (pos == curr.limit) {
                curr = curr.next;
                pos = curr.pos;
            }

            final var b = (int) curr.data[pos++];
            written++;

            builder.append(HEX_DIGIT_CHARS[(b >> 4) & 0xf])
                    .append(HEX_DIGIT_CHARS[b & 0xf]);
        }

        if (segmentQueue.size() > maxPrintableBytes) {
            builder.append('…');
        }

        return "Buffer(size=" + segmentQueue.size() + " hex=" + builder + ")";
    }

    @Override
    public @NonNull Buffer copy() {
        final var result = new RealBuffer();
        if (segmentQueue.size() == 0L) {
            return result;
        }
        segmentQueue.forEach(segment -> {
            final var segmentCopy = segment.sharedCopy();
            result.segmentQueue.addTail(segmentCopy);
            result.segmentQueue.incrementSize(segmentCopy.limit - segmentCopy.pos);
        });
        return result;
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public @NonNull Buffer clone() {
        return copy();
    }

    @Override
    public @NonNull ByteString snapshot() {
        final var size = segmentQueue.size();
        if (size > Integer.MAX_VALUE) {
            throw new IllegalStateException("size > Integer.MAX_VALUE: " + segmentQueue.size());
        }
        return snapshot((int) size);
    }

    @Override
    public @NonNull ByteString snapshot(final @NonNegative int byteCount) {
        if (byteCount == 0) {
            return ByteString.EMPTY;
        }
        checkOffsetAndCount(segmentQueue.size(), 0L, byteCount);

        // Walk through the buffer to count how many segments we'll need.
        var offset = 0;
        var segmentCount = 0;
        var s = segmentQueue.head();
        while (offset < byteCount) {
            assert s != null;
            final var currentPos = s.pos;
            final var currentLimit = s.limit;
            if (currentLimit == currentPos) {
                // Empty segment. This should not happen!
                throw new AssertionError("segment.limit == segment.pos");
            }
            offset += currentLimit - currentPos;
            segmentCount++;
            s = s.next;
        }

        // Walk through the buffer again to assign segments and build the directory.
        final var segments = new Segment[segmentCount];
        final var directory = new int[segmentCount * 2];
        offset = 0;
        segmentCount = 0;
        s = segmentQueue.head();
        while (offset < byteCount) {
            assert s != null;
            final var copy = s.sharedCopy();
            segments[segmentCount] = copy;
            final var copyPos = copy.pos;
            offset += copy.limit - copyPos;
            // Despite sharing more bytes, only report having up to byteCount.
            directory[segmentCount] = Math.min(offset, byteCount);
            directory[segmentCount + segments.length] = copyPos;
            segmentCount++;
            s = s.next;
        }
        return new SegmentedByteString(segments, directory);
    }

    @Override
    public @NonNull UnsafeCursor readUnsafe() {
        return readUnsafe(new RealUnsafeCursor());
    }

    @Override
    public @NonNull UnsafeCursor readUnsafe(final @NonNull UnsafeCursor unsafeCursor) {
        if (Objects.requireNonNull(unsafeCursor).buffer != null) {
            throw new IllegalStateException("this cursor is already attached to a buffer");
        }

        unsafeCursor.buffer = this;
        unsafeCursor.readWrite = false;
        return unsafeCursor;
    }

    @Override
    public @NonNull UnsafeCursor readAndWriteUnsafe() {
        return readAndWriteUnsafe(new RealUnsafeCursor());
    }

    @Override
    public @NonNull UnsafeCursor readAndWriteUnsafe(final @NonNull UnsafeCursor unsafeCursor) {
        if (Objects.requireNonNull(unsafeCursor).buffer != null) {
            throw new IllegalStateException("this cursor is already attached to a buffer");
        }

        unsafeCursor.buffer = this;
        unsafeCursor.readWrite = true;
        return unsafeCursor;
    }

    /**
     * Invoke `lambda` with the segment and offset at `startIndex`. Searches from the front or the back
     * depending on what's closer to `startIndex`.
     */
    private <T> T seek(final long startIndex, BiFunction<Segment, Long, T> lambda) {
        var s = segmentQueue.head();
        if (s == null) {
            return lambda.apply(null, -1L);
        }

        final var size = segmentQueue.size();
        long offset;
        if (size - startIndex < startIndex) {
            // We're scanning in the back half of this buffer. Find the segment starting at the back.
            offset = size;
            s = segmentQueue.tail();
            while (true) {
                assert s != null;
                offset -= (s.limit - s.pos);
                if (offset <= startIndex || s.prev == segmentQueue) {
                    break;
                }
                s = s.prev;
            }
        } else {
            // We're scanning in the front half of this buffer. Find the segment starting at the front.
            offset = 0L;
            while (true) {
                final var nextOffset = offset + (s.limit - s.pos);
                if (nextOffset > startIndex || s.next == segmentQueue) {
                    break;
                }
                s = s.next;
                offset = nextOffset;
            }
        }
        return lambda.apply(s, offset);
    }

    /**
     * Returns true if the range within this buffer starting at {@code segmentPos} in {@code segment} is equal to
     * {@code bytes[1..bytesLimit)}.
     */
    private static boolean rangeEquals(
            final Segment segment,
            final int segmentPos,
            final byte[] bytes,
            final int bytesLimit
    ) {
        var _segment = segment;
        var _segmentPos = segmentPos;
        var data = _segment.data;
        var segmentLimit = _segment.limit;

        var i = 1;
        while (i < bytesLimit) {
            if (_segmentPos == segmentLimit) {
                _segment = _segment.next;
                data = _segment.data;
                _segmentPos = _segment.pos;
                segmentLimit = _segment.limit;
            }

            if (data[_segmentPos] != bytes[i]) {
                return false;
            }

            _segmentPos++;
            i++;
        }

        return true;
    }

    public static final class RealUnsafeCursor extends UnsafeCursor {
        private @Nullable Segment segment = null;

        @Override
        public int next() {
            Objects.requireNonNull(buffer, "not attached to a buffer");
            if (offset == buffer.getSize()) {
                throw new IllegalStateException("no more bytes");
            }
            return (offset == -1L) ? seek(0L) : seek(offset + (end - start));
        }

        @Override
        public int seek(final @NonNegative long offset) {
            Objects.requireNonNull(buffer, "not attached to a buffer");
            if (!(buffer instanceof RealBuffer _buffer)) {
                throw new IllegalStateException("buffer must be an instance of JayoBuffer");
            }
            final var size = _buffer.segmentQueue.size();
            if (offset < -1L || offset > size) {
                throw new ArrayIndexOutOfBoundsException("offset=" + offset + " > size=" + size);
            }

            if (offset == -1L || offset == size) {
                this.segment = null;
                this.offset = offset;
                this.data = null;
                this.start = -1;
                this.end = -1;
                return -1;
            }

            // Navigate to the segment that contains `offset`. Start from our current segment if possible.
            var min = 0L;
            var max = size;
            var head = _buffer.segmentQueue.head();
            var tail = _buffer.segmentQueue.tail();
            if (this.segment != null) {
                final var segmentOffset = this.offset - (this.start - this.segment.pos);
                if (segmentOffset > offset) {
                    // Set the cursor segment to be the 'end'
                    max = segmentOffset;
                    tail = this.segment;
                } else {
                    // Set the cursor segment to be the 'beginning'
                    min = segmentOffset;
                    head = this.segment;
                }
            }

            Segment next;
            long nextOffset;
            if (max - offset > offset - min) {
                // Start at the 'beginning' and search forwards
                assert head != null;
                next = head;
                nextOffset = min;
                var nextSize = next.limit - next.pos;
                while (offset >= nextOffset + nextSize) {
                    nextOffset += nextSize;
                    next = next.next;
                    nextSize = next.limit - next.pos;
                }
            } else {
                // Start at the 'end' and search backwards
                assert tail != null;
                next = tail;
                nextOffset = max - (next.limit - next.pos);
                while (nextOffset > offset) {
                    next = next.prev;
                    nextOffset -= (next.limit - next.pos);
                }
            }

            // If we're going to write and our segment is shared, swap it for a read-write one.
            if (readWrite && next.shared) {
                next = next.unsharedCopy();
            }

            // Update this cursor to the requested offset within the found segment.
            this.segment = next;
            this.offset = offset;
            this.data = next.data;
            this.start = next.pos + (int) (offset - nextOffset);
            this.end = next.limit;
            return end - start;
        }

        @Override
        public @NonNegative long resizeBuffer(final long newSize) {
            Objects.requireNonNull(buffer, "not attached to a buffer");
            if (!readWrite) {
                throw new IllegalStateException("resizeBuffer() is only permitted for read/write buffers");
            }
            if (!(buffer instanceof RealBuffer _buffer)) {
                throw new IllegalStateException("buffer must be an instance of JayoBuffer");
            }

            final var oldSize = _buffer.segmentQueue.size();
            if (newSize <= oldSize) {
                if (newSize < 0L) {
                    throw new IllegalArgumentException("newSize < 0: " + newSize);
                }
                // Shrink the buffer by either shrinking segments or removing them.
                var bytesToSubtract = oldSize - newSize;
                while (bytesToSubtract > 0L) {
                    final var tail = _buffer.segmentQueue.tail();
                    assert tail != null;
                    final var currentTailLimit = tail.limit;
                    final var tailSize = currentTailLimit - tail.pos;
                    if (tailSize <= bytesToSubtract) {
                        SegmentPool.recycle(_buffer.segmentQueue.removeTail());
                        bytesToSubtract -= tailSize;
                    } else {
                        tail.limit = currentTailLimit - (int) bytesToSubtract;
                        break;
                    }
                }
                // Seek to the end.
                this.segment = null;
                this.offset = newSize;
                this.data = null;
                this.start = -1;
                this.end = -1;
                _buffer.segmentQueue.decrementSize(oldSize - newSize);
            } else {
                // Enlarge the buffer by either enlarging segments or adding them.
                var needsToSeek = true;
                var bytesToAdd = newSize - oldSize;
                while (bytesToAdd > 0L) {
                    final var tail = _buffer.segmentQueue.writableSegment(1);
                    var tailLimit = tail.limit;
                    final var segmentBytesToAdd = (int) Math.min(bytesToAdd, Segment.SIZE - tailLimit);
                    final var isNewTail = tailLimit == 0;
                    tailLimit += segmentBytesToAdd;
                    tail.limit = tailLimit;
                    if (isNewTail) {
                        _buffer.segmentQueue.addTail(tail);
                    }
                    _buffer.segmentQueue.incrementSize(segmentBytesToAdd);
                    bytesToAdd -= segmentBytesToAdd;

                    // If this is the first segment we're adding, seek to it.
                    if (needsToSeek) {
                        this.segment = _buffer.segmentQueue.tail();
                        this.offset = oldSize;
                        this.data = tail.data;
                        this.start = tailLimit - segmentBytesToAdd;
                        this.end = tailLimit;
                        needsToSeek = false;
                    }
                }
            }

            return oldSize;
        }

        @Override
        public long expandBuffer(final @NonNegative int minByteCount) {
            if (minByteCount <= 0L) {
                throw new IllegalArgumentException("minByteCount <= 0: " + minByteCount);
            }
            if (minByteCount > Segment.SIZE) {
                throw new IllegalArgumentException("minByteCount > Segment.SIZE: " + minByteCount);
            }
            Objects.requireNonNull(buffer, "not attached to a buffer");
            if (!readWrite) {
                throw new IllegalStateException("expandBuffer() is only permitted for read/write buffers");
            }
            if (!(buffer instanceof RealBuffer _buffer)) {
                throw new IllegalStateException("buffer must be an instance of JayoBuffer");
            }

            final var oldSize = _buffer.segmentQueue.size();
            final var tail = _buffer.segmentQueue.writableSegment(minByteCount);
            final var currentTailLimit = tail.limit;
            final var result = Segment.SIZE - currentTailLimit;
            final var isNewTail = currentTailLimit == 0;
            tail.limit = Segment.SIZE;
            if (isNewTail) {
                _buffer.segmentQueue.addTail(tail);
            }
            _buffer.segmentQueue.incrementSize(result);

            // Seek to the old size.
            this.offset = oldSize;
            this.segment = _buffer.segmentQueue.tail();
            this.data = tail.data;
            this.start = Segment.SIZE - result;
            this.end = Segment.SIZE;

            return result;
        }

        @Override
        public void close() {
            // TODO(jwilson): use edit counts or other information to track unexpected changes?
            Objects.requireNonNull(buffer, "not attached to a buffer");

            buffer = null;
            segment = null;
            offset = -1L;
            data = null;
            start = -1;
            end = -1;
        }
    }
}
