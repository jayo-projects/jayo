/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
 *
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.internal;

import jayo.Buffer;
import jayo.CancelScope;
import jayo.RawReader;
import jayo.RawWriter;
import jayo.JayoException;
import jayo.JayoTimeoutException;
import jayo.external.AsyncTimeout;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static jayo.external.JayoUtils.checkOffsetAndCount;

/**
 * This timeout uses a background watchdog thread to take action exactly when the timeout occurs. Use this to implement
 * timeouts where they aren't supported natively, such as to sockets or channels that are blocked on writing.
 * <p>
 * Subclasses should provide {@link #onTimeout} to take action when a timeout occurs. This method will be invoked by
 * the shared watchdog thread, so it should not do any long-running operations. Otherwise, we risk starving other
 * timeouts from being triggered.
 * <p>
 * Use {@link #writer} and {@link #reader} to apply this timeout to a stream. The returned value will apply the timeout
 * to each operation on the wrapped stream.
 * <p>
 * Callers should call {@link #enter} before doing work that is subject to timeouts, and {@link #exit} afterward. The
 * return value of {@link #exit} indicates whether a timeout was triggered. Note that the call to {@link #onTimeout} is
 * asynchronous, and may be called after {@link #exit}.
 */
public final class RealAsyncTimeout implements AsyncTimeout {
    /**
     * Don't write more than 4 full segments (~67 KiB) of data at a time. Otherwise, slow connections may suffer
     * timeouts even when they're making (slow) progress. Without this, writing a single 1 MiB buffer may never succeed
     * on a sufficiently slow connection.
     */
    static final int TIMEOUT_WRITE_SIZE = 4 * Segment.SIZE;

    /**
     * True if this node is currently in the queue.
     */
    private boolean inQueue = false;

    /**
     * The next node in the linked list.
     */
    @Nullable
    private RealAsyncTimeout next = null;

    /**
     * If scheduled, this is the time that the watchdog should time this out.
     */
    private long timeoutAt = 0L;

    private final @NonNull Runnable onTimeout;

    public RealAsyncTimeout(final @NonNull Runnable onTimeout) {
        this.onTimeout = onTimeout;
    }


    @Override
    public void enter(final @NonNull CancelScope cancelScope) {
        if (!(Objects.requireNonNull(cancelScope) instanceof RealCancelToken cancelToken)) {
            throw new IllegalArgumentException("cancelScope must be an instance of CancelToken");
        }
        // CancelToken is finished, shielded or there is no timeout and no deadline : Don't bother with the queue.
        if (cancelToken.finished || cancelToken.shielded || (cancelToken.deadlineNanoTime == 0L
                && cancelToken.timeoutNanos == 0L)) {
            return;
        }
        scheduleTimeout(this, cancelToken.timeoutNanos, cancelToken.deadlineNanoTime);
    }

    @Override
    public boolean exit() {
        return cancelScheduledTimeout(this);
    }

    /**
     * Returns the amount of time left until the time-out. This will be negative if the timeout has
     * elapsed and the timeout should occur immediately.
     */
    private long remainingNanos(final long now) {
        return timeoutAt - now;
    }

    @Override
    public @NonNull RawWriter writer(final @NonNull RawWriter writer) {
        Objects.requireNonNull(writer);
        return new RawWriter() {
            @Override
            public void write(final @NonNull Buffer reader, final @NonNegative long byteCount) {
                Objects.requireNonNull(reader);
                checkOffsetAndCount(reader.bytesAvailable(), 0, byteCount);
                if (!(reader instanceof RealBuffer _reader)) {
                    throw new IllegalArgumentException("reader must be an instance of RealBuffer");
                }

                // get cancel token immediately, if present it will be used in all I/O calls
                final var cancelToken = CancellableUtils.getCancelToken();

                if (cancelToken == null) {
                    writer.write(reader, byteCount);
                    return;
                }

                var remaining = byteCount;
                while (remaining > 0L) {
                    // Count how many bytes to write. This loop guarantees we split on a segment boundary.
                    var _toWrite = 0L;
                    var segment = _reader.segmentQueue.head();
                    while (_toWrite < TIMEOUT_WRITE_SIZE) {
                        assert segment != null;
                        final var segmentSize = segment.limitVolatile() - segment.pos;
                        _toWrite += segmentSize;
                        if (_toWrite >= remaining) {
                            _toWrite = remaining;
                            break;
                        }
                        segment = segment.nextVolatile();
                    }

                    final var toWrite = _toWrite;
                    // Emit one write. Only this section is subject to the timeout.
                    withTimeout(cancelToken, () -> {
                        writer.write(reader, toWrite);
                        return null;
                    });
                    remaining -= _toWrite;
                }
            }

            @Override
            public void flush() {
                final var cancelToken = CancellableUtils.getCancelToken();
                if (cancelToken != null) {
                    withTimeout(cancelToken, () -> {
                        writer.flush();
                        return null;
                    });
                    return;
                }
                writer.flush();
            }

            @Override
            public void close() {
                final var cancelToken = CancellableUtils.getCancelToken();
                if (cancelToken != null) {
                    withTimeout(cancelToken, () -> {
                        writer.close();
                        return null;
                    });
                    return;
                }
                writer.close();
            }

            @Override
            @NonNull
            public String toString() {
                return "AsyncTimeout.writer(" + writer + ")";
            }
        };
    }

    @Override
    public @NonNull RawReader reader(final @NonNull RawReader reader) {
        Objects.requireNonNull(reader);
        return new RawReader() {
            @Override
            public long readAtMostTo(final @NonNull Buffer writer, final @NonNegative long byteCount) {
                Objects.requireNonNull(writer);
                if (byteCount < 0L) {
                    throw new IllegalArgumentException("byteCount < 0 : " + byteCount);
                }
                final var cancelToken = CancellableUtils.getCancelToken();
                if (cancelToken != null) {
                    return withTimeout(cancelToken, () -> reader.readAtMostTo(writer, byteCount));
                }
                return reader.readAtMostTo(writer, byteCount);
            }

            @Override
            public void close() {
                final var cancelToken = CancellableUtils.getCancelToken();
                if (cancelToken != null) {
                    withTimeout(cancelToken, () -> {
                        reader.close();
                        return null;
                    });
                    return;
                }
                reader.close();
            }

            @Override
            @NonNull
            public String toString() {
                return "AsyncTimeout.reader(" + reader + ")";
            }
        };
    }

    @Override
    public <T> T withTimeout(final @NonNull CancelScope cancelScope, final @NonNull Supplier<T> block) {
        Objects.requireNonNull(cancelScope);
        Objects.requireNonNull(block);

        var throwOnTimeout = false;
        enter(cancelScope);
        try {
            final var result = block.get();
            throwOnTimeout = true;
            return result;
        } catch (JayoException e) {
            cancelScope.cancel();
            throw (!exit()) ? e : newTimeoutException(e);
        } finally {
            final var timedOut = exit();
            if (timedOut && throwOnTimeout) {
                cancelScope.cancel();
                throw newTimeoutException(null);
            }
        }
    }

    /**
     * @return a {@link JayoTimeoutException} to represent a timeout. If {@code cause} is non-null it is set as the
     * cause of the returned exception.
     */
    private @NonNull JayoTimeoutException newTimeoutException(final @Nullable JayoException cause) {
        final var e = new JayoTimeoutException("timeout");
        if (cause != null) {
            e.initCause(cause);
        }
        return e;
    }

    private static final Lock LOCK = new ReentrantLock();
    private static final Condition CONDITION = LOCK.newCondition();

    /**
     * Duration for the watchdog thread to be idle before it shuts itself down.
     */
    private static final long IDLE_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(60);
    private static final long IDLE_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(IDLE_TIMEOUT_MILLIS);

    /**
     * The watchdog thread processes a linked list of pending timeouts, sorted in the order to be
     * triggered. This class synchronizes on AsyncTimeout.class. This lock guards the queue.
     * <p>
     * Head's 'next' points to the first element of the linked list. The first element is the next
     * node to time out, or null if the queue is empty. The head is null until the watchdog thread
     * is started and also after being idle for {@link #IDLE_TIMEOUT_MILLIS}.
     */
    private static @Nullable RealAsyncTimeout head = null;

    private final static Thread.Builder ASYNC_TIMEOUT_WATCHDOG_THREAD_BUILDER =
            Utils.threadBuilder("JayoWatchdog#");

    private static void scheduleTimeout(RealAsyncTimeout node, final long timeoutNanos, final long deadlineNanoTime) {
        LOCK.lock();
        try {
            if (node.inQueue) {
                throw new IllegalStateException("Unbalanced enter/exit");
            }
            node.inQueue = true;

            // Start the watchdog thread and create the head node when the first timeout is scheduled.
            if (head == null) {
                head = new RealAsyncTimeout(() -> {
                });
                ASYNC_TIMEOUT_WATCHDOG_THREAD_BUILDER.start(new Watchdog());
            }

            final var now = System.nanoTime();
            if (timeoutNanos != 0L && deadlineNanoTime != 0L) {
                // Compute the earliest event; either timeout or deadline. Because nanoTime can wrap
                // around, minOf() is undefined for absolute values, but meaningful for relative ones.
                node.timeoutAt = now + Math.min(timeoutNanos, deadlineNanoTime - now);
            } else if (timeoutNanos != 0L) {
                node.timeoutAt = now + timeoutNanos;
            } else if (deadlineNanoTime != 0L) {
                node.timeoutAt = deadlineNanoTime;
            } else {
                throw new AssertionError();
            }

            // Insert the node in sorted order.
            final var remainingNanos = node.remainingNanos(now);
            var prev = head;
            while (true) {
                if (prev.next == null || remainingNanos < prev.next.remainingNanos(now)) {
                    node.next = prev.next;
                    prev.next = node;
                    if (prev == head) {
                        // Wake up the watchdog when inserting at the front.
                        CONDITION.signal();
                    }
                    break;
                }
                prev = prev.next;
            }
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Returns true if the timeout occurred.
     */
    private static boolean cancelScheduledTimeout(final @NonNull RealAsyncTimeout node) {
        LOCK.lock();
        try {
            if (!node.inQueue) {
                return false;
            }
            node.inQueue = false;

            // Remove the node from the linked list.
            var prev = head;
            while (prev != null) {
                if (prev.next == node) {
                    prev.next = node.next;
                    node.next = null;
                    return false;
                }
                prev = prev.next;
            }

            // The node wasn't found in the linked list: it must have timed out!
            return true;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Removes and returns the node at the head of the list, waiting for it to time out if
     * necessary. This returns {@link #head} if there was no node at the head of the list when starting,
     * and there continues to be no node after waiting {@link #IDLE_TIMEOUT_NANOS}. It returns null if a
     * new node was inserted while waiting. Otherwise, this returns the node being waited on that has
     * been removed.
     */
    private static @Nullable RealAsyncTimeout awaitTimeout() throws InterruptedException {
        assert head != null;
        // Get the next eligible node.
        final var node = head.next;

        // The queue is empty. Wait until either something is enqueued or the idle timeout elapses.
        if (node == null) {
            final var startNanos = System.nanoTime();
            CONDITION.await(IDLE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            return (head.next == null && System.nanoTime() - startNanos >= IDLE_TIMEOUT_NANOS)
                    ? head // The idle timeout elapsed.
                    : null; // The situation has changed.
        }

        var waitNanos = node.remainingNanos(System.nanoTime());

        // The head of the queue hasn't timed out yet. Await that.
        if (waitNanos > 0) {
            CONDITION.await(waitNanos, TimeUnit.NANOSECONDS);
            return null;
        }

        // The head of the queue has timed out. Remove it.
        head.next = node.next;
        node.next = null;
        return node;
    }

    private static final class Watchdog implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    RealAsyncTimeout timedOut;
                    LOCK.lock();
                    try {
                        timedOut = awaitTimeout();

                        // The queue is completely empty. Let this thread exit and let another watchdog thread get
                        // created on the next call to scheduleTimeout().
                        if (timedOut == head) {
                            head = null;
                            return;
                        }
                    } finally {
                        LOCK.unlock();
                    }

                    // Close the timed out node, if one was found.
                    if (timedOut != null) {
                        timedOut.onTimeout.run();
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }
    }
}
