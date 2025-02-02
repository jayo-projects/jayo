/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.JayoInterruptedIOException;
import jayo.RawReader;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

sealed class ReaderSegmentQueue extends SegmentQueue permits ReaderSegmentQueue.Async {

    static @NonNull ReaderSegmentQueue newReaderSegmentQueue(final @NonNull RawReader reader,
                                                             final boolean preferAsync) {
        Objects.requireNonNull(reader);

        // If reader is a RealReader, we return its existing segment queue as is (async or sync).
        if (reader instanceof RealReader realReader) {
            return realReader.segmentQueue;
        }

        if (preferAsync) {
            if (reader instanceof PeekRawReader) {
                throw new IllegalArgumentException("PeekRawReader does not support the 'async' option");
            }
            return new ReaderSegmentQueue.Async(reader);
        }

        return new ReaderSegmentQueue(reader);
    }

    static @NonNull ReaderSegmentQueue newSyncReaderSegmentQueue(final @NonNull RawReader reader) {
        Objects.requireNonNull(reader);

        if (reader instanceof RealReader realReader && !(realReader.segmentQueue instanceof ReaderSegmentQueue.Async)) {
            return realReader.segmentQueue;
        }

        return new ReaderSegmentQueue(reader);
    }

    final @NonNull RawReader reader;
    final @NonNull RealBuffer buffer;
    boolean closed = false;

    ReaderSegmentQueue(final @NonNull RawReader reader) {
        this.reader = Objects.requireNonNull(reader);
        this.buffer = new RealBuffer(this);
    }

    @Override
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

        reader.close();
        buffer.clear();
    }

    final static class Async extends ReaderSegmentQueue {
        private static final System.Logger LOGGER = System.getLogger("jayo.AsyncReaderSegmentQueue");
        private final static ThreadFactory SOURCE_CONSUMER_THREAD_FACTORY =
                JavaVersionUtils.threadFactory("JayoReaderConsumer#");

        private final @NonNull Thread readerConsumerThread;

        private volatile @Nullable RuntimeException exception = null;

        // non-volatile because always used inside the lock
        private long expectedSize = 0;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition expectingSize = lock.newCondition();
        // when reader is temporarily exhausted or the segment queue is full
        private final Condition readerConsumerPaused = lock.newCondition();
        private volatile boolean readerConsumerTerminated = false;

        private Async(final @NonNull RawReader reader) {
            super(reader);
            readerConsumerThread = SOURCE_CONSUMER_THREAD_FACTORY.newThread(new ReaderConsumer());
            readerConsumerThread.start();
        }

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

        long expectSize(final long expectedSize) {
            assert expectedSize > 0L;
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
                // consumer thread may have not read all data, continue here in current thread
                final var stillExpectingSize = this.expectedSize;
                if (stillExpectingSize > 0) {
                    this.expectedSize = 0L;
                    currentSize = super.expectSize(stillExpectingSize);
                    // resume reader consumer thread to continue reading asynchronously
                    readerConsumerPaused.signal();
                } else {
                    currentSize = size();
                }

                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "AsyncReaderSegmentQueue#{0}: expectSize({1}) resumed expecting more " +
                                    "bytes, current size = {2}{3}",
                            hashCode(), expectedSize, currentSize, System.lineSeparator());
                }
                return currentSize;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Retain interrupted status.
                close();
                throw new JayoInterruptedIOException("current thread is interrupted");
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void close() {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "AsyncReaderSegmentQueue#{0}: Start close(){1}",
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
            reader.close();
            buffer.clear();

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
                            final boolean readSuccess;
                            if (currentExpectedSize != 0L) {
                                // if size is already reached -> success, else must continue to call the reader in the
                                // calling thread to ensure no race occurs
                                readSuccess = size() >= currentExpectedSize;
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
                                                            " last read did not return any result, expected size = " +
                                                            "{1}, current size = {2} pausing consumer thread{3}",
                                                    hashCode(), currentExpectedSize, currentSize,
                                                    System.lineSeparator());
                                        } else {
                                            LOGGER.log(DEBUG,
                                                    "AsyncReaderSegmentQueue#{0}:ReaderConsumer Runnable task:" +
                                                            " buffer reached or exceeded max capacity: {1}/{2}," +
                                                            " pausing consumer thread{3}",
                                                    hashCode(), currentSize, MAX_BYTE_SIZE, System.lineSeparator());
                                        }
                                    }
                                    // if read did not return any result or buffer reached max capacity, resume
                                    // expecting size, then pause consumer thread
                                    resumeExpectingSize();
                                    readerConsumerPaused.await();
                                    currentExpectedSize = 0L;
                                }

                                if (currentExpectedSize == 0L) {
                                    currentExpectedSize = expectedSize;
                                } else {
                                    currentExpectedSize = 0L;
                                    expectedSize = 0L;
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
                    LOGGER.log(DEBUG, "AsyncReaderSegmentQueue#{0}: ReaderConsumer Runnable task: end",
                            hashCode());
                }
            }

            private void resumeExpectingSize() {
                assert lock.isHeldByCurrentThread();
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "AsyncReaderSegmentQueue#{0}: resumeExpectingSize()", hashCode());
                }
                expectingSize.signal();
            }
        }
    }
}
