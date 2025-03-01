/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from OkHttp (https://github.com/square/okhttp), original copyright is below
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

package jayo.internal.scheduling;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

import static java.lang.System.Logger.Level.TRACE;

final class TaskLogger {
    // un-instantiable
    private TaskLogger() {
    }

    static void taskLog(final System. @NonNull Logger logger,
                        final @NonNull Task<?> task,
                        final @Nullable RealTaskQueue<?> queue,
                        final @NonNull Supplier<@NonNull String> message) {
        assert logger != null;
        assert task != null;
        assert message != null;

        if (logger.isLoggable(TRACE)) {
            log(logger, task, queue, message.get());
        }
    }

    static <T> T logElapsed(final System. @NonNull Logger logger,
                            final @NonNull Task<?> task,
                            final @NonNull RealTaskQueue<?> queue,
                            final @NonNull Supplier<T> block) {
        assert logger != null;
        assert task != null;
        assert queue != null;
        assert block != null;

        var startNs = -1L;
        final var loggingEnabled = logger.isLoggable(TRACE);
        if (loggingEnabled) {
            startNs = queue.taskRunner.backend.nanoTime();
            log(logger, task, queue, "starting");
        }

        var completedNormally = false;
        try {
            final var result = block.get();
            completedNormally = true;
            return result;
        } finally {
            if (loggingEnabled) {
                final var elapsedNs = queue.taskRunner.backend.nanoTime() - startNs;
                if (completedNormally) {
                    log(logger, task, queue, "finished run in " + formatDuration(elapsedNs));
                } else {
                    log(logger, task, queue, "failed a run in " + formatDuration(elapsedNs));
                }
            }
        }
    }

    /**
     * @return a formatted duration in the nearest whole-number units like "999 µs" or "  1 s". This rounds 0.5 units
     * away from 0 and 0.499 towards 0. The smallest unit this returns is "µs"; the largest unit it returns is "s". For
     * values in [-499..499] this returns "  0 µs".
     * <p>
     * The returned string attempts to be column-aligned to 6 characters. For negative and large values the returned
     * string may be longer.
     */
    static String formatDuration(final long elapsedNs) {
        final String s;
        if (elapsedNs <= -999_500_000) {
            s = (elapsedNs - 500_000_000) / 1_000_000_000 + " s ";
        } else if (elapsedNs <= -999_500) {
            s = (elapsedNs - 500_000) / 1_000_000 + " ms";
        } else if (elapsedNs <= 0) {
            s = (elapsedNs - 500) / 1_000 + " µs";
        } else if (elapsedNs < 999_500) {
            s = (elapsedNs + 500) / 1_000 + " µs";
        } else if (elapsedNs < 999_500_000) {
            s = (elapsedNs + 500_000) / 1_000_000 + " ms";
        } else {
            s = (elapsedNs + 500_000_000) / 1_000_000_000 + " s ";
        }
        return String.format("%6s", s);
    }

    private static void log(final System. @NonNull Logger logger,
                            final @NonNull Task<?> task,
                            final @Nullable RealTaskQueue<?> queue,
                            final @NonNull String message) {
        assert logger != null;
        assert task != null;
        assert message != null;

        final var queueInfo = (queue != null) ? queue.name + " " : "";
        logger.log(TRACE, queueInfo + String.format("%-22s", message) + ": " + task.name);
    }
}
