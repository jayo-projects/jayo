/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.JayoClosedResourceException;
import jayo.JayoInterruptedIOException;
import jayo.RawWriter;
import jayo.scheduling.TaskRunner;
import jayo.tools.BasicFifoQueue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.Logger.Level.TRACE;

sealed class WriterSegmentQueue extends SegmentQueue permits WriterSegmentQueue.Async {

    static @NonNull WriterSegmentQueue newWriterSegmentQueue(final @NonNull RawWriter writer,
                                                             final @Nullable TaskRunner taskRunner) {
        assert writer != null;

        // If writer is a RealWriter, we return its existing segment queue as is (async or sync).
        if (writer instanceof RealWriter realWriter) {
            return realWriter.segmentQueue;
        }

        return taskRunner != null ? new WriterSegmentQueue.Async(writer, taskRunner) : new WriterSegmentQueue(writer);
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

        /**
         * A specific STOP event that will be sent to {@link #emitEvents} queue
         * to trigger the end of the async emitter thread
         */
        private final static EmitEvent FLUSH_EVENT = new EmitEvent(
                new Segment(new byte[0], 0, 0, null, false),
                false, -42);

        private final @NonNull TaskRunner taskRunner;

        private volatile @Nullable RuntimeException exception = null;

        private final @NonNull BasicFifoQueue<EmitEvent> emitEvents = BasicFifoQueue.create();
        private @Nullable EmitEvent lastEmittedEvent = null;

        // status
        static final byte NOT_STARTED = 1;
        static final byte START_NEEDED = 2;
        static final byte STARTED = 3;

        byte status;

        private boolean isSegmentQueueFull = false;
        private final Lock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();

        private final @NonNull Runnable writerEmitter;

        Async(final @NonNull RawWriter writer, final @NonNull TaskRunner taskRunner) {
            super(writer);

            status = NOT_STARTED;
            this.taskRunner = taskRunner;

            writerEmitter = () -> {
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, "AsyncWriterSegmentQueue#{0}: WriterEmitter Runnable task: start",
                            hashCode());
                }
                try {
                    while (true) {
                        var toWrite = 0L;
                        lock.lock();
                        try {
                            status = STARTED; // not a problem to do this several times.

                            final var currentSize = size();
                            final var wasFull = isSegmentQueueFull;
                            if (wasFull && currentSize <= MAX_BYTE_SIZE) {
                                isSegmentQueueFull = false;
                            }

                            if (wasFull && !isSegmentQueueFull) {
                                condition.signal();
                            }

                            final var emitEvent = emitEvents.poll();
                            if (LOGGER.isLoggable(TRACE)) {
                                LOGGER.log(TRACE, "AsyncWriterSegmentQueue#{0}: consuming event{1}{2}{3}",
                                        hashCode(), System.lineSeparator(), emitEvent, System.lineSeparator());
                            }
                            if (emitEvent == null) {
                                break;
                            }
                            if (emitEvent == FLUSH_EVENT) {
                                writer.flush();
                                break;
                            }

                            var segment = head;
                            assert segment != null;
                            if ((emitEvent.including || segment.next != null)) {
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
                                    assert segment != null;
                                    toWrite += segment.limit - segment.pos;
                                }
                            }
                            // guarding against a concurrent read that may have reduced head(s) size(s)
                            toWrite = Math.min(toWrite, currentSize);
                        } finally {
                            lock.unlock();
                        }

                        if (toWrite > 0) {
                            writer.write(buffer, toWrite);
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
                    lock.lock();
                    try {
                        status = NOT_STARTED;
                        condition.signal();
                    } finally {
                        lock.unlock();
                    }
                }
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, "AsyncWriterSegmentQueue#{0}: WriterEmitter Runnable task: end",
                            hashCode());
                }
            };
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
                    if (currentSize > MAX_BYTE_SIZE) {
                        isSegmentQueueFull = true;
                        condition.await();
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
            lock.lock();
            try {
                var currentTail = tail;
                if (currentTail == null) {
                    // can happen when we write nothing, like writer.write("")
                    return;
                }

                final var includingTail = !currentTail.owner || currentTail.limit == Segment.SIZE;
                if (includingTail || currentTail != head) { // we have something to emit
                    emitEventIfRequired(currentTail, includingTail);
                }
            } finally {
                lock.unlock();
            }
        }

        private void emitEventIfRequired(final @NonNull Segment segment, final boolean includingTail) {
            final var toEmit = new EmitEvent(segment, includingTail, segment.limit);
            if (!toEmit.equals(lastEmittedEvent)) {
                emitEvent(toEmit);
            }
        }

        @Override
        void emit(final boolean flush) {
            throwIfNeeded();

            lock.lock();
            try {
                final var currentTail = tail;
                if (currentTail == null) {
                    // can happen when we write nothing, like writer.write("")
                    return;
                }

                emitEventIfRequired(currentTail, true);

                if (flush) {
                    // emit the flush event
                    emitEvent(FLUSH_EVENT);

                    condition.await();
                    if (LOGGER.isLoggable(TRACE)) {
                        LOGGER.log(TRACE, "AsyncWriterSegmentQueue#{0}: flush done{1}{2}{3}",
                                hashCode(), System.lineSeparator(), this, System.lineSeparator());
                    }
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

        private void emitEvent(final @NonNull EmitEvent emitEvent) {
            assert emitEvent != null;

            emitEvents.offer(emitEvent);
            lastEmittedEvent = emitEvent;

            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "AsyncWriterSegmentQueue#{0}: emitting event{1}{2}{3}",
                        hashCode(), System.lineSeparator(), emitEvent, System.lineSeparator());
            }
            // resume reader consumer thread if needed, then await on expected size
            if (status == NOT_STARTED) {
                status = START_NEEDED;
                taskRunner.execute(false, writerEmitter);
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
            lock.lock();
            try {
                if (status != NOT_STARTED) {
                    emitEvents.clear();
                    condition.await();
                }
            } catch (InterruptedException e) {
                // ignore
            } finally {
                lock.unlock();
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

        private record EmitEvent(@NonNull Segment segment, boolean including, int limit) {
        }
    }
}
