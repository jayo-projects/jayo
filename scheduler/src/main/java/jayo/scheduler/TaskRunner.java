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

package jayo.scheduler;

import jayo.scheduler.internal.RealTaskRunner;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

/**
 * A set of worker threads that are shared among a set of task queues.
 * <p>
 * Note: Most applications should share a process-wide {@link TaskRunner}, and create queues for per-client work with
 * {@link #newQueue()} and {@link #newScheduledQueue()}.
 *
 * @implNote The task runner is also responsible for releasing held threads when the library is unloaded. This is for
 * the benefit of container environments that implement code unloading.
 */
public sealed interface TaskRunner permits RealTaskRunner {
    /**
     * Create a new {@link TaskRunner} that will use the provided {@code executor} to schedule and execute asynchronous
     * tasks.
     */
    static TaskRunner create(final @NonNull ExecutorService executor) {
        Objects.requireNonNull(executor);
        return new RealTaskRunner(executor);
    }

    /**
     * @return a new {@link TaskQueue} that will execute tasks sequentially.
     */
    @NonNull
    TaskQueue newQueue();

    /**
     * @return a new {@link ScheduledTaskQueue} that will execute and/or schedule tasks sequentially.
     */
    @NonNull
    ScheduledTaskQueue newScheduledQueue();

    /**
     * Execute once on a task runner thread.
     */
    void execute(final boolean cancellable, final @NonNull Runnable block);

    /**
     * Schedules immediate cancellation on all currently enqueued tasks. These calls will not be made until any
     * currently executing task has completed. All cancellable tasks will be removed from the execution schedule.
     */
    void shutdown();

    @NonNull
    Backend getBackend();

    interface Backend {
        long nanoTime();

        <T> @NonNull BlockingQueue<T> decorate(final @NonNull BlockingQueue<T> queue);
    }
}
