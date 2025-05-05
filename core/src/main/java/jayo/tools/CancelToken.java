/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.tools;

import jayo.JayoInterruptedIOException;
import jayo.internal.CancellableUtils;
import jayo.internal.RealCancelToken;
import org.jspecify.annotations.Nullable;

/**
 * A cancellable token that may be present in the thread context (either thread local or scoped value). This interface
 * is provided for implementors, so you can call {@link #throwIfReached(CancelToken)} regularly to ensure Jayo's
 * cancellation support in your APIs.
 */
public interface CancelToken {
    /**
     * @return the current CancelToken, if at least one is present in the thread context.
     */
    static @Nullable CancelToken getCancelToken() {
        return CancellableUtils.getCancelToken();
    }

    /**
     * Throws a {@link JayoInterruptedIOException} if a manual cancellation was done, a deadline has been reached or if
     * the current thread has been interrupted.
     * <p>
     * Note: this method doesn't detect timeouts; that should be implemented to asynchronously abort an in-progress
     * operation.
     *
     * @see AsyncTimeout
     */
    static void throwIfReached(final @Nullable CancelToken cancelToken) {
        if (Thread.currentThread().isInterrupted()) {
            if (cancelToken != null) {
                if (!(cancelToken instanceof RealCancelToken _cancelToken)) {
                    throw new IllegalArgumentException("cancelToken must be an instance of RealCancelToken");
                }
                _cancelToken.cancel();
            }
            throw new JayoInterruptedIOException("current thread is interrupted");
        }

        if (cancelToken != null) {
            if (!(cancelToken instanceof RealCancelToken _cancelToken)) {
                throw new IllegalArgumentException("cancelToken must be an instance of RealCancelToken");
            }
            _cancelToken.throwIfReached();
        }
    }
}
