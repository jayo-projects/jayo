/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.JayoInterruptedIOException;
import jayo.RawReader;
import jayo.scheduling.TaskRunner;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.Logger.Level.TRACE;

sealed class ReaderSegmentQueue extends SegmentQueue permits ReaderSegmentQueue.Async {

    static @NonNull ReaderSegmentQueue newReaderSegmentQueue(final @NonNull RawReader reader,
                                                             final @Nullable TaskRunner taskRunner) {
        assert reader != null;

        // If reader is a RealReader, we return its existing segment queue as is (async or sync).
        if (reader instanceof RealReader realReader) {
            return realReader.segmentQueue;
        }

        if (taskRunner != null) {
            if (reader instanceof PeekRawReader) {
                throw new IllegalArgumentException("PeekRawReader does not support the 'async' option");
            }
            return new Async(reader, taskRunner);
        }

        return new ReaderSegmentQueue(reader);
    }

    static @NonNull ReaderSegmentQueue newSyncReaderSegmentQueue(final @NonNull RawReader reader) {
        assert reader != null;

        if (reader instanceof RealReader realReader && !(realReader.segmentQueue instanceof Async)) {
            return realReader.segmentQueue;
        }

        return new ReaderSegmentQueue(reader);
    }

    final @NonNull RawReader reader;
    final @NonNull RealBuffer buffer;
    boolean closed = false;

    ReaderSegmentQueue(final @NonNull RawReader reader) {
        assert reader != null;
        this.reader = reader;
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

        private final @NonNull TaskRunner taskRunner;

        // non-volatile because always used inside the lock
        private long expectedSize = 0;
        private final @NonNull Lock asyncReaderLock = new ReentrantLock();
        private final @NonNull Condition expectingSize = asyncReaderLock.newCondition();

        private volatile @Nullable RuntimeException exception = null;

        // status
        private static final byte NOT_STARTED = 1;
        private static final byte START_NEEDED = 2;
        private static final byte STARTED = 3;

        private byte status = NOT_STARTED;

        private final @NonNull Runnable readerConsumer;

        private Async(final @NonNull RawReader reader, final @NonNull TaskRunner taskRunner) {
            super(reader);
            assert taskRunner != null;

            this.taskRunner = taskRunner;
            readerConsumer = () -> {
                try {
                    if (LOGGER.isLoggable(TRACE)) {
                        LOGGER.log(TRACE, "AsyncReaderSegmentQueue#{0}:ReaderConsumer Runnable task: start",
                                hashCode());
                    }
                    var currentExpectedSize = 0L;
                    while (true) {
                        final long currentSize;
                        if (currentExpectedSize == 0L) {
                            asyncReaderLock.lock();
                            try {
                                status = STARTED; // not a problem to do this several times.

                                currentExpectedSize = expectedSize;
                                currentSize = size();
                                // could happen in harmless race condition
                                if (currentExpectedSize > 0L && currentSize >= currentExpectedSize) {
                                    currentExpectedSize = 0L;
                                    expectingSize.signal();
                                }
                            } finally {
                                asyncReaderLock.unlock();
                            }
                        } else {
                            currentSize = size();
                            // if size is already reached -> success, else must continue to call the reader in the
                            // calling thread to ensure no race occurs
                            if (currentSize >= currentExpectedSize) {
                                asyncReaderLock.lock();
                                try {
                                    currentExpectedSize = 0L;
                                    expectedSize = 0L;
                                    expectingSize.signal();
                                } finally {
                                    asyncReaderLock.unlock();
                                }
                            }
                        }

                        if (currentExpectedSize == 0L && currentSize >= MAX_BYTE_SIZE) {
                            if (LOGGER.isLoggable(TRACE)) {
                                LOGGER.log(TRACE,
                                        "AsyncReaderSegmentQueue#{0}:ReaderConsumer Runnable task:" +
                                                " buffer reached or exceeded max capacity: {1}/{2}," +
                                                " stopping consumer thread{3}",
                                        hashCode(), currentSize, MAX_BYTE_SIZE, System.lineSeparator());
                            }
                            if (tryBreak(false)) {
                                break;
                            } else {
                                continue;
                            }
                        }

                        final var toRead = Math.max(Segment.SIZE, currentExpectedSize - currentSize);
                        final var readSuccess = reader.readAtMostTo(buffer, toRead) > 0L;

                        if (!readSuccess) {
                            if (LOGGER.isLoggable(TRACE)) {
                                LOGGER.log(TRACE,
                                        "AsyncReaderSegmentQueue#{0}:ReaderConsumer Runnable task:" +
                                                " last read did not return any result, expected size = " +
                                                "{1}, current size = {2} stopping consumer thread{3}",
                                        hashCode(), currentExpectedSize, currentSize,
                                        System.lineSeparator());
                            }
                            if (tryBreak(false)) {
                                break;
                            }
                        }
                    }
                } catch (Throwable t) {
                    if (LOGGER.isLoggable(TRACE)) {
                        LOGGER.log(TRACE, "AsyncReaderSegmentQueue#" + hashCode() + ": Exception thrown.", t);
                    }
                    if (t instanceof RuntimeException runtimeException) {
                        exception = runtimeException;
                    } else {
                        exception = new RuntimeException(t);
                    }
                    tryBreak(true);
                }
            };
        }

        private boolean tryBreak(final boolean force) {
            asyncReaderLock.lock();
            try {
                // end of reader consumer thread : we mark it as terminated, and we signal (= resume) the main thread
                if (force || status == STARTED) {
                    status = NOT_STARTED;
                    if (LOGGER.isLoggable(TRACE)) {
                        LOGGER.log(TRACE, "AsyncReaderSegmentQueue#{0}: ReaderConsumer Runnable task: end",
                                hashCode());
                    }
                    expectingSize.signal();
                    return true;
                }
                return false;
            } finally {
                asyncReaderLock.unlock();
            }
        }

        @Override
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

        @Override
        long expectSize(final long expectedSize) {
            assert expectedSize > 0L;
            // fast-path : current size is enough
            var currentSize = size();
            if (currentSize >= expectedSize || closed) {
                return currentSize;
            }

            // else read from reader until expected size is reached or reader is exhausted
            asyncReaderLock.lock();
            try {
                // try again after acquiring the lock
                currentSize = size();
                if (currentSize >= expectedSize) {
                    return currentSize;
                }
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, """
                                    AsyncReaderSegmentQueue#{0}: expectSize({1}) pausing expecting more bytes
                                    , current size = {2}
                                    segment queue =
                                    {3}{4}""",
                            hashCode(), expectedSize, currentSize, this, System.lineSeparator());
                }
                // we must wait until expected size is reached, or no more write operation
                this.expectedSize = expectedSize;
                startEmitterIfNeeded();
                // resume reader consumer thread if needed, then await on expected size
                expectingSize.await();

                currentSize = size();
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, "AsyncReaderSegmentQueue#{0}: expectSize({1}) resumed expecting more " +
                                    "bytes, current size = {2}{3}",
                            hashCode(), expectedSize, currentSize, System.lineSeparator());
                }
                return currentSize;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Retain interrupted status.
                close();
                throw new JayoInterruptedIOException("current thread is interrupted");
            } finally {
                asyncReaderLock.unlock();
            }
        }

        private void startEmitterIfNeeded() {
            final var mustStart = status == NOT_STARTED;
            status = START_NEEDED;
            if (mustStart) {
                taskRunner.execute(false, readerConsumer);
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

            asyncReaderLock.lock();
            try {
                if (status != NOT_STARTED) {
                    // force reader consumer task to end as soon as possible and wait
                    expectedSize = 1L;
                    expectingSize.await();
                }
            } catch (InterruptedException e) {
                // ignore
            } finally {
                asyncReaderLock.unlock();
            }
            reader.close();

            lock.lock();
            try {
                buffer.clear();
            } finally {
                lock.unlock();
            }

            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "AsyncReaderSegmentQueue#{0}: Finished close{1}",
                        hashCode(), System.lineSeparator());
            }
        }
    }
}
