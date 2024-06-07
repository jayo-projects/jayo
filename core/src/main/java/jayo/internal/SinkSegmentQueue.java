/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.RawSink;
import jayo.exceptions.JayoCancelledException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

sealed class SinkSegmentQueue extends SegmentQueue {
    final @NonNull RawSink sink;
    final @NonNull RealBuffer buffer;

    SinkSegmentQueue(final @NonNull RawSink sink) {
        this.sink = Objects.requireNonNull(sink);
        this.buffer = new RealBuffer(this);
    }

    void pauseIfFull() {
        // nop for synchronous segment queue
    }

    void emitCompleteSegments() {
        final var byteCount = buffer.completeSegmentByteCount();
        if (byteCount > 0L) {
            sink.write(buffer, byteCount);
        }
    }

    void emit(final boolean flush) {
        final var byteCount = buffer.byteSize();
        if (byteCount > 0L) {
            sink.write(buffer, byteCount);
        }
        if (flush) {
            sink.flush();
        }
    }

    @Override
    public void close() {
        // nop
    }

    @NonNull
    final RealBuffer getBuffer() {
        return buffer;
    }

    final static class Async extends SinkSegmentQueue {
        private static final System.Logger LOGGER = System.getLogger("jayo.SinkSegmentQueue");
        private final static Thread.Builder SINK_EMITTER_THREAD_BUILDER = Utils.threadBuilder("JayoSinkEmitter#");

        private volatile @Nullable RuntimeException exception = null;
        boolean closed = false;

        private final @NonNull Thread sinkEmitterThread;

        private volatile boolean sinkEmitterTerminated = false;
        private final @NonNull BlockingQueue<EmitEvent> emitEvents = new LinkedBlockingQueue<>();
        private @Nullable Segment lastEmittedCompleteSegment = null;
        private boolean lastEmittedIncluding = false;

        private volatile boolean isSegmentQueueFull = false;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition segmentQueueNotFull = lock.newCondition();
        private final Condition pausedForFlush = lock.newCondition();

        Async(final @NonNull RawSink sink) {
            super(sink);
            sinkEmitterThread = SINK_EMITTER_THREAD_BUILDER.start(new SinkEmitter());
        }

        @Override
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

        @Override
        void emitCompleteSegments() {
            var currentTail = tailVolatile();
            if (currentTail == null) {
                // can happen when we write nothing, like sink.writeUtf8("")
                return;
            }

            final var includingTail = !currentTail.owner || currentTail.limit() == Segment.SIZE;
            emitEventIfRequired(currentTail, includingTail);
        }

        private void emitEventIfRequired(final @NonNull Segment segment, final boolean includingTail) {
            if (lastEmittedCompleteSegment == null || lastEmittedCompleteSegment != segment
                    || (includingTail && !lastEmittedIncluding)) {
                emitEvents.add(new EmitEvent(segment, includingTail, segment.limit(), false));
                lastEmittedCompleteSegment = segment;
                lastEmittedIncluding = includingTail;
            }
        }

        @Override
        void emit(final boolean flush) {
            throwIfNeeded();
            final var currentTail = tailVolatile();
            if (currentTail == null) {
                LOGGER.log(DEBUG, "AsyncSinkSegmentQueue#{0}: You should not emit or flush without writing data " +
                        "first. We do nothing", hashCode());
                return;
            }
            final var emitEvent = new EmitEvent(currentTail, true, currentTail.limit(), flush);
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
        public void close() {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "AsyncSinkSegmentQueue#{0}: Start close{1}",
                        hashCode(), System.lineSeparator());
            }
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
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "AsyncSinkSegmentQueue#{0}: Finished close{1}",
                        hashCode(), System.lineSeparator());
            }
        }

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
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "AsyncSinkSegmentQueue#{0}:SinkEmitter Runnable task: start", hashCode());
                }
                try {
                    mainLoop:
                    while (!Thread.interrupted()) {
                        try {
                            final var emitEvent = emitEvents.take();
                            var segment = headVolatile();
                            if (segment != null && (emitEvent.including || segment.nextVolatile() != null)) {
                                var toWrite = 0L;
                                while (true) {
                                    final var nextSegment = segment.nextVolatile();
                                    if (!emitEvent.including && nextSegment != null && emitEvent.segment == nextSegment) {
                                        toWrite += segment.limit() - segment.pos;
                                        break;
                                    }
                                    if (emitEvent.including && emitEvent.segment == segment) {
                                        toWrite += emitEvent.limit - segment.pos;
                                        break;
                                    }
                                    segment = nextSegment;
                                    if (segment == null) {
                                        continue mainLoop;
                                    }
                                    toWrite += segment.limit() - segment.pos;
                                }
                                if (toWrite > 0) {
                                    sink.write(getBuffer(), toWrite);
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
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "AsyncSinkSegmentQueue#{0}:SinkEmitter Runnable task: end", hashCode());
                }
            }
        }

        private record EmitEvent(@NonNull Segment segment, boolean including, @NonNegative int limit, boolean flush) {
        }
    }
}
