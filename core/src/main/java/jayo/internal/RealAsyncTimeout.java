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

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ThreadFactory;
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
            return insertIntoQueue(cancelToken, isTemporary);
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
            return !QUEUE.remove(_node);
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
    private static final long IDLE_TIMEOUT_NANOS = Duration.ofSeconds(60).toNanos();

    /**
     * The watchdog thread processes this queue containing pending timeouts. It synchronizes on {@link #CONDITION} to
     * guard accesses to the queue.
     * <p>
     * The queue's first element is the next node to time out, which is null if the queue is empty.
     * <p>
     * The {@link #IDLE_SENTINEL} is null until the watchdog thread is started and also after being idle for
     * {@link #IDLE_TIMEOUT_NANOS}.
     */
    private static final @NonNull PriorityQueue QUEUE = new PriorityQueue();
    private static @Nullable TimeoutNode IDLE_SENTINEL = null;

    private static final @NonNull TimeoutNode IDLE_SENTINEL_WATCHDOG_RUNNING = new TimeoutNode(0L, () -> {
    }, null);
    private static final ThreadFactory ASYNC_TIMEOUT_WATCHDOG_THREAD_FACTORY =
            JavaVersionUtils.threadFactory("JayoAsyncTimeoutWatchdog#", true);

    private @NonNull TimeoutNode insertIntoQueue(final @NonNull RealCancelToken cancelToken,
                                                 final boolean isTemporary) {
        assert cancelToken != null;

        // Start the watchdog thread and create the head node when the first timeout is scheduled.
        if (IDLE_SENTINEL == null) {
            IDLE_SENTINEL = IDLE_SENTINEL_WATCHDOG_RUNNING;
            ASYNC_TIMEOUT_WATCHDOG_THREAD_FACTORY.newThread(RealAsyncTimeout::watchdogLoop).start();
        }

        final var now = System.nanoTime();
        final long timeoutAt;
        if (cancelToken.deadlineNanoTime > 0L) {
            timeoutAt = cancelToken.deadlineNanoTime;
        } else {
            timeoutAt = now + cancelToken.timeoutNanos;
        }
        final var node = new TimeoutNode(timeoutAt, onTimeout,
                isTemporary ? cancelToken : null);

        // Insert the node into the queue.
        QUEUE.add(node);
        if (node.index == 1) {
            // Wake up the watchdog when inserting at the front.
            CONDITION.signal();
        }
        return node;
    }

    /**
     * Removes and returns the next node to timeout, waiting for it to time out if necessary.
     * <p>
     * This returns {@link #IDLE_SENTINEL} if the queue was empty when starting, and it continues to be empty after
     * waiting {@link #IDLE_TIMEOUT_NANOS}.
     * <p>
     * This returns null if a new node was inserted while waiting.
     */
    private static @Nullable TimeoutNode awaitTimeout() throws InterruptedException {
        // Get the next eligible node.
        final var node = QUEUE.first();

        // The queue is empty. Wait until either something is enqueued or the idle timeout elapses.
        if (node == null) {
            final var startNanos = System.nanoTime();
            final var ignored = CONDITION.awaitNanos(IDLE_TIMEOUT_NANOS);
            assert IDLE_SENTINEL != null;
            return (QUEUE.first() == null && System.nanoTime() - startNanos >= IDLE_TIMEOUT_NANOS)
                    ? IDLE_SENTINEL // The idle timeout elapsed.
                    : null; // The situation has changed.
        }

        final var waitNanos = remainingNanos(node, System.nanoTime());

        // The first node in the queue hasn't timed out yet. Await that.
        if (waitNanos > 0) {
            final var ignored = CONDITION.awaitNanos(waitNanos);
            return null;
        }

        // The first node in the queue has timed out. Remove it.
        QUEUE.remove(node);
        return node;
    }

    private static void watchdogLoop() {
        while (true) {
            try {
                TimeoutNode node;
                LOCK.lock();
                try {
                    node = awaitTimeout();

                    // The queue is completely empty. Let this thread exit and let another watchdog thread get created
                    // on the next call to enter().
                    if (node == IDLE_SENTINEL) {
                        IDLE_SENTINEL = null;
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

    /**
     * Returns the amount of time left until the time-out. This will be negative if the timeout has elapsed and the
     * timeout should occur immediately.
     */
    private static long remainingNanos(final AsyncTimeout.@NonNull Node timeoutNode, final long now) {
        assert timeoutNode != null;
        return timeoutNode.getTimeoutAt() - now;
    }

    public static final class TimeoutNode implements AsyncTimeout.Node, Comparable<TimeoutNode> {
        private final long timeoutAt;
        private final @NonNull Runnable onTimeout;
        private @Nullable RealCancelToken tmpCancelToken;

        /**
         * The index of this timeout node in {@link RealAsyncTimeout#QUEUE}, or -1 if this isn't currently in the heap.
         */
        private int index = -1;

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

        @Override
        public int compareTo(final @NonNull TimeoutNode other) {
            assert other != null;
            return Long.compare(other.timeoutAt, timeoutAt);
        }
    }

    /**
     * A min-heap binary heap, stored in an array.
     * <p>
     * Nodes are {@link TimeoutNode} instances. To support fast random removals, each {@link TimeoutNode} knows its
     * index in the heap.
     * <p>
     * The first node is at array index 1.
     * <p>
     * See <a href="https://en.wikipedia.org/wiki/Binary_heap">Binary heap</a>
     */
    static final class PriorityQueue {
        int size = 0;
        @Nullable
        TimeoutNode @NonNull [] array = new TimeoutNode[8];

        @Nullable
        TimeoutNode first() {
            return array[1];
        }

        void add(final @NonNull TimeoutNode node) {
            assert node != null;

            final var newSize = size + 1;
            size = newSize;
            if (newSize == array.length) {
                array = Arrays.copyOf(array, newSize * 2);
            }

            heapifyUp(newSize, node);
        }

        /**
         * @return true if the node was removed from the queue
         */
        boolean remove(final @NonNull TimeoutNode node) {
            assert node != null;

            if (node.index == -1) {
                // node was not in the queue
                return false;
            }

            final var oldSize = size;

            // Take the heap's last node to fill this node's position.
            final var removedIndex = node.index;
            final var last = array[oldSize];
            assert last != null;
            node.index = -1;
            array[oldSize] = null;
            size = oldSize - 1;

            if (node == last) {
                return true; // The last node is the removed node.
            }

            final var nodeCompareToLast = node.compareTo(last);
            if (nodeCompareToLast == 0) {
                // The last node fits in the vacated spot.
                array[removedIndex] = last;
                last.index = removedIndex;
            } else if (nodeCompareToLast < 0) {
                // The last node might be too large for the vacated spot.
                heapifyDown(removedIndex, last);
            } else {
                // The last node might be too small for the vacated spot.
                heapifyUp(removedIndex, last);
            }
            return true;
        }

        /**
         * Put {@code node} in the right position in the heap by moving it up the heap.
         * <p>
         * When this is done it'll put something in {@code vacantIndex}, and {@code node} somewhere in the heap.
         *
         * @param vacantIndex an index in {@link #array} that is vacant.
         */
        private void heapifyUp(final int vacantIndex, final @NonNull TimeoutNode node) {
            assert node != null;

            var _vacantIndex = vacantIndex;
            while (true) {
                final var parentIndex = _vacantIndex >> 1;
                if (parentIndex == 0) {
                    break; // No parent.
                }

                final var parentNode = array[parentIndex];
                assert parentNode != null;
                if (parentNode.compareTo(node) <= 0) {
                    break; // No need to swap with the parent.
                }

                // Put our parent in the vacant index, and its index is the new vacant index.
                parentNode.index = _vacantIndex;
                array[_vacantIndex] = parentNode;
                _vacantIndex = parentIndex;
            }

            array[_vacantIndex] = node;
            node.index = _vacantIndex;
        }

        /**
         * Put {@code node} in the right position in the heap by moving it down the heap.
         * <p>
         * When this is done it'll put something in {@code vacantIndex}, and {@code node} somewhere in the heap.
         *
         * @param vacantIndex an index in {@link #array} that is vacant.
         */
        private void heapifyDown(final int vacantIndex, final @NonNull TimeoutNode node) {
            assert node != null;

            var _vacantIndex = vacantIndex;
            while (true) {
                final var leftIndex = _vacantIndex << 1;
                final var rightIndex = leftIndex + 1;

                final TimeoutNode smallestChild;
                if (rightIndex <= size) {
                    final var leftNode = array[leftIndex];
                    final var rightNode = array[rightIndex];
                    assert leftNode != null;
                    assert rightNode != null;
                    smallestChild = (leftNode.compareTo(rightNode) < 0) ? leftNode : rightNode;
                } else if (leftIndex <= size) {
                    smallestChild = array[leftIndex]; // Left node.
                } else {
                    break; // No children.
                }

                assert smallestChild != null;
                if (node.compareTo(smallestChild) <= 0) {
                    break; // No need to swap with the children.
                }

                // Put our smallest child in the vacant index, and its index is the new vacant index.
                final var newVacantIndex = smallestChild.index;
                smallestChild.index = _vacantIndex;
                array[_vacantIndex] = smallestChild;
                _vacantIndex = newVacantIndex;
            }

            array[_vacantIndex] = node;
            node.index = _vacantIndex;
        }
    }
}
