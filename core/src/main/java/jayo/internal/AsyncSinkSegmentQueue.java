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

import static java.lang.System.Logger.Level.DEBUG;

final class AsyncSinkSegmentQueue extends AsyncSegmentQueue {
    private static final System.Logger LOGGER = System.getLogger("jayo.SinkSegmentQueue");
    private final static Thread.Builder SINK_EMITTER_THREAD_BUILDER = Utils.threadBuilder("JayoSinkEmitter#");

    private final @NonNull RawSink sink;
    private final @NonNull Thread sinkEmitterThread;

    private volatile boolean sinkEmitterTerminated = false;
    private final @NonNull BlockingQueue<EmitEvent> emitEvents = new LinkedBlockingQueue<>();
    private @Nullable Segment lastEmittedCompleteSegment = null;
    private boolean lastEmittedIncluding = false;

    private volatile boolean isSegmentQueueFull = false;
    private final Condition segmentQueueNotFull = lock.newCondition();
    private final Condition pausedForFlush = lock.newCondition();

    AsyncSinkSegmentQueue(final @NonNull RawSink sink) {
        super();
        this.sink = Objects.requireNonNull(sink);
        sinkEmitterThread = SINK_EMITTER_THREAD_BUILDER.start(new SinkEmitter());
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
        var currentTail = tail();
        if (currentTail == null) {
            // can happen when we write nothing, like sink.writeUtf8("")
            return;
        }

        final var includingTail = !currentTail.segment.owner || currentTail.segment.limit == Segment.SIZE;
        emitEventIfRequired(currentTail.segment, includingTail);
    }

    private void emitEventIfRequired(Segment s, boolean includingTail) {
        if (lastEmittedCompleteSegment == null || lastEmittedCompleteSegment != s || (includingTail && !lastEmittedIncluding)) {
            emitEvents.add(new EmitEvent(s, includingTail, s.limit, false));
            lastEmittedCompleteSegment = s;
            lastEmittedIncluding = includingTail;
        }
    }

    void emit(final boolean flush) {
        throwIfNeeded();
        final var currentTail = tail();
        if (currentTail == null) {
            LOGGER.log(DEBUG, "You should not emit or flush without writing data first. We do nothing");
            return;
        }
        final var emitEvent = new EmitEvent(currentTail.segment, true, currentTail.segment.limit, flush);
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

    private final class SinkEmitter implements Runnable {
        @Override
        public void run() {
            try {
                mainLoop:
                while (!Thread.interrupted()) {
                    try {
                        final var emitEvent = emitEvents.take();
                        var node = head();
                        if (node != null && (emitEvent.including || node.next() != null)) {
                            var toWrite = 0L;
                            while (true) {
                                final var nextNode = node.next();
                                if (!emitEvent.including && nextNode != null && emitEvent.segment == nextNode.segment) {
                                    toWrite += node.segment.limit - node.segment.pos;
                                    break;
                                }
                                if (emitEvent.including && emitEvent.segment == node.segment) {
                                    toWrite += emitEvent.limit - node.segment.pos;
                                    break;
                                }
                                node = nextNode;
                                if (node == null) {
                                    continue mainLoop;
                                }
                                toWrite += node.segment.limit - node.segment.pos;
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
        }
    }

    long expectSize(long expectedSize) {
        throw new IllegalStateException("expectSize is only needed for Source mode, it must not be used for Sink mode");
    }

    private record EmitEvent(@NonNull Segment segment, boolean including, @NonNegative int limit, boolean flush) {
    }
}
