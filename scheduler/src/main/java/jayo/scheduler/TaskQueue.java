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

import jayo.scheduler.internal.RealTaskQueue;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CountDownLatch;

/**
 * A set of tasks that are executed in sequential order.
 * <p>
 * Work within queues is not concurrent. This is equivalent to each queue having a dedicated thread for its work.
 *
 * @implNote In practice, a set of queues may share a set of threads to save resources.
 */
public sealed interface TaskQueue permits RealTaskQueue, ScheduledTaskQueue {
    /**
     * @return the name of this task queue.
     */
    @NonNull
    String getName();

    /**
     * Execute a task once on a task runner thread, this task is guaranteed to be executed after all previous tasks
     * enqueued in this queue.
     */
    void execute(final @NonNull String name,
                 final boolean cancellable,
                 final @NonNull Runnable block);

    /**
     * @return a latch that reaches 0 when the queue is next idle.
     */
    @NonNull
    CountDownLatch idleLatch();

    /**
     * Schedules immediate cancellation on all currently enqueued tasks. These calls will not be made until any
     * currently executing task has completed. All cancellable tasks will be removed from the execution schedule.
     */
    void cancelAll();

    /**
     * First, initiates an orderly shutdown; no new tasks will be accepted. Then schedules immediate cancellation on all
     * currently enqueued tasks by calling {@link #cancelAll()}.
     */
    void shutdown();
}
