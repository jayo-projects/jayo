/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import jayo.RawSink;
import jayo.exceptions.JayoCancelledException;
import jayo.external.NonNegative;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.DEBUG;

final class SinkSegmentQueue extends SegmentQueue {
    private static final System.Logger LOGGER = System.getLogger("o.u.d.SinkSegmentQueue");
    private final static Thread.Builder SINK_EMITTER_THREAD_BUILDER =
            ThreadUtils.threadBuilder("JayoSinkEmitter#");

    private final @NonNull RawSink sink;
    private final @NonNull RealBuffer buffer;
    private final @NonNull Thread sinkEmitterThread;

    private final @NonNull LongAdder size = new LongAdder();
    private boolean closed = false;
    private volatile boolean sinkEmitterTerminated = false;
    private volatile @Nullable RuntimeException exception = null;
    private final @NonNull BlockingQueue<EmitEvent> emitEvents = new LinkedBlockingQueue<>();
    private @Nullable Segment lastEmittedCompleteSegment = null;

    private final Lock lock = new ReentrantLock();
    private volatile boolean isSegmentQueueFull = false;
    private final Condition segmentQueueNotFull = lock.newCondition();
    private final Condition pausedForFlush = lock.newCondition();

    SinkSegmentQueue(final @NonNull RawSink sink) {
        this.sink = Objects.requireNonNull(sink);
        this.buffer = new RealBuffer(this);
        sinkEmitterThread = SINK_EMITTER_THREAD_BUILDER.start(new SinkEmitter());
    }

    @Override
    @Nullable Segment head() {
        return cleanupAndGetHead(false);
    }

    private @Nullable Segment cleanupAndGetHead(final boolean remove) {
        var currentNext = next;
        while (currentNext != this) {
            if (currentNext.pos != currentNext.limit) {
                return currentNext;
            }
            if (remove) {
                SegmentPool.recycle(forceRemoveHead());
                currentNext = next;
            } else {
                // no remove head, attempt with the next node
                currentNext = currentNext.next;
            }
        }
        return null;
    }

    @Override
    @Nullable Segment removeHead() {
        if (closed) {
            return super.removeHead();
        }
        return null; // will be recycled later, by the next forceRemoveHead() call or when closing the Sink
    }

    // @SuppressWarnings("RedundantMethodOverride")
    @Override
    @NonNull Segment forceRemoveHead() {
        final var removedHead = super.removeHead();
        assert removedHead != null;
        return removedHead;
    }

    void pauseIfFull() {
        throwIfNeeded();
        var currentSize = size();
        if (currentSize > MAX_BYTE_SIZE) {
            lock.lock();
            try {
                // try again after acquiring the lock
                currentSize = size();
                if (currentSize > MAX_BYTE_SIZE && !sinkEmitterTerminated) {
                    isSegmentQueueFull = true;
                    segmentQueueNotFull.await();
                    throwIfNeeded();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Retain interrupted status.
                close();
                throw new JayoCancelledException("current thread is interrupted");
            } finally {
                lock.unlock();
            }
        }
    }

    void emitCompleteSegments() {
        var s = tail();
        if (s == null) {
            // can happen when we write nothing, like sink.writeUtf8("")
            return;
        }

        // 1) scan tail
        if (!s.owner || s.limit == Segment.SIZE) {
            emitEventIfRequired(s);
            return;
        }
        // 2) scan tail's previous
        s = s.prev;
        if (s != this && s != null) {
            emitEventIfRequired(s);
        }
    }

    private void emitEventIfRequired(Segment s) {
        if (lastEmittedCompleteSegment == null || lastEmittedCompleteSegment != s) {
            emitEvents.add(new EmitEvent(s, s.limit, false));
            lastEmittedCompleteSegment = s;
        }
    }

    void emit(final boolean flush) {
        throwIfNeeded();
        final var tail = tail();
        if (tail == null) {
            LOGGER.log(DEBUG, "You should not emit or flush without writing data first. We do nothing");
            return;
        }
        final var emitEvent = new EmitEvent(tail, tail.limit, flush);
        if (!flush) {
            emitEvents.add(emitEvent);
            return;
        }

        // must acquire the lock because thread will pause until flush really executes
        lock.lock();
        try {
            if (!sinkEmitterTerminated) {
                emitEvents.add(emitEvent);
                pausedForFlush.await();
                throwIfNeeded();
            } else {
                emitEvents.add(emitEvent);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Retain interrupted status.
            close();
            throw new JayoCancelledException("current thread is interrupted");
        } finally {
            lock.unlock();
        }
    }

    @Override
    long size() {
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
    public void close() {
        throwIfNeeded();
        if (closed) {
            return;
        }
        closed = true;
        if (!sinkEmitterTerminated) {
            sinkEmitterThread.interrupt();
            try {
                sinkEmitterThread.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    @NonNull RealBuffer getBuffer() {
        return buffer;
    }

//    private void recycleDeletingHeads() {
//        var currentNext = next;
//        while (currentNext != this && (currentNext.pos == currentNext.limit)) {
//            final var newNext = currentNext.next;
//            next = newNext;
//            newNext.prev = this;
//            // clean head for recycling
//            currentNext.prev = null;
//            currentNext.next = null;
//            SegmentPool.recycle(currentNext);
//            currentNext = newNext;
//        }
//    }

    private void throwIfNeeded() {
        final var currentException = exception;
        if (currentException != null && !closed) {
            // remove exception, then throw it
            exception = null;
            throw currentException;
        }
    }

    private final class SinkEmitter implements Runnable {
        @Override
        public void run() {
            try {
                Segment lastHead = null;
                while (!Thread.interrupted()) {
                    try {
                        final var emitEvent = emitEvents.take();
                        var s = cleanupAndGetHead(true);
                        if (s == null) {
                            if (lastHead == null || emitEvent.segment != lastHead) {
                                throw new IllegalStateException("EmitEvent must target the head segment, head = " + s
                                        + " lastHead = " + lastHead + " emitEvent = " + emitEvent);
                            }
                        } else {
                            var toWrite = 0L;
                            while (true) {
                                if (s == emitEvent.segment) {
                                    toWrite += emitEvent.limit - s.pos;
                                    break;
                                } else if (s == SinkSegmentQueue.this) {
                                    throw new IllegalStateException("EmitEvent must target the head segment, head = " + s
                                            + " lastHead = " + lastHead + " emitEvent = " + emitEvent);
                                } else {
                                    toWrite += s.limit - s.pos;
                                    s = s.next;
                                }
                            }
                            if (toWrite > 0) {
                                sink.write(buffer, toWrite);
                            }
                        }

                        final var awaitingNonFull = isSegmentQueueFull;
                        final var stillFull = size() > MAX_BYTE_SIZE;
                        if (awaitingNonFull) {
                            if (stillFull) {
                                continue;
                            }
                            isSegmentQueueFull = false;
                        }

                        if (emitEvent.flush) {
                            sink.flush();
                        }

                        if (awaitingNonFull || emitEvent.flush) {
                            lock.lockInterruptibly();
                            try {
                                if (emitEvent.flush) {
                                    pausedForFlush.signal();
                                } else {
                                    segmentQueueNotFull.signal();
                                }
                            } finally {
                                lock.unlock();
                            }
                        }

                        lastHead = s;
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
                sinkEmitterTerminated = true;
                lock.lock();
                try {
                    segmentQueueNotFull.signal();
                    pausedForFlush.signal();
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    @Override
    @NonNull Segment removeTail() {
        throw new IllegalStateException("removeTail is only needed for UnsafeCursor in Buffer mode, " +
                "it must not be used for Sink mode");
    }

    @Override
    void forEach(@NonNull Consumer<Segment> action) {
        throw new IllegalStateException("forEach is only needed for hash in Buffer mode, " +
                "it must not be used for Sink mode");
    }

    private record EmitEvent(@NonNull Segment segment, @NonNegative int limit, boolean flush) {
    }
}
