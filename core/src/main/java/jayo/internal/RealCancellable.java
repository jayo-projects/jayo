/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;
import jayo.CancelScope;
import jayo.Cancellable;
import jayo.exceptions.JayoCancelledException;
import jayo.external.NonNegative;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public final class RealCancellable implements Cancellable {
    private final @NonNegative long timeoutNanos;
    private final @NonNegative long deadlineNanos;

    public RealCancellable() {
        this(0L, 0L);
    }

    public RealCancellable(final @NonNegative long timeoutNanos, final @NonNegative long deadlineNanos) {
        if (timeoutNanos < 0L) {
            throw new IllegalArgumentException("timeoutNanos < 0L: " + timeoutNanos);
        }
        if (deadlineNanos < 0L) {
            throw new IllegalArgumentException("deadlineNanos < 0L: " + deadlineNanos);
        }
        this.timeoutNanos = timeoutNanos;
        this.deadlineNanos = deadlineNanos;
    }

    @Override
    public <T> T executeCancellable(final @NonNull Function<CancelScope, T> block) {
        Objects.requireNonNull(block);
        final var cancelToken = new RealCancelToken(timeoutNanos, deadlineNanos);
        CancellableUtils.addCancelToken(cancelToken);
        try {
            return block.apply(cancelToken);
        } catch (JayoCancelledException cancellationException) {
            if (cancellationException.getCause() != null) {
                throw cancellationException.getCause();
            }
            // else rethrow
            throw cancellationException;
        } finally {
            CancellableUtils.finishCancelToken(cancelToken);
        }
    }

    @Override
    public void executeCancellable(final @NonNull Consumer<CancelScope> block) {
        Objects.requireNonNull(block);
        final var cancelToken = new RealCancelToken(timeoutNanos, deadlineNanos);
        CancellableUtils.addCancelToken(cancelToken);
        try {
            block.accept(cancelToken);
        } catch (JayoCancelledException cancellationException) {
            if (cancellationException.getCause() != null) {
                throw cancellationException.getCause();
            }
            // else rethrow
            throw cancellationException;
        } finally {
            CancellableUtils.finishCancelToken(cancelToken);
        }
    }

    public static final class Builder implements Cancellable.Config {
        private @NonNegative long timeoutNanos = 0L;
        private @NonNegative long deadlineNanos = 0L;

        @Override
        public void setTimeout(long timeout, @NonNull TimeUnit unit) {
            Objects.requireNonNull(unit);
            if (timeout < 1L) {
                throw new IllegalArgumentException("timeout < 1L: " + timeout);
            }
            this.timeoutNanos = unit.toNanos(timeout);
        }

        @Override
        public void setDeadline(long duration, @NonNull TimeUnit unit) {
            Objects.requireNonNull(unit);
            if (duration < 1L) {
                throw new IllegalArgumentException("duration < 1L: " + duration);
            }
            this.deadlineNanos = unit.toNanos(duration);
        }

        /**
         * @return the {@link Cancellable} reusable instance.
         */
        public @NonNull Cancellable build() {
            return new RealCancellable(timeoutNanos, deadlineNanos);
        }
    }
}
