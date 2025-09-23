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
import java.util.function.Function;

/**
 * This timeout uses a background watchdog thread to take action exactly when the timeout occurs. Use this to
 * implement timeouts where they aren't supported natively, such as IO sockets or NIO channels that are blocked on
 * writing.
 * <p>
 * Callers must use {@link #create(Runnable)} to implement the action that will be called when a timeout occurs.
 * This method will be invoked by the shared watchdog thread, so it should not do any long-running operations.
 * Otherwise, we risk starving other timeouts from being triggered.
 * <p>
 * Callers should call {@link #withTimeout(long, Function)} to do work that is subject to timeouts.
 */
public sealed interface AsyncTimeout permits RealAsyncTimeout {
    /**
     * @param onTimeout this code block will be invoked by the watchdog thread when the time to execute the code block
     *                  passed to {@link #withTimeout(long, Function)} has exceeded the timeout.
     * @return a new {@link AsyncTimeout}
     */
    static @NonNull AsyncTimeout create(final @NonNull Runnable onTimeout) {
        Objects.requireNonNull(onTimeout);
        return new RealAsyncTimeout(onTimeout);
    }

    /**
     * Execute {@code block} with timeout support. Use the {@link Node} argument to {@linkplain Node#exit() exit} the
     * work that is subject to timeouts, its boolean result indicates whether the timeout occurred.
     */
    <T> T withTimeout(final long defaultTimeout, final @NonNull Function<@NonNull Node, T> block);

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
