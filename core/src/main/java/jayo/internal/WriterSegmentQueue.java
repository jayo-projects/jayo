/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.JayoClosedResourceException;
import jayo.JayoInterruptedIOException;
import jayo.RawWriter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

sealed class WriterSegmentQueue extends SegmentQueue permits WriterSegmentQueue.Async {

    static @NonNull WriterSegmentQueue newWriterSegmentQueue(final @NonNull RawWriter writer,
                                                             final boolean preferAsync) {
        Objects.requireNonNull(writer);

        // If writer is a RealWriter, we return its existing segment queue as is (async or sync).
        if (writer instanceof RealWriter realWriter) {
            return realWriter.segmentQueue;
        }

        return preferAsync ? new WriterSegmentQueue.Async(writer) : new WriterSegmentQueue(writer);
    }

    final @NonNull RawWriter writer;
    final @NonNull RealBuffer buffer;
    boolean closed = false;

    WriterSegmentQueue(final @NonNull RawWriter writer) {
        this.writer = Objects.requireNonNull(writer);
        this.buffer = new RealBuffer(this);
    }

    void pauseIfFull() {
        // nop for synchronous segment queue
    }

    void emitCompleteSegments() {
        final var byteCount = buffer.completeSegmentByteCount();
        if (byteCount > 0L) {
            writer.write(buffer, byteCount);
        }
    }

    void emit(final boolean flush) {
        final var byteCount = buffer.bytesAvailable();
        if (byteCount > 0L) {
            writer.write(buffer, byteCount);
        }
        if (flush) {
            writer.flush();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        // best effort to emit buffered data to the underlying writer. If this fails, we still need to close the writer;
        // otherwise we risk leaking resources.
        Throwable thrown = null;
        try {
            final var size = buffer.bytesAvailable();
            if (size > 0) {
                writer.write(buffer, size);
            }
        } catch (JayoInterruptedIOException | JayoClosedResourceException ignored) {
            // cancellation lead to closing, and a closed endpoint must be ignored
        } catch (Throwable e) {
            thrown = e;
        }

        try {
            writer.close();
        } catch (Throwable e) {
            if (thrown == null) {
                thrown = e;
            }
        }

        // clear buffer in case the previous write operation could not write all remaining data from the buffer
        final var size = buffer.bytesAvailable();
        if (size > 0) {
            buffer.clear();
        }

        closed = true;

        if (thrown != null) {
            if (thrown instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw (Error) thrown;
        }
    }

    final static class Async extends WriterSegmentQueue {
        private static final System.Logger LOGGER = System.getLogger("jayo.WriterSegmentQueue");
        private final static ThreadFactory SINK_EMITTER_THREAD_FACTORY =
                JavaVersionUtils.threadFactory("JayoWriterEmitter#");

        /**
         * A specific STOP event that will be sent to {@link #emitEvents} queue
         * to trigger the end of the async emitter thread
         */
        private final static EmitEvent STOP_EMITTER_THREAD = new EmitEvent(
                new Segment(new byte[0], 0, 0, null, false),
                false, -42, false);

        private volatile @Nullable RuntimeException exception = null;

        private final @NonNull Thread writerEmitterThread;

        private volatile boolean writerEmitterTerminated = false;
        private final @NonNull BlockingQueue<EmitEvent> emitEvents = new LinkedBlockingQueue<>();
        private @Nullable Segment lastEmittedCompleteSegment = null;
        private boolean lastEmittedIncluding = false;

        private volatile boolean isSegmentQueueFull = false;
        private final Lock lock = new ReentrantLock();
        private final Condition segmentQueueNotFull = lock.newCondition();
        private final Condition pausedForFlush = lock.newCondition();

        Async(final @NonNull RawWriter writer) {
            super(writer);
            writerEmitterThread = SINK_EMITTER_THREAD_FACTORY.newThread(new WriterEmitter());
            writerEmitterThread.start();
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
                    if (currentSize > MAX_BYTE_SIZE && !writerEmitterTerminated) {
                        isSegmentQueueFull = true;
                        segmentQueueNotFull.await();
                        throwIfNeeded();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Retain interrupted status.
                    close();
                    throw new JayoInterruptedIOException("current thread is interrupted");
                } finally {
                    lock.unlock();
                }
            }
        }

        @Override
        void emitCompleteSegments() {
            var currentTail = tail;
            if (currentTail == null) {
                // can happen when we write nothing, like writer.write("")
                return;
            }

            final var includingTail = !currentTail.owner || currentTail.limit == Segment.SIZE;
            emitEventIfRequired(currentTail, includingTail);
        }

        private void emitEventIfRequired(final @NonNull Segment segment, final boolean includingTail) {
            if (lastEmittedCompleteSegment == null || lastEmittedCompleteSegment != segment
                    || (includingTail && !lastEmittedIncluding)) {
                emitEvents.add(new EmitEvent(segment, includingTail, segment.limit, false));
                lastEmittedCompleteSegment = segment;
                lastEmittedIncluding = includingTail;
            }
        }

        @Override
        void emit(final boolean flush) {
            throwIfNeeded();
            final var currentTail = tail;
            if (currentTail == null) {
                // can happen when we write nothing, like writer.write("")
                return;
            }
            final var emitEvent = new EmitEvent(currentTail, true, currentTail.limit, flush);
            if (!flush) {
                emitEvents.add(emitEvent);
                return;
            }

            // must acquire the lock because thread will pause until flush really executes
            lock.lock();
            try {
                if (!writerEmitterTerminated) {
                    emitEvents.add(emitEvent);
                    pausedForFlush.await();
                    throwIfNeeded();
                } else {
                    emitEvents.add(emitEvent);
                }
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
                LOGGER.log(TRACE, "AsyncWriterSegmentQueue#{0}: Start close(){1}",
                        hashCode(), System.lineSeparator());
            }
            throwIfNeeded();
            if (closed) {
                return;
            }
            if (!writerEmitterTerminated) {
                // send a stop event
                emitEvents.add(STOP_EMITTER_THREAD);
                try {
                    writerEmitterThread.join();
                } catch (InterruptedException e) {
                    // ignore
                }
            }

            super.close();

            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "AsyncWriterSegmentQueue#{0}: Finished close.{1}Queue = {2}{3}",
                        hashCode(), System.lineSeparator(), this, System.lineSeparator());
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

        private final class WriterEmitter implements Runnable {
            @Override
            public void run() {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "AsyncWriterSegmentQueue#{0}: WriterEmitter Runnable task: start", hashCode());
                }
                try {
                    mainLoop:
                    while (!Thread.interrupted()) {
                        try {
                            final var emitEvent = emitEvents.take();
                            if (emitEvent == STOP_EMITTER_THREAD) {
                                break;
                            }
                            var segment = head;
                            if (segment != null && (emitEvent.including || segment.next != null)) {
                                var toWrite = 0L;
                                while (true) {
                                    final var nextSegment = segment.next;
                                    if (!emitEvent.including && nextSegment != null && emitEvent.segment == nextSegment) {
                                        toWrite += segment.limit - segment.pos;
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
                                    toWrite += segment.limit - segment.pos;
                                }
                                if (toWrite > 0) {
                                    // guarding against a concurrent read that may have reduced head(s) size(s)
                                    toWrite = Math.min(toWrite, size());
                                    writer.write(buffer, toWrite);
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
                                writer.flush();
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
                    // end of reader consumer thread : we mark it as terminated,
                    // and we signal (= resume) the main thread
                    writerEmitterTerminated = true;
                    lock.lock();
                    try {
                        segmentQueueNotFull.signal();
                        pausedForFlush.signal();
                    } finally {
                        lock.unlock();
                    }
                }
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "AsyncWriterSegmentQueue#{0}: WriterEmitter Runnable task: end", hashCode());
                }
            }
        }

        private record EmitEvent(@NonNull Segment segment, boolean including, int limit, boolean flush) {
        }
    }
}
