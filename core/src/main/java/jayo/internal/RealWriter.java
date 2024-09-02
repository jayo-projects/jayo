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
import jayo.exceptions.JayoInterruptedIOException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.Objects;

@SuppressWarnings("resources")
public final class RealWriter implements Writer {
    private final @NonNull RawWriter writer;
    private final @NonNull WriterSegmentQueue segmentQueue;
    final @NonNull RealBuffer buffer;
    private boolean closed = false;

    public static @NonNull Writer buffer(final @NonNull RawWriter writer, final boolean async) {
        Objects.requireNonNull(writer);
        if (writer instanceof RealWriter realWriter) {
            final var isAsync = realWriter.segmentQueue instanceof WriterSegmentQueue.Async;
            if (isAsync == async) {
                return realWriter;
            }
        }
        return new RealWriter(writer, async);
    }

    RealWriter(final @NonNull RawWriter writer, final boolean async) {
        this.writer = writer;
        if (async) {
            final var asyncWriterSegmentQueue = new WriterSegmentQueue.Async(writer);
            segmentQueue = asyncWriterSegmentQueue;
            buffer = asyncWriterSegmentQueue.getBuffer();
        } else {
            final var syncWriterSegmentQueue = new WriterSegmentQueue(writer);
            segmentQueue = syncWriterSegmentQueue;
            buffer = syncWriterSegmentQueue.getBuffer();
        }
    }

    @Override
    public void write(final @NonNull Buffer reader, final @NonNegative long byteCount) {
        Objects.requireNonNull(reader);
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.write(reader, byteCount);
        emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final @NonNull ByteString byteString) {
        Objects.requireNonNull(byteString);
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.write(byteString);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final @NonNull ByteString byteString,
                                 final @NonNegative int offset,
                                 final @NonNegative int byteCount) {
        Objects.requireNonNull(byteString);
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.write(byteString, offset, byteCount);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeUtf8(final @NonNull Utf8 utf8) {
        return write(utf8);
    }

    @Override
    public @NonNull Writer writeUtf8(final @NonNull Utf8 utf8,
                                     final @NonNegative int offset,
                                     final @NonNegative int byteCount) {
        return write(utf8, offset, byteCount);
    }

    @Override
    public @NonNull Writer writeUtf8(final @NonNull CharSequence charSequence) {
        Objects.requireNonNull(charSequence);
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeUtf8(charSequence);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeUtf8(final @NonNull CharSequence charSequence,
                                     final @NonNegative int startIndex,
                                     final @NonNegative int endIndex) {
        Objects.requireNonNull(charSequence);
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeUtf8(charSequence, startIndex, endIndex);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeUtf8CodePoint(final @NonNegative int codePoint) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeUtf8CodePoint(codePoint);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeString(final @NonNull String string, final @NonNull Charset charset) {
        Objects.requireNonNull(string);
        Objects.requireNonNull(charset);
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeString(string, charset);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeString(final @NonNull String string,
                                       final @NonNegative int startIndex,
                                       final @NonNegative int endIndex,
                                       final @NonNull Charset charset) {
        Objects.requireNonNull(string);
        Objects.requireNonNull(charset);
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeString(string, startIndex, endIndex, charset);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final byte @NonNull [] source) {
        Objects.requireNonNull(source);
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.write(source);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final byte @NonNull [] source,
                                 final @NonNegative int offset,
                                 final @NonNegative int byteCount) {
        Objects.requireNonNull(source);
        if (closed) {
            throw new IllegalStateException("closed");
        }
        return writePrivate(source, offset, byteCount);
    }

    private @NonNull Writer writePrivate(final byte @NonNull [] source,
                                         final @NonNegative int offset,
                                         final @NonNegative int byteCount) {
        segmentQueue.pauseIfFull();
        buffer.write(source, offset, byteCount);
        return emitCompleteSegments();
    }

    @Override
    public @NonNegative int transferFrom(final @NonNull ByteBuffer source) {
        Objects.requireNonNull(source);
        if (closed) {
            throw new IllegalStateException("closed");
        }
        return transferFromPrivate(source);
    }

    private @NonNegative int transferFromPrivate(final @NonNull ByteBuffer reader) {
        segmentQueue.pauseIfFull();
        final var totalBytesRead = buffer.transferFrom(reader);
        emitCompleteSegments();
        return totalBytesRead;
    }

    @Override
    @NonNegative
    public long transferFrom(final @NonNull RawReader reader) {
        Objects.requireNonNull(reader);
        var totalBytesRead = 0L;
        while (true) {
            if (closed) {
                throw new IllegalStateException("closed");
            }
            segmentQueue.pauseIfFull();
            final var readCount = reader.readAtMostTo(buffer, Segment.SIZE);
            if (readCount == -1L) {
                break;
            }
            totalBytesRead += readCount;
            emitCompleteSegments();
        }
        return totalBytesRead;
    }

    @Override
    public @NonNull Writer write(final @NonNull RawReader reader, final @NonNegative long byteCount) {
        Objects.requireNonNull(reader);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        var _byteCount = byteCount;
        while (_byteCount > 0L) {
            if (closed) {
                throw new IllegalStateException("closed");
            }
            segmentQueue.pauseIfFull();
            final var read = reader.readAtMostTo(buffer, _byteCount);
            if (read == -1L) {
                throw new JayoEOFException();
            }
            _byteCount -= read;
            emitCompleteSegments();
        }
        return this;
    }

    @Override
    public @NonNull Writer writeByte(final byte b) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        return writeBytePrivate(b);
    }

    private @NonNull Writer writeBytePrivate(final byte b) {
        segmentQueue.pauseIfFull();
        buffer.writeByte(b);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeShort(final short s) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeShort(s);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeInt(final int i) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeInt(i);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeLong(final long l) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeLong(l);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeDecimalLong(final long l) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeDecimalLong(l);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeHexadecimalUnsignedLong(final long l) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeHexadecimalUnsignedLong(l);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer emitCompleteSegments() {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.emitCompleteSegments();
        return this;
    }

    @Override
    public @NonNull Writer emit() {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.emit(false);
        return this;
    }

    @Override
    public void flush() {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.emit(true);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        // Emit buffered data to the underlying writer. If this fails, we still need
        // to close the writer; otherwise we risk leaking resourcess.
        Throwable thrown = null;
        try {
            segmentQueue.close();
        } catch (Throwable e) {
            thrown = e;
        }

        try {
            final var size = buffer.byteSize();
            if (size > 0) {
                writer.write(buffer, size);
            }
        } catch (JayoInterruptedIOException ignored) {
            // cancellation lead to closing, ignore
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
        return "buffered(" + writer + ")";
    }

    @Override
    public @NonNull OutputStream asOutputStream() {
        return new OutputStream() {
            @Override
            public void write(final int b) throws IOException {
                if (closed) {
                    throw new IOException("Underlying writer is closed.");
                }
                try {
                    writeBytePrivate((byte) b);
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public void write(final byte @NonNull [] data, final int offset, final int byteCount) throws IOException {
                Objects.requireNonNull(data);
                if (closed) {
                    throw new IOException("Underlying writer is closed.");
                }
                try {
                    writePrivate(data, offset, byteCount);
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public void flush() throws IOException {
                if (closed) {
                    throw new IOException("Underlying writer is closed.");
                }
                try {
                    segmentQueue.emit(true);
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public void close() throws IOException {
                try {
                    RealWriter.this.close();
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public String toString() {
                return RealWriter.this + ".asOutputStream()";
            }
        };
    }

    @Override
    public @NonNull WritableByteChannel asWritableByteChannel() {
        return new WritableByteChannel() {
            @Override
            public int write(final @NonNull ByteBuffer reader) throws IOException {
                Objects.requireNonNull(reader);
                if (closed) {
                    throw new ClosedChannelException();
                }
                try {
                    return transferFromPrivate(reader);
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
                    RealWriter.this.close();
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public String toString() {
                return RealWriter.this + ".asWritableByteChannel()";
            }
        };
    }
}
