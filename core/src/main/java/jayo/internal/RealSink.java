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
import jayo.exceptions.JayoCancelledException;
import jayo.exceptions.JayoEOFException;
import jayo.exceptions.JayoException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.Objects;

@SuppressWarnings("resource")
public final class RealSink implements Sink {
    private final @NonNull RawSink sink;
    private final @NonNull SinkSegmentQueue segmentQueue;
    final @NonNull RealBuffer buffer;
    private boolean closed = false;

    public static @NonNull Sink buffer(final @NonNull RawSink sink, final boolean async) {
        Objects.requireNonNull(sink);
        if (sink instanceof RealSink realSink) {
            final var isAsync = realSink.segmentQueue instanceof SinkSegmentQueue.Async;
            if (isAsync == async) {
                return realSink;
            }
        }
        return new RealSink(sink, async);
    }

    RealSink(final @NonNull RawSink sink, final boolean async) {
        this.sink = sink;
        if (async) {
            final var asyncSinkSegmentQueue = new SinkSegmentQueue.Async(sink);
            segmentQueue = asyncSinkSegmentQueue;
            buffer = asyncSinkSegmentQueue.getBuffer();
        } else {
            final var syncSinkSegmentQueue = new SinkSegmentQueue(sink);
            segmentQueue = syncSinkSegmentQueue;
            buffer = syncSinkSegmentQueue.getBuffer();
        }
    }

    @Override
    public void write(final @NonNull Buffer source, final @NonNegative long byteCount) {
        Objects.requireNonNull(source);
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.write(source, byteCount);
        emitCompleteSegments();
    }

    @Override
    public @NonNull Sink write(final @NonNull ByteString byteString) {
        Objects.requireNonNull(byteString);
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.write(byteString);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Sink write(final @NonNull ByteString byteString,
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
    public @NonNull Sink writeUtf8(final @NonNull CharSequence charSequence) {
        Objects.requireNonNull(charSequence);
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeUtf8(charSequence);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Sink writeUtf8(final @NonNull CharSequence charSequence,
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
    public @NonNull Sink writeUtf8CodePoint(final @NonNegative int codePoint) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeUtf8CodePoint(codePoint);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Sink writeString(final @NonNull String string, final @NonNull Charset charset) {
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
    public @NonNull Sink writeString(final @NonNull String string,
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
    public @NonNull Sink write(final byte @NonNull [] source) {
        Objects.requireNonNull(source);
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.write(source);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Sink write(final byte @NonNull [] source,
                               final @NonNegative int offset,
                               final @NonNegative int byteCount) {
        Objects.requireNonNull(source);
        if (closed) {
            throw new IllegalStateException("closed");
        }
        return writePrivate(source, offset, byteCount);
    }

    private @NonNull Sink writePrivate(final byte @NonNull [] source,
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

    private @NonNegative int transferFromPrivate(final @NonNull ByteBuffer source) {
        segmentQueue.pauseIfFull();
        final var totalBytesRead = buffer.transferFrom(source);
        emitCompleteSegments();
        return totalBytesRead;
    }

    @Override
    @NonNegative
    public long transferFrom(final @NonNull RawSource source) {
        Objects.requireNonNull(source);
        var totalBytesRead = 0L;
        while (true) {
            if (closed) {
                throw new IllegalStateException("closed");
            }
            segmentQueue.pauseIfFull();
            final var readCount = source.readAtMostTo(buffer, Segment.SIZE);
            if (readCount == -1L) {
                break;
            }
            totalBytesRead += readCount;
            emitCompleteSegments();
        }
        return totalBytesRead;
    }

    @Override
    public @NonNull Sink write(final @NonNull RawSource source, final @NonNegative long byteCount) {
        Objects.requireNonNull(source);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        var _byteCount = byteCount;
        while (_byteCount > 0L) {
            if (closed) {
                throw new IllegalStateException("closed");
            }
            segmentQueue.pauseIfFull();
            final var read = source.readAtMostTo(buffer, _byteCount);
            if (read == -1L) {
                throw new JayoEOFException();
            }
            _byteCount -= read;
            emitCompleteSegments();
        }
        return this;
    }

    @Override
    public @NonNull Sink writeByte(final byte b) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        return writeBytePrivate(b);
    }

    private @NonNull Sink writeBytePrivate(final byte b) {
        segmentQueue.pauseIfFull();
        buffer.writeByte(b);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Sink writeShort(final short s) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeShort(s);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Sink writeInt(final int i) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeInt(i);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Sink writeLong(final long l) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeLong(l);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Sink writeDecimalLong(final long l) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeDecimalLong(l);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Sink writeHexadecimalUnsignedLong(final long l) {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.pauseIfFull();
        buffer.writeHexadecimalUnsignedLong(l);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Sink emitCompleteSegments() {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        segmentQueue.emitCompleteSegments();
        return this;
    }

    @Override
    public @NonNull Sink emit() {
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
        // Emit buffered data to the underlying sink. If this fails, we still need
        // to close the sink; otherwise we risk leaking resources.
        Throwable thrown = null;
        try {
            segmentQueue.close();
        } catch (Throwable e) {
            thrown = e;
        }

        try {
            if (buffer.byteSize() > 0) {
                sink.write(buffer, buffer.byteSize());
            }
        } catch (JayoCancelledException _cancelled) {
            // cancellation lead to closing, ignore
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
        return "buffered(" + sink + ")";
    }

    @Override
    public @NonNull OutputStream asOutputStream() {
        return new OutputStream() {
            @Override
            public void write(final int b) throws IOException {
                if (closed) {
                    throw new IOException("Underlying sink is closed.");
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
                    throw new IOException("Underlying sink is closed.");
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
                    throw new IOException("Underlying sink is closed.");
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
                    RealSink.this.close();
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public String toString() {
                return RealSink.this + ".asOutputStream()";
            }
        };
    }

    @Override
    public @NonNull WritableByteChannel asWritableByteChannel() {
        return new WritableByteChannel() {
            @Override
            public int write(final @NonNull ByteBuffer source) throws IOException {
                Objects.requireNonNull(source);
                if (closed) {
                    throw new ClosedChannelException();
                }
                try {
                    return transferFromPrivate(source);
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
                    RealSink.this.close();
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public String toString() {
                return RealSink.this + ".asWritableByteChannel()";
            }
        };
    }
}
