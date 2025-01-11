/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.CancelScope;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class RealCancellable {
    private final @NonNegative long timeoutNanos;

    public RealCancellable() {
        this(0L);
    }

    public RealCancellable(final @NonNegative long timeoutNanos) {
        this.timeoutNanos = timeoutNanos;
    }

    public <T> T call(final @NonNull Function<CancelScope, T> block) {
        Objects.requireNonNull(block);
        final var cancelToken = new RealCancelToken(timeoutNanos);
        CancellableUtils.addCancelToken(cancelToken);
        try {
            return block.apply(cancelToken);
        } finally {
            CancellableUtils.finishCancelToken(cancelToken);
        }
    }

    public void run(final @NonNull Consumer<CancelScope> block) {
        Objects.requireNonNull(block);
        final var cancelToken = new RealCancelToken(timeoutNanos);
        CancellableUtils.addCancelToken(cancelToken);
        try {
            block.accept(cancelToken);
        } finally {
            CancellableUtils.finishCancelToken(cancelToken);
        }
    }
}
