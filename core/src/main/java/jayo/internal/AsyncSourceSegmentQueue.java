/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.RawSource;
import jayo.exceptions.JayoCancelledException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.locks.Condition;


final class AsyncSourceSegmentQueue extends AsyncSegmentQueue {
    private final static Thread.Builder SOURCE_CONSUMER_THREAD_BUILDER =
            Utils.threadBuilder("JayoSourceConsumer#");

    // VarHandle mechanics
    static final VarHandle EXPECTED_SIZE;

    static {
        try {
            final var l = MethodHandles.lookup();
            EXPECTED_SIZE = l.findVarHandle(AsyncSourceSegmentQueue.class, "expectedSize", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @NonNull RawSource source;
    private final @NonNull Thread sourceConsumerThread;

    @SuppressWarnings("FieldMayBeFinal")
    private volatile @NonNegative long expectedSize = 0;
    private final Condition expectingSize = lock.newCondition();
    // when source is temporarily exhausted or the segment queue is full
    private final Condition sourceConsumerPaused = lock.newCondition();
    private volatile boolean sourceConsumerTerminated = false;

    AsyncSourceSegmentQueue(final @NonNull RawSource source) {
        super();
        this.source = Objects.requireNonNull(source);
        sourceConsumerThread = SOURCE_CONSUMER_THREAD_BUILDER.start(new SourceConsumer());
    }

    @Override
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
            // we must wait until expected size is reached, or no more write operation
            if (!EXPECTED_SIZE.compareAndSet(this, 0L, expectedSize)) {
                throw new IllegalStateException("Current expected size must be OL");
            }
            // resume source consumer thread if needed, then await on expected size
            sourceConsumerPaused.signal();
            expectingSize.await();
            return size();
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
    }

    private final class SourceConsumer implements Runnable {
        @Override
        public void run() {
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
                                    final var read = source.readAtMostTo(getBuffer(), toRead);
                                    if (read == -1) {
                                        break;
                                    }
                                    remaining -= read;
                                }
                            }
                        } else {
                            readSuccess = source.readAtMostTo(getBuffer(), Segment.SIZE) > 0L;
                        }

                        lock.lockInterruptibly();
                        try {
                            if (!readSuccess || size() >= MAX_BYTE_SIZE) {
                                // if read did not return any result or buffer reached max capacity,
                                // resume expecting size, then pause consumer thread
                                currentExpectedSize = 0L;
                                resumeExpectingSize();
                                sourceConsumerPaused.await();
                            }

                            if (currentExpectedSize == 0L) {
                                currentExpectedSize = (long) EXPECTED_SIZE.getAndSet(AsyncSourceSegmentQueue.this, 0L);
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
        }

        private void resumeExpectingSize() {
            assert lock.isHeldByCurrentThread();
            EXPECTED_SIZE.setVolatile(AsyncSourceSegmentQueue.this, 0L);
            expectingSize.signal();
        }
    }
}
