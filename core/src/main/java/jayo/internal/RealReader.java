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
import jayo.exceptions.JayoEOFException;
import jayo.exceptions.JayoException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.Objects;

import static jayo.external.JayoUtils.checkOffsetAndCount;
import static jayo.internal.ReaderSegmentQueue.newReaderSegmentQueue;

public final class RealReader implements Reader {
    private static final long INTEGER_MAX_PLUS_1 = (long) Integer.MAX_VALUE + 1;

    final @NonNull ReaderSegmentQueue segmentQueue;

    public RealReader(final @NonNull RawReader reader, final boolean async) {
        Objects.requireNonNull(reader);
        segmentQueue = newReaderSegmentQueue(reader, async);
    }

    @Override
    public long readAtMostTo(final @NonNull Buffer writer, final @NonNegative long byteCount) {
        Objects.requireNonNull(writer);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }

        if (segmentQueue.expectSize(1L) == 0L) {
            return -1;
        }

        return segmentQueue.buffer.readAtMostTo(writer, byteCount);
    }

    @Override
    public @NonNull ByteString readByteString() {
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.expectSize(INTEGER_MAX_PLUS_1);
        return segmentQueue.buffer.readByteString();
    }

    @Override
    public @NonNull ByteString readByteString(final @NonNegative long byteCount) {
        require(byteCount);
        return segmentQueue.buffer.readByteString(byteCount);
    }

    @Override
    public @NonNull Utf8 readUtf8() {
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.expectSize(INTEGER_MAX_PLUS_1);
        return segmentQueue.buffer.readUtf8();
    }

    @Override
    public @NonNull Utf8 readUtf8(long byteCount) {
        require(byteCount);
        return segmentQueue.buffer.readUtf8(byteCount);
    }

    @Override
    public int select(final @NonNull Options options) {
        Objects.requireNonNull(options);
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        return segmentQueue.buffer.select(options);
    }

    @Override
    public byte @NonNull [] readByteArray() {
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        return readByteArrayPrivate();
    }

    private byte @NonNull [] readByteArrayPrivate() {
        segmentQueue.expectSize(INTEGER_MAX_PLUS_1);
        return segmentQueue.buffer.readByteArray();
    }

    @Override
    public byte @NonNull [] readByteArray(final @NonNegative long byteCount) {
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        return readByteArrayPrivate(byteCount);
    }

    private byte @NonNull [] readByteArrayPrivate(final @NonNegative long byteCount) {
        require(byteCount);
        return segmentQueue.buffer.readByteArray(byteCount);
    }

    @Override
    public void readTo(final byte @NonNull [] writer) {
        readTo(writer, 0, writer.length);
    }

    @Override
    public void readTo(final byte @NonNull [] writer, final @NonNegative int offset, final @NonNegative int byteCount) {
        checkOffsetAndCount(Objects.requireNonNull(writer).length, offset, byteCount);
        // can fail if not enough bytes, then segmentQueue.buffer.readTo will read as many bytes as possible and throw EOF
        request(byteCount);
        segmentQueue.buffer.readTo(writer, offset, byteCount);
    }

    @Override
    public int readAtMostTo(final byte @NonNull [] writer) {
        return readAtMostTo(writer, 0, writer.length);
    }

    @Override
    public int readAtMostTo(final byte @NonNull [] writer,
                            final @NonNegative int offset,
                            final @NonNegative int byteCount) {
        Objects.requireNonNull(writer);
        checkOffsetAndCount(writer.length, offset, byteCount);
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        return readAtMostToPrivate(writer, offset, byteCount);
    }

    private int readAtMostToPrivate(final byte @NonNull [] writer,
                                    final @NonNegative int offset,
                                    final @NonNegative int byteCount) {
        if (segmentQueue.expectSize(1L) == 0L) {
            return -1;
        }
        return segmentQueue.buffer.readAtMostTo(writer, offset, byteCount);
    }

    @Override
    public int readAtMostTo(final @NonNull ByteBuffer writer) {
        Objects.requireNonNull(writer);
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        return readAtMostToPrivate(writer);
    }

    private int readAtMostToPrivate(final @NonNull ByteBuffer writer) {
        if (segmentQueue.expectSize(1L) == 0L) {
            return -1;
        }
        return segmentQueue.buffer.readAtMostTo(writer);
    }

    @Override
    public void readTo(final @NonNull RawWriter writer, final @NonNegative long byteCount) {
        Objects.requireNonNull(writer);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        var remaining = byteCount;
        while (remaining > 0L) {
            // trying to read Segment.SIZE, so we have at least one complete segment in the segmentQueue.buffer
            final var size = segmentQueue.expectSize(Segment.SIZE);
            if (size == 0L) {
                // The underlying reader is exhausted.
                throw new JayoEOFException("could not read " + byteCount + " bytes from reader");
            }
            final var emitByteCount = segmentQueue.buffer.completeSegmentByteCount();
            final long toWrite;
            if (emitByteCount > 0L) {
                toWrite = Math.min(remaining, emitByteCount);
            } else {
                // write the last uncompleted segment, if any
                toWrite = Math.min(remaining, size);
            }
            writer.write(segmentQueue.buffer, toWrite);
            remaining -= toWrite;
        }
    }

    @Override
    public @NonNegative long transferTo(final @NonNull RawWriter writer) {
        Objects.requireNonNull(writer);
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        var written = 0L;
        while (segmentQueue.expectSize(Segment.SIZE) > 0L) {
            final var emitByteCount = segmentQueue.buffer.completeSegmentByteCount();
            if (emitByteCount == 0L) {
                break;
            }
            written += emitByteCount;
            writer.write(segmentQueue.buffer, emitByteCount);
        }
        // write the last remaining bytes in the last uncompleted segment, if any
        final var remaining = segmentQueue.size();
        if (remaining > 0L) {
            written += remaining;
            writer.write(segmentQueue.buffer, remaining);
        }
        return written;
    }

    @Override
    public @NonNull String readUtf8String() {
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.expectSize(INTEGER_MAX_PLUS_1);
        return segmentQueue.buffer.readUtf8String();
    }

    @Override
    public @NonNull String readUtf8String(final @NonNegative long byteCount) {
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        require(byteCount);
        return segmentQueue.buffer.readUtf8String(byteCount);
    }

    @Override
    public @NonNull String readString(final @NonNull Charset charset) {
        Objects.requireNonNull(charset);
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.expectSize(INTEGER_MAX_PLUS_1);
        return segmentQueue.buffer.readString(charset);
    }

    @Override
    public @NonNull String readString(final @NonNegative long byteCount, final @NonNull Charset charset) {
        Objects.requireNonNull(charset);
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        require(byteCount);
        return segmentQueue.buffer.readString(byteCount, charset);
    }

    @Override
    public @Nullable String readUtf8Line() {
        final var newline = indexOf((byte) ((int) '\n'));

        if (newline == -1L) {
            final var size = segmentQueue.expectSize(INTEGER_MAX_PLUS_1);
            if (size != 0L) {
                return readUtf8String(size);
            } else {
                return null;
            }
        }

        return Utf8Utils.readUtf8Line(segmentQueue.buffer, newline);
    }

    @Override
    public @NonNull String readUtf8LineStrict() {
        return readUtf8LineStrict(Long.MAX_VALUE);
    }

    @Override
    public @NonNull String readUtf8LineStrict(final @NonNegative long limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit < 0: " + limit);
        }
        final var scanLength = (limit == Long.MAX_VALUE) ? Long.MAX_VALUE : limit + 1;
        final var newline = indexOf((byte) ((int) '\n'), 0, scanLength);
        if (newline != -1L) {
            return Utf8Utils.readUtf8Line(segmentQueue.buffer, newline);
        }
        if (scanLength < Long.MAX_VALUE &&
                request(scanLength) && segmentQueue.buffer.getByte(scanLength - 1) == (byte) ((int) '\r') &&
                request(scanLength + 1) && segmentQueue.buffer.getByte(scanLength) == (byte) ((int) '\n')
        ) {
            return Utf8Utils.readUtf8Line(segmentQueue.buffer, scanLength); // The line was 'limit' UTF-8 bytes followed by \r\n.
        }
        final var data = new RealBuffer();
        final var size = segmentQueue.size();
        segmentQueue.buffer.copyTo(data, 0, Math.min(32, size));
        throw new JayoEOFException(
                "\\n not found: limit=" + Math.min(size, limit) +
                        " content=" + data.readByteString().hex() + '…'
        );
    }

    @Override
    public @NonNegative int readUtf8CodePoint() {
        require(1L);

        final var b0 = (int) segmentQueue.buffer.getByte(0);
        if ((b0 & 0xe0) == 0xc0) {
            require(2L);
        } else if ((b0 & 0xf0) == 0xe0) {
            require(3L);
        } else if ((b0 & 0xf8) == 0xf0) {
            require(4L);
        }

        return segmentQueue.buffer.readUtf8CodePoint();
    }

    @Override
    public boolean exhausted() {
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        return segmentQueue.expectSize(1L) == 0L;
    }

    @Override
    public boolean request(final @NonNegative long byteCount) {
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        if (byteCount == 0) {
            return true;
        }
        return segmentQueue.expectSize(byteCount) >= byteCount;
    }

    @Override
    public void require(final @NonNegative long byteCount) {
        if (!request(byteCount)) {
            throw new JayoEOFException("could not read " + byteCount + " bytes from reader, had " + segmentQueue.size());
        }
    }

    @Override
    public byte readByte() {
        require(1);
        return segmentQueue.buffer.readByte();
    }

    @Override
    public short readShort() {
        require(2L);
        return segmentQueue.buffer.readShort();
    }

    @Override
    public int readInt() {
        require(4L);
        return segmentQueue.buffer.readInt();
    }

    @Override
    public long readLong() {
        require(8L);
        return segmentQueue.buffer.readLong();
    }

    @Override
    public long readDecimalLong() {
        request(20L);
        return segmentQueue.buffer.readDecimalLong();
    }

    @Override
    public long readHexadecimalUnsignedLong() {
        request(17L);
        return segmentQueue.buffer.readHexadecimalUnsignedLong();
    }

    @Override
    public void skip(final @NonNegative long byteCount) {
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0L: " + byteCount);
        }
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        final var skipped = skipPrivate(byteCount);
        if (skipped < byteCount) {
            throw new JayoEOFException("could not skip " + byteCount + " bytes, skipped: " + skipped);
        }
    }

    private @NonNegative long skipPrivate(final @NonNegative long byteCount) {
        var remaining = byteCount;
        while (remaining > 0) {
            // trying to read Segment.SIZE, so we have at least one complete segment in the buffer
            final var size = segmentQueue.expectSize(Segment.SIZE);
            if (size == 0L) {
                break;
            }
            final var toSkip = Math.min(remaining, size);
            segmentQueue.buffer.skipInternal(toSkip);
            remaining -= toSkip;
        }
        return byteCount - remaining;
    }

    @Override
    public long indexOf(final byte b) {
        return indexOf(b, 0L);
    }

    @Override
    public long indexOf(final byte b, final @NonNegative long startIndex) {
        return indexOf(b, startIndex, Long.MAX_VALUE);
    }

    @Override
    public long indexOf(final byte b, final @NonNegative long startIndex, final @NonNegative long endIndex) {
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        if (startIndex < 0L || startIndex > endIndex) {
            throw new IllegalArgumentException("startIndex=" + startIndex + " endIndex=" + endIndex);
        }

        final var expected = Math.max(1L, startIndex);
        var lastBufferSize = segmentQueue.expectSize(expected);
        if (lastBufferSize < startIndex) {
            return -1; // there is not enough bytes in the segmentQueue.buffer
        }
        var _startIndex = startIndex;
        while (_startIndex < endIndex) {
            final var result = segmentQueue.buffer.indexOf(b, _startIndex, endIndex);
            if (result != -1L) {
                return result;
            }

            // The byte wasn't in the segmentQueue.buffer. Give up if we've already reached our target size.
            if (lastBufferSize >= endIndex) {
                return -1L;
            }

            final var newBufferSize = segmentQueue.expectSize(lastBufferSize + 1);
            // Give up if the underlying stream is exhausted.
            if (newBufferSize == lastBufferSize) {
                return -1L;
            }
            // Keep searching, picking up from where we left off.
            _startIndex = Math.max(_startIndex, lastBufferSize);

            lastBufferSize = newBufferSize;
        }
        return -1L;
    }

    @Override
    public long indexOf(final @NonNull ByteString byteString) {
        return indexOf(byteString, 0L);
    }

    @Override
    public long indexOf(final @NonNull ByteString byteString, final @NonNegative long startIndex) {
        Objects.requireNonNull(byteString);
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        if (startIndex < 0L) {
            throw new IllegalArgumentException("startIndex < 0: " + startIndex);
        }

        final var minSearchSize = startIndex + byteString.byteSize();
        final var expected = Math.max(1L, minSearchSize);
        var lastBufferSize = segmentQueue.expectSize(expected);
        if (lastBufferSize < minSearchSize) {
            return -1; // there is not enough bytes in the segmentQueue.buffer
        }
        var _startIndex = startIndex;
        while (true) {
            final var result = segmentQueue.buffer.indexOf(byteString, _startIndex);
            if (result != -1L) {
                return result;
            }

            final var newBufferSize = segmentQueue.expectSize(lastBufferSize + 1);
            // Give up if the underlying stream is exhausted.
            if (newBufferSize == lastBufferSize) {
                return -1L;
            }
            // Keep searching, picking up from where we left off.
            _startIndex = Math.max(_startIndex, lastBufferSize - byteString.byteSize() + 1);

            lastBufferSize = newBufferSize;
        }
    }

    @Override
    public long indexOfElement(final @NonNull ByteString targetBytes) {
        return indexOfElement(targetBytes, 0L);
    }

    @Override
    public long indexOfElement(final @NonNull ByteString targetBytes, final @NonNegative long startIndex) {
        Objects.requireNonNull(targetBytes);
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }
        if (startIndex < 0L) {
            throw new IllegalArgumentException("startIndex < 0: " + startIndex);
        }

        final var expected = Math.max(1L, startIndex);
        var lastBufferSize = segmentQueue.expectSize(expected);
        if (lastBufferSize < startIndex) {
            return -1; // there is not enough bytes in the segmentQueue.buffer
        }

        var _startIndex = startIndex;
        while (true) {
            final var result = segmentQueue.buffer.indexOfElement(targetBytes, _startIndex);
            if (result != -1L) {
                return result;
            }

            final var newBufferSize = segmentQueue.expectSize(lastBufferSize + 1);
            // Give up if the underlying stream is exhausted.
            if (newBufferSize == lastBufferSize) {
                return -1L;
            }

            // Keep searching, picking up from where we left off.
            _startIndex = Math.max(_startIndex, lastBufferSize);

            lastBufferSize = newBufferSize;
        }
    }

    @Override
    public boolean rangeEquals(final @NonNegative long offset, final @NonNull ByteString byteString) {
        return rangeEquals(offset, byteString, 0, byteString.byteSize());
    }

    @Override
    public boolean rangeEquals(final @NonNegative long offset,
                               final @NonNull ByteString byteString,
                               final @NonNegative int bytesOffset,
                               final @NonNegative int byteCount) {
        Objects.requireNonNull(byteString);
        if (segmentQueue.closed) {
            throw new IllegalStateException("closed");
        }

        if (offset < 0L ||
                bytesOffset < 0 ||
                byteCount < 0 ||
                byteString.byteSize() - bytesOffset < byteCount
        ) {
            return false;
        }
        for (var i = 0; i < byteCount; i++) {
            final var bufferOffset = offset + i;
            if (!request(bufferOffset + 1)) {
                return false;
            }
            if (segmentQueue.buffer.getByte(bufferOffset) != byteString.getByte(bytesOffset + i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public @NonNull Reader peek() {
        return new RealReader(new PeekRawReader(this), false);
    }

    @Override
    public void close() {
        segmentQueue.close();
    }

    @Override
    public String toString() {
        return "buffered(" + segmentQueue.reader + ")";
    }

    @Override
    public @NonNull InputStream asInputStream() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                if (segmentQueue.closed) {
                    throw new IOException("Underlying reader is closed.");
                }
                try {
                    if (exhausted()) {
                        return -1;
                    }
                    return segmentQueue.buffer.readByte() & 0xff;
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public int read(final byte @NonNull [] data, final int offset, final int byteCount) throws IOException {
                Objects.requireNonNull(data);
                checkOffsetAndCount(data.length, offset, byteCount);
                if (segmentQueue.closed) {
                    throw new IOException("Underlying reader is closed.");
                }
                try {
                    return readAtMostToPrivate(data, offset, byteCount);
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public byte @NonNull [] readAllBytes() throws IOException {
                if (segmentQueue.closed) {
                    throw new IOException("Underlying reader is closed.");
                }
                try {
                    return readByteArrayPrivate();
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public byte @NonNull [] readNBytes(final @NonNegative int len) throws IOException {
                if (len < 0) {
                    throw new IllegalArgumentException("invalid length: " + len);
                }
                if (segmentQueue.closed) {
                    throw new IOException("Underlying reader is closed.");
                }
                try {
                    return readByteArrayPrivate(len);
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public @NonNegative long skip(final @NonNegative long byteCount) throws IOException {
                if (segmentQueue.closed) {
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
                if (segmentQueue.closed) {
                    throw new IOException("Underlying reader is closed.");
                }
                try {
                    return (int) Math.min(segmentQueue.size(), Segment.SIZE);
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
            public @NonNegative long transferTo(final @NonNull OutputStream out) throws IOException {
                Objects.requireNonNull(out);
                if (segmentQueue.closed) {
                    throw new IOException("Underlying reader is closed.");
                }
                try {
                    var written = 0L;
                    while (segmentQueue.expectSize(Segment.SIZE) > 0L) {
                        final var emitByteCount = segmentQueue.buffer.completeSegmentByteCount();
                        if (emitByteCount == 0L) {
                            break;
                        }
                        written += emitByteCount;
                        segmentQueue.buffer.readTo(out, emitByteCount);
                    }
                    // write the last remaining bytes in the last uncompleted segment, if any
                    final var remaining = segmentQueue.size();
                    if (remaining > 0L) {
                        written += remaining;
                        segmentQueue.buffer.readTo(out, remaining);
                    }
                    return written;
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
                if (segmentQueue.closed) {
                    throw new ClosedChannelException();
                }
                try {
                    return readAtMostToPrivate(writer);
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public boolean isOpen() {
                return !segmentQueue.closed;
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
