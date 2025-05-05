/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo;

import jayo.internal.RealCancellable;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This class allows initiating and executing <b>cancellable code blocks</b>. All these methods create a
 * {@code CancelScope} implementation called a <i>Cancel Token</i> that is bound to the current thread, and will
 * automatically propagate cancellation and timeouts to children threads.
 * <p>
 * The Cancel Token only applies inside that code block and is removed when the code block has ended.
 */
public final class Cancellable {
    // un-instantiable
    private Cancellable() {
    }

    /**
     * Execute {@code block} in a cancellable context, throwing a {@link JayoInterruptedIOException} if a cancellation
     * occurred. All I/O operations invoked in this code block, including children threads, will wait at most
     * {@code timeout} time before aborting, and will also respect the cancel scope actions: manual cancellation, await
     * for {@linkplain java.util.concurrent.locks.Condition Condition} signal...
     * <p>
     * The provided timeout is used to set a deadline of now plus {@code timeout} time. This deadline will start when
     * the associated cancellable code {@code block} will start executing. All I/O operations invoked in this
     * cancellable code block, and in children threads, will regularly check if this deadline is reached.
     */
    public static void run(final @NonNull Duration timeout, final @NonNull Consumer<CancelScope> block) {
        Objects.requireNonNull(timeout);
        Objects.requireNonNull(block);
        final var cancellable = new RealCancellable(timeout.toNanos());
        cancellable.run(block);
    }

    /**
     * Execute {@code block} in a cancellable context, and return its result, throwing a
     * {@link JayoInterruptedIOException} if a cancellation occurred. All I/O operations invoked in this code block,
     * including children threads, will wait at most {@code timeout} time before aborting, and will also respect the
     * cancel scope actions: manual cancellation, await for {@linkplain java.util.concurrent.locks.Condition Condition}
     * signal...
     * <p>
     * The provided timeout is used to set a deadline of now plus {@code timeout} time. This deadline will start when
     * the associated cancellable code {@code block} will start executing. All I/O operations invoked in this
     * cancellable code block, and in children threads, will regularly check if this deadline is reached.
     */
    public static <T> T call(final @NonNull Duration timeout, final @NonNull Function<CancelScope, T> block) {
        Objects.requireNonNull(timeout);
        Objects.requireNonNull(block);
        final var cancellable = new RealCancellable(timeout.toNanos());
        return cancellable.call(block);
    }

    /**
     * Execute {@code block} in a cancellable context, throwing a {@link JayoInterruptedIOException} if a cancellation
     * occurred. All I/O operations invoked in this code block, including children threads, will respect the cancel
     * scope actions: manual cancellation, await for {@linkplain java.util.concurrent.locks.Condition Condition}
     * signal...
     */
    public static void run(final @NonNull Consumer<CancelScope> block) {
        Objects.requireNonNull(block);
        final var cancellable = new RealCancellable();
        cancellable.run(block);
    }

    /**
     * Execute {@code block} in a cancellable context, and return its result, throwing a
     * {@link JayoInterruptedIOException} if a cancellation occurred. All I/O operations invoked in this code block,
     * including children threads, will respect the cancel scope actions: manual cancellation, await for
     * {@linkplain java.util.concurrent.locks.Condition Condition} signal...
     */
    public static <T> T call(final @NonNull Function<CancelScope, T> block) {
        Objects.requireNonNull(block);
        final var cancellable = new RealCancellable();
        return cancellable.call(block);
    }
}
