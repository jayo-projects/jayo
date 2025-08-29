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
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.Objects;

@SuppressWarnings("resource")
public final class RealWriter implements Writer {
    private final @NonNull RawWriter writer;
    final @NonNull RealBuffer buffer = new RealBuffer();
    private boolean closed = false;

    public RealWriter(final @NonNull RawWriter writer) {
        assert writer != null;
        this.writer = writer;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public void writeFrom(final @NonNull Buffer source, final long byteCount) {
        Objects.requireNonNull(source);
        if (closed) {
            throw new JayoClosedResourceException();
        }

        buffer.writeFrom(source, byteCount);
        emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final @NonNull ByteString byteString) {
        Objects.requireNonNull(byteString);

        if (closed) {
            throw new JayoClosedResourceException();
        }

        buffer.write(byteString);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final @NonNull ByteString byteString,
                                 final int offset,
                                 final int byteCount) {
        Objects.requireNonNull(byteString);
        if (closed) {
            throw new JayoClosedResourceException();
        }

        buffer.write(byteString, offset, byteCount);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final @NonNull String string) {
        Objects.requireNonNull(string);
        if (closed) {
            throw new JayoClosedResourceException();
        }

        buffer.write(string);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final @NonNull String string, final @NonNull Charset charset) {
        Objects.requireNonNull(string);
        Objects.requireNonNull(charset);
        if (closed) {
            throw new JayoClosedResourceException();
        }

        buffer.write(string, charset);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeUtf8CodePoint(final int codePoint) {
        if (closed) {
            throw new JayoClosedResourceException();
        }

        buffer.writeUtf8CodePoint(codePoint);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final byte @NonNull [] source) {
        Objects.requireNonNull(source);
        if (closed) {
            throw new JayoClosedResourceException();
        }

        buffer.write(source);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer write(final byte @NonNull [] source,
                                 final int offset,
                                 final int byteCount) {
        Objects.requireNonNull(source);
        if (closed) {
            throw new JayoClosedResourceException();
        }

        return writePrivate(source, offset, byteCount);
    }

    private @NonNull Writer writePrivate(final byte @NonNull [] source,
                                         final int offset,
                                         final int byteCount) {
        Objects.requireNonNull(source);
        if (closed) {
            throw new JayoClosedResourceException();
        }

        buffer.write(source, offset, byteCount);
        return emitCompleteSegments();
    }

    @Override
    public int writeAllFrom(final @NonNull ByteBuffer source) {
        Objects.requireNonNull(source);
        if (closed) {
            throw new JayoClosedResourceException();
        }

        final var totalBytesRead = buffer.writeAllFrom(source);
        emitCompleteSegments();
        return totalBytesRead;
    }

    @Override
    public long writeAllFrom(final @NonNull RawReader source) {
        Objects.requireNonNull(source);

        var totalBytesRead = 0L;
        while (true) {
            if (closed) {
                throw new JayoClosedResourceException();
            }

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
    public @NonNull Writer writeFrom(final @NonNull RawReader source, final long byteCount) {
        Objects.requireNonNull(source);

        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        var _byteCount = byteCount;
        while (_byteCount > 0L) {
            if (closed) {
                throw new JayoClosedResourceException();
            }

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
    public @NonNull Writer writeByte(final byte b) {
        if (closed) {
            throw new JayoClosedResourceException();
        }

        buffer.writeByte(b);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeShort(final short s) {
        if (closed) {
            throw new JayoClosedResourceException();
        }

        buffer.writeShort(s);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeInt(final int i) {
        if (closed) {
            throw new JayoClosedResourceException();
        }

        buffer.writeInt(i);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeLong(final long l) {
        if (closed) {
            throw new JayoClosedResourceException();
        }

        buffer.writeLong(l);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeDecimalLong(final long l) {
        if (closed) {
            throw new JayoClosedResourceException();
        }

        buffer.writeDecimalLong(l);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer writeHexadecimalUnsignedLong(final long l) {
        if (closed) {
            throw new JayoClosedResourceException();
        }

        buffer.writeHexadecimalUnsignedLong(l);
        return emitCompleteSegments();
    }

    @Override
    public @NonNull Writer emitCompleteSegments() {
        if (closed) {
            throw new JayoClosedResourceException();
        }
        final var byteCount = buffer.completeSegmentByteCount();
        if (byteCount > 0L) {
            writer.writeFrom(buffer, byteCount);
        }
        return this;
    }

    @Override
    public @NonNull Writer emit() {
        if (closed) {
            throw new JayoClosedResourceException();
        }
        if (buffer.byteSize > 0L) {
            writer.writeFrom(buffer, buffer.byteSize);
        }
        return this;
    }

    @Override
    public void flush() {
        emit();
        writer.flush();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        // Emit buffered data to the underlying writer. If this fails, we still need to close the writer; otherwise we
        // risk leaking resources.
        Throwable thrown = null;
        try {
            if (buffer.byteSize > 0L) {
                writer.writeFrom(buffer, buffer.byteSize);
            }
        } catch (Throwable e) {
            thrown = e;
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
                    writeByte((byte) b);
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
                    RealWriter.this.flush();
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
                    return writeAllFrom(reader);
                } catch (JayoException e) {
                    throw e.getCause();
                }
            }

            @Override
            public boolean isOpen() {
                return RealWriter.this.isOpen();
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
