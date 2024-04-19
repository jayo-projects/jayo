/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo;

import jayo.internal.RealCancellable;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This class allows to build <b>cancellable code blocks</b> via different utility methods for the most frequent
 * use-cases ({@link #withTimeout}, {@link #withCancellable}).
 * <p>
 * For advanced cancellable configuration, or of you need a cancellable instance that you can reuse to build multiple
 * cancellable code blocks with the same configuration, then use {@link #builder()}.
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

    static @NonNull Builder builder() {
        return new RealCancellable.Builder();
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

    /**
     * @return a new {@link Cancellable} without timeout nor deadline.
     */
    static Cancellable create() {
        return new RealCancellable();
    }

    /**
     * The configuration used to create a {@link Cancellable} reusable instance.
     */
    interface Builder {
        /**
         * Sets a timeout of {@code duration} time. All I/O operations invoked in the cancellable code block, and its
         * children, will wait at most {@code timeout} time before aborting.
         * <p>
         * Using a per-operation timeout means that as long as forward progress is being made, no sequence of operations
         * will fail.
         */
        @NonNull Builder timeout(final long timeout, final @NonNull TimeUnit unit);

        /**
         * Sets a deadline of now plus {@code duration} time. The deadline will start when the associated cancellable
         * code block will execute. All I/O operations invoked in the cancellable code block, and in children threads,
         * will regularly check if this deadline is reached.
         */
        @NonNull Builder deadline(final long duration, final @NonNull TimeUnit unit);

        /**
         * @return the {@link Cancellable} reusable instance.
         */
        @NonNull Cancellable build();
    }
}
