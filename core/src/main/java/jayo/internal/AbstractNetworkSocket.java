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
import jayo.network.NetworkSocket;
import jayo.tools.CancelToken;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static jayo.internal.Utils.TIMEOUT_WRITE_SIZE;
import static jayo.internal.Utils.setBitsOrZero;
import static jayo.tools.JayoUtils.checkOffsetAndCount;

/**
 * A {@link NetworkSocket} backed by an underlying {@linkplain Socket IO Socket}.
 */
public sealed abstract class AbstractNetworkSocket implements NetworkSocket
        permits IoSocketNetworkSocket, SocketChannelNetworkSocket {
    final @NonNull RealAsyncTimeout timeout;
    long readTimeoutNanos = 0L;
    final @NonNull RealReader reader;
    private long writeTimeoutNanos = 0L;
    final @NonNull RealWriter writer;

    private final @NonNull AtomicInteger closeBits = new AtomicInteger();
    private static final int WRITER_CLOSED_BIT = 1;
    private static final int READER_CLOSED_BIT = 2;
    private static final int ALL_CLOSED_BITS = WRITER_CLOSED_BIT | READER_CLOSED_BIT;

    AbstractNetworkSocket(final @NonNull RealAsyncTimeout timeout) {
        assert timeout != null;

        this.timeout = timeout;
        reader = new RealReader(new SocketRawReader());
        writer = new RealWriter(new SocketRawWriter());
    }

    @Override
    public final @NonNull Reader getReader() {
        return reader;
    }

    @Override
    public final @NonNull Writer getWriter() {
        return writer;
    }

    @Override
    public final @NonNull Duration getReadTimeout() {
        return Duration.ofNanos(readTimeoutNanos);
    }

    @Override
    public final @NonNull Duration getWriteTimeout() {
        return Duration.ofNanos(writeTimeoutNanos);
    }

    @Override
    public final void setWriteTimeout(final @NonNull Duration writeTimeout) {
        Objects.requireNonNull(writeTimeout);
        writeTimeoutNanos = writeTimeout.toNanos();
    }

    private final class SocketRawReader implements RawReader {
        @Override
        public long readAtMostTo(final @NonNull Buffer destination, final long byteCount) {
            assert destination != null;
            if (byteCount == 0L) {
                return 0L;
            }
            if (byteCount < 0L) {
                throw new IllegalArgumentException("byteCount < 0: " + byteCount);
            }

            final var dst = (RealBuffer) destination;
            // get the cancel token immediately; if present, it will be used in all IO calls of this read operation
            var cancelToken = CancellableUtils.getCancelToken();
            if (cancelToken != null) {
                cancelToken.timeoutNanos = readTimeoutNanos;
                try {
                    return read(dst, byteCount, cancelToken);
                } finally {
                    cancelToken.timeoutNanos = 0L;
                }
            }

            if (readTimeoutNanos != 0L) {
                // use timeoutNanos to create a temporary cancel token, just for this read operation
                cancelToken = new RealCancelToken(readTimeoutNanos, 0L, false);
                CancellableUtils.addCancelToken(cancelToken);
                try {
                    return read(dst, byteCount, cancelToken);
                } finally {
                    CancellableUtils.finishCancelToken(cancelToken);
                }
            }
            // no need for cancellation
            return read(dst, byteCount, null);
        }

        private long read(final @NonNull RealBuffer dst,
                          final long byteCount,
                          final @Nullable RealCancelToken cancelToken) {
            CancelToken.throwIfReached(cancelToken);
            final var dstTail = dst.writableTail(1);
            final var toRead = (int) Math.min(byteCount, Segment.SIZE - dstTail.limit);
            final int read = timeout.withTimeout(cancelToken, () -> {
                try {
                    return AbstractNetworkSocket.this.read(dstTail, toRead);
                } catch (IOException e) {
                    throw JayoException.buildJayoException(e);
                }
            });
            if (read > 0) {
                dstTail.limit += read;
                dst.byteSize += read;
            } else if (dstTail.pos == dstTail.limit) {
                // We allocated a tail segment, but didn't end up needing it. Recycle!
                dst.head = dstTail.pop();
                SegmentPool.recycle(dstTail);
            }
            return read;
        }

        @Override
        public void close() {
            final var cancelToken = CancellableUtils.getCancelToken();
            timeout.withTimeout(cancelToken, () ->
                    switch (setBitsOrZero(closeBits, READER_CLOSED_BIT)) {
                        // If setBitOrZero() returns 0, this reader is already closed.
                        case 0 -> null;
                        // Release the socket if both streams are closed.
                        case ALL_CLOSED_BITS -> {
                            cancel();
                            yield null;
                        }
                        // Close this stream only.
                        default -> {
                            try {
                                shutdownInput();
                                yield null;
                            } catch (IOException e) {
                                throw JayoException.buildJayoException(e);
                            }
                        }
                    });
        }

        @Override
        public @NonNull String toString() {
            return "RawReader(" + getUnderlying() + ")";
        }
    }

    abstract int read(final @NonNull Segment dstTail, final int toRead) throws IOException;

    abstract void shutdownInput() throws IOException;

    private final class SocketRawWriter implements RawWriter {
        @Override
        public void writeFrom(final @NonNull Buffer source, final long byteCount) {
            assert source != null;
            checkOffsetAndCount(source.bytesAvailable(), 0, byteCount);

            final var src = (RealBuffer) source;
            // get the cancel token immediately; if present, it will be used in all IO calls of this write operation
            var cancelToken = CancellableUtils.getCancelToken();
            if (cancelToken != null) {
                cancelToken.timeoutNanos = writeTimeoutNanos;
                try {
                    write(src, byteCount, cancelToken);
                } finally {
                    cancelToken.timeoutNanos = 0L;
                }
            } else if (writeTimeoutNanos != 0L) {
                // use timeoutNanos to create a temporary cancel token, just for this write operation
                cancelToken = new RealCancelToken(writeTimeoutNanos, 0L, false);
                CancellableUtils.addCancelToken(cancelToken);
                try {
                    write(src, byteCount, cancelToken);
                } finally {
                    CancellableUtils.finishCancelToken(cancelToken);
                }
            } else {
                // no need for cancellation
                write(src, byteCount, null);
            }
        }

        private void write(final @NonNull RealBuffer src,
                           final long byteCount,
                           final @Nullable RealCancelToken cancelToken) {
            assert src != null;

            var remaining = byteCount;
            while (remaining > 0L) {
                /*
                 * Don't write more than 4 full segments (~67 KiB) of data at a time. Otherwise, slow connections may
                 * suffer timeouts even when they're making (slow) progress. Without this, writing a single 1 MiB buffer
                 * may never succeed on a sufficiently slow connection.
                 */
                final var toWrite = (int) Math.min(remaining, TIMEOUT_WRITE_SIZE);
                AbstractNetworkSocket.this.write(src, toWrite, cancelToken);
                remaining -= toWrite;
            }
        }

        @Override
        public void flush() {
            final var cancelToken = CancellableUtils.getCancelToken();
            timeout.withTimeout(cancelToken, () -> {
                try {
                    AbstractNetworkSocket.this.flush();
                    return null;
                } catch (IOException e) {
                    throw JayoException.buildJayoException(e);
                }
            });
        }

        @Override
        public void close() {
            final var cancelToken = CancellableUtils.getCancelToken();
            timeout.withTimeout(cancelToken, () ->
                    switch (setBitsOrZero(closeBits, WRITER_CLOSED_BIT)) {
                        // If setBitOrZero() returns 0, this writer is already closed.
                        case 0 -> null;
                        // Release the socket if both streams are closed.
                        case ALL_CLOSED_BITS -> {
                            cancel();
                            yield null;
                        }
                        // Close this stream only.
                        default -> {
                            try {
                                shutdownOutput();
                                yield null;
                            } catch (IOException e) {
                                throw JayoException.buildJayoException(e);
                            }
                        }
                    });
        }

        @Override
        public @NonNull String toString() {
            return "RawWriter(" + getUnderlying() + ")";
        }
    }

    abstract void write(final @NonNull RealBuffer src,
                        final int byteCount,
                        final @Nullable RealCancelToken cancelToken);

    void flush() throws IOException {
    }

    abstract void shutdownOutput() throws IOException;
}
