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

import jayo.*;
import jayo.tools.AsyncTimeout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static jayo.tools.JayoUtils.checkOffsetAndCount;

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

    /*
     *                                       .-------------.
     *                                       |             |
     *            .------------ exit() ------|  CANCELED   |
     *            |                          |             |
     *            |                          '-------------'
     *            |                                 ^
     *            |                                 |  cancel()
     *            v                                 |
     *     .-------------.                   .-------------.
     *     |             |---- enter() ----->|             |
     *     |    IDLE     |                   |  IN QUEUE   |
     *     |             |<---- exit() ------|             |
     *     '-------------'                   '-------------'
     *            ^                                 |
     *            |                                 |  time out
     *            |                                 v
     *            |                          .-------------.
     *            |                          |             |
     *            '------------ exit() ------|  TIMED OUT  |
     *                                       |             |
     *                                       '-------------'
     *
     * Notes:
     *  * enter() crashes if called from a state other than IDLE.
     *  * If there's no timeout (ie. wait forever), then enter() is a no-op. There's no state to track entered but not
     *    in the queue.
     *  * exit() is a no-op from IDLE. This is probably too lenient, but it made it simpler for early implementations to
     *    support cases where enter() as a no-op.
     *  * cancel() is a no-op from every state but IN QUEUE.
     */

    private static final int STATE_IDLE = 0;
    private static final int STATE_IN_QUEUE = 1;
    private static final int STATE_TIMED_OUT = 2;
    private static final int STATE_CANCELED = 3;

    /**
     * True if this node is currently in the queue.
     */
    private int state = STATE_IDLE;

    private static final Lock LOCK = new ReentrantLock();
    private static final Condition CONDITION = LOCK.newCondition();

    /**
     * The next node in the linked list.
     */
    @Nullable
    private RealAsyncTimeout next = null;

    /**
     * If scheduled, this is the time that the watchdog should time this out.
     */
    private long timeoutAt = 0L;
    private final long defaultReadTimeoutNanos;
    private final long defaultWriteTimeoutNanos;

    private final @NonNull Runnable onTimeout;

    public RealAsyncTimeout(final @NonNull Runnable onTimeout) {
        this(0L, 0L, onTimeout);
    }

    /**
     * @param defaultReadTimeoutNanos  The default read timeout (in nanoseconds). It will be used as fallback for each
     *                                 read operation, only if no timeout is present in the cancellable context. It must
     *                                 be non-negative. A timeout of zero is interpreted as an infinite timeout.
     * @param defaultWriteTimeoutNanos The default write timeout (in nanoseconds). It will be used as fallback for each
     *                                 write operation, only if no timeout is present in the cancellable context. It
     *                                 must be non-negative. A timeout of zero is interpreted as an infinite timeout.
     */
    public RealAsyncTimeout(final long defaultReadTimeoutNanos,
                            final long defaultWriteTimeoutNanos,
                            final @NonNull Runnable onTimeout) {
        assert defaultReadTimeoutNanos >= 0;
        assert defaultWriteTimeoutNanos >= 0;
        assert onTimeout != null;

        this.defaultReadTimeoutNanos = defaultReadTimeoutNanos;
        this.defaultWriteTimeoutNanos = defaultWriteTimeoutNanos;
        this.onTimeout = onTimeout;
    }


    @Override
    public void enter(final @NonNull CancelScope cancelScope) {
        if (!(Objects.requireNonNull(cancelScope) instanceof RealCancelToken cancelToken)) {
            throw new IllegalArgumentException("cancelScope must be an instance of CancelToken");
        }
        // CancelToken is finished, shielded or there is no timeout and no deadline : Don't bother with the queue.
        if (cancelToken.finished || cancelToken.shielded ||
                (cancelToken.deadlineNanoTime == 0L && cancelToken.timeoutNanos == 0L)) {
            return;
        }

        LOCK.lock();
        try {
            if (state != STATE_IDLE) {
                throw new IllegalStateException("Unbalanced enter/exit");
            }
            state = STATE_IN_QUEUE;
            insertIntoQueue(this, cancelToken.timeoutNanos, cancelToken.deadlineNanoTime);
        } finally {
            LOCK.unlock();
        }
    }

    @Override
    public boolean exit() {
        LOCK.lock();
        try {
            final var oldState = this.state;
            state = STATE_IDLE;

            if (oldState == STATE_IN_QUEUE) {
                removeFromQueue(this);
                return false;
            }
            // else
            return oldState == STATE_TIMED_OUT;
        } finally {
            LOCK.unlock();
        }
    }

    void cancel(final boolean isTimeout) {
        LOCK.lock();
        try {
            if (state == STATE_IN_QUEUE) {
                removeFromQueue(this);
                state = STATE_CANCELED;
            }
            if (isTimeout) {
                state = STATE_TIMED_OUT;
            }
        } finally {
            LOCK.unlock();
        }
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
            public void write(final @NonNull Buffer reader, final long byteCount) {
                Objects.requireNonNull(reader);
                checkOffsetAndCount(reader.bytesAvailable(), 0, byteCount);
                if (!(reader instanceof RealBuffer _reader)) {
                    throw new IllegalArgumentException("reader must be an instance of RealBuffer");
                }

                // get cancel token immediately, if present it will be used in all I/O calls of this write operation
                var cancelToken = CancellableUtils.getCancelToken();
                if (cancelToken != null) {
                    cancelToken.timeoutNanos = defaultWriteTimeoutNanos;
                    try {
                        writeCancellable(_reader, byteCount, cancelToken);
                        return;
                    } finally {
                        cancelToken.timeoutNanos = 0L;
                    }
                }

                // no need for cancellation case
                if (defaultWriteTimeoutNanos == 0L) {
                    writer.write(reader, byteCount);
                    return;
                }

                // use defaultWriteTimeoutNanos to create a temporary cancel token, just for this write operation
                cancelToken = new RealCancelToken(defaultWriteTimeoutNanos, 0L, false);
                CancellableUtils.addCancelToken(cancelToken);
                try {
                    writeCancellable(_reader, byteCount, cancelToken);
                } finally {
                    CancellableUtils.finishCancelToken(cancelToken);
                }
            }

            private void writeCancellable(RealBuffer reader, long byteCount, RealCancelToken cancelToken) {
                var remaining = byteCount;
                while (remaining > 0L) {
                    // Count how many bytes to write. This loop guarantees we split on a segment boundary.
                    var _toWrite = 0L;
                    var segment = reader.segmentQueue.head();
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
            public long readAtMostTo(final @NonNull Buffer writer, final long byteCount) {
                Objects.requireNonNull(writer);
                if (byteCount < 0L) {
                    throw new IllegalArgumentException("byteCount < 0 : " + byteCount);
                }

                // get cancel token immediately, if present it will be used in all I/O calls of this read operation
                var cancelToken = CancellableUtils.getCancelToken();
                return withTimeoutOrDefault(cancelToken, defaultReadTimeoutNanos,
                        () -> reader.readAtMostTo(writer, byteCount));
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

    public <T> T withTimeoutOrDefault(final @Nullable RealCancelToken cancelToken,
                                      final long defaultTimeoutNanos,
                                      final @NonNull Supplier<T> block) {
        assert defaultTimeoutNanos >= 0L;
        assert block != null;

        if (cancelToken != null) {
            cancelToken.timeoutNanos = defaultTimeoutNanos;
            try {
                return withTimeout(cancelToken, block);
            } finally {
                cancelToken.timeoutNanos = 0L;
            }
        }

        // no need for cancellation case
        if (defaultTimeoutNanos == 0L) {
            return block.get();
        }

        // use defaultTimeoutNanos to create a temporary cancel token, just for this operation
        final var tempCancelToken = new RealCancelToken(defaultTimeoutNanos, 0L, false);
        CancellableUtils.addCancelToken(tempCancelToken);
        try {
            return withTimeout(tempCancelToken, block);
        } finally {
            CancellableUtils.finishCancelToken(tempCancelToken);
        }
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
        // if exception is already a timeout, just return it as-is
        if (cause instanceof JayoTimeoutException jayoTimeoutException) {
            return jayoTimeoutException;
        }
        final var e = new JayoTimeoutException("timeout");
        if (cause != null) {
            e.initCause(cause);
        }
        return e;
    }

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

    private final static ThreadFactory ASYNC_TIMEOUT_WATCHDOG_THREAD_FACTORY =
            JavaVersionUtils.threadFactory("JayoAsyncTimeoutWatchdog#");

    private static void insertIntoQueue(final @NonNull RealAsyncTimeout node,
                                        final long timeoutNanos,
                                        final long deadlineNanoTime) {
        assert node != null;
        assert timeoutNanos > 0L || deadlineNanoTime > 0L;

        // Start the watchdog thread and create the head node when the first timeout is scheduled.
        if (head == null) {
            head = new RealAsyncTimeout(() -> {
            });
            ASYNC_TIMEOUT_WATCHDOG_THREAD_FACTORY.newThread(new Watchdog()).start();
        }

        final var now = System.nanoTime();
        if (deadlineNanoTime > 0L) {
            node.timeoutAt = deadlineNanoTime;
        } else {
            node.timeoutAt = now + timeoutNanos;
        }

        // Insert the node in sorted order.
        final var remainingNanos = node.remainingNanos(now);
        var prev = head;
        // Insert the node in sorted order.
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
    }

    /**
     * Returns true if the timeout occurred.
     */
    private static void removeFromQueue(final @NonNull RealAsyncTimeout node) {
        // Remove the node from the linked list.
        var prev = head;
        while (prev != null) {
            if (prev.next == node) {
                prev.next = node.next;
                node.next = null;
                return;
            }
            prev = prev.next;
        }

        throw new IllegalStateException("node was not found in the queue");
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
            final var ignored = CONDITION.await(IDLE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            return (head.next == null && System.nanoTime() - startNanos >= IDLE_TIMEOUT_NANOS)
                    ? head // The idle timeout elapsed.
                    : null; // The situation has changed.
        }

        final var waitNanos = node.remainingNanos(System.nanoTime());

        // The head of the queue hasn't timed out yet. Await that.
        if (waitNanos > 0) {
            final var ignored = CONDITION.awaitNanos(waitNanos);
            return null;
        }

        // The head of the queue has timed out. Remove it.
        head.next = node.next;
        node.next = null;
        node.state = STATE_TIMED_OUT;
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
