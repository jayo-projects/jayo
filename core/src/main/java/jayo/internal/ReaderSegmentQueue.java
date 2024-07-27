/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.RawReader;
import jayo.exceptions.JayoCancelledException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

sealed class ReaderSegmentQueue extends SegmentQueue permits ReaderSegmentQueue.Async {
    final @NonNull RawReader reader;
    final @NonNull RealBuffer buffer;
    boolean closed = false;

    ReaderSegmentQueue(final @NonNull RawReader reader) {
        this.reader = Objects.requireNonNull(reader);
        this.buffer = new RealBuffer(this);
    }

    @Override
    @NonNegative
    long expectSize(final long expectedSize) {
        assert expectedSize > 0L;
        // fast-path : current size is enough
        final var currentSize = size();
        if (currentSize >= expectedSize || closed) {
            return currentSize;
        }
        // else read from reader until expected size is reached or reader is exhausted
        var remaining = expectedSize - currentSize;
        while (remaining > 0L) {
            final var toRead = Math.max(remaining, Segment.SIZE);
            final var read = reader.readAtMostTo(buffer, toRead);
            if (read == -1) {
                break;
            }
            remaining -= read;
        }
        return size();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
    }

    @NonNull
    final RealBuffer getBuffer() {
        return buffer;
    }

    final static class Async extends ReaderSegmentQueue {
        private final static Thread.Builder SOURCE_CONSUMER_THREAD_BUILDER =
                Utils.threadBuilder("JayoReaderConsumer#");

        private static final System.Logger LOGGER = System.getLogger("jayo.AsyncReaderSegmentQueue");

        private final @NonNull Thread readerConsumerThread;

        private volatile @Nullable RuntimeException exception = null;

        private @NonNegative long expectedSize = 0;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition expectingSize = lock.newCondition();
        // when reader is temporarily exhausted or the segment queue is full
        private final Condition readerConsumerPaused = lock.newCondition();
        private volatile boolean readerConsumerTerminated = false;

        Async(final @NonNull RawReader reader) {
            super(reader);
            readerConsumerThread = SOURCE_CONSUMER_THREAD_BUILDER.start(new ReaderConsumer());
        }

        @NonNegative
        long size() {
            throwIfNeeded();
            return super.size();
        }

        private void throwIfNeeded() {
            final var currentException = exception;
            if (currentException != null && !closed) {
                // remove exception, then throw it
                exception = null;
                throw currentException;
            }
        }

        @NonNegative
        long expectSize(final long expectedSize) {
            if (expectedSize < 1L) {
                throw new IllegalArgumentException("expectedSize < 1 : " + expectedSize);
            }
            // fast-path : current size is enough
            var currentSize = size();
            if (currentSize >= expectedSize || closed) {
                return currentSize;
            }

            // else read from reader until expected size is reached or reader is exhausted
            lock.lock();
            try {
                // try again after acquiring the lock
                currentSize = size();
                if (currentSize >= expectedSize || readerConsumerTerminated) {
                    return currentSize;
                }
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, """
                                    AsyncReaderSegmentQueue#{0}: expectSize({1}) pausing expecting more bytes
                                    , current size = {2}
                                    segment queue =
                                    {3}{4}""",
                            hashCode(), expectedSize, currentSize, this, System.lineSeparator());
                }
                // we must wait until expected size is reached, or no more write operation
                this.expectedSize = expectedSize;
                // resume reader consumer thread if needed, then await on expected size
                readerConsumerPaused.signal();
                expectingSize.await();
                currentSize = size();
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "AsyncReaderSegmentQueue#{0}: expectSize({1}) resumed expecting more " +
                                    "bytes, current size = {2}{3}",
                            hashCode(), expectedSize, currentSize, System.lineSeparator());
                }
                return currentSize;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Retain interrupted status.
                close();
                throw new JayoCancelledException("current thread is interrupted");
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void close() {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "AsyncReaderSegmentQueue#{0}: Start close{1}",
                        hashCode(), System.lineSeparator());
            }
            if (closed) {
                return;
            }
            closed = true;
            if (!readerConsumerTerminated) {
                readerConsumerThread.interrupt();
                try {
                    readerConsumerThread.join();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "AsyncReaderSegmentQueue#{0}: Finished close{1}",
                        hashCode(), System.lineSeparator());
            }
        }

        private final class ReaderConsumer implements Runnable {
            @Override
            public void run() {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "AsyncReaderSegmentQueue#{0}:ReaderConsumer Runnable task: start",
                            hashCode());
                }
                try {
                    var currentExpectedSize = 0L;
                    while (!Thread.interrupted()) {
                        try {
                            var readSuccess = true;
                            if (currentExpectedSize != 0L) {
                                final var currentSize = size();
                                if (currentSize < currentExpectedSize) {
                                    var remaining = currentExpectedSize - currentSize;
                                    while (remaining > 0L) {
                                        final var toRead = Math.max(remaining, Segment.SIZE);
                                        final var read = reader.readAtMostTo(buffer, toRead);
                                        if (read == -1) {
                                            break;
                                        }
                                        remaining -= read;
                                    }
                                }
                            } else {
                                readSuccess = reader.readAtMostTo(buffer, Segment.SIZE) > 0L;
                            }

                            lock.lockInterruptibly();
                            try {
                                final var currentSize = size();
                                if (!readSuccess || currentSize >= MAX_BYTE_SIZE) {
                                    if (LOGGER.isLoggable(DEBUG)) {
                                        if (!readSuccess) {
                                            LOGGER.log(DEBUG,
                                                    "AsyncReaderSegmentQueue#{0}:ReaderConsumer Runnable task:" +
                                                            " reader is exhausted, expected size = {1}, pausing" +
                                                            " consumer thread{2}",
                                                    hashCode(), currentExpectedSize, System.lineSeparator());
                                        } else {
                                            LOGGER.log(DEBUG,
                                                    "AsyncReaderSegmentQueue#{0}:ReaderConsumer Runnable task:" +
                                                            " buffer reached or exceeded max capacity: {1}/{2}," +
                                                            " pausing consumer thread{3}",
                                                    hashCode(), currentSize, MAX_BYTE_SIZE, System.lineSeparator());
                                        }
                                    }
                                    // if read did not return any result or buffer reached max capacity,
                                    // resume expecting size, then pause consumer thread
                                    currentExpectedSize = 0L;
                                    resumeExpectingSize();
                                    readerConsumerPaused.await();
                                }

                                if (currentExpectedSize == 0L) {
                                    currentExpectedSize = expectedSize;
                                    expectedSize = 0L;
                                } else {
                                    currentExpectedSize = 0L;
                                    resumeExpectingSize();
                                }
                            } finally {
                                lock.unlock();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt(); // Will stop looping just after
                        }
                    }
                } catch (Throwable t) {
                    if (t instanceof RuntimeException runtimeException) {
                        exception = runtimeException;
                    } else {
                        exception = new RuntimeException(t);
                    }
                } finally {
                    // end of reader consumer thread : we mark it as terminated, and we signal (= resume) the main thread
                    readerConsumerTerminated = true;
                    lock.lock();
                    try {
                        resumeExpectingSize();
                    } finally {
                        lock.unlock();
                    }
                }
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "AsyncReaderSegmentQueue#{0}:ReaderConsumer Runnable task: end",
                            hashCode());
                }
            }

            private void resumeExpectingSize() {
                assert lock.isHeldByCurrentThread();
                expectedSize = 0L;
                LOGGER.log(DEBUG, "resumeExpectingSize()");
                expectingSize.signal();
            }
        }
    }
}
