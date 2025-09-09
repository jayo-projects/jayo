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

import jayo.RawReader;
import jayo.RawWriter;
import jayo.internal.RealAsyncTimeout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * This timeout uses a background watchdog thread to take action exactly when the timeout occurs. Use this to
 * implement timeouts where they aren't supported natively, such as to sockets or channels that are blocked on
 * writing.
 * <p>
 * Subclasses must call {@link #create(Runnable)} to implement the action that will be called when a timeout occurs.
 * This method will be invoked by the shared watchdog thread, so it should not do any long-running operations.
 * Otherwise, we risk starving other timeouts from being triggered.
 * <p>
 * Use {@link #writer(RawWriter)} and {@link #reader(RawReader)} to apply this timeout to a stream. The returned value
 * will apply the timeout to each operation on the wrapped stream.
 * <p>
 * Callers should call {@link #enter(long)} before doing work that is subject to timeouts, and {@link #exit(Node)}
 * afterward. The return value of {@link #exit(Node)} indicates whether a timeout was triggered.
 */
public sealed interface AsyncTimeout permits RealAsyncTimeout {
    /**
     * @param onTimeout this code block will be invoked by the watchdog thread when the time between
     *                  calls to {@link #enter(long)} and {@link #exit(Node)} has exceeded the timeout.
     * @return a new {@link AsyncTimeout}
     */
    static @NonNull AsyncTimeout create(final @NonNull Runnable onTimeout) {
        Objects.requireNonNull(onTimeout);
        return new RealAsyncTimeout(onTimeout);
    }

    /**
     * Call this method when starting doing work that is subject to timeouts, and get a node that can be passed to
     * {@link #exit(Node)}.
     *
     * @param defaultTimeout the default timeout (in nanoseconds). It will be used as a fallback for this operation only
     *                       if no timeout is present in the cancellable context. It must be non-negative. A timeout of
     *                       zero is interpreted as an infinite timeout.
     * @see #exit(Node)
     */
    @Nullable
    Node enter(final long defaultTimeout);

    /**
     * Call this method when ending doing work that is subject to timeouts, pass the node returned by
     * {@link #enter(long)} as an argument.
     *
     * @return {@code true} if the timeout occurred.
     * @see #enter(long)
     */
    boolean exit(final @Nullable Node node);

    /**
     * @param reader the delegate reader.
     * @return a new reader that delegates to {@code reader}, using this to implement timeouts. If a timeout occurs, the
     * {@code onTimeout} code block declared in {@link #create(Runnable)} will execute.
     */
    @NonNull
    RawReaderWithTimeout reader(final @NonNull RawReader reader);

    /**
     * @param writer the delegate writer.
     * @return a new writer that delegates to {@code writer}, using this to implement timeouts. If a timeout occurs, the
     * {@code onTimeout} code block declared in {@link #create(Runnable)} will execute.
     */
    @NonNull
    RawWriterWithTimeout writer(final @NonNull RawWriter writer);

    /**
     * A node in the AsyncTimeout queue.
     */
    sealed interface Node permits RealAsyncTimeout.TimeoutNode {
        /**
         * If scheduled, this is the time that the watchdog should time this out.
         */
        long getTimeoutAt();
    }

    sealed interface RawReaderWithTimeout extends RawReader permits RealAsyncTimeout.RawReaderWithTimeout {
        /**
         * @return the default read timeout. It will be used as a fallback for each read operation, only if no timeout
         * is present in the cancellable context.
         */
        @NonNull
        Duration getTimeout();

        /**
         * Sets the default read timeout. It will be used as a fallback for each read operation, only if no timeout is
         * present in the cancellable context. A timeout of zero is interpreted as an infinite timeout.
         */
        void setTimeout(final @NonNull Duration readTimeout);
    }

    sealed interface RawWriterWithTimeout extends RawWriter permits RealAsyncTimeout.RawWriterWithTimeout {
        /**
         * @return the default write timeout. It will be used as a fallback for each write operation, only if no timeout
         * is present in the cancellable context.
         */
        @NonNull
        Duration getTimeout();

        /**
         * Sets the default write timeout. It will be used as a fallback for each write operation, only if no timeout is
         * present in the cancellable context. A timeout of zero is interpreted as an infinite timeout.
         */
        void setTimeout(final @NonNull Duration readTimeout);
    }
}
