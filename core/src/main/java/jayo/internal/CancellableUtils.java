/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

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

    private static final ThreadLocal<Deque<RealCancelToken>> CANCEL_TOKENS =
            ThreadLocal.withInitial(ConcurrentLinkedDeque::new);

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

            // a finished cancel token does not apply anymore
            if (cancelToken.finished) {
                cancelTokensIterator.remove();

                // a shielded cancel token prevents from applying itself and previous ones
            } else if (cancelToken.shielded) {
                break;

                // several cancel tokens apply, we merge them
            } else if (result != null) {
                result = intersect(result, cancelToken);

            } else {
                result = cancelToken;
            }
        }

        return result;
    }

    static void addCancelToken(final @NonNull RealCancelToken cancelToken) {
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

    private static long minDeadline(final long aNanos, final long bNanos) {
        if (aNanos == 0L) {
            return bNanos;
        }
        if (bNanos == 0L) {
            return aNanos;
        }
        return Math.min(aNanos, bNanos);
    }
}
