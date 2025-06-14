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
import jayo.bytestring.Ascii;
import jayo.bytestring.ByteString;
import jayo.bytestring.Utf8;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static jayo.internal.Utils.selectPrefix;
import static jayo.tools.JayoUtils.checkOffsetAndCount;

public final class RealReader implements Reader {
    private final @NonNull RawReader reader;
    final @NonNull RealBuffer buffer = new RealBuffer();
    private boolean closed = false;

    public RealReader(final @NonNull RawReader reader) {
        assert reader != null;
        this.reader = reader;
    }

    @Override
    public long readAtMostTo(final @NonNull Buffer destination, final long byteCount) {
        Objects.requireNonNull(destination);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        if (closed) {
            throw new JayoClosedResourceException();
        }

        if (buffer.bytesAvailable() == 0L) {
            if (byteCount == 0L) {
                return -1L;
            }
            if (reader.readAtMostTo(buffer, Segment.SIZE) == -1L) {
                return -1L;
            }
        }

        long toRead = Math.min(byteCount, buffer.bytesAvailable());
        return buffer.readAtMostTo(destination, toRead);
    }

    @Override
    public @NonNull ByteString readByteString() {
        buffer.transferFrom(reader);
        return buffer.readByteString();
    }

    @Override
    public @NonNull ByteString readByteString(final long byteCount) {
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        require(byteCount);
        return buffer.readByteString(byteCount);
    }

    @Override
    public @NonNull Utf8 readUtf8() {
        buffer.transferFrom(reader);
        return buffer.readUtf8();
    }

    @Override
    public @NonNull Utf8 readUtf8(final long byteCount) {
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        require(byteCount);
        return buffer.readUtf8(byteCount);
    }

    @Override
    public @NonNull Ascii readAscii() {
        buffer.transferFrom(reader);
        return buffer.readAscii();
    }

    @Override
    public @NonNull Ascii readAscii(final long byteCount) {
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        require(byteCount);
        return buffer.readAscii(byteCount);
    }

    @Override
    public int select(final @NonNull Options options) {
        Objects.requireNonNull(options);
        if (closed) {
            throw new JayoClosedResourceException();
        }
        final var _options = (RealOptions) options;

        while (true) {
            final var index = selectPrefix(buffer, _options, true);
            switch (index) {
                case -1 -> {
                    return -1;
                }
                case -2 -> {
                    // We need to grow the buffer. Do that, then try it all again.
                    if (reader.readAtMostTo(buffer, Segment.SIZE) == -1L) {
                        return -1;
                    }
                }
                default -> {
                    // We matched a full byte string: consume it and return it.
                    final var selectedSize = _options.byteStrings[index].byteSize();
                    buffer.skip(selectedSize);
                    return index;
                }
            }
        }
    }

    @Override
    public byte @NonNull [] readByteArray() {
        buffer.transferFrom(reader);
        return buffer.readByteArray();
    }

    @Override
    public byte @NonNull [] readByteArray(final long byteCount) {
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        require(byteCount);
        return buffer.readByteArray(byteCount);
    }

    @Override
    public void readTo(final byte @NonNull [] destination) {
        readTo(destination, 0, destination.length);
    }

    @Override
    public void readTo(final byte @NonNull [] destination, final int offset, final int byteCount) {
        Objects.requireNonNull(destination);
        checkOffsetAndCount(destination.length, offset, byteCount);

        // if not enough bytes, then buffer.readTo will read as many bytes as possible and throw EOF
        request(byteCount);
        buffer.readTo(destination, offset, byteCount);
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

        if (buffer.byteSize == 0L) {
            if (byteCount == 0) {
                return 0;
            }
            final var read = reader.readAtMostTo(buffer, Segment.SIZE);
            if (read == -1L) {
                return -1;
            }
        }

        final var toRead = (int) Math.min(byteCount, buffer.byteSize);
        return buffer.readAtMostTo(destination, offset, toRead);
    }

    @Override
    public int readAtMostTo(final @NonNull ByteBuffer destination) {
        Objects.requireNonNull(destination);

        if (buffer.byteSize == 0L) {
            if (destination.remaining() == 0) {
                return 0;
            }
            final var read = reader.readAtMostTo(buffer, Segment.SIZE);
            if (read == -1L) {
                return -1;
            }
        }
        return buffer.readAtMostTo(destination);
    }

    @Override
    public void readTo(final @NonNull RawWriter destination, final long byteCount) {
        Objects.requireNonNull(destination);

        // if not enough bytes, then buffer.readTo will read as many bytes as possible and throw EOF
        request(byteCount);
        buffer.readTo(destination, byteCount);
    }

    @Override
    public long transferTo(final @NonNull RawWriter destination) {
        Objects.requireNonNull(destination);

        var totalBytesWritten = 0L;
        while (reader.readAtMostTo(buffer, Segment.SIZE) != -1L) {
            final var emitByteCount = buffer.completeSegmentByteCount();
            if (emitByteCount > 0L) {
                totalBytesWritten += emitByteCount;
                destination.write(buffer, emitByteCount);
            }
        }
        if (buffer.byteSize > 0L) {
            totalBytesWritten += buffer.byteSize;
            destination.write(buffer, buffer.byteSize);
        }
        return totalBytesWritten;
    }

    @Override
    public @NonNull String readString() {
        buffer.transferFrom(reader);
        return buffer.readString();
    }

    @Override
    public @NonNull String readString(final long byteCount) {
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        require(byteCount);
        return buffer.readString(byteCount);
    }

    @Override
    public @NonNull String readString(final @NonNull Charset charset) {
        Objects.requireNonNull(charset);

        buffer.transferFrom(reader);
        return buffer.readString(charset);
    }

    @Override
    public @NonNull String readString(final long byteCount, final @NonNull Charset charset) {
        Objects.requireNonNull(charset);
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }

        require(byteCount);
        return buffer.readString(byteCount, charset);
    }

    @Override
    public @Nullable String readLine() {
        return readLine(StandardCharsets.UTF_8);
    }

    @Override
    public @Nullable String readLine(final @NonNull Charset charset) {
        Objects.requireNonNull(charset);

        final var newline = indexOf((byte) ((int) '\n'));
        if (newline == -1L) {
            if (buffer.byteSize != 0L) {
                return readString(buffer.byteSize, charset);
            }
            return null;
        }

        return Utils.readUtf8Line(buffer, newline, charset);
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
        if (limit < 0) {
            throw new IllegalArgumentException("limit < 0: " + limit);
        }

        final var scanLength = (limit == Long.MAX_VALUE) ? Long.MAX_VALUE : limit + 1;
        final var newline = indexOf((byte) ((int) '\n'), 0, scanLength);

        // a new line was found in the scanned bytes.
        if (newline != -1L) {
            return Utils.readUtf8Line(buffer, newline, charset);
        }

        // else check that the line was 'limit' bytes followed by \r\n.
        if (scanLength < Long.MAX_VALUE &&
                request(scanLength) && buffer.getByte(scanLength - 1) == (byte) ((int) '\r') &&
                request(scanLength + 1) && buffer.getByte(scanLength) == (byte) ((int) '\n')) {
            return Utils.readUtf8Line(buffer, scanLength, charset);
        }

        // else build and throw a JayoEOFException.
        final var data = new RealBuffer();
        buffer.copyTo(data, 0, Math.min(32, buffer.byteSize));
        throw new JayoEOFException(
                "\\n not found: limit=" + Math.min(buffer.byteSize, limit) +
                        " content=" + data.readByteString().hex() + '…'
        );
    }

    @Override
    public int readUtf8CodePoint() {
        require(1L);

        final var b0 = (int) buffer.getByte(0);
        if ((b0 & 0xe0) == 0xc0) {
            require(2L);
        } else if ((b0 & 0xf0) == 0xe0) {
            require(3L);
        } else if ((b0 & 0xf8) == 0xf0) {
            require(4L);
        }

        return buffer.readUtf8CodePoint();
    }

    @Override
    public long bytesAvailable() {
        if (closed) {
            throw new JayoClosedResourceException();
        }
        return buffer.byteSize;
    }

    @Override
    public boolean exhausted() {
        if (closed) {
            throw new JayoClosedResourceException();
        }
        return buffer.exhausted() && reader.readAtMostTo(buffer, Segment.SIZE) == -1L;
    }

    @Override
    public boolean request(final long byteCount) {
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        if (closed) {
            throw new JayoClosedResourceException();
        }
        while (buffer.byteSize < byteCount) {
            if (reader.readAtMostTo(buffer, Segment.SIZE) == -1L) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void require(final long byteCount) {
        if (!request(byteCount)) {
            throw new JayoEOFException("could not read " + byteCount + " bytes from reader, had " + bytesAvailable());
        }
    }

    @Override
    public byte readByte() {
        require(1);
        return buffer.readByte();
    }

    @Override
    public short readShort() {
        require(2L);
        return buffer.readShort();
    }

    @Override
    public int readInt() {
        require(4L);
        return buffer.readInt();
    }

    @Override
    public long readLong() {
        require(8L);
        return buffer.readLong();
    }

    @Override
    public long readDecimalLong() {
        require(1);

        var pos = 0L;
        while (request(pos + 1)) {
            final var b = buffer.getByte(pos);
            if ((b < (byte) ((int) '0') || b > (byte) ((int) '9')) && (pos != 0L || b != (byte) ((int) '-'))) {
                // Non-digit, or non-leading negative sign.
                if (pos == 0L) {
                    throw new NumberFormatException("Expected a digit or '-' but was 0x" +
                            Integer.toString(b, 16));
                }
                break;
            }
            pos++;
        }

        return buffer.readDecimalLong();
    }

    @Override
    public long readHexadecimalUnsignedLong() {
        require(1);

        var pos = 0L;
        while (request(pos + 1)) {
            final var b = buffer.getByte(pos);
            if ((b < (byte) ((int) '0') || b > (byte) ((int) '9')) &&
                    (b < (byte) ((int) 'a') || b > (byte) ((int) 'f')) &&
                    (b < (byte) ((int) 'A') || b > (byte) ((int) 'F'))) {
                // Non-digit.
                if (pos == 0L) {
                    throw new NumberFormatException("Expected leading [0-9a-fA-F] character but was 0x" +
                            Integer.toString(b, 16));
                }
                break;
            }
            pos++;
        }

        return buffer.readHexadecimalUnsignedLong();
    }

    @Override
    public void skip(final long byteCount) {
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0L: " + byteCount);
        }
        if (closed) {
            throw new JayoClosedResourceException();
        }
        final var skipped = skipPrivate(byteCount);
        if (skipped < byteCount) {
            throw new JayoEOFException("could not skip " + byteCount + " bytes, skipped: " + skipped);
        }
    }

    private long skipPrivate(final long byteCount) {
        var remaining = byteCount;
        while (remaining > 0) {
            if (buffer.byteSize == 0L && reader.readAtMostTo(buffer, Segment.SIZE) == -1L) {
                break;
            }
            final var toSkip = Math.min(remaining, buffer.byteSize);
            buffer.skipInternal(toSkip);
            remaining -= toSkip;
        }
        return byteCount - remaining;
    }

    @Override
    public long indexOf(final byte b) {
        return indexOf(b, 0L);
    }

    @Override
    public long indexOf(final byte b, final long startIndex) {
        return indexOf(b, startIndex, Long.MAX_VALUE);
    }

    @Override
    public long indexOf(final byte b, final long startIndex, final long endIndex) {
        if (closed) {
            throw new JayoClosedResourceException();
        }
        if (startIndex < 0L || startIndex > endIndex) {
            throw new IllegalArgumentException("startIndex=" + startIndex + " endIndex=" + endIndex);
        }

        var _startIndex = startIndex;
        while (_startIndex < endIndex) {
            final var result = buffer.indexOf(b, _startIndex, endIndex);
            if (result != -1L) {
                return result;
            }

            // The byte wasn't in the buffer. Give up if we've already reached our target size or if the underlying
            // stream is exhausted.
            final var lastBufferSize = buffer.byteSize;
            if (lastBufferSize >= endIndex || reader.readAtMostTo(buffer, Segment.SIZE) == -1L) {
                return -1L;
            }

            // Keep searching, picking up from where we left off.
            _startIndex = Math.max(_startIndex, lastBufferSize);
        }
        return -1L;
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
        Objects.requireNonNull(byteString);
        if (closed) {
            throw new JayoClosedResourceException();
        }
        checkOffsetAndCount(byteString.byteSize(), byteStringOffset, byteCount);

        var _startIndex = startIndex;
        while (true) {
            final var result = buffer.indexOfInternal(
                    byteString,
                    byteStringOffset,
                    byteCount,
                    _startIndex,
                    endIndex);
            if (result != -1L) {
                return result;
            }

            final var lastBufferSize = buffer.byteSize;
            final var nextFromIndex = lastBufferSize - byteCount + 1;
            if (nextFromIndex >= endIndex) {
                return -1L;
            }
            // The ByteString wasn't in the buffer. Give up if we've already reached our target size or if the
            // underlying stream is exhausted.
            if (!isMatchPossibleByExpandingBuffer(
                    buffer,
                    byteString,
                    byteStringOffset,
                    byteCount,
                    _startIndex,
                    endIndex)) {
                return -1L;
            }
            if (reader.readAtMostTo(buffer, Segment.SIZE) == -1L) {
                return -1L;
            }

            // Keep searching, picking up from where we left off.
            _startIndex = Math.max(_startIndex, nextFromIndex);
        }
    }

    /**
     * @return true if loading more data could result in an {@code indexOf} match.
     * <p>
     * This function's utility is avoiding potentially slow {@code readAtMostTo} calls that cannot impact the result
     * of an {@code indexOf} call. For example, consider this situation:
     * <pre>
     * {@code
     * Reader reader = ...
     * reader.indexOf("hello",
     *   0, // startIndex
     *   4); // endIndex
     * }
     * </pre>
     * If the reader's loaded content is the string "shell", it is necessary to load more data because if the next
     * loaded byte is {@code o} then the result will be 1. But if the reader's loaded content is {@code look}, we know
     * the result is -1 without loading more data.
     */
    private static boolean isMatchPossibleByExpandingBuffer(final @NonNull RealBuffer buffer,
                                                            final @NonNull ByteString byteString,
                                                            final int byteStringOffset,
                                                            final int byteCount,
                                                            final long startIndex,
                                                            final long endIndex) {
        assert buffer != null;
        assert byteString != null;

        // Load new data if the match could come entirely in that new data.
        if (buffer.byteSize < endIndex) {
            return true;
        }

        // Load new data if a prefix of 'byteString' matches a suffix of 'buffer'.
        final var begin = (int) Math.max(1, buffer.byteSize - endIndex + 1);
        final var limit = (int) Math.min(byteCount, buffer.byteSize - startIndex + 1);
        for (var i = limit - 1; i >= begin; i--) {
            if (buffer.rangeEquals(buffer.byteSize - i, byteString, byteStringOffset, i)) {
                return true;
            }
        }

        // No matter what we load, we won't find a match.
        return false;
    }

    @Override
    public long indexOfElement(final @NonNull ByteString targetBytes) {
        return indexOfElement(targetBytes, 0L);
    }

    @Override
    public long indexOfElement(final @NonNull ByteString targetBytes, final long startIndex) {
        Objects.requireNonNull(targetBytes);
        if (closed) {
            throw new JayoClosedResourceException();
        }
        if (startIndex < 0L) {
            throw new IllegalArgumentException("startIndex < 0: " + startIndex);
        }

        var _startIndex = startIndex;
        while (true) {
            final var result = buffer.indexOfElement(targetBytes, _startIndex);
            if (result != -1L) {
                return result;
            }

            final var lastBufferSize = buffer.byteSize;
            if (reader.readAtMostTo(buffer, Segment.SIZE) == -1L) {
                return -1L;
            }

            // Keep searching, picking up from where we left off.
            _startIndex = Math.max(_startIndex, lastBufferSize);
        }
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
        if (closed) {
            throw new JayoClosedResourceException();
        }

        if (byteCount < 0 ||
                offset < 0L ||
                byteStringOffset < 0 || byteStringOffset + byteCount > byteString.byteSize()) {
            return false;
        }

        if (byteCount == 0) {
            return true;
        }

        return indexOfInternal(byteString, byteStringOffset, byteCount, offset, offset + 1) != -1L;
    }

    @Override
    public @NonNull Reader peek() {
        return new RealReader(new PeekRawReader(this));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        reader.close();
        buffer.clear();
    }

    @Override
    public String toString() {
        return "buffered(" + reader + ")";
    }

    @Override
    public @NonNull InputStream asInputStream() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                if (closed) {
                    throw new IOException("Underlying reader is closed.");
                }
                try {
                    if (exhausted()) {
                        return -1;
                    }
                    return buffer.readByte() & 0xff;
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public int read(final byte @NonNull [] data, final int offset, final int byteCount) throws IOException {
                Objects.requireNonNull(data);
                if (closed) {
                    throw new IOException("Underlying reader is closed.");
                }
                try {
                    return readAtMostTo(data, offset, byteCount);
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public byte @NonNull [] readAllBytes() throws IOException {
                if (closed) {
                    throw new IOException("Underlying reader is closed.");
                }
                try {
                    return readByteArray();
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public byte @NonNull [] readNBytes(final int len) throws IOException {
                if (closed) {
                    throw new IOException("Underlying reader is closed.");
                }
                try {
                    return readByteArray(len);
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public long skip(final long byteCount) throws IOException {
                if (closed) {
                    throw new IOException("Underlying reader is closed.");
                }
                if (byteCount < 0L) {
                    return 0L;
                }
                try {
                    return skipPrivate(byteCount);
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public int available() throws IOException {
                if (closed) {
                    throw new IOException("Underlying reader is closed.");
                }
                try {
                    return (int) Math.min(buffer.byteSize, Segment.SIZE);
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public void close() throws IOException {
                try {
                    RealReader.this.close();
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public long transferTo(final @NonNull OutputStream out) throws IOException {
                Objects.requireNonNull(out);
                if (closed) {
                    throw new IOException("Underlying reader is closed.");
                }
                try {
                    buffer.transferFrom(reader);
                    final var bufferSize = buffer.byteSize;
                    buffer.readTo(out, bufferSize);
                    return bufferSize;
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public String toString() {
                return RealReader.this + ".asInputStream()";
            }
        };
    }

    @Override
    public @NonNull ReadableByteChannel asReadableByteChannel() {
        return new ReadableByteChannel() {
            @Override
            public int read(final @NonNull ByteBuffer writer) throws IOException {
                Objects.requireNonNull(writer);
                if (closed) {
                    throw new ClosedChannelException();
                }
                try {
                    return readAtMostTo(writer);
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public boolean isOpen() {
                return !closed;
            }

            @Override
            public void close() throws IOException {
                try {
                    RealReader.this.close();
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public String toString() {
                return RealReader.this + ".asReadableByteChannel()";
            }
        };
    }
}
