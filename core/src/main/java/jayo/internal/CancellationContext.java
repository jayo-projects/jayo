/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The cancellation context in the scope of a cancellable block.
 * <p>
 * It may be accessed concurrently by multiple asynchronous tasks inside a {@code StructuredTaskScope}, so we use a
 * {@link Lock} to ensure thread safety.
 */
final class CancellationContext {
    private final @NonNull Lock lock = new ReentrantLock();
    private @Nullable RealCancelToken head;
    private final long initialThreadId = JavaVersionUtils.threadId(Thread.currentThread());

    public CancellationContext(final @NonNull RealCancelToken initialNode) {
        assert initialNode != null;
        this.head = initialNode;
    }

    @Nullable
    RealCancelToken getCancelToken() {
        lock.lock();
        try {
            var current = head;
            if (current == null) {
                return null;
            }

            final var threadId = JavaVersionUtils.threadId(Thread.currentThread());
            RealCancelToken result = null;
            RealCancelToken previous = null;
            while (current != null) {
                final var next = current.next;
                if (current.threadId != threadId && current.threadId != initialThreadId) {
                    // the current cancel token was not added by the current thread or the initial thread, skip it
                } else if (current.finished) {
                    // a finished cancel token does not apply anymore, we remove it from the queue
                    if (previous == null) {
                        head = next;
                    } else {
                        previous.next = next;
                    }
                    current.next = null;

                } else if (current.shielded) {
                    // a shielded cancel token prevents from applying itself and previous ones
                    break;

                } else if (result == null) {
                    // the first cancel token applies
                    result = current;

                } else {
                    // several cancel tokens apply, we merge them
                    result = intersect(result, current);
                }

                if (result != null && result.cancelled) {
                    // manual cancellation requested
                    break;
                }

                previous = current;
                current = next;
            }

            return result;
        } finally {
            lock.unlock();
        }
    }

    void addCancelToken(final @NonNull RealCancelToken cancelToken) {
        assert cancelToken != null;

        lock.lock();
        try {
            // always add first
            cancelToken.next = head;
            head = cancelToken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return a new cancel token that is the intersection between two cancel tokens.
     */
    private static @NonNull RealCancelToken intersect(final @NonNull RealCancelToken current,
                                                      final @NonNull RealCancelToken next) {
        assert current != null;
        assert next != null;

        final var deadlineNanoTime = minDeadline(current.deadlineNanoTime, next.deadlineNanoTime);

        // Only the last default timeout applies, if no deadline
        final long timeoutNanos;
        if (deadlineNanoTime == 0L) {
            timeoutNanos = (current.timeoutNanos != 0L) ? current.timeoutNanos : next.timeoutNanos;
        } else {
            timeoutNanos = 0L;
        }

        final var cancelled = current.cancelled || next.cancelled;
        return new RealCancelToken(timeoutNanos, deadlineNanoTime, cancelled, 0L);
    }

    private static long minDeadline(final long aNanos, final long bNanos) {
        if (aNanos == 0L) {
            return bNanos;
        }
        if (bNanos == 0L) {
            return aNanos;
        }
        return Math.min(aNanos, bNanos);
    }
}
