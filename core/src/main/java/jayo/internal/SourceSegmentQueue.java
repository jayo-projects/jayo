/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.RawSource;
import jayo.exceptions.JayoCancelledException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.locks.Condition;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

sealed class SourceSegmentQueue extends SegmentQueue permits SourceSegmentQueue.Async {
    final @NonNull RawSource source;
    final @NonNull RealBuffer buffer;
    boolean closed = false;

    SourceSegmentQueue(final @NonNull RawSource source) {
        this.source = Objects.requireNonNull(source);
        this.buffer = new RealBuffer(this);
    }

    @Override
    @NonNegative
    long expectSize(final long expectedSize) {
        if (expectedSize < 1L) {
            throw new IllegalArgumentException("expectedSize < 1 : " + expectedSize);
        }
        // fast-path : current size is enough
        final var currentSize = size();
        if (currentSize >= expectedSize || closed) {
            return currentSize;
        }
        // else read from source until expected size is reached or source is exhausted
        var remaining = expectedSize - currentSize;
        while (remaining > 0L) {
            final var toRead = Math.max(remaining, Segment.SIZE);
            final var read = source.readAtMostTo(buffer, toRead);
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

    final static class Async extends SourceSegmentQueue {
        private final static Thread.Builder SOURCE_CONSUMER_THREAD_BUILDER =
                Utils.threadBuilder("JayoSourceConsumer#");

        private static final System.Logger LOGGER = System.getLogger("jayo.AsyncSourceSegmentQueue");

        private final @NonNull Thread sourceConsumerThread;

        private volatile @Nullable RuntimeException exception = null;

        private @NonNegative long expectedSize = 0;
        private final Condition expectingSize = lock.newCondition();
        // when source is temporarily exhausted or the segment queue is full
        private final Condition sourceConsumerPaused = lock.newCondition();
        private volatile boolean sourceConsumerTerminated = false;

        Async(final @NonNull RawSource source) {
            super(source);
            sourceConsumerThread = SOURCE_CONSUMER_THREAD_BUILDER.start(new SourceConsumer());
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

            // else read from source until expected size is reached or source is exhausted
            lock.lock();
            try {
                // try again after acquiring the lock
                currentSize = size();
                if (currentSize >= expectedSize || sourceConsumerTerminated) {
                    return currentSize;
                }
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, """
                                    AsyncSourceSegmentQueue#{0}: expectSize({1}) pausing expecting more bytes
                                    , current size = {2}
                                    segment queue =
                                    {3}{4}""",
                            segmentQueueId, expectedSize, currentSize, this, System.lineSeparator());
                }
                // we must wait until expected size is reached, or no more write operation
                this.expectedSize = expectedSize;
                // resume source consumer thread if needed, then await on expected size
                sourceConsumerPaused.signal();
                expectingSize.await();
                currentSize = size();
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "AsyncSourceSegmentQueue#{0}: expectSize({1}) resumed expecting more " +
                                    "bytes, current size = {2}{3}",
                            segmentQueueId, expectedSize, currentSize, System.lineSeparator());
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
                LOGGER.log(TRACE, "AsyncSourceSegmentQueue#{0}: Start close{1}",
                        segmentQueueId, System.lineSeparator());
            }
            if (closed) {
                return;
            }
            closed = true;
            if (!sourceConsumerTerminated) {
                sourceConsumerThread.interrupt();
                try {
                    sourceConsumerThread.join();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "AsyncSourceSegmentQueue#{0}: Finished close{1}",
                        segmentQueueId, System.lineSeparator());
            }
        }

        private final class SourceConsumer implements Runnable {
            @Override
            public void run() {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "AsyncSourceSegmentQueue#{0}:SourceConsumer Runnable task: start",
                            segmentQueueId);
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
                                        final var read = source.readAtMostTo(buffer, toRead);
                                        if (read == -1) {
                                            break;
                                        }
                                        remaining -= read;
                                    }
                                }
                            } else {
                                readSuccess = source.readAtMostTo(buffer, Segment.SIZE) > 0L;
                            }

                            lock.lockInterruptibly();
                            try {
                                final var currentSize = size();
                                if (!readSuccess || currentSize >= MAX_BYTE_SIZE) {
                                    if (LOGGER.isLoggable(DEBUG)) {
                                        if (!readSuccess) {
                                            LOGGER.log(DEBUG,
                                                    "AsyncSourceSegmentQueue#{0}:SourceConsumer Runnable task:" +
                                                            " source is exhausted, expected size = {1}, pausing" +
                                                            " consumer thread{2}",
                                                    segmentQueueId, currentExpectedSize, System.lineSeparator());
                                        } else {
                                            LOGGER.log(DEBUG,
                                                    "AsyncSourceSegmentQueue#{0}:SourceConsumer Runnable task:" +
                                                            " buffer reached or exceeded max capacity: {1}/{2}," +
                                                            " pausing consumer thread{3}",
                                                    segmentQueueId, currentSize, MAX_BYTE_SIZE, System.lineSeparator());
                                        }
                                    }
                                    // if read did not return any result or buffer reached max capacity,
                                    // resume expecting size, then pause consumer thread
                                    currentExpectedSize = 0L;
                                    resumeExpectingSize();
                                    sourceConsumerPaused.await();
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
                    // end of source consumer thread : we mark it as terminated, and we signal (= resume) the main thread
                    sourceConsumerTerminated = true;
                    lock.lock();
                    try {
                        resumeExpectingSize();
                    } finally {
                        lock.unlock();
                    }
                }
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "AsyncSourceSegmentQueue#{0}:SourceConsumer Runnable task: end",
                            segmentQueueId);
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
