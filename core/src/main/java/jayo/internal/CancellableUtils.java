/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

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
            return new ConcurrentLinkedDeque<>();
        }
    };

    public static @Nullable RealCancelToken getCancelToken() {
        final var cancelTokens = CANCEL_TOKENS.get();
        RealCancelToken result = null;

        // cancellable context is empty, use defaultTimeoutNanos, if any
        if (cancelTokens.isEmpty()) {
            return null;
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

        final var deadlineNanoTime = minDeadline(previous.deadlineNanoTime, current.deadlineNanoTime);

        // Only the last default timeout applies, if no deadline
        final long timeoutNanos;
        if (deadlineNanoTime == 0L) {
            timeoutNanos = (previous.timeoutNanos != 0L) ? previous.timeoutNanos : current.timeoutNanos;
        } else {
            timeoutNanos = 0L;
        }

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
