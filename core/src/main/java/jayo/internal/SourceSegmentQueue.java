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

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;


final class SourceSegmentQueue extends SegmentQueue {
    private final static Thread.Builder SOURCE_CONSUMER_THREAD_BUILDER =
            Utils.threadBuilder("JayoSourceConsumer#");

    private final @NonNull RawSource source;
    private final @NonNull RealBuffer buffer;
    private final @NonNull Thread sourceConsumerThread;

    private final @NonNull LongAdder size = new LongAdder();
    private volatile @NonNegative long expectedSize = 0;
    private volatile boolean sourceConsumerTerminated = false;
    private volatile @Nullable RuntimeException exception = null;
    private boolean closed = false;

    private final Lock lock = new ReentrantLock();
    private final Condition expectingSize = lock.newCondition();
    // when source is temporarily exhausted or the segment queue is full
    private final Condition sourceConsumerPaused = lock.newCondition();

    SourceSegmentQueue(final @NonNull RawSource source) {
        this.source = Objects.requireNonNull(source);
        buffer = new RealBuffer(this);
        sourceConsumerThread = SOURCE_CONSUMER_THREAD_BUILDER.start(new SourceConsumer());
    }

    /**
     * Retrieves the first segment of this queue.
     * <p>
     * If this queue is currently empty or if head is exhausted ({@code head.pos == head.limit}) block until a segment
     * node becomes available or head is not exhausted anymore after a read operation.
     *
     * @return the first segment of this queue, or {@code null} if this queue is empty and there is no read
     * operation left to do.
     */
    @Override
    @Nullable Segment head() {
        // fast-path
        final var currentHead = cleanupAndGetHead();
        if (currentHead != null) {
            return currentHead;
        }
        // read from source once and return head
        expectSize(1L);
        return cleanupAndGetHead();
    }

    private @Nullable Segment cleanupAndGetHead() {
        var currentNext = next;
        while (currentNext != this) {
            if (currentNext.pos != currentNext.limit) {
                return currentNext;
            }
            // try to remove current head
            final var removed = removeHead(true);
            if (removed != null) {
                SegmentPool.recycle(removed);
                currentNext = next;
            } else {
                // remove head failed, attempt with the next node
                currentNext = currentNext.next;
            }
        }
        return null;
    }

    @Override
    @NonNull Segment forceRemoveHead() {
        final var removedHead = removeHead(false);
        assert removedHead != null;
        return removedHead;
    }

    @Override
    @Nullable Segment removeHead() {
        return null; // will be recycled later, by the next head() call or when closing the Source
    }

    private @Nullable Segment removeHead(final boolean mustBeEmpty) {
        final var currentNext = next;
        if (currentNext == this) {
            throw new NoSuchElementException("queue must not be empty to call removeHead");
        }

        final var previousState = (byte) STATUS.compareAndExchange(currentNext, AVAILABLE, DELETING);

        if (previousState != AVAILABLE && previousState != TRANSFERRING) {
            return null;
        }
        final var currentPos = currentNext.pos;
        if (mustBeEmpty && currentPos != currentNext.limit) {
            // reset status and return, must not delete because limit changed
            STATUS.compareAndSet(currentNext, DELETING, AVAILABLE);
            return null;
        }

        final var newNext = currentNext.next;
        next = newNext;
        newNext.prev = this;

        // clean head for recycling
        currentNext.prev = null;
        currentNext.next = null;

        if (!sourceConsumerTerminated) {
            lock.lock();
            try {
                sourceConsumerPaused.signal();
            } finally {
                lock.unlock();
            }
        }

        return currentNext;
    }

    @Override
    long size() {
        throwIfNeeded();
        return size.longValue();
    }

    /**
     * Increment the byte size by {@code increment}.
     *
     * @param increment the value to add.
     * @throws IllegalArgumentException If {@code increment < 1L}
     */
    @Override
    void incrementSize(long increment) {
        size.add(increment);
    }

    /**
     * Decrement the byte size by {@code decrement}.
     *
     * @param decrement the value to subtract.
     * @throws IllegalArgumentException If {@code decrement < 1L}
     */
    @Override
    void decrementSize(long decrement) {
        size.add(-decrement);
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
        // else lock
        lock.lock();
        try {
            // try again after acquiring the lock
            currentSize = size();
            if (currentSize >= expectedSize || sourceConsumerTerminated) {
                return currentSize;
            }
            // we must wait until expected size is reached, or no more write operation
            this.expectedSize = expectedSize;
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

    @NonNull RealBuffer getBuffer() {
        return buffer;
    }

    private void throwIfNeeded() {
        final var currentException = exception;
        if (currentException != null && !closed) {
            // remove exception, then throw it
            exception = null;
            throw currentException;
        }
    }

    private final class SourceConsumer implements Runnable {
        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    try {
                        var expectedS = expectedSize;
                        var size = size();
                        if (expectedS != 0L && size >= expectedS) {
                            // signal expectedSize if byte size fulfilled the expectation
                            lock.lockInterruptibly();
                            try {
                                // byte size fulfilled the expectation
                                expectedSize = 0L; // reset the expectation
                                expectingSize.signal();
                            } finally {
                                lock.unlock();
                            }
                            expectedS = expectedSize;
                        }
                        
                        final var toRead = Math.max(expectedS, Segment.SIZE);
                        // read from source
                        final var read = source.readAtMostTo(buffer, toRead);
                        size = size();
                        if (read > 0 && expectedS != 0L && size < expectedS) {
                            continue; // when expecting a given byte size we must not stop reading from source
                        }

                        lock.lockInterruptibly();
                        try {
                            var mustSignalExpectingSize = false;
                            if (expectedS != 0L && size >= expectedS) {
                                // signal expectedSize if byte size fulfilled the expectation
                                expectedSize = 0L; // reset the expectation
                                mustSignalExpectingSize = true;
                            }
                            if (read <= 0 || size >= MAX_BYTE_SIZE) {
                                // if read did not return any result or buffer reached max capacity,
                                // resume expected size, then pause consumer thread
                                expectingSize.signal();
                                sourceConsumerPaused.await();
                            } else if (mustSignalExpectingSize) {
                                expectingSize.signal();
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
                    expectingSize.signal();
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    @Override
    @NonNull Segment removeTail() {
        throw new IllegalStateException("removeTail is only needed for UnsafeCursor in Buffer mode, " +
                "it must not be used for Source mode");
    }

    @Override
    void forEach(@NonNull Consumer<Segment> action) {
        throw new IllegalStateException("forEach is only needed for hash in Buffer mode, " +
                "it must not be used for Source mode");
    }
}
