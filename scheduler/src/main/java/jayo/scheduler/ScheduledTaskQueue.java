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

import java.util.function.LongSupplier;

/**
 * A {@link TaskQueue} with an additional method to {@linkplain #schedule(String, long, LongSupplier) schedule} a task,
 * it may have an initial delay and run several times.
 */
public sealed interface ScheduledTaskQueue extends TaskQueue permits RealTaskQueue.ScheduledQueue {
    /**
     * Schedule a delayed or/and repeating task that will be run on a task runner thread. This task is cancellable.
     */
    void schedule(final @NonNull String name,
                  final long initialDelayNanos,
                  final @NonNull LongSupplier block);
}
