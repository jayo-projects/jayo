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

import jayo.JayoException;
import jayo.JayoTimeoutException;
import jayo.tools.AsyncTimeout;
import jayo.tools.CancelToken;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class RealAsyncTimeout implements AsyncTimeout {
    private static final Lock LOCK = new ReentrantLock();
    private static final Condition CONDITION = LOCK.newCondition();

    private final @NonNull Runnable onTimeout;

    public RealAsyncTimeout(final @NonNull Runnable onTimeout) {
        assert onTimeout != null;

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
            return enter(cancelToken, false);
        }

        // no default timeout, no-op
        if (defaultTimeout == 0L) {
            return null;
        }

        // use defaultTimeout to create a temporary cancel token
        final var tmpCancelToken = new RealCancelToken(defaultTimeout);
        CancellableUtils.addCancelToken(tmpCancelToken);
        return enter(tmpCancelToken, true);
    }

    private @Nullable TimeoutNode enter(final @NonNull RealCancelToken cancelToken, final boolean isTemporary) {
        assert cancelToken != null;

        // CancelToken is finished, shielded, or there is no timeout and no deadline: don't bother with the queue.
        if (cancelToken.finished || cancelToken.shielded ||
                (cancelToken.deadlineNanoTime == 0L && cancelToken.timeoutNanos == 0L)) {
            return null;
        }

        LOCK.lock();
        try {
            return insertInQueue(cancelToken, isTemporary);
        } finally {
            LOCK.unlock();
        }
    }

    @Override
    public boolean exit(final AsyncTimeout.@Nullable Node node) {
        if (node == null) {
            return false;
        }

        final var _node = (TimeoutNode) node;
        LOCK.lock();
        try {
            return !removeFromQueue(_node);
        } finally {
            LOCK.unlock();

            if (_node.tmpCancelToken != null) {
                CancellableUtils.finishCancelToken(_node.tmpCancelToken);
                _node.tmpCancelToken = null;
            }
        }
    }

    /**
     * Surrounds {@code block} with calls to {@link #enter(long)} and {@link #exit(Node)} , throwing a
     * {@linkplain jayo.JayoTimeoutException JayoTimeoutException} if a timeout occurred. You must provide a
     * {@code cancelToken} obtained by calling {@link CancelToken#getCancelToken()}.
     */
    public <T> T withTimeout(final @Nullable RealCancelToken cancelToken, final @NonNull Supplier<T> block) {
        assert block != null;

        if (cancelToken == null) {
            return block.get();
        }

        var throwOnTimeout = false;
        final var node = enter(cancelToken, false);
        try {
            final var result = block.get();
            throwOnTimeout = true;
            return result;
        } catch (JayoException e) {
            cancelToken.cancel();
            throw (!exit(node)) ? e : newTimeoutException(e);
        } finally {
            final var timedOut = exit(node);
            if (timedOut && throwOnTimeout) {
                cancelToken.cancel();
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

    private @NonNull TimeoutNode insertInQueue(final @NonNull RealCancelToken cancelToken, final boolean isTemporary) {
        assert cancelToken != null;

        // Start the watchdog thread and create the head node when the first timeout is scheduled.
        if (HEAD == null) {
            HEAD = new TimeoutNode(0L, () -> {
            }, null);
            ASYNC_TIMEOUT_WATCHDOG_THREAD_FACTORY.newThread(new Watchdog()).start();
        }

        final var now = System.nanoTime();
        final long timeoutAt;
        if (cancelToken.deadlineNanoTime > 0L) {
            timeoutAt = cancelToken.deadlineNanoTime;
        } else {
            timeoutAt = now + cancelToken.timeoutNanos;
        }
        final var node = new TimeoutNode(
                timeoutAt,
                onTimeout,
                isTemporary ? cancelToken : null);

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
        private @Nullable RealCancelToken tmpCancelToken;
        /**
         * The next node in the linked list.
         */
        private @Nullable TimeoutNode next = null;

        private TimeoutNode(final long timeoutAt,
                            final @NonNull Runnable onTimeout,
                            final @Nullable RealCancelToken tmpCancelToken) {
            assert timeoutAt >= 0L;
            assert onTimeout != null;

            this.timeoutAt = timeoutAt;
            this.onTimeout = onTimeout;
            this.tmpCancelToken = tmpCancelToken;
        }


        @Override
        public long getTimeoutAt() {
            return timeoutAt;
        }
    }
}
