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
import jayo.tools.CancelToken;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.locks.Condition;

public final class RealCancelToken implements CancelScope, CancelToken {
    static final @NonNull RealCancelToken SHIELDED = new RealCancelToken(0L);
    static {
        SHIELDED.shielded = true;
    }

    long timeoutNanos;
    final long deadlineNanoTime;

    // These volatile statuses can be changed by other threads
    volatile boolean cancelled;
    volatile boolean shielded = false;
    volatile boolean finished = false;

    final long threadId;
    @Nullable
    RealCancelToken next = null;

    public RealCancelToken(final long deadlineNanos) {
        this(0L,
                // the deadline is set immediately
                (deadlineNanos > 0L) ? (System.nanoTime() + deadlineNanos) : 0L);
    }

    RealCancelToken(final long timeoutNanos,
                    final long deadlineNanoTime) {
        this(timeoutNanos, deadlineNanoTime, false, JavaVersionUtils.threadId(Thread.currentThread()));
    }

    RealCancelToken(final long timeoutNanos,
                    final long deadlineNanoTime,
                    final boolean cancelled,
                    final long threadId) {
        this.timeoutNanos = timeoutNanos;
        this.deadlineNanoTime = deadlineNanoTime;
        this.cancelled = cancelled;
        this.threadId = threadId;
    }

    @Override
    public void shield() {
        shielded = true;
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    @Override
    public void awaitSignal(final @NonNull Condition condition) {
        Objects.requireNonNull(condition);
        final var cancelToken = JavaVersionUtils.getCancelToken();
        assert cancelToken != null;

        try {
            // CancelToken is finished, shielded, or there is no timeout and no deadline: wait forever.
            if (cancelToken.finished || cancelToken.shielded ||
                    (cancelToken.deadlineNanoTime == 0L && cancelToken.timeoutNanos == 0L)) {
                condition.await();
                return;
            }

            // Compute how long we'll wait.
            long remainingNanos;
            if (cancelToken.deadlineNanoTime > 0L) {
                remainingNanos = cancelToken.deadlineNanoTime - System.nanoTime();
            } else {
                remainingNanos = cancelToken.timeoutNanos;
            }

            // Attempt to wait that long. This will break out early if the condition is notified.
            // timeout may already be reached
            if (remainingNanos <= 0) {
                cancel();
                throw new JayoTimeoutException("timeout");
            }

            // await for condition
            remainingNanos = condition.awaitNanos(remainingNanos);

            // check again if timeout was reached immediately after the condition is notified.
            if (remainingNanos <= 0) {
                cancel();
                throw new JayoTimeoutException("timeout");
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
        final var cancelToken = JavaVersionUtils.getCancelToken();
        assert cancelToken != null;

        try {
            // CancelToken is finished, shielded, or there is no timeout and no deadline: wait forever.
            if (cancelToken.finished || cancelToken.shielded ||
                    (cancelToken.deadlineNanoTime == 0L && cancelToken.timeoutNanos == 0L)) {
                monitor.wait();
                return;
            }

            // Compute how long we'll wait.
            long remainingNanos;
            if (cancelToken.deadlineNanoTime > 0L) {
                remainingNanos = cancelToken.deadlineNanoTime - System.nanoTime();
            } else {
                remainingNanos = cancelToken.timeoutNanos;
            }

            // Attempt to wait that long. This will break out early if the monitor is notified.
            // timeout may be reached now
            if (remainingNanos <= 0) {
                cancel();
                throw new JayoTimeoutException("timeout");
            }

            // await for monitor
            final var remainingMillis = (long) Math.ceil(remainingNanos / 1000000d);
            monitor.wait(remainingMillis);

            // check again if timeout was reached immediately after monitor is notified.
            remainingNanos = deadlineNanoTime - System.nanoTime();
            if (remainingNanos <= 0) {
                cancel();
                throw new JayoTimeoutException("timeout");
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
            throw new JayoTimeoutException("timeout");
        }
    }

    @Override
    public String toString() {
        return "RealCancelToken{" +
                "timeoutNanos=" + timeoutNanos +
                ", deadlineNanoTime=" + deadlineNanoTime +
                ", cancelled=" + cancelled +
                ", shielded=" + shielded +
                ", finished=" + finished +
                '}';
    }
}
