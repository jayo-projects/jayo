/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.CancelScope;
import jayo.Cancellable;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class RealCancellable implements Cancellable {
    private final @NonNegative long timeoutNanos;
    private final @NonNegative long deadlineNanos;

    public RealCancellable() {
        this(0L, 0L);
    }

    public RealCancellable(final @NonNegative long timeoutNanos, final @NonNegative long deadlineNanos) {
        this.timeoutNanos = timeoutNanos;
        this.deadlineNanos = deadlineNanos;
    }

    public <T> T call(final @NonNull Function<CancelScope, T> block) {
        Objects.requireNonNull(block);
        final var cancelToken = new RealCancelToken(timeoutNanos, deadlineNanos);
        CancellableUtils.addCancelToken(cancelToken);
        try {
        return block.apply(cancelToken);
        } finally {
            CancellableUtils.finishCancelToken(cancelToken);
        }
    }

    public void run(final @NonNull Consumer<CancelScope> block) {
        Objects.requireNonNull(block);
        final var cancelToken = new RealCancelToken(timeoutNanos, deadlineNanos);
        CancellableUtils.addCancelToken(cancelToken);
        try {
            block.accept(cancelToken);
        } finally {
            CancellableUtils.finishCancelToken(cancelToken);
        }
    }

    public static final class Builder implements Cancellable.Builder {
        private @NonNegative long timeoutNanos = 0L;
        private @NonNegative long deadlineNanos = 0L;

        @Override
        public @NonNull Builder timeout(final @NonNull Duration timeout) {
            Objects.requireNonNull(timeout);
            this.timeoutNanos = timeout.toNanos();
            return this;
        }

        @Override
        public @NonNull Builder deadline(final @NonNull Duration deadline) {
            Objects.requireNonNull(deadline);
            this.deadlineNanos = deadline.toNanos();
            return this;
        }

        @Override
        public @NonNull Cancellable build() {
            return new RealCancellable(timeoutNanos, deadlineNanos);
        }
    }
}
