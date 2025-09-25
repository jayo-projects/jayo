/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
 *
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.tools;

import jayo.internal.RealAsyncTimeout;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * This timeout uses a background watchdog thread to take action exactly when the timeout occurs. Use this to
 * implement timeouts where they aren't supported natively, such as IO sockets or NIO channels that are blocked on
 * writing.
 * <p>
 * Callers must use {@link #create(Runnable)} to implement the action that will be called when a timeout occurs.
 * This method will be invoked by the shared watchdog thread, so it should not do any long-running operations.
 * Otherwise, we risk starving other timeouts from being triggered.
 * <p>
 * Callers should call {@link #enter(long)} before doing work that is subject to timeouts, use the returned node and
 * call {@link Node#exit()} when this work is finished. Its boolean result indicates whether the timeout occurred.
 * <p>
 * Note: the call to the timeout action is asynchronous and may be called after {@link Node#exit()}.
 */
public sealed interface AsyncTimeout permits RealAsyncTimeout {
    /**
     * @param onTimeout this code block will be invoked by the watchdog thread when the time between calls to
     *                  {@link #enter(long)} and {@link Node#exit()} has exceeded the timeout.
     * @return a new {@link AsyncTimeout}
     */
    static @NonNull AsyncTimeout create(final @NonNull Runnable onTimeout) {
        Objects.requireNonNull(onTimeout);
        return new RealAsyncTimeout(onTimeout);
    }

    /**
     * Call this method when starting doing work that is subject to timeouts. Use the returned {@link Node} and call
     * {@linkplain Node#exit() exit} when this work is finished. Its boolean result indicates whether the timeout
     * occurred.
     *
     * @param defaultTimeout the default timeout (in nanoseconds). It will be used as a fallback for this operation only
     *                       if no timeout is present in the cancellable context. It must be non-negative. A timeout of
     *                       zero is interpreted as an infinite timeout.
     * @see Node#exit()
     */
    @NonNull
    Node enter(final long defaultTimeout);

    /**
     * A node in the AsyncTimeout queue.
     */
    sealed interface Node permits RealAsyncTimeout.TimeoutNode, RealAsyncTimeout.TimeoutNodeNone {
        /**
         * If scheduled, this is the time that the watchdog should time this out. Else it returns {@code 0}.
         */
        long getTimeoutAt();

        /**
         * Call this method when ending doing work that is subject to timeouts.
         *
         * @return {@code true} if the timeout occurred.
         */
        boolean exit();
    }
}
