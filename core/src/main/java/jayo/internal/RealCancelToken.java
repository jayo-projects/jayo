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

import jayo.CancelScope;
import jayo.JayoInterruptedIOException;
import jayo.JayoTimeoutException;
import jayo.external.CancelToken;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * CancelToken is thread safe
 */
public final class RealCancelToken implements CancelScope, CancelToken {
    @NonNegative long timeoutNanos;
    final @NonNegative long deadlineNanoTime;
    volatile boolean cancelled = false;
    volatile boolean shielded = false;
    volatile boolean finished = false;

    RealCancelToken(final @NonNegative long timeoutNanos, final @NonNegative long deadlineNanos) {
        this.timeoutNanos = timeoutNanos;
        this.deadlineNanoTime = (deadlineNanos > 0L) ? (System.nanoTime() + deadlineNanos) : 0L;
    }

    RealCancelToken(final @NonNegative long timeoutNanos,
                    final @NonNegative long deadlineNanoTime,
                    final boolean cancelled) {
        this.timeoutNanos = timeoutNanos;
        this.deadlineNanoTime = deadlineNanoTime;
        this.cancelled = cancelled;
    }

    @Override
    public void shield() {
        shielded = true;
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    /**
     * Called when the code block that was executed with this CancelToken is finished
     */
    void finish() {
        finished = true;
    }

    @Override
    public void awaitSignal(final @NonNull Condition condition) {
        Objects.requireNonNull(condition);
        final var cancelToken = CancellableUtils.getCancelToken();
        assert cancelToken != null;

        try {
            // CancelToken is finished, shielded or there is no timeout and no deadline : wait forever.
            if (cancelToken.finished || cancelToken.shielded || (cancelToken.deadlineNanoTime == 0L
                    && cancelToken.timeoutNanos == 0L)) {
                condition.await();
                return;
            }

            // Compute how long we'll wait.
            final var start = System.nanoTime();
            final long waitNanos;
            if (cancelToken.deadlineNanoTime != 0L && cancelToken.timeoutNanos != 0L) {
                final var deadlineNanos = cancelToken.deadlineNanoTime - start;
                waitNanos = Math.min(cancelToken.timeoutNanos, deadlineNanos);
            } else if (cancelToken.deadlineNanoTime != 0L) {
                waitNanos = cancelToken.deadlineNanoTime - start;
            } else {
                waitNanos = cancelToken.timeoutNanos;
            }

            // Attempt to wait that long. This will break out early if the condition is notified.
            var elapsedNanos = 0L;
            if (waitNanos > 0L) {
                condition.await(waitNanos, TimeUnit.NANOSECONDS);
                elapsedNanos = System.nanoTime() - start;
            }

            // Throw if the timeout elapsed before the condition was signalled.
            if (elapsedNanos >= waitNanos) {
                cancel();
                throw new JayoTimeoutException("timeout or deadline elapsed before the condition was signalled");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Retain interrupted status.
            cancel();
            throw new JayoInterruptedIOException("current thread is interrupted");
        }
    }

    @Override
    public void waitUntilNotified(final @NonNull Object monitor) {
        Objects.requireNonNull(monitor);
        final var cancelToken = CancellableUtils.getCancelToken();
        assert cancelToken != null;

        try {
            // CancelToken is finished, shielded or there is no timeout and no deadline : wait forever.
            if (cancelToken.finished || cancelToken.shielded || (cancelToken.deadlineNanoTime == 0L
                    && cancelToken.timeoutNanos == 0L)) {
                monitor.wait();
                return;
            }

            // Compute how long we'll wait.
            final var start = System.nanoTime();
            final long waitNanos;
            if (cancelToken.deadlineNanoTime != 0L && cancelToken.timeoutNanos != 0L) {
                final var deadlineNanos = cancelToken.deadlineNanoTime - start;
                waitNanos = Math.min(cancelToken.timeoutNanos, deadlineNanos);
            } else if (cancelToken.deadlineNanoTime != 0L) {
                waitNanos = cancelToken.deadlineNanoTime - start;
            } else {
                waitNanos = cancelToken.timeoutNanos;
            }

            // Attempt to wait that long. This will break out early if the monitor is notified.
            var elapsedNanos = 0L;
            if (waitNanos > 0L) {
                final var waitMillis = waitNanos / 1000000L;
                monitor.wait(waitMillis, (int) (waitNanos - waitMillis * 1000000L));
                elapsedNanos = System.nanoTime() - start;
            }

            // Throw if the timeout elapsed before the monitor was notified.
            if (elapsedNanos >= waitNanos) {
                cancel();
                throw new JayoTimeoutException("timeout or deadline elapsed before the monitor was notified");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Retain interrupted status.
            cancel();
            throw new JayoInterruptedIOException("current thread is interrupted");
        }
    }

    public void throwIfReached() {
        if (finished || shielded) {
            return;
        }
        if (cancelled) {
            throw new JayoInterruptedIOException("cancelled");
        }

        if (deadlineNanoTime != 0L && (deadlineNanoTime - System.nanoTime()) <= 0) {
            cancel();
            throw new JayoTimeoutException("deadline reached");
        }
    }
}
