/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Utils for cancellable, relies on {@link ThreadLocal}.
 */
public final class CancellableUtils {
    // un-instantiable
    private CancellableUtils() {
    }

    /**
     * We use {@code InheritableThreadLocal} so it is propagated to child threads
     */
    private static final ThreadLocal<Deque<RealCancelToken>> CANCEL_TOKENS = new InheritableThreadLocal<>() {
        @Override
        protected Deque<RealCancelToken> initialValue() {
            return new ArrayDeque<>();
        }
    };

    public static @Nullable RealCancelToken getCancelToken() {
        return getCancelToken(0L);
    }

    public static @Nullable RealCancelToken getCancelToken(final @NonNegative long defaultTimeoutNanos) {
        final var cancelTokens = CANCEL_TOKENS.get();
        RealCancelToken result = null;

        // cancellable context is empty, use defaultTimeoutNanos, if any
        if (cancelTokens.isEmpty()) {
            if (defaultTimeoutNanos > 0L) {
                result = new RealCancelToken(defaultTimeoutNanos, 0L);
            }
            return result;
        }

        final var cancelTokensIterator = cancelTokens.iterator();
        while (cancelTokensIterator.hasNext()) {
            final var cancelToken = cancelTokensIterator.next();
            if (cancelToken.finished) { // finished cancel token does not apply anymore
                cancelTokensIterator.remove();
            } else if (cancelToken.shielded) { // shielded cancel token prevent from applying itself and previous ones
                break;
            } else if (result != null) {
                result = intersect(result, cancelToken);
            } else {
                result = cancelToken;
            }
        }

        // fallback to defaultTimeoutNanos, if any, if no timeout is already present in the cancellable context
        if (result != null && result.timeoutNanos == 0L && defaultTimeoutNanos > 0L) {
            result.timeoutNanos = defaultTimeoutNanos;
        }
        return result;
    }

    public static void addCancelToken(final @NonNull RealCancelToken cancelToken) {
        assert cancelToken != null;

        final var cancelTokens = CANCEL_TOKENS.get();
        cancelTokens.addFirst(cancelToken); // always add first
    }

    static void finishCancelToken(final @NonNull RealCancelToken cancelToken) {
        assert cancelToken != null;

        cancelToken.finish();
        final var cancelTokens = CANCEL_TOKENS.get();
        cancelTokens.remove(cancelToken);
    }

    /**
     * Applies the intersection between this {@code one} and {@code two}.
     */
    private static @NonNull RealCancelToken intersect(final @NonNull RealCancelToken previous,
                                                      final @NonNull RealCancelToken current) {
        assert previous != null;
        assert current != null;

        Objects.requireNonNull(current);
        // Only the last timeout applies
        final var timeoutNanos = (previous.timeoutNanos != 0L) ? previous.timeoutNanos : current.timeoutNanos;
        final var deadlineNanoTime = minDeadline(previous.deadlineNanoTime, current.deadlineNanoTime);
        final var cancelled = previous.cancelled || current.cancelled;
        return new RealCancelToken(timeoutNanos, deadlineNanoTime, cancelled);
    }

    private static @NonNegative long minDeadline(final @NonNegative long aNanos, final @NonNegative long bNanos) {
        if (aNanos == 0L) {
            return bNanos;
        }
        if (bNanos == 0L) {
            return aNanos;
        }
        return Math.min(aNanos, bNanos);
    }
}
