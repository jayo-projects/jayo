/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio) and kotlinx-io (https://github.com/Kotlin/kotlinx-io), original
 * copyrights are below
 *
 * Copyright 2017-2023 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENCE file.
 *
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.internal;

import jayo.*;
import jayo.bytestring.ByteString;
import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.ToLongFunction;

import static java.lang.System.Logger.Level.TRACE;
import static jayo.internal.UnsafeUtils.*;
import static jayo.internal.Utils.*;
import static jayo.tools.JayoUtils.checkOffsetAndCount;


public final class RealBuffer implements Buffer {
    private static final System.Logger LOGGER = System.getLogger("jayo.Buffer");

    long byteSize = 0L;
    @Nullable
    Segment head = null;

    @NonNull
    Segment writableTail(final int minimumCapacity) {
        assert minimumCapacity > 0;

        if (head == null) {
            final var result = SegmentPool.take(); // Acquire this first segment.
            head = result;
            result.prev = result;
            result.next = result;
            return result;
        }

        final var tail = head.prev;
        assert tail != null;
        // the current tail has enough room
        if (tail.owner && tail.limit + minimumCapacity <= Segment.SIZE) {
            return tail;
        }

        // Append a new empty segment to fill up.
        return tail.push(SegmentPool.take());
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public long bytesAvailable() {
        return byteSize;
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
    public @NonNull Reader peek() {
        return new RealReader(new PeekRawReader(this));
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull OutputStream out) {
        return copyTo(out, 0L);
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull OutputStream out, final long offset) {
        return copyTo(out, offset, byteSize - offset);
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull OutputStream out,
                                  final long offset,
                                  final long byteCount) {
        Objects.requireNonNull(out);
        checkOffsetAndCount(byteSize, offset, byteCount);

        if (byteCount == 0L) {
            return this;
        }

        var _offset = offset;

        // Skip segments that we aren't copying from.
        var segment = head;
        assert segment != null;
        while (_offset >= segment.limit - segment.pos) {
            _offset -= (segment.limit - segment.pos);
            segment = segment.next;
            assert segment != null;
        }

        var remaining = byteCount;
        // Copy from one segment at a time.
        while (remaining > 0L) {
            assert segment != null;
            final var pos = (int) (segment.pos + _offset);
            final var toCopy = (int) Math.min(segment.limit - pos, remaining);
            try {
                out.write(segment.data, pos, toCopy);
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
            remaining -= toCopy;
            _offset = 0L;
            segment = segment.next;
        }
        return this;
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull Buffer out) {
        return copyTo(out, 0L);
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull Buffer out, final long offset) {
        return copyTo(out, offset, byteSize - offset);
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull Buffer out,
                                  final long offset,
                                  final long byteCount) {
        Objects.requireNonNull(out);
        checkOffsetAndCount(byteSize, offset, byteCount);

        if (byteCount == 0L) {
            return this;
        }

        final var _out = (RealBuffer) out;
        var _offset = offset;

        // Skip segments that we aren't copying from.
        var segment = head;
        assert segment != null;
        while (_offset >= segment.limit - segment.pos) {
            _offset -= (segment.limit - segment.pos);
            segment = segment.next;
            assert segment != null;
        }

        var remaining = byteCount;
        // Copy from one segment at a time.
        while (remaining > 0L) {
            assert segment != null;
            final var segmentCopy = segment.sharedCopy();
            segmentCopy.pos += (int) _offset;
            segmentCopy.limit = (int) Math.min(segmentCopy.pos + remaining, segmentCopy.limit);
            if (_out.head == null) {
                segmentCopy.prev = segmentCopy;
                segmentCopy.next = segmentCopy;
                _out.head = segmentCopy;
            } else {
                assert _out.head.prev != null;
                _out.head.prev.push(segmentCopy);
            }
            remaining -= segmentCopy.limit - segmentCopy.pos;
            _offset = 0L;
            segment = segment.next;
        }
        _out.byteSize += byteCount;

        return this;
    }

    @Override
    public @NonNull Buffer readTo(final @NonNull OutputStream out) {
        return readTo(out, byteSize);
    }

    @Override
    public @NonNull Buffer readTo(final @NonNull OutputStream out, final long byteCount) {
        Objects.requireNonNull(out);
        checkOffsetAndCount(byteSize, 0L, byteCount);

        if (byteCount == 0L) {
            return this;
        }

        var remaining = byteCount;
        var head = this.head;
        while (remaining > 0L) {
            assert head != null;
            final var toWrite = (int) Math.min(remaining, head.limit - head.pos);
            try {
                out.write(head.data, head.pos, toWrite);
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
            head.pos += toWrite;
            byteSize -= toWrite;
            remaining -= toWrite;

            if (head.pos == head.limit) {
                final var toRecycle = head;
                head = head.pop();
                this.head = head;
                SegmentPool.recycle(toRecycle);
            }
        }

        return this;
    }

    @Override
    public @NonNull Buffer transferFrom(final @NonNull InputStream input) {
        Objects.requireNonNull(input);
        return writeFrom(input, Long.MAX_VALUE, true);
    }

    @Override
    public @NonNull Buffer writeFrom(final @NonNull InputStream input, final long byteCount) {
        Objects.requireNonNull(input);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        return writeFrom(input, byteCount, false);
    }

    private @NonNull Buffer writeFrom(final @NonNull InputStream in,
                                      final long byteCount,
                                      final boolean forever) {
        assert in != null;

        var remaining = byteCount;
        while (remaining > 0L/* || forever*/) {
            final var tail = writableTail(1);
            final var toRead = (int) Math.min(remaining, Segment.SIZE - tail.limit);
            final int read;
            try {
                read = in.read(tail.data, tail.limit, toRead);
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
            if (read == -1) {
                if (tail.pos == tail.limit) {
                    // We allocated a tail segment, but didn't end up needing it. Recycle!
                    head = tail.pop();
                    SegmentPool.recycle(tail);
                }
                if (forever) {
                    return this;
                }
                throw new JayoEOFException();
            }
            tail.limit += read;
            byteSize += read;
            remaining -= read;
        }
        return this;
    }

    @Override
    public long completeSegmentByteCount() {
        var result = byteSize;
        if (result == 0L) {
            return 0L;
        }

        // Omit the tail if it's still writable.
        assert head != null;
        final var tail = head.prev;
        assert tail != null;
        if (tail.limit < Segment.SIZE && tail.owner) {
            result -= (tail.limit - tail.pos);
        }

        return result;
    }

    @Override
    public byte getByte(final long pos) {
        checkOffsetAndCount(byteSize, pos, 1L);
        return seek(pos, (segment, offset) -> segment.data[(int) (segment.pos + pos - offset)]);
    }

    @Override
    public boolean exhausted() {
        return byteSize == 0L;
    }


    @Override
    public boolean request(final long byteCount) {
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0L: " + byteCount);
        }
        return byteSize >= byteCount;
    }

    @Override
    public void require(final long byteCount) {
        if (!request(byteCount)) {
            throw new JayoEOFException();
        }
    }

    @Override
    public byte readByte() {
        final var head = this.head;
        if (head == null) {
            throw new JayoEOFException();
        }
        final var b = head.data[head.pos++];
        byteSize -= 1L;

        if (head.pos == head.limit) {
            this.head = head.pop();
            SegmentPool.recycle(head);
        }
        return b;
    }

    @Override
    public short readShort() {
        final var head = this.head;
        if (head == null) {
            throw new JayoEOFException();
        }
        // If the short is split across multiple segments, delegate to readByte().
        if (head.limit - head.pos < 2) {
            if (byteSize < 2L) {
                throw new JayoEOFException();
            }
            return (short) (((readByte() & 0xff) << 8) | (readByte() & 0xff));
        }

        final var s = (short) (((head.data[head.pos++] & 0xff) << 8) | (head.data[head.pos++] & 0xff));
        byteSize -= 2L;

        if (head.pos == head.limit) {
            this.head = head.pop();
            SegmentPool.recycle(head);
        }
        return s;
    }

    @Override
    public int readInt() {
        final var head = this.head;
        if (head == null) {
            throw new JayoEOFException();
        }
        // If the int is split across multiple segments, delegate to readByte().
        if (head.limit - head.pos < 4) {
            if (byteSize < 4L) {
                throw new JayoEOFException();
            }
            return (((readByte() & 0xff) << 24)
                    | ((readByte() & 0xff) << 16)
                    | ((readByte() & 0xff) << 8)
                    | (readByte() & 0xff));
        }

        final var i = (((head.data[head.pos++] & 0xff) << 24)
                | ((head.data[head.pos++] & 0xff) << 16)
                | ((head.data[head.pos++] & 0xff) << 8)
                | (head.data[head.pos++] & 0xff));
        byteSize -= 4L;

        if (head.pos == head.limit) {
            this.head = head.pop();
            SegmentPool.recycle(head);
        }
        return i;
    }

    @Override
    public long readLong() {
        final var head = this.head;
        if (head == null) {
            throw new JayoEOFException();
        }
        // If the long is split across multiple segments, delegate to readInt().
        if (head.limit - head.pos < 8) {
            if (byteSize < 8L) {
                throw new JayoEOFException();
            }
            return (((readInt() & 0xffffffffL) << 32) | (readInt() & 0xffffffffL));
        }

        final var l = (((head.data[head.pos++] & 0xffL) << 56)
                | ((head.data[head.pos++] & 0xffL) << 48)
                | ((head.data[head.pos++] & 0xffL) << 40)
                | ((head.data[head.pos++] & 0xffL) << 32)
                | ((head.data[head.pos++] & 0xffL) << 24)
                | ((head.data[head.pos++] & 0xffL) << 16)
                | ((head.data[head.pos++] & 0xffL) << 8)
                | (head.data[head.pos++] & 0xffL));
        byteSize -= 8L;

        if (head.pos == head.limit) {
            this.head = head.pop();
            SegmentPool.recycle(head);
        }
        return l;
    }

    @Override
    public long readDecimalLong() {
        if (byteSize == 0L) {
            throw new JayoEOFException();
        }

        // This value is always built negatively in order to accommodate Long.MIN_VALUE.
        var value = 0L;
        var seen = 0;
        var negative = false;
        var done = false;

        var overflowDigit = OVERFLOW_DIGIT_START;

        do {
            final var segment = this.head;
            assert segment != null;

            final var data = segment.data;
            var pos = segment.pos;
            final var limit = segment.limit;

            while (pos < limit) {
                final var b = data[pos];
                if (b >= (byte) ((int) '0') && b <= (byte) ((int) '9')) {
                    final var digit = (byte) ((int) '0') - b;

                    // Detect when the digit would cause an overflow.
                    if (value < OVERFLOW_ZONE || value == OVERFLOW_ZONE && digit < overflowDigit) {
                        final var buffer = new RealBuffer()
                                .writeDecimalLong(value)
                                .writeByte(b);
                        if (!negative) {
                            buffer.readByte(); // Skip negative sign.
                        }
                        throw new NumberFormatException("Number too large: " + buffer.readString());
                    }
                    value *= 10L;
                    value += digit;
                } else if (b == (byte) ((int) '-') && seen == 0) {
                    negative = true;
                    overflowDigit -= 1;
                } else {
                    // Set a flag to stop iteration. We still need to run through segment updating below.
                    done = true;
                    break;
                }
                pos++;
                seen++;
            }

            if (pos == limit) {
                this.head = segment.pop();
                SegmentPool.recycle(segment);
            } else {
                segment.pos = pos;
            }
        } while (!done && head != null);

        byteSize -= seen;

        final var minimumSeen = (negative) ? 2 : 1;
        if (seen < minimumSeen) {
            if (byteSize == 0L) {
                throw new JayoEOFException();
            }
            final var expected = (negative) ? "Expected a digit" : "Expected a digit or '-'";
            throw new NumberFormatException(expected + " but was 0x" + toHexString(getByte(0)));
        }

        return (negative) ? value : -value;
    }

    @Override
    public long readHexadecimalUnsignedLong() {
        if (byteSize == 0L) {
            throw new JayoEOFException();
        }

        var value = 0L;
        var seen = 0;
        var done = false;

        do {
            final var segment = this.head;
            assert segment != null;

            final var data = segment.data;
            var pos = segment.pos;
            final var limit = segment.limit;

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
                        throw new NumberFormatException("Expected leading [0-9a-fA-F] character but was 0x" +
                                toHexString(b));
                    }
                    // Set a flag to stop iteration. We still need to run through segment updating below.
                    done = true;
                    break;
                }

                // Detect when the shift overflows.
                if ((value & -0x1000000000000000L) != 0L) {
                    final var buffer = new RealBuffer().writeHexadecimalUnsignedLong(value).writeByte(b);
                    throw new NumberFormatException("Number too large: " + buffer.readString());
                }

                value = value << 4;
                value = value | (long) digit;
                pos++;
                seen++;
            }

            if (pos == limit) {
                this.head = segment.pop();
                SegmentPool.recycle(segment);
            } else {
                segment.pos = pos;
            }
        } while (!done && head != null);

        byteSize -= seen;
        return value;
    }

    @Override
    public @NonNull ByteString readByteString() {
        return readByteString(byteSize);
    }

    // Threshold determined empirically via ReadByteStringBenchmark
    /**
     * Create SegmentedByteString when size is greater than this many bytes.
     */
    private static final int SEGMENTING_THRESHOLD = 4096;

    @Override
    public @NonNull ByteString readByteString(final long byteCount) {
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        if (byteSize < byteCount) {
            throw new JayoEOFException();
        }
        if (byteCount == 0L) {
            return ByteString.EMPTY;
        }

        if (byteCount < SEGMENTING_THRESHOLD) {
            return new RealByteString(readByteArray(byteCount));
        }


        // Walk through the buffer to count how many segments we'll need.
        final var segmentCount = checkAndCountSegments((int) byteCount);

        // Walk through the buffer again to assign segments and build the directory.
        final var segments = new Segment[segmentCount];
        final var directory = new int[segmentCount];
        fillSegmentsAndDirectory(segments, directory, (int) byteCount, true);

        return new SegmentedByteString(segments, directory);
    }

    @Override
    public int select(final @NonNull Options options) {
        Objects.requireNonNull(options);

        final var _options = (RealOptions) options;
        final var index = selectPrefix(this, _options, false);
        // no match found
        if (index == -1) {
            return -1;
        }

        // If the prefix match actually matched a full byte string, consume it and return it.
        final var selectedSize = _options.byteStrings[index].byteSize();
        skip(selectedSize);
        return index;
    }

    @Override
    public void readTo(final @NonNull RawWriter destination, final long byteCount) {
        Objects.requireNonNull(destination);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0L: " + byteCount);
        }

        final var currentSize = byteSize;
        if (currentSize < byteCount) {
            destination.writeFrom(this, currentSize); // Exhaust ourselves.
            throw new JayoEOFException("Buffer exhausted before writing " + byteCount + " bytes. Only " + currentSize +
                    " bytes were written.");
        }
        destination.writeFrom(this, byteCount);
    }

    @Override
    public long readAllTo(final @NonNull RawWriter destination) {
        Objects.requireNonNull(destination);

        final var byteCount = byteSize;
        if (byteCount > 0L) {
            destination.writeFrom(this, byteCount);
        }
        return byteCount;
    }

    @Override
    public @NonNull String readString() {
        return readString(byteSize, StandardCharsets.UTF_8);
    }

    @Override
    public @NonNull String readString(final long byteCount) {
        return readString(byteCount, StandardCharsets.UTF_8);
    }

    @Override
    public @NonNull String readString(final @NonNull Charset charset) {
        return readString(byteSize, charset);
    }

    @Override
    public @NonNull String readString(final long byteCount, final @NonNull Charset charset) {
        Objects.requireNonNull(charset);
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        if (byteSize < byteCount) {
            throw new JayoEOFException();
        }
        if (byteCount == 0L) {
            return "";
        }

        final var head = this.head;
        assert head != null;
        if (byteCount > head.limit - head.pos) {
            // If the string spans multiple segments, delegate to readByteArray().
            return new String(readByteArray(byteCount), charset);
        }

        // else all bytes of this future String are in the head segment itself
        final var result = new String(head.data, head.pos, (int) byteCount, charset);
        head.pos += (int) byteCount;
        byteSize -= byteCount;

        if (head.pos == head.limit) {
            this.head = head.pop();
            SegmentPool.recycle(head);
        }

        return result;
    }

    @Override
    public @Nullable String readLine() {
        return readLine(StandardCharsets.UTF_8);
    }

    @Override
    public @Nullable String readLine(final @NonNull Charset charset) {
        Objects.requireNonNull(charset);

        final var newline = indexOf((byte) ((int) '\n'));
        if (newline != -1L) {
            return Utils.readUtf8Line(this, newline, charset);
        }
        if (byteSize != 0L) {
            return readString(byteSize, charset);
        }

        return null;
    }

    @Override
    public @NonNull String readLineStrict() {
        return readLineStrict(Long.MAX_VALUE, StandardCharsets.UTF_8);
    }

    @Override
    public @NonNull String readLineStrict(final @NonNull Charset charset) {
        return readLineStrict(Long.MAX_VALUE, charset);
    }

    @Override
    public @NonNull String readLineStrict(long limit) {
        return readLineStrict(limit, StandardCharsets.UTF_8);
    }

    @Override
    public @NonNull String readLineStrict(final long limit, final @NonNull Charset charset) {
        Objects.requireNonNull(charset);
        if (limit < 0L) {
            throw new IllegalArgumentException("limit < 0: " + limit);
        }

        final var scanLength = (limit == Long.MAX_VALUE) ? Long.MAX_VALUE : limit + 1L;
        final var newline = indexOf((byte) ((int) '\n'), 0L, scanLength);

        // a new line was found in the scanned bytes.
        if (newline != -1L) {
            return Utils.readUtf8Line(this, newline, charset);
        }

        // else check that the line was 'limit' bytes followed by \r\n.
        if (scanLength < byteSize &&
                getByte(scanLength - 1) == (byte) ((int) '\r') &&
                getByte(scanLength) == (byte) ((int) '\n')) {
            return Utils.readUtf8Line(this, scanLength, charset);
        }

        // else build and throw a JayoEOFException.
        final var data = new RealBuffer();
        copyTo(data, 0, Math.min(32, byteSize));
        throw new JayoEOFException(
                "\\n not found: limit=" + Math.min(byteSize, limit) + " content="
                        + data.readByteString().hex() + "'…'");
    }

    @Override
    public int readUtf8CodePoint() {
        if (byteSize == 0L) {
            throw new JayoEOFException();
        }

        final var b0 = getByte(0);
        int codePoint;
        final int byteCount;
        final int min;

        if (b0 >= 0) {
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

        if (byteSize < byteCount) {
            throw new JayoEOFException(
                    "size < " + byteCount + ": " + byteSize + " (to read code point prefixed 0x"
                            + toHexString(b0) + ")");
        }

        // Read the continuation bytes. If we encounter a non-continuation byte, the sequence consumed thus far is
        // truncated and is decoded as the replacement character. That non-continuation byte is left in the stream for
        // processing by the next call to readUtf8CodePoint().
        for (var i = 1; i < byteCount; i++) {
            final var b = getByte(i);
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
        return readByteArray(byteSize);
    }

    @Override
    public byte @NonNull [] readByteArray(final long byteCount) {
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        if (byteSize < byteCount) {
            throw new JayoEOFException();
        }
        if (byteCount == 0L) {
            return new byte[0];
        }

        final var result = new byte[(int) byteCount];
        readTo(result, 0, (int) byteCount);
        return result;
    }

    @Override
    public void readTo(final byte @NonNull [] destination) {
        readTo(destination, 0, destination.length);
    }

    @Override
    public void readTo(final byte @NonNull [] destination,
                       final int offset,
                       final int byteCount) {
        Objects.requireNonNull(destination);
        checkOffsetAndCount(destination.length, offset, byteCount);

        if (byteCount == 0L || byteSize == 0L) {
            return;
        }

        var _offset = offset;
        final var toWrite = (int) Math.min(byteCount, byteSize);
        var remaining = toWrite;
        while (remaining > 0) {
            final var bytesRead = readAtMostToPrivate(destination, _offset, remaining);
            _offset += bytesRead;
            remaining -= bytesRead;
        }

        if (toWrite < byteCount) {
            throw new JayoEOFException("could not write all the requested bytes to byte array, written " +
                    toWrite + "/" + byteCount);
        }
    }

    @Override
    public int readAtMostTo(final byte @NonNull [] destination) {
        return readAtMostTo(destination, 0, destination.length);
    }

    @Override
    public int readAtMostTo(final byte @NonNull [] destination,
                            final int offset,
                            final int byteCount) {
        Objects.requireNonNull(destination);
        checkOffsetAndCount(destination.length, offset, byteCount);

        if (byteCount == 0L) {
            return 0;
        }
        if (byteSize == 0L) {
            return -1;
        }

        return readAtMostToPrivate(destination, offset, byteCount);
    }

    private int readAtMostToPrivate(final byte @NonNull [] destination,
                                    final int offset,
                                    final int byteCount) {
        assert destination != null;

        final var head = this.head;
        assert head != null;
        final var toRead = Math.min(byteCount, head.limit - head.pos);
        System.arraycopy(head.data, head.pos, destination, offset, toRead);
        head.pos += toRead;
        byteSize -= toRead;

        if (head.pos == head.limit) {
            this.head = head.pop();
            SegmentPool.recycle(head);
        }
        return toRead;
    }

    @Override
    public int readAtMostTo(final @NonNull ByteBuffer destination) {
        Objects.requireNonNull(destination);

        if (byteSize == 0L) {
            return -1;
        }

        final var head = this.head;
        assert head != null;
        final var toRead = Math.min(destination.remaining(), head.limit - head.pos);
        destination.put(head.data, head.pos, toRead);
        head.pos += toRead;
        byteSize -= toRead;

        if (head.pos == head.limit) {
            this.head = head.pop();
            SegmentPool.recycle(head);
        }
        return toRead;
    }

    @Override
    public void clear() {
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "Buffer#{0} clear: Start clearing all {1} bytes from this{2}",
                    hashCode(), byteSize, System.lineSeparator());
        }
        if (byteSize == 0L) {
            return;
        }

        var segment = head;
        while (segment != null) {
            final var removed = segment;
            segment = segment.pop();
            SegmentPool.recycle(removed);
        }
        byteSize = 0L;
        head = null;
    }

    @Override
    public void skip(final long byteCount) {
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0L: " + byteCount);
        }
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "Buffer#{0} skip: Start skipping {1} bytes{2}",
                    hashCode(), byteCount, System.lineSeparator());
        }
        if (byteCount == 0L) {
            return;
        }
        final var toSkip = Math.min(byteCount, byteSize);
        skipInternal(toSkip);
        if (toSkip < byteCount) {
            throw new JayoEOFException("could not skip " + byteCount + " bytes, skipped: " + toSkip);
        }
    }

    void skipInternal(final long byteCount) {
        var remaining = byteCount;
        while (remaining > 0L) {
            final var head = this.head;
            assert head != null;
            final var toSkip = (int) Math.min(remaining, head.limit - head.pos);
            head.pos += toSkip;
            byteSize -= toSkip;
            remaining -= toSkip;

            if (head.pos == head.limit) {
                this.head = head.pop();
                SegmentPool.recycle(head);
            }
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "Buffer#{0} : Finished skipping {1} bytes from this{2}",
                    hashCode(), byteCount, System.lineSeparator());
        }
    }

    @Override
    public @NonNull Buffer write(final @NonNull ByteString byteString) {
        return write(byteString, 0, byteString.byteSize());
    }

    @Override
    public @NonNull Buffer write(final @NonNull ByteString byteString,
                                 final int offset,
                                 final int byteCount) {
        Objects.requireNonNull(byteString);

        if (byteString instanceof RealByteString realByteString) {
            realByteString.write(this, offset, byteCount);
        } else if (byteString instanceof SegmentedByteString segmentedByteString) {
            segmentedByteString.write(this, offset, byteCount);
        } else {
            throw new IllegalArgumentException(
                    "byteString must be an instance of RealByteString or SegmentedByteString");
        }
        return this;
    }

    @Override
    public @NonNull Buffer write(final @NonNull String string) {
        return write(string, StandardCharsets.UTF_8);
    }

    @Override
    public @NonNull Buffer write(final @NonNull String string, final @NonNull Charset charset) {
        Objects.requireNonNull(string);

        final byte[] stringBytes;
        // Unsafe fast-path for ISO_8859_1 encoding
        if (charset.equals(StandardCharsets.ISO_8859_1) && UNSAFE_AVAILABLE && isLatin1(string)) {
            stringBytes = getBytes(string);
        } else {
            stringBytes = string.getBytes(charset);
        }
        return write(stringBytes);
    }

    @SuppressWarnings("resource")
    @Override
    public @NonNull Buffer writeUtf8CodePoint(final int codePoint) {
        if (codePoint < 0x80) {
            // Emit a 7-bit code point with 1 byte.
            writeByte((byte) codePoint);
        } else if (codePoint < 0x800) {
            // Emit a 11-bit code point with 2 bytes.
            final var tail = writableTail(2);
            final var data = tail.data;
            data[tail.limit++] = (byte) (codePoint >> 6 | 0xc0); // 110xxxxx
            data[tail.limit++] = (byte) (codePoint & 0x3f | 0x80); // 10xxxxxx
            byteSize += 2L;

        } else if (codePoint >= 0xd800 && codePoint <= 0xdfff) {
            // Emit a replacement character for a partial surrogate.
            writeByte((byte) ((int) '?'));

        } else if (codePoint < 0x10000) {
            // Emit a 16-bit code point with 3 bytes.
            final var tail = writableTail(3);
            final var data = tail.data;
            data[tail.limit++] = (byte) (codePoint >> 12 | 0xe0); // 1110xxxx
            data[tail.limit++] = (byte) (codePoint >> 6 & 0x3f | 0x80); // 10xxxxxx
            data[tail.limit++] = (byte) (codePoint & 0x3f | 0x80); // 10xxxxxx
            byteSize += 3L;

        } else if (codePoint <= 0x10ffff) {
            // Emit a 21-bit code point with 4 bytes.
            final var tail = writableTail(4);
            final var data = tail.data;
            data[tail.limit++] = (byte) (codePoint >> 18 | 0xf0); // 11110xxx
            data[tail.limit++] = (byte) (codePoint >> 12 & 0x3f | 0x80); // 10xxxxxx
            data[tail.limit++] = (byte) (codePoint >> 6 & 0x3f | 0x80); // 10xxyyyy
            data[tail.limit++] = (byte) (codePoint & 0x3f | 0x80); // 10yyyyyy
            byteSize += 4L;

        } else {
            throw new IllegalArgumentException("Unexpected code point: 0x" + toHexString(codePoint));
        }

        return this;
    }

    @Override
    public @NonNull Buffer write(final byte @NonNull [] source) {
        return write(source, 0, source.length);
    }

    @Override
    public @NonNull Buffer write(final byte @NonNull [] source,
                                 final int offset,
                                 final int byteCount) {
        Objects.requireNonNull(source);
        checkOffsetAndCount(source.length, offset, byteCount);

        final var limit = offset + byteCount;
        var _offset = offset;
        while (_offset < limit) {
            final var tail = writableTail(1);
            final var toWrite = Math.min(limit - _offset, Segment.SIZE - tail.limit);
            System.arraycopy(source, _offset, tail.data, tail.limit, toWrite);
            tail.limit += toWrite;
            _offset += toWrite;
        }
        byteSize += byteCount;
        return this;
    }

    @Override
    public int writeAllFrom(final @NonNull ByteBuffer source) {
        Objects.requireNonNull(source);

        final var byteBufferSize = source.remaining();
        var remaining = byteBufferSize;
        while (remaining > 0) {
            final var tail = writableTail(1);
            final var toWrite = Math.min(remaining, Segment.SIZE - tail.limit);
            source.get(tail.data, tail.limit, toWrite);
            tail.limit += toWrite;
            remaining -= toWrite;
        }
        byteSize += byteBufferSize;
        return byteBufferSize;
    }

    @Override
    public long writeAllFrom(final @NonNull RawReader source) {
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
    public @NonNull Buffer writeFrom(final @NonNull RawReader source, final long byteCount) {
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
        final var tail = writableTail(1);
        tail.data[tail.limit++] = b;
        byteSize++;
        return this;
    }

    @Override
    public @NonNull Buffer writeShort(final short s) {
        final var tail = writableTail(2);
        final var data = tail.data;
        data[tail.limit++] = (byte) (s >>> 8 & 0xff);
        data[tail.limit++] = (byte) (s & 0xff);
        byteSize += 2L;
        return this;
    }

    @Override
    public @NonNull Buffer writeInt(final int i) {
        final var tail = writableTail(4);
        final var data = tail.data;
        data[tail.limit++] = (byte) (i >>> 24 & 0xff);
        data[tail.limit++] = (byte) (i >>> 16 & 0xff);
        data[tail.limit++] = (byte) (i >>> 8 & 0xff);
        data[tail.limit++] = (byte) (i & 0xff);
        byteSize += 4L;
        return this;
    }

    @Override
    public @NonNull Buffer writeLong(final long l) {
        final var tail = writableTail(8);
        final var data = tail.data;
        data[tail.limit++] = (byte) (l >>> 56 & 0xffL);
        data[tail.limit++] = (byte) (l >>> 48 & 0xffL);
        data[tail.limit++] = (byte) (l >>> 40 & 0xffL);
        data[tail.limit++] = (byte) (l >>> 32 & 0xffL);
        data[tail.limit++] = (byte) (l >>> 24 & 0xffL);
        data[tail.limit++] = (byte) (l >>> 16 & 0xffL);
        data[tail.limit++] = (byte) (l >>> 8 & 0xffL);
        data[tail.limit++] = (byte) (l & 0xffL);
        byteSize += 8L;
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
                return write("-9223372036854775808");
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

        final var tail = writableTail(width);
        final var data = tail.data;
        var pos = tail.limit + width; // We write backwards from right to left.
        while (_l != 0L) {
            final var digit = (int) (_l % 10);
            data[--pos] = HEX_DIGIT_BYTES[digit];
            _l /= 10;
        }
        if (negative) {
            data[--pos] = (byte) ((int) '-');
        }
        tail.limit += width;
        byteSize += width;
        return this;
    }

    @Override
    public @NonNull Buffer writeHexadecimalUnsignedLong(final long l) {
        if (l == 0L) {
            // Both a shortcut and required since the following code can't handle zero.
            return writeByte((byte) ((int) '0'));
        }

        // Mask every bit below the most significant bit to a 1
        // https://aggregate.org/MAGIC/#Most%20Significant%201%20Bit
        final var width = getHexadecimalUnsignedLongWidth(l);

        var _l = l;
        final var tail = writableTail(width);
        final var data = tail.data;
        var pos = tail.limit + width - 1; // We write backwards from right to left.
        while (pos >= tail.limit) {
            data[pos--] = HEX_DIGIT_BYTES[(int) (_l & 0xF)];
            _l = _l >>> 4;
        }
        tail.limit += width;
        byteSize += width;
        return this;
    }

    @Override
    public void writeFrom(final @NonNull Buffer source, final long byteCount) {
        // Move bytes from the head of the source buffer to the tail of this buffer in the most possible effective way!
        // This method is one of the most crucial parts of the Jayo concept based on Buffer = a queue of segments.
        //
        // We must do it while balancing two conflicting goals: don't waste CPU and don't waste memory.
        //
        //
        // Don't waste CPU (i.e., don't copy data around).
        //
        // Copying large amounts of data is expensive. Instead, we prefer to reassign entire segments from one buffer to
        // the other.
        //
        //
        // Don't waste memory.
        //
        // As an invariant, adjacent pairs of segments in a buffer should be at least 50% full, except for the head
        // segment and the tail segment.
        //
        // The head segment cannot maintain the invariant because the application is consuming bytes from this segment,
        // decreasing its level.
        //
        // The tail segment cannot maintain the invariant because the application is producing bytes, which may require
        // new nearly empty tail segments to be appended.
        //
        //
        // Moving segments between buffers.
        //
        // When writing one buffer to another, we prefer to reassign entire segments over copying bytes into their most
        // compact form. Suppose we have a buffer with these segment levels [91%, 61%]. If we append a buffer with a
        // single [72%] segment, that yields [91%, 61%, 72%]. No bytes are copied.
        //
        // Or suppose we have a buffer with these segment levels: [100%, 2%], and we want to append it to a buffer with
        // these segment levels [99%, 3%]. This operation will yield the following segments: [100%, 2%, 99%, 3%]. That
        // is, we do not spend time copying bytes around to achieve more efficient memory use like [100%, 100%, 4%].
        //
        // When combining buffers, we will compact adjacent buffers when their combined level doesn't exceed 100%. For
        // example, when we start with [100%, 40%] and append [30%, 80%], the result is [100%, 70%, 80%].
        //
        //
        // Splitting segments.
        //
        // Occasionally we write only a part of a reader buffer to a writer buffer. For example, given a writer
        // [51%, 91%], we may want to write the first 30% of a reader [92%, 82%] to it. To simplify, we first transform
        // the reader to an equivalent buffer [30%, 62%, 82%] and then move the head segment.
        // The final result is writer [51%, 91%, 30%] and reader [62%, 82%].

        if (Objects.requireNonNull(source) == this) {
            throw new IllegalArgumentException("source == this, cannot write in itself");
        }
        checkOffsetAndCount(source.bytesAvailable(), 0, byteCount);
        if (byteCount == 0L) {
            return;
        }
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "Buffer#{0}: Start writing {1} bytes from source buffer {2} into this buffer{3}",
                    hashCode(), byteCount, source.hashCode(), System.lineSeparator());
        }

        final var src = (RealBuffer) source;
        var remaining = byteCount;
        while (remaining > 0L) {
            var srcHead = src.head;
            assert srcHead != null;
            final var tail = (head != null) ? head.prev : null;
            // Is a prefix of the source's head segment all that we need to move?
            if (remaining < srcHead.limit - srcHead.pos) {
                if (tail != null && tail.owner &&
                        remaining + tail.limit - ((tail.isShared()) ? 0 : tail.pos) <= Segment.SIZE) {
                    // Our existing segments are sufficient. Move bytes from the source's head to our tail.
                    srcHead.writeTo(tail, (int) remaining);
                    src.byteSize -= remaining;
                    byteSize += remaining;
                    return;
                }
                // We're going to need another segment. Split the source's head segment in two, then move the first
                // of those two to this buffer.
                srcHead = srcHead.splitHead((int) remaining);
            } else {
                src.head = srcHead.pop();
            }

            // We removed the source's head segment, now we append it to our tail.
            final var movedByteCount = srcHead.limit - srcHead.pos;
            if (tail == null) {
                head = srcHead;
                srcHead.prev = srcHead;
                srcHead.next = srcHead;
            } else if (mustPushNewTail(tail, srcHead)) {
                tail.push(srcHead);
            }
            remaining -= movedByteCount;
            src.byteSize -= movedByteCount;
            byteSize += movedByteCount;
        }
    }

    /**
     * Call this when the tail and its predecessor may both be less than half full. In this case, we will copy data so
     * that a segment can be recycled.
     */
    private static boolean mustPushNewTail(final @NonNull Segment currentTail, final @NonNull Segment newTail) {
        assert currentTail != null;
        assert newTail != null;

        if (!currentTail.owner) {
            return true; // Cannot compact: current tail isn't writable.
        }
        final var toWrite = newTail.limit - newTail.pos;
        final var availableInCurrentTail = Segment.SIZE - currentTail.limit
                + ((currentTail.isShared()) ? 0 : currentTail.pos);
        if (toWrite > availableInCurrentTail) {
            return true; // Cannot compact: not enough writable space in the current tail.
        }

        newTail.writeTo(currentTail, toWrite);
        SegmentPool.recycle(newTail);
        return false;
    }

    @Override
    public long readAtMostTo(final @NonNull Buffer destination, final long byteCount) {
        Objects.requireNonNull(destination);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }

        if (byteSize == 0L) {
            return -1L;
        }

        final var toWrite = Math.min(byteCount, byteSize);
        destination.writeFrom(this, toWrite);
        return toWrite;
    }

    @Override
    public long indexOf(final byte b) {
        return indexOf(b, 0L, Long.MAX_VALUE);
    }

    @Override
    public long indexOf(final byte b, final long startIndex) {
        return indexOf(b, startIndex, Long.MAX_VALUE);
    }

    @Override
    public long indexOf(final byte b, final long startIndex, final long endIndex) {
        if (startIndex < 0 || startIndex > endIndex) {
            throw new IllegalArgumentException("size=" + byteSize + " startIndex=" + startIndex
                    + " endIndex=" + endIndex);
        }

        final long _endIndex = Math.min(endIndex, byteSize);
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
                final var limit = (int) Math.min(segment.limit, segment.pos + _endIndex - offset);
                var pos = (int) (segment.pos + _startIndex - offset);
                while (pos < limit) {
                    if (data[pos] == b) {
                        return pos - segment.pos + offset;
                    }
                    pos++;
                }

                // Not in this segment. Try the next one.
                offset += (segment.limit - segment.pos);
                _startIndex = offset;
                segment = segment.next;
                assert segment != null;
            }

            return -1L;
        });
    }

    @Override
    public long indexOf(final @NonNull ByteString byteString) {
        return indexOf(byteString, 0L);
    }

    @Override
    public long indexOf(final @NonNull ByteString byteString, final long startIndex) {
        return indexOf(byteString, startIndex, Long.MAX_VALUE);
    }

    @Override
    public long indexOf(final @NonNull ByteString byteString, final long startIndex, final long endIndex) {
        Objects.requireNonNull(byteString);
        if (byteString.isEmpty()) {
            return 0L;
        }
        return indexOfInternal(byteString, 0, byteString.byteSize(), startIndex, endIndex);
    }

    long indexOfInternal(final @NonNull ByteString byteString,
                         int byteStringOffset,
                         int byteCount,
                         final long startIndex,
                         final long endIndex) {
        assert byteString != null;
        checkOffsetAndCount(byteString.byteSize(), byteStringOffset, byteCount);
        if (byteCount <= 0L) {
            throw new IllegalArgumentException("byteCount <= 0: " + byteCount);
        }
        if (startIndex < 0) {
            throw new IllegalArgumentException("startIndex <= 0: " + startIndex);
        }
        if (startIndex > endIndex) {
            throw new IllegalArgumentException("startIndex > endIndex:" + startIndex + " > " + endIndex);
        }

        final long _endIndex = Math.min(endIndex, byteSize);
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

            // Scan through the segments, searching for the lead byte. Each time that is found, delegate to
            // rangeEquals() to check for a complete match.
            final var targetByteArray = Utils.internalArray(byteString);
            final var b0 = targetByteArray[byteStringOffset];
            final var resultLimit = Math.min(_endIndex, byteSize - byteCount + 1L);
            while (offset < resultLimit) {
                // Scan through the current segment.
                final var data = segment.data;
                final var segmentLimit = (int) Math.min(segment.limit, segment.pos + resultLimit - offset);
                for (var pos = (int) (segment.pos + _startIndex - offset); pos < segmentLimit; pos++) {
                    if (data[pos] == b0
                            && rangeEquals(segment,
                            pos + 1, targetByteArray,
                            byteStringOffset + 1,
                            byteCount)) {
                        return pos - segment.pos + offset;
                    }
                }

                // Not in this segment. Try the next one.
                offset += (segment.limit - segment.pos);
                _startIndex = offset;
                segment = segment.next;
                assert segment != null;
            }

            return -1L;
        });
    }

    @Override
    public long indexOfElement(final @NonNull ByteString targetBytes) {
        return indexOfElement(targetBytes, 0L);
    }

    @Override
    public long indexOfElement(final @NonNull ByteString targetBytes, final long startIndex) {
        Objects.requireNonNull(targetBytes);
        if (startIndex < 0L) {
            throw new IllegalArgumentException("startIndex < 0: " + startIndex);
        }

        return seek(startIndex, (s, o) -> {
            if (s == null) {
                return -1L;
            }
            var segment = s;
            var offset = o;
            var _startIndex = startIndex;

            // Special case searching for one of two bytes. This is a common case for tools like Moshi, which search for
            // pairs of chars like `\r` and `\n` or {@code `"` and `\`. The impact of this optimization is a ~5x speedup
            // for this case without a significant cost to other cases.
            if (targetBytes.byteSize() == 2) {
                // Scan through the segments, searching for either of the two bytes.
                final var b0 = targetBytes.getByte(0);
                final var b1 = targetBytes.getByte(1);
                while (offset < byteSize) {
                    final var data = segment.data;
                    var pos = (int) (segment.pos + _startIndex - offset);
                    final var currentLimit = segment.limit;
                    while (pos < currentLimit) {
                        final var b = (int) data[pos];
                        if (b == (int) b0 || b == (int) b1) {
                            return pos - segment.pos + offset;
                        }
                        pos++;
                    }

                    // Not in this segment. Try the next one.
                    offset += (currentLimit - segment.pos);
                    _startIndex = offset;
                    segment = segment.next;
                    assert segment != null;
                }
            } else {
                // Scan through the segments, searching for a byte that's also in the array.
                final var targetByteArray = Utils.internalArray(targetBytes);
                while (offset < byteSize) {
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
                    assert segment != null;
                }
            }

            return -1L;
        });
    }

    @Override
    public boolean rangeEquals(final long offset, final @NonNull ByteString byteString) {
        return rangeEquals(offset, byteString, 0, byteString.byteSize());
    }

    @Override
    public boolean rangeEquals(final long offset,
                               final @NonNull ByteString byteString,
                               final int byteStringOffset,
                               final int byteCount) {
        Objects.requireNonNull(byteString);
        if (byteCount < 0 ||
                offset < 0L || offset + byteCount > byteSize ||
                byteStringOffset < 0 || byteStringOffset + byteCount > byteString.byteSize()) {
            return false;
        }

        if (byteCount == 0) {
            return true;
        }

        return indexOfInternal(byteString, byteStringOffset, byteCount, offset, offset + 1) != -1L;
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    @Override
    public @NonNull ByteString hash(final @NonNull Digest digest) {
        final var messageDigest = messageDigest(digest);
        final var head = this.head;
        if (head != null) {
            messageDigest.update(head.data, head.pos, head.limit - head.pos);
            var segment = head.next;
            while (segment != head) {
                assert segment != null;
                messageDigest.update(segment.data, segment.pos, segment.limit - segment.pos);
                segment = segment.next;
            }
        }
        return new RealByteString(messageDigest.digest());
    }

    @Override
    public @NonNull ByteString hmac(final @NonNull Hmac hMac, final @NonNull ByteString key) {
        final var javaMac = mac(hMac, key);
        final var head = this.head;
        if (head != null) {
            javaMac.update(head.data, head.pos, head.limit - head.pos);
            var segment = head.next;
            while (segment != head) {
                assert segment != null;
                javaMac.update(segment.data, segment.pos, segment.limit - segment.pos);
                segment = segment.next;
            }
        }
        return new RealByteString(javaMac.doFinal());
    }

    @Override
    public @NonNull String toString() {
        if (byteSize == 0L) {
            return "Buffer(size=0)";
        }

        final var maxPrintableBytes = 64;
        var toPrint = (int) Math.min(maxPrintableBytes, byteSize);

        final var builder = new StringBuilder(toPrint * 2 + ((byteSize > maxPrintableBytes) ? 1 : 0));

        var segment = head;
        while (true) {
            assert segment != null;
            final var data = segment.data;
            var pos = segment.pos;
            final var limit = Math.min(segment.limit, pos + toPrint);

            while (pos < limit) {
                final var b = (int) data[pos++];
                toPrint--;
                // @formatter:off
                builder.append(HEX_DIGIT_CHARS[b >> 4 & 0xf])
                        .append(HEX_DIGIT_CHARS[b      & 0xf]);
                // @formatter:on
            }
            if (toPrint == 0) {
                break;
            }
            segment = segment.next;
        }

        if (byteSize > maxPrintableBytes) {
            builder.append('…');
        }

        return "Buffer(size=" + byteSize + " hex=" + builder + ")";
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public @NonNull Buffer clone() {
        final var result = new RealBuffer();
        final var head = this.head;
        if (head == null) {
            return result;
        }

        final var headCopy = head.sharedCopy();

        result.head = headCopy;
        headCopy.prev = result.head;
        headCopy.next = headCopy.prev;

        var segment = head.next;
        while (segment != head) {
            assert segment != null;
            headCopy.prev.push(segment.sharedCopy());
            segment = segment.next;
        }

        result.byteSize = byteSize;
        return result;
    }

    @Override
    public @NonNull ByteString snapshot() {
        final var size = byteSize;
        if (size > Integer.MAX_VALUE) {
            throw new IllegalStateException("size > Integer.MAX_VALUE: " + byteSize);
        }
        return snapshot((int) size);
    }

    @Override
    public @NonNull ByteString snapshot(final int byteCount) {
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }

        if (byteCount == 0) {
            return ByteString.EMPTY;
        }

        // Walk through the buffer to count how many segments we'll need.
        final var segmentCount = checkAndCountSegments(byteCount);

        // Walk through the buffer again to assign segments and build the directory.
        final var segments = new Segment[segmentCount];
        final var directory = new int[segmentCount];
        fillSegmentsAndDirectory(segments, directory, byteCount, false);

        return new SegmentedByteString(segments, directory);
    }

    private int checkAndCountSegments(final int byteCount) {
        checkOffsetAndCount(byteSize, 0L, byteCount);

        var offset = 0;
        var segmentCount = 0;
        var segment = this.head;
        while (offset < byteCount) {
            assert segment != null;
            if (segment.limit == segment.pos) {
                // Empty segment. This should not happen!
                throw new AssertionError("segment.limit == segment.pos");
            }
            offset += segment.limit - segment.pos;
            segmentCount++;
            segment = segment.next;
        }
        return segmentCount;
    }

    private void fillSegmentsAndDirectory(final Segment @NonNull [] segments,
                                          final int @NonNull [] directory,
                                          final int byteCount,
                                          final boolean consume) {
        assert segments != null;
        assert directory != null;

        var offset = 0;
        var segmentCount = 0;
        var segment = head;
        while (offset < byteCount) {
            assert segment != null;
            final var copy = segment.sharedCopy();
            segments[segmentCount] = copy;
            final var segmentSize = segment.limit - segment.pos;
            offset += segmentSize;
            // Despite sharing more bytes, only report having up to byteCount.
            directory[segmentCount] = Math.min(offset, byteCount);
            segmentCount++;

            if (consume) {
                if (offset > byteCount) {
                    // partial sharing of this segment
                    final var toRead = segmentSize - (offset - byteCount);
                    segment.pos += toRead;
                    byteSize -= toRead;
                } else {
                    // full share
                    segment = segment.pop();
                    this.head = segment;
                    byteSize -= segmentSize;
                }
            } else {
                segment = segment.next;
            }
        }
    }

    @Override
    public @NonNull UnsafeCursor readUnsafe() {
        return readUnsafe(new RealUnsafeCursor());
    }

    @Override
    public @NonNull UnsafeCursor readUnsafe(final @NonNull UnsafeCursor unsafeCursor) {
        Objects.requireNonNull(unsafeCursor);
        if (unsafeCursor.buffer != null) {
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
        Objects.requireNonNull(unsafeCursor);
        if (unsafeCursor.buffer != null) {
            throw new IllegalStateException("this cursor is already attached to a buffer");
        }

        unsafeCursor.buffer = this;
        unsafeCursor.readWrite = true;
        return unsafeCursor;
    }

    long withHeadsAsByteBuffers(final long toRead,
                                final @NonNull ToLongFunction<@NonNull ByteBuffer @NonNull []> readAction) {
        assert readAction != null;
        assert toRead > 0;

        // 1) build the ByteBuffer list to read from
        final var byteBuffers = new ArrayList<ByteBuffer>();
        var segment = this.head;
        var remaining = toRead;
        while (remaining > 0) {
            assert segment != null;
            final var toReadInSegment = (int) Math.min(remaining, segment.limit - segment.pos);
            byteBuffers.add(segment.asByteBuffer(segment.pos, toReadInSegment));
            remaining -= toReadInSegment;
            segment = segment.next;
        }
        final var sources = byteBuffers.toArray(new ByteBuffer[0]);

        // 2) call readAction
        final var read = readAction.applyAsLong(sources);
        if (read <= 0) {
            return read;
        }

        // 3) apply changes to head segments
        remaining = read;
        while (remaining > 0) {
            final var head = this.head;
            assert head != null;
            final var readFromHead = (int) Math.min(remaining, head.limit - head.pos);
            head.pos += readFromHead;
            byteSize -= readFromHead;
            remaining -= readFromHead;

            if (head.pos == head.limit) {
                this.head = head.pop();
                SegmentPool.recycle(head);
            }
        }

        return read;
    }

    /**
     * @return the head of this buffer, that may have been aggregated with follow-up segments so it contains
     * {@code toRead} bytes.
     */
    @NonNull
    Segment aggregatedHead(final int toRead) {
        var head = this.head;
        assert head != null;

        // 1) adapt the head segment to red from, compact it if needed
        final var bytesInHead = head.limit - head.pos;

        if (bytesInHead < toRead) {
            // must compact several segments in the head alone.
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "Compact head start, Buffer:{0}{1},{2}",
                        System.lineSeparator(), this, System.lineSeparator());
            }

            var _toRead = toRead - bytesInHead;
            if (needNewHead(head, bytesInHead, _toRead)) {
                // we swap head and its content for a writable one with data shifted at the start
                final var newHead = SegmentPool.take();
                head.writeTo(newHead, bytesInHead);
                this.head = newHead;
                head.push(newHead);
                head.pop();
                SegmentPool.recycle(head);
                head = newHead;
            }

            // copy bytes from next segments into the head
            var segment = head.next;
            while (_toRead > 0) {
                assert segment != null;
                final var toTransfer = Math.min(_toRead, segment.limit - segment.pos);
                System.arraycopy(segment.data, segment.pos, head.data, head.limit, toTransfer);
                segment.pos += toTransfer;
                head.limit += toTransfer;
                _toRead -= toTransfer;

                if (segment.pos == segment.limit) {
                    final var toRecycle = segment;
                    segment = segment.pop();
                    SegmentPool.recycle(toRecycle);
                }
            }

            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "Compact head end, SegmentQueue:{0}{1},{2}",
                        System.lineSeparator(), this, System.lineSeparator());
            }
        }

        return head;
    }

    private static boolean needNewHead(final @NonNull Segment currentHead,
                                       final int bytesInHead,
                                       final int toRead) {
        assert currentHead != null;

        final var available = Segment.SIZE - currentHead.limit;
        if (available >= toRead) {
            return false; // The current head has enough writable space
        }

        if (!currentHead.owner) {
            return true; // Cannot compact: current head isn't writable.
        }
        if (currentHead.isShared() || toRead > available + currentHead.pos) {
            return true; // Cannot compact: not enough writable space in the current head.
        }

        // 1.1) compact by shifting bytes in the head
        System.arraycopy(currentHead.data, currentHead.pos, currentHead.data, 0, bytesInHead);
        currentHead.limit = bytesInHead;
        currentHead.pos = 0;
        return false;
    }

    /**
     * Invoke {@code lambda} with the segment and offset at {@code startIndex}. Searches from the front or the back
     * depending on what's closer to {@code startIndex}.
     */
    private <T> T seek(final long startIndex, final @NonNull BiFunction<Segment, Long, T> lambda) {
        assert lambda != null;

        var segment = head;
        if (segment == null) {
            return lambda.apply(null, -1L);
        }

        long offset;
        if (byteSize - startIndex < startIndex) {
            // We're scanning in the back half of this buffer. Find the segment starting at the back.
            offset = byteSize;
            while (offset > startIndex) {
                segment = segment.prev;
                assert segment != null;
                offset -= (segment.limit - segment.pos);
            }
        } else {
            // We're scanning in the front half of this buffer. Find the segment starting at the front.
            offset = 0L;
            while (segment != null) {
                final var nextOffset = offset + (segment.limit - segment.pos);
                if (nextOffset > startIndex) {
                    break;
                }
                segment = segment.next;
                offset = nextOffset;
            }
        }
        return lambda.apply(segment, offset);
    }

    /**
     * Returns true if the range within this buffer starting at {@code segmentPos} in {@code segment} is equal to
     * {@code bytes[1..bytesLimit)}.
     */
    private static boolean rangeEquals(final @NonNull Segment segment,
                                       final int segmentPos,
                                       final byte @NonNull [] bytes,
                                       final int bytesOffset,
                                       final int bytesLimit) {
        assert segment != null;
        assert bytes != null;

        var _segment = segment;
        var data = _segment.data;
        var _segmentPos = segmentPos;
        var segmentLimit = _segment.limit;

        var i = bytesOffset;
        while (i < bytesLimit) {
            if (_segmentPos == segmentLimit) {
                _segment = _segment.next;
                assert _segment != null;
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
            public String toString() {
                return RealBuffer.this + ".asOutputStream()";
            }
        };
    }

    @Override
    public @NonNull InputStream asInputStream() {
        return new InputStream() {
            @Override
            public int read() {
                if (byteSize > 0L) {
                    return readByte() & 0xff;
                }
                return -1;
            }

            @Override
            public int read(final byte @NonNull [] writer, final int offset, final int byteCount) {
                return readAtMostTo(writer, offset, byteCount);
            }

            @Override
            public byte @NonNull [] readAllBytes() {
                return readByteArray();
            }

            @Override
            public byte @NonNull [] readNBytes(final int len) {
                return readByteArray(len);
            }

            @Override
            public long skip(final long byteCount) {
                if (byteCount <= 0L) {
                    return 0L;
                }
                final var toSkip = Math.min(byteCount, byteSize);
                skipInternal(toSkip);
                return toSkip;
            }

            @Override
            public int available() {
                return (int) Math.min(byteSize, Integer.MAX_VALUE);
            }

            @Override
            public String toString() {
                return RealBuffer.this + ".asInputStream()";
            }
        };
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
            public int read(final @NonNull ByteBuffer writer) {
                return RealBuffer.this.readAtMostTo(writer);
            }

            @Override
            public int write(final @NonNull ByteBuffer reader) {
                return RealBuffer.this.writeAllFrom(reader);
            }

            @Override
            public boolean isOpen() {
                return RealBuffer.this.isOpen();
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

    public static final class RealUnsafeCursor extends UnsafeCursor {
        private Segment segment = null;

        @Override
        public int next() {
            if (buffer == null) {
                throw new IllegalStateException("not attached to a buffer");
            }
            if (offset == buffer.bytesAvailable()) {
                throw new IllegalStateException("no more bytes");
            }
            return (offset == -1L) ? seek(0L) : seek(offset + (limit - pos));
        }

        @Override
        public int seek(final long offset) {
            if (buffer == null) {
                throw new IllegalStateException("not attached to a buffer");
            }
            final var _buffer = (RealBuffer) buffer;
            if (offset < -1L || offset > _buffer.byteSize) {
                throw new ArrayIndexOutOfBoundsException("offset=" + offset + " > size=" + _buffer.byteSize);
            }

            if (offset == -1L || offset == _buffer.byteSize) {
                this.segment = null;
                this.offset = offset;
                this.data = null;
                this.pos = -1;
                this.limit = -1;
                return -1;
            }

            // Navigate to the segment that contains `offset`. Start from our current segment if possible.
            var min = 0L;
            var max = _buffer.byteSize;
            var head = _buffer.head;
            var tail = _buffer.head;
            if (this.segment != null) {
                final var segmentOffset = this.offset - (this.pos - this.segment.pos);
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
                next = head;
                nextOffset = min;
                assert next != null;
                while (offset >= nextOffset + (next.limit - next.pos)) {
                    nextOffset += (next.limit - next.pos);
                    next = next.next;
                    assert next != null;
                }
            } else {
                // Start at the 'end' and search backwards
                next = tail;
                nextOffset = max;
                while (nextOffset > offset) {
                    assert next != null;
                    next = next.prev;
                    assert next != null;
                    nextOffset -= (next.limit - next.pos);
                }
            }

            // If we're going to write and our segment is shared, swap it for a read-write one.
            if (readWrite && next.isShared()) {
                final var unsharedNext = next.unsharedCopy();
                if (_buffer.head == next) {
                    _buffer.head = unsharedNext;
                }
                next = next.push(unsharedNext);
                assert next.prev != null;
                next.prev.pop();
            }

            // Update this cursor to the requested offset within the found segment.
            this.segment = next;
            this.offset = offset;
            this.data = next.data;
            this.pos = next.pos + (int) (offset - nextOffset);
            this.limit = next.limit;
            return limit - pos;
        }

        @Override
        public long resizeBuffer(final long newSize) {
            if (buffer == null) {
                throw new IllegalStateException("not attached to a buffer");
            }
            if (!readWrite) {
                throw new IllegalStateException("resizeBuffer() is only permitted for read/write buffers");
            }
            final var _buffer = (RealBuffer) buffer;

            final var oldSize = _buffer.byteSize;
            if (newSize <= oldSize) {
                if (newSize < 0L) {
                    throw new IllegalArgumentException("newSize < 0: " + newSize);
                }
                // Shrink the buffer by either shrinking segments or removing them.
                var bytesToSubtract = oldSize - newSize;
                while (bytesToSubtract > 0L) {
                    assert _buffer.head != null;
                    final var tail = _buffer.head.prev;
                    assert tail != null;
                    final var tailSize = tail.limit - tail.pos;
                    if (tailSize <= bytesToSubtract) {
                        _buffer.head = tail.pop();
                        SegmentPool.recycle(tail);
                        bytesToSubtract -= tailSize;
                    } else {
                        tail.limit -= (int) bytesToSubtract;
                        break;
                    }
                }
                // Seek to the end.
                this.segment = null;
                this.offset = newSize;
                this.data = null;
                this.pos = -1;
                this.limit = -1;
            } else {
                // Enlarge the buffer by either enlarging segments or adding them.
                var needsToSeek = true;
                var bytesToAdd = newSize - oldSize;
                while (bytesToAdd > 0L) {
                    final var tail = _buffer.writableTail(1);
                    final var segmentBytesToAdd = (int) Math.min(bytesToAdd, Segment.SIZE - tail.limit);
                    tail.limit += segmentBytesToAdd;
                    bytesToAdd -= segmentBytesToAdd;

                    // If this is the first segment we're adding, seek to it.
                    if (needsToSeek) {
                        this.segment = tail;
                        this.offset = oldSize;
                        this.data = tail.data;
                        this.pos = tail.limit - segmentBytesToAdd;
                        this.limit = tail.limit;
                        needsToSeek = false;
                    }
                }
            }

            _buffer.byteSize = newSize;

            return oldSize;
        }

        @Override
        public long expandBuffer(final int minByteCount) {
            if (minByteCount <= 0L) {
                throw new IllegalArgumentException("minByteCount <= 0: " + minByteCount);
            }
            if (minByteCount > Segment.SIZE) {
                throw new IllegalArgumentException("minByteCount > Segment.SIZE: " + minByteCount);
            }
            if (buffer == null) {
                throw new IllegalStateException("not attached to a buffer");
            }
            if (!readWrite) {
                throw new IllegalStateException("expandBuffer() is only permitted for read/write buffers");
            }
            final var _buffer = (RealBuffer) buffer;

            final var oldSize = _buffer.byteSize;
            final var tail = _buffer.writableTail(minByteCount);
            final var result = Segment.SIZE - tail.limit;
            tail.limit = Segment.SIZE;
            _buffer.byteSize = oldSize + result;

            // Seek to the old size.
            this.segment = tail;
            this.offset = oldSize;
            this.data = tail.data;
            this.pos = Segment.SIZE - result;
            this.limit = Segment.SIZE;

            return result;
        }

        @Override
        public void close() {
            if (buffer == null) {
                throw new IllegalStateException("not attached to a buffer");
            }

            buffer = null;
            segment = null;
            offset = -1L;
            data = null;
            pos = -1;
            limit = -1;
        }
    }
}
