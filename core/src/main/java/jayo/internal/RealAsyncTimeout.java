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
import jayo.tools.CancelToken;
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

public final class RealAsyncTimeout implements AsyncTimeout {
    /**
     * Don't write more than 4 full segments (~67 KiB) of data at a time. Otherwise, slow connections may suffer
     * timeouts even when they're making (slow) progress. Without this, writing a single 1 MiB buffer may never succeed
     * on a slow enough connection.
     */
    static final int TIMEOUT_WRITE_SIZE = 4 * Segment.SIZE;

    private static final Lock LOCK = new ReentrantLock();
    private static final Condition CONDITION = LOCK.newCondition();

    private final long defaultReadTimeoutNanos;
    private final long defaultWriteTimeoutNanos;

    private final @NonNull Runnable onTimeout;
    private @Nullable RealCancelToken tmpCancelToken = null;

    public RealAsyncTimeout(final @NonNull Runnable onTimeout) {
        this(0L, 0L, onTimeout);
    }

    /**
     * @param defaultReadTimeoutNanos  The default read timeout (in nanoseconds). It will be used as a fallback for each
     *                                 read operation, only if no timeout is present in the cancellable context. It must
     *                                 be non-negative. A timeout of zero is interpreted as an infinite timeout.
     * @param defaultWriteTimeoutNanos The default write timeout (in nanoseconds). It will be used as a fallback for
     *                                 each write operation, only if no timeout is present in the cancellable context.
     *                                 It must be non-negative. A timeout of zero is interpreted as an infinite timeout.
     */
    public RealAsyncTimeout(final long defaultReadTimeoutNanos,
                            final long defaultWriteTimeoutNanos,
                            final @NonNull Runnable onTimeout) {
        assert onTimeout != null;
        assert defaultReadTimeoutNanos >= 0;
        assert defaultWriteTimeoutNanos >= 0;

        this.defaultReadTimeoutNanos = defaultReadTimeoutNanos;
        this.defaultWriteTimeoutNanos = defaultWriteTimeoutNanos;
        this.onTimeout = onTimeout;
    }

    @Override
    public @Nullable TimeoutNode enter(final long defaultTimeout) {
        if (defaultTimeout < 0L) {
            throw new IllegalArgumentException("defaultTimeout < 0: " + defaultTimeout);
        }

        final var cancelToken = CancellableUtils.getCancelToken();
        // A timeout is already defined, use it
        if (cancelToken != null) {
            return enter(cancelToken);
        }

        // no default timeout, no-op
        if (defaultTimeout == 0L) {
            return null;
        }

        // use defaultTimeout to create a temporary cancel token
        final var tmpCancelToken = new RealCancelToken(defaultTimeout);
        CancellableUtils.addCancelToken(tmpCancelToken);
        this.tmpCancelToken = tmpCancelToken;
        return enter(tmpCancelToken);
    }

    private @Nullable TimeoutNode enter(final @NonNull RealCancelToken cancelToken) {
        // CancelToken is finished, shielded, or there is no timeout and no deadline: don't bother with the queue.
        if (cancelToken.finished || cancelToken.shielded ||
                (cancelToken.deadlineNanoTime == 0L && cancelToken.timeoutNanos == 0L)) {
            return null;
        }

        LOCK.lock();
        try {
            return insertIntoQueue(cancelToken.timeoutNanos, cancelToken.deadlineNanoTime);
        } finally {
            LOCK.unlock();
        }
    }

    @Override
    public boolean exit(final AsyncTimeout.@Nullable Node node) {
        LOCK.lock();
        try {
            if (node == null) {
                return false;
            }

            return !removeFromQueue((TimeoutNode) node);
        } finally {
            LOCK.unlock();

            if (tmpCancelToken != null) {
                CancellableUtils.finishCancelToken(tmpCancelToken);
                tmpCancelToken = null;
            }
        }
    }

    @Override
    public @NonNull RawWriter writer(final @NonNull RawWriter writer) {
        Objects.requireNonNull(writer);
        return new RawWriter() {
            @Override
            public void write(final @NonNull Buffer source, final long byteCount) {
                Objects.requireNonNull(source);
                checkOffsetAndCount(source.bytesAvailable(), 0, byteCount);

                final var src = (RealBuffer) source;
                // get the cancel token immediately; if present, it will be used in all IO calls of this write operation
                var cancelToken = CancellableUtils.getCancelToken();
                if (cancelToken != null) {
                    cancelToken.timeoutNanos = defaultWriteTimeoutNanos;
                    try {
                        writeCancellable(src, byteCount, cancelToken);
                        return;
                    } finally {
                        cancelToken.timeoutNanos = 0L;
                    }
                }

                // no need for cancellation
                if (defaultWriteTimeoutNanos == 0L) {
                    writer.write(source, byteCount);
                    return;
                }

                // use defaultWriteTimeoutNanos to create a temporary cancel token, just for this write operation
                cancelToken = new RealCancelToken(defaultWriteTimeoutNanos, 0L, false);
                CancellableUtils.addCancelToken(cancelToken);
                try {
                    writeCancellable(src, byteCount, cancelToken);
                } finally {
                    CancellableUtils.finishCancelToken(cancelToken);
                }
            }

            private void writeCancellable(RealBuffer src, long byteCount, RealCancelToken cancelToken) {
                var remaining = byteCount;
                while (remaining > 0L) {
                    // Count how many bytes to write. This loop guarantees we split on a segment boundary.
                    var _toWrite = 0L;
                    var segment = src.head;
                    while (_toWrite < TIMEOUT_WRITE_SIZE) {
                        assert segment != null;
                        _toWrite += (segment.limit - segment.pos);
                        if (_toWrite >= remaining) {
                            _toWrite = remaining;
                            break;
                        }
                        segment = segment.next;
                    }

                    final var toWrite = _toWrite;
                    // Emit one write operation. Only this section is subject to the timeout.
                    withTimeout(cancelToken, () -> {
                        writer.write(src, toWrite);
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
                    throw new IllegalArgumentException("byteCount < 0: " + byteCount);
                }

                // get the cancel token immediately; if present, it will be used in all IO calls of this read operation
                var cancelToken = CancellableUtils.getCancelToken();
                if (cancelToken != null) {
                    cancelToken.timeoutNanos = defaultReadTimeoutNanos;
                    try {
                        return withTimeout(cancelToken, () -> reader.readAtMostTo(writer, byteCount));
                    } finally {
                        cancelToken.timeoutNanos = 0L;
                    }
                }

                // no need for cancellation
                if (defaultReadTimeoutNanos == 0L) {
                    return reader.readAtMostTo(writer, byteCount);
                }

                // use defaultReadTimeoutNanos to create a temporary cancel token, just for this read operation
                cancelToken = new RealCancelToken(defaultReadTimeoutNanos, 0L, false);
                CancellableUtils.addCancelToken(cancelToken);
                try {
                    return withTimeout(cancelToken, () -> reader.readAtMostTo(writer, byteCount));
                } finally {
                    CancellableUtils.finishCancelToken(cancelToken);
                }
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
    public <T> T withTimeout(final @NonNull CancelToken cancelToken, final @NonNull Supplier<T> block) {
        Objects.requireNonNull(cancelToken);
        Objects.requireNonNull(block);
        final var _cancelToken = (RealCancelToken) cancelToken;

        var throwOnTimeout = false;
        final var node = enter(_cancelToken);
        try {
            final var result = block.get();
            throwOnTimeout = true;
            return result;
        } catch (JayoException e) {
            _cancelToken.cancel();
            throw (!exit(node)) ? e : newTimeoutException(e);
        } finally {
            final var timedOut = exit(node);
            if (timedOut && throwOnTimeout) {
                _cancelToken.cancel();
                throw newTimeoutException(null);
            }
        }
    }

    /**
     * @return a {@link JayoTimeoutException} to represent a timeout. If {@code cause} is non-null it is set as the
     * cause of the returned exception.
     */
    private @NonNull JayoTimeoutException newTimeoutException(final @Nullable JayoException cause) {
        // if the exception is already a timeout, return it as-is
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
     * The watchdog thread processes a linked list of pending timeouts, sorted in the order to be triggered. This lock
     * guards the queue.
     * <p>
     * Head's 'next' points to the first element of the linked list. The first element is the next node to time out, or
     * null if the queue is empty. The head is null until the watchdog thread is started and also after being idle for
     * {@link #IDLE_TIMEOUT_MILLIS}.
     */
    private static @Nullable TimeoutNode HEAD = null;

    private static final ThreadFactory ASYNC_TIMEOUT_WATCHDOG_THREAD_FACTORY =
            JavaVersionUtils.threadFactory("JayoAsyncTimeoutWatchdog#", false);

    private @NonNull TimeoutNode insertIntoQueue(final long timeoutNanos,
                                                 final long deadlineNanoTime) {
        assert timeoutNanos >= 0L || deadlineNanoTime >= 0L;

        // Start the watchdog thread and create the head node when the first timeout is scheduled.
        if (HEAD == null) {
            HEAD = new TimeoutNode(0L, () -> {
            });
            ASYNC_TIMEOUT_WATCHDOG_THREAD_FACTORY.newThread(new Watchdog()).start();
        }

        final var now = System.nanoTime();
        final long timeoutAt;
        if (deadlineNanoTime > 0L) {
            timeoutAt = deadlineNanoTime;
        } else {
            timeoutAt = now + timeoutNanos;
        }
        final var node = new TimeoutNode(timeoutAt, onTimeout);

        // Insert the node in sorted order.
        final var remainingNanos = remainingNanos(node, now);
        var prev = HEAD;
        // Insert the node in sorted order.
        while (true) {
            if (prev.next == null || remainingNanos < remainingNanos(prev.next, now)) {
                node.next = prev.next;
                prev.next = node;
                if (prev == HEAD) {
                    // Wake up the watchdog when inserting at the front.
                    CONDITION.signal();
                }
                break;
            }
            prev = prev.next;
        }
        return node;
    }

    /**
     * @return true if the node was removed from the queue
     */
    private static boolean removeFromQueue(final @NonNull TimeoutNode node) {
        // Remove the node from the linked list.
        var prev = HEAD;
        while (prev != null) {
            if (prev.next == node) {
                prev.next = node.next;
                node.next = null;
                return true;
            }
            prev = prev.next;
        }

        return false; // node was not found in the queue
    }

    /**
     * Removes and returns the node at the head of the list, waiting for it to time out if necessary. This returns
     * {@link #HEAD} if there was no node at the head of the list when starting, and there continues to be no node after
     * waiting {@link #IDLE_TIMEOUT_NANOS}. It returns null if a new node was inserted while waiting. Otherwise, this
     * returns the node being waited on that has been removed.
     */
    private static @Nullable TimeoutNode awaitTimeout() throws InterruptedException {
        assert HEAD != null;
        // Get the next eligible node.
        final var node = HEAD.next;

        // The queue is empty. Wait until either something is enqueued or the idle timeout elapses.
        if (node == null) {
            final var startNanos = System.nanoTime();
            final var ignored = CONDITION.await(IDLE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            return (HEAD.next == null && System.nanoTime() - startNanos >= IDLE_TIMEOUT_NANOS)
                    ? HEAD // The idle timeout elapsed.
                    : null; // The situation has changed.
        }

        final var waitNanos = remainingNanos(node, System.nanoTime());

        // The head of the queue hasn't timed out yet. Await that.
        if (waitNanos > 0) {
            final var ignored = CONDITION.awaitNanos(waitNanos);
            return null;
        }

        // The head of the queue has timed out. Remove it.
        HEAD.next = node.next;
        node.next = null;
        return node;
    }

    private static final class Watchdog implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    TimeoutNode node;
                    LOCK.lock();
                    try {
                        node = awaitTimeout();

                        // The queue is completely empty. Let this thread exit and let another watchdog thread be
                        // created on the next call to scheduleTimeout().
                        if (node == HEAD) {
                            HEAD = null;
                            return;
                        }
                    } finally {
                        LOCK.unlock();
                    }

                    // Close the timed out node, if one was found.
                    if (node != null) {
                        node.onTimeout.run();
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /**
     * Returns the amount of time left until the time-out. This will be negative if the timeout has elapsed and the
     * timeout should occur immediately.
     */
    private static long remainingNanos(final AsyncTimeout.@NonNull Node timeoutNode, final long now) {
        assert timeoutNode != null;
        return timeoutNode.getTimeoutAt() - now;
    }

    public static final class TimeoutNode implements AsyncTimeout.Node {
        private final long timeoutAt;
        private final @NonNull Runnable onTimeout;
        /**
         * The next node in the linked list.
         */
        private @Nullable TimeoutNode next = null;

        private TimeoutNode(final long timeoutAt, final @NonNull Runnable onTimeout) {
            assert timeoutAt >= 0L;
            assert onTimeout != null;

            this.timeoutAt = timeoutAt;
            this.onTimeout = onTimeout;
        }


        @Override
        public long getTimeoutAt() {
            return timeoutAt;
        }
    }
}
