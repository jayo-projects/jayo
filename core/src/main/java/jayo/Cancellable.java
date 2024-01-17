/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo;

import org.jspecify.annotations.NonNull;
import jayo.external.NonNegative;
import jayo.internal.RealCancellable;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This class allows to build <b>cancellable code blocks</b> via different utility methods for the most frequent
 * use-cases ({@linkplain Cancellable#withTimeout withTimeout},
 * {@linkplain Cancellable#withCancellable withCancellable}). For advanced cancellable configuration, or of you need a
 * cancellable instance that you can reuse to build multiple cancellable code blocks with the same configuration, then
 * use our {@link Builder} class.
 * <p>
 * All these cancellable builders create a {@code CancelScope} implementation called a {@code CancelToken} that is bound
 * to the current thread, and will automatically propagate cancellation and timeouts to children threads.
 * <p>
 * The {@code CancelToken} will only apply inside the code block, and will be removed when the code block has ended.
 */
public sealed interface Cancellable permits RealCancellable {
    /**
     * Execute {@code block} in a cancellable context, throwing a {@link CancellationException} if a cancellation
     * occurred.
     * All operations invoked in this code block, and in children threads, will respect the cancel scope : timeout,
     * deadline, manual cancellation, await for {@link Condition} signal...
     */
    <T> T executeCancellable(final @NonNull Function<CancelScope, T> block);

    /**
     * Execute {@code block} in a cancellable context, throwing a {@link CancellationException} if a cancellation
     * occurred.
     * All operations invoked in this code block, and in children threads, will respect the cancel scope : timeout,
     * deadline, manual cancellation, await for {@link Condition} signal...
     */
    void executeCancellable(final @NonNull Consumer<CancelScope> block);

    /**
     * A builder class to create a {@link Cancellable} reusable instance.
     */
    final class Builder {
        private @NonNegative long timeoutNanos = 0L;
        private @NonNegative long deadlineNanos = 0L;

        /**
         * Sets a timeout of {@code duration} time. All I/O operations invoked in the cancellable code block, and its
         * children, will wait at most {@code timeout} time before aborting.
         * <p>
         * Using a per-operation timeout means that as long as forward progress is being made, no sequence of operations
         * will fail.
         *
         * @return {@code this}
         */
        public @NonNull Builder timeout(final long timeout, final @NonNull TimeUnit unit) {
            Objects.requireNonNull(unit);
            if (timeout < 1L) {
                throw new IllegalArgumentException("timeout < 1L: " + timeout);
            }
            this.timeoutNanos = unit.toNanos(timeout);
            return this;
        }


        /**
         * Sets a deadline of now plus {@code duration} time. The deadline will start when the associated cancellable
         * code block will execute. All I/O operations invoked in the cancellable code block, and in children threads, will
         * regularly check if this deadline is reached.
         *
         * @return {@code this}
         */
        public @NonNull Builder deadline(final long duration, final @NonNull TimeUnit unit) {
            Objects.requireNonNull(unit);
            if (duration < 1L) {
                throw new IllegalArgumentException("duration < 1L: " + duration);
            }
            this.deadlineNanos = unit.toNanos(duration);
            return this;
        }

        /**
         * @return the {@link Cancellable} reusable instance.
         */
        public @NonNull Cancellable build() {
            return new RealCancellable(timeoutNanos, deadlineNanos);
        }
    }

    /**
     * Execute {@code block} in a cancellable context, throwing a {@link CancellationException} if a cancellation
     * occurred.
     * All operations invoked in this code block, and in children threads, will respect the cancel scope : manual cancellation,
     * await for {@link Condition} signal...
     */
    static <T> T withCancellable(final @NonNull Function<CancelScope, T> block) {
        Objects.requireNonNull(block);
        final var cancellable = new RealCancellable();
        return cancellable.executeCancellable(block);
    }

    /**
     * Execute {@code block} in a cancellable context, throwing a {@link CancellationException} if a cancellation
     * occurred.
     * All operations invoked in this code block, and in children threads, will wait at most {@code timeout} time before
     * aborting, and will also respect the cancel scope : manual cancellation, await for {@link Condition} signal...
     * <p>
     * Using a per-operation timeout means that as long as forward progress is being made, no sequence of operations
     * will fail.
     */
    static <T> T withTimeout(final long timeout,
                             final @NonNull TimeUnit unit,
                             final @NonNull Function<CancelScope, T> block) {
        Objects.requireNonNull(block);
        Objects.requireNonNull(unit);
        if (timeout < 1L) {
            throw new IllegalArgumentException("timeout < 1L: " + timeout);
        }
        final var cancellable = new RealCancellable(unit.toNanos(timeout), 0L);
        return cancellable.executeCancellable(block);
    }

    /**
     * Execute {@code block} in a cancellable context, throwing a {@link CancellationException} if a cancellation
     * occurred.
     * All operations invoked in this {@code block} will respect the cancel scope : manual cancellation,
     * await for {@link Condition} signal...
     */
    static void withCancellable(final @NonNull Consumer<CancelScope> block) {
        Objects.requireNonNull(block);
        final var cancellable = new RealCancellable();
        cancellable.executeCancellable(block);
    }

    /**
     * Execute {@code block} in a cancellable context, throwing a {@link CancellationException} if a cancellation
     * occurred.
     * All operations invoked in this {@code block} will wait at most {@code timeout} time before aborting, and will
     * also respect the cancel scope : manual cancellation, await for {@link Condition} signal...
     * <p>
     * Using a per-operation timeout means that as long as forward progress is being made, no sequence of operations
     * will fail.
     */
    static void withTimeout(final long timeout,
                            final @NonNull TimeUnit unit,
                            final @NonNull Consumer<CancelScope> block) {
        Objects.requireNonNull(block);
        Objects.requireNonNull(unit);
        if (timeout < 1L) {
            throw new IllegalArgumentException("timeout < 1L: " + timeout);
        }
        final var cancellable = new RealCancellable(unit.toNanos(timeout), 0L);
        cancellable.executeCancellable(block);
    }
}
