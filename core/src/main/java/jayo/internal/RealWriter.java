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
import jayo.bytestring.ByteString;
import jayo.scheduling.TaskRunner;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.Objects;

import static jayo.internal.WriterSegmentQueue.newWriterSegmentQueue;

@SuppressWarnings("resource")
public final class RealWriter implements Writer {
    final @NonNull WriterSegmentQueue segmentQueue;

    public RealWriter(final @NonNull RawWriter writer, final @Nullable TaskRunner taskRunner) {
        Objects.requireNonNull(writer);
        segmentQueue = newWriterSegmentQueue(writer, taskRunner);
    }

    @Override
    public void write(final @NonNull Buffer source, final long byteCount) {
        Objects.requireNonNull(source);

        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.write(source, byteCount);
        emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final @NonNull ByteString byteString) {
        Objects.requireNonNull(byteString);

        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.write(byteString);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final @NonNull ByteString byteString,
                                 final int offset,
                                 final int byteCount) {
        Objects.requireNonNull(byteString);

        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.write(byteString, offset, byteCount);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final @NonNull String string) {
        Objects.requireNonNull(string);

        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.write(string);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final @NonNull String string,
                                 final int startIndex,
                                 final int endIndex) {
        Objects.requireNonNull(string);

        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.write(string, startIndex, endIndex);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final @NonNull String string, final @NonNull Charset charset) {
        Objects.requireNonNull(string);
        Objects.requireNonNull(charset);

        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.write(string, charset);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final @NonNull String string,
                                 final int startIndex,
                                 final int endIndex,
                                 final @NonNull Charset charset) {
        Objects.requireNonNull(string);
        Objects.requireNonNull(charset);

        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.write(string, startIndex, endIndex, charset);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeUtf8CodePoint(final int codePoint) {
        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.writeUtf8CodePoint(codePoint);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final byte @NonNull [] source) {
        Objects.requireNonNull(source);
        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.write(source);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final byte @NonNull [] source,
                                 final int offset,
                                 final int byteCount) {
        Objects.requireNonNull(source);

        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        return writePrivate(source, offset, byteCount);
    }

    private @NonNull Writer writePrivate(final byte @NonNull [] source,
                                         final int offset,
                                         final int byteCount) {
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.write(source, offset, byteCount);
        return emitCompleteSegments();
    }

    @Override
    public int transferFrom(final @NonNull ByteBuffer source) {
        Objects.requireNonNull(source);

        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        return transferFromPrivate(source);
    }

    private int transferFromPrivate(final @NonNull ByteBuffer reader) {
        segmentQueue.pauseIfFull();
        final var totalBytesRead = segmentQueue.buffer.transferFrom(reader);
        emitCompleteSegments();
        return totalBytesRead;
    }

    @Override
    public long transferFrom(final @NonNull RawReader source) {
        Objects.requireNonNull(source);

        var totalBytesRead = 0L;
        while (true) {
            if (segmentQueue.closed) {
                throw new JayoClosedResourceException();
            }
            segmentQueue.pauseIfFull();
            final var readCount = source.readAtMostTo(segmentQueue.buffer, Segment.SIZE);
            if (readCount == -1L) {
                break;
            }
            totalBytesRead += readCount;
            emitCompleteSegments();
        }
        return totalBytesRead;
    }

    @Override
    public @NonNull Writer write(final @NonNull RawReader source, final long byteCount) {
        Objects.requireNonNull(source);

        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        var _byteCount = byteCount;
        while (_byteCount > 0L) {
            if (segmentQueue.closed) {
                throw new JayoClosedResourceException();
            }
            segmentQueue.pauseIfFull();
            final var read = source.readAtMostTo(segmentQueue.buffer, _byteCount);
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
        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        return writeBytePrivate(b);
    }

    private @NonNull Writer writeBytePrivate(final byte b) {
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.writeByte(b);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeShort(final short s) {
        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.writeShort(s);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeInt(final int i) {
        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.writeInt(i);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeLong(final long l) {
        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.writeLong(l);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeDecimalLong(final long l) {
        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.writeDecimalLong(l);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeHexadecimalUnsignedLong(final long l) {
        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.pauseIfFull();
        segmentQueue.buffer.writeHexadecimalUnsignedLong(l);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer emitCompleteSegments() {
        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.emitCompleteSegments();
        return this;
    }

    @Override
    public @NonNull Writer emit() {
        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.emit(false);
        return this;
    }

    @Override
    public void flush() {
        if (segmentQueue.closed) {
            throw new JayoClosedResourceException();
        }
        segmentQueue.emit(true);
    }

    @Override
    public void close() {
        segmentQueue.close();
    }

    @Override
    public String toString() {
        return "buffered(" + segmentQueue.writer + ")";
    }

    @Override
    public @NonNull OutputStream asOutputStream() {
        return new OutputStream() {
            @Override
            public void write(final int b) throws IOException {
                if (segmentQueue.closed) {
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

                if (segmentQueue.closed) {
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
                if (segmentQueue.closed) {
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

                if (segmentQueue.closed) {
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
                return !segmentQueue.closed;
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
