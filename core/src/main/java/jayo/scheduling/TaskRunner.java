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

package jayo.scheduling;

import jayo.internal.scheduling.RealTaskRunner;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

/**
 * A set of worker threads that are shared among a set of task queues.
 * <p>
 * Most applications should share a process-wide {@link TaskRunner}, and create queues for per-client work with
 * {@link #newQueue()}.
 *
 * @implNote The task runner is also responsible for releasing held threads when the library is unloaded. This is for
 * the benefit of container environments that implement code unloading.
 */
public sealed interface TaskRunner permits RealTaskRunner {
    /**
     * Create a new {@link TaskRunner}. The threads it will use will start with the provided {@code name}.
     */
    static TaskRunner create(final @NonNull String name) {
        Objects.requireNonNull(name);
        return new RealTaskRunner(name);
    }

    /**
     * Create a new {@link TaskRunner} that will use the provided {@code executor} to schedule and execute asynchronous
     * tasks.
     */
    static TaskRunner create(final @NonNull ExecutorService executor) {
        Objects.requireNonNull(executor);
        return new RealTaskRunner(executor);
    }

    @NonNull
    TaskQueue newQueue();

    @NonNull
    ScheduledTaskQueue newScheduledQueue();

    void execute(final boolean cancellable, final @NonNull Runnable block);

    void shutdown();

    @NonNull
    Backend getBackend();

    interface Backend {
        long nanoTime();

        <T> @NonNull BlockingQueue<T> decorate(final @NonNull BlockingQueue<T> queue);
    }
}
