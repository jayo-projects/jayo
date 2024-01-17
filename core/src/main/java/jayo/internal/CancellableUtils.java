/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import jayo.external.NonNegative;

import java.util.*;

/**
 * Utils for cancellable, relies on {@link ThreadLocal}.
 */
public final class CancellableUtils {
    // un-instantiable
    private CancellableUtils() {
    }

    /**
     * We use {@code InheritableThreadLocal} so it is propagated to child threads
     * @see SourceSegmentQueue
     */
    private static final ThreadLocal<Deque<RealCancelToken>> CANCEL_TOKENS = new InheritableThreadLocal<>() {
        @Override
        protected Deque<RealCancelToken> initialValue() {
            return new ArrayDeque<>();
        }
    };

    public static @Nullable RealCancelToken getCancelToken() {
        final var cancelTokens = CANCEL_TOKENS.get();
        RealCancelToken result = null;
        if (cancelTokens.isEmpty()) {
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
        return result;
    }

    /**
     * Applies the intersection between this {@code one} and {@code two}.
     */
    private static @NonNull RealCancelToken intersect(final @NonNull RealCancelToken previous, final @NonNull RealCancelToken current) {
        Objects.requireNonNull(previous);
        Objects.requireNonNull(current);
        // Only the last timeout applies
        final var timeoutNanos = (previous.timeoutNanos != 0) ? previous.timeoutNanos : current.timeoutNanos;
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

    public static void addCancelToken(final @NonNull RealCancelToken cancelToken) {
        Objects.requireNonNull(cancelToken);
        final var cancelTokens = CANCEL_TOKENS.get();
        cancelTokens.addFirst(cancelToken); // always add first
    }

    static void finishCancelToken(final @NonNull RealCancelToken cancelToken) {
        Objects.requireNonNull(cancelToken).finish();
        final var cancelTokens = CANCEL_TOKENS.get();
        cancelTokens.remove(cancelToken);
    }
}
