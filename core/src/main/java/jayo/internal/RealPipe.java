/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static jayo.tools.JayoUtils.checkOffsetAndCount;

public final class RealPipe implements Pipe {
    final long maxBufferSize;

    final @NonNull RealBuffer buffer = new RealBuffer();
    /**
     * Reference to the current read buffer supplied by the client. This field is only valid during a read operation.
     * This field is used instead of {@link #buffer} in order to avoid any copy of the returned bytes when possible.
     */
    private @Nullable RealBuffer suppliedBuffer;
    private long suppliedBytesToRead = 0;

    private boolean canceled = false;
    private boolean writerClosed = false;
    private boolean readerClosed = false;
    private @Nullable RawWriter foldedWriter = null;

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    public RealPipe(final long maxBufferSize) {
        if (maxBufferSize < 1L) {
            throw new IllegalArgumentException("maxBufferSize < 1: " + maxBufferSize);
        }
        this.maxBufferSize = maxBufferSize;
    }

    @Override
    public @NonNull RawReader getReader() {
        return new RawReader() {
            @Override
            public long readAtMostTo(final @NonNull Buffer destination, final long byteCount) {
                Objects.requireNonNull(destination);
                if (byteCount < 0L) {
                    throw new IllegalArgumentException("byteCount < 0: " + byteCount);
                }

                if (byteCount == 0L) {
                    return 0L;
                }

                lock.lock();
                try {
                    if (readerClosed) {
                        throw new IllegalStateException("closed");
                    }
                    if (canceled) {
                        throw new JayoException("canceled");
                    }

                    // the buffer may already have available data
                    if (!buffer.exhausted()) {
                        final var result = buffer.readAtMostTo(destination, byteCount);
                        condition.signalAll(); // Notify the writer that it can resume writing.
                        return result;
                    }

                    final var cancelToken = JavaVersionUtils.getCancelToken();

                    while (true) {
                        if (writerClosed) {
                            return -1L;
                        }

                        final var initialDestByteSize = destination.bytesAvailable();
                        suppliedBuffer = (RealBuffer) destination;
                        suppliedBytesToRead = byteCount;
                        try {
                            awaitCondition(cancelToken); // Wait until the writer fills the buffer.
                        } finally {
                            suppliedBuffer = null;
                            suppliedBytesToRead = 0L;
                        }

                        final var bytesRead = destination.bytesAvailable() - initialDestByteSize;
                        if (bytesRead > 0L) {
                            condition.signalAll(); // Notify the writer that it can resume writing.
                            return bytesRead;
                        }
                        if (canceled) {
                            throw new JayoException("canceled");
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public void close() {
                lock.lock();
                try {
                    readerClosed = true;
                    condition.signalAll(); // Notify the writer that no more bytes are desired.
                } finally {
                    lock.unlock();
                }
            }
        };
    }

    @Override
    public @NonNull RawWriter getWriter() {
        return new RawWriter() {
            @Override
            public void writeFrom(final @NonNull Buffer source, final long byteCount) {
                Objects.requireNonNull(source);
                checkOffsetAndCount(source.bytesAvailable(), 0L, byteCount);

                if (byteCount == 0L) {
                    return;
                }

                var remaining = byteCount;
                RawWriter delegate = null;
                lock.lock();
                try {
                    if (writerClosed) {
                        throw new IllegalStateException("closed");
                    }
                    if (canceled) {
                        throw new JayoException("canceled");
                    }

                    final var cancelToken = JavaVersionUtils.getCancelToken();

                    while (remaining > 0) {
                        if (foldedWriter != null) {
                            delegate = foldedWriter;
                            break;
                        }

                        if (readerClosed) {
                            throw new JayoException("reader is closed");
                        }

                        final RealBuffer destination;
                        final long bytesToWrite;
                        if (suppliedBuffer != null) {
                            destination = suppliedBuffer;
                            bytesToWrite = Math.min(suppliedBytesToRead, remaining);
                        } else {
                            final var bufferSpaceAvailable = maxBufferSize - buffer.byteSize;
                            if (bufferSpaceAvailable == 0L) {
                                awaitCondition(cancelToken); // Wait until the reader drains the buffer.
                                if (canceled) {
                                    throw new JayoException("canceled");
                                }
                                continue;
                            }

                            destination = buffer;
                            bytesToWrite = Math.min(bufferSpaceAvailable, remaining);
                        }

                        destination.writeFrom(source, bytesToWrite);
                        remaining -= bytesToWrite;

                        if (suppliedBuffer != null) {
                            suppliedBuffer = null;
                            suppliedBytesToRead = 0L;
                        }

                        condition.signalAll(); // Notify the reader that it can resume reading.
                    }
                } finally {
                    lock.unlock();
                }

                if (delegate != null) {
                    delegate.writeFrom(source, remaining);
                }
            }

            @Override
            public void flush() {
                RawWriter delegate = null;
                lock.lock();
                try {
                    // check if the writer is closed
                    if (writerClosed) {
                        throw new IllegalStateException("closed");
                    }
                    if (canceled) {
                        throw new JayoException("canceled");
                    }

                    if (foldedWriter != null) {
                        delegate = foldedWriter;
                    } else if (readerClosed && buffer.byteSize > 0L) {
                        throw new JayoException("reader is closed");
                    }
                } finally {
                    lock.unlock();
                }

                if (delegate != null) {
                    delegate.flush();
                }
            }

            @Override
            public void close() {
                RawWriter delegate = null;
                lock.lock();
                try {
                    if (writerClosed) {
                        return;
                    }

                    if (foldedWriter != null) {
                        delegate = foldedWriter;
                    } else {
                        if (readerClosed && buffer.byteSize > 0L) {
                            throw new JayoException("reader is closed");
                        }
                        writerClosed = true;
                        condition.signalAll(); // Notify the reader that no more bytes are coming.
                    }
                } finally {
                    lock.unlock();
                }

                if (delegate != null) {
                    delegate.close();
                }
            }
        };
    }

    private void awaitCondition(final @Nullable RealCancelToken cancelToken) {
        if (cancelToken != null) {
            cancelToken.awaitSignal(condition);
            return;
        }

        try {
            condition.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Retain interrupted status.
            throw new JayoInterruptedIOException("current thread is interrupted");
        }
    }

    @Override
    public void fold(final @NonNull RawWriter writer) {
        Objects.requireNonNull(writer);

        while (true) {
            // Either the buffer is empty, and we can swap and return. Or the buffer is non-empty, and we must copy it
            // to the writer without holding any locks, then try it all again.
            var closed = false;
            var done = false;
            RealBuffer writerBuffer = null;
            lock.lock();
            try {
                if (foldedWriter != null) {
                    throw new IllegalStateException("writer already folded");
                }

                if (canceled) {
                    foldedWriter = writer;
                    throw new JayoException("canceled");
                }

                closed = writerClosed;
                if (buffer.exhausted()) {
                    readerClosed = true;
                    foldedWriter = writer;
                    done = true;
                } else {
                    writerBuffer = new RealBuffer();
                    writerBuffer.writeFrom(buffer, buffer.byteSize);
                    condition.signalAll(); // Notify the writer that it can resume writing.
                }
            } finally {
                lock.unlock();
            }

            if (done) {
                if (closed) {
                    writer.close();
                }
                return;
            }

            var success = false;
            try {
                writer.writeFrom(writerBuffer, writerBuffer.bytesAvailable());
                writer.flush();
                success = true;
            } finally {
                if (!success) {
                    lock.lock();
                    try {
                        readerClosed = true;
                        condition.signalAll(); // Notify the writer that it can resume writing.
                    } finally {
                        lock.unlock();
                    }
                }
            }
        }

    }

    @Override
    public void cancel() {
        lock.lock();
        try {
            canceled = true;
            buffer.clear();
            condition.signalAll(); // Notify the reader and writer that they're canceled.
        } finally {
            lock.unlock();
        }
    }
}
