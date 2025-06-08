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

package jayo.scheduler.internal

import jayo.scheduler.TaskQueue
import jayo.scheduler.TaskRunner
import kotlin.concurrent.withLock

/**
 * Returns a snapshot of queues that currently have tasks scheduled. The task runner does not track queues that have no
 * tasks scheduled.
 */
fun TaskRunner.activeQueues(): Set<TaskQueue> {
    this as RealTaskRunner // smart cast
    return scheduledLock.withLock {
        lock.withLock {
            mutableSetOf<TaskQueue>().apply {
                for (task in futureTasks) {
                    if (task.queue != null) {
                        add(task.queue!!)
                    }
                }
                for (task in futureScheduledTasks) {
                    if (task.queue != null) {
                        add(task.queue!!)
                    }
                }
            }
        }
    }
}
