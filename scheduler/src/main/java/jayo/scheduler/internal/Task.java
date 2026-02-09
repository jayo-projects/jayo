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

package jayo.scheduler.internal;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A unit of work that can be executed one or more times.
 * <h3>Scheduled task recurrence</h3>
 * Tasks control their recurrence schedule. The {@link ScheduledTask#runOnce()} function returns -1L to signify that the task should
 * not be executed again. Otherwise, it returns a delay until the next execution.
 * <p>
 * A task has at most one next execution. If the same task instance is scheduled multiple times, the earliest one wins.
 * This applies to both executions scheduled directly and the scheduled tasks implied by the returned execution delay.
 * <h3>Cancellation</h3>
 * Tasks may be canceled while they are waiting to be executed, or while they are executing.
 * <p>
 * Canceling a task that is waiting to execute prevents that upcoming execution. Canceling a task that is currently
 * executing does not impact the ongoing run, but it does prevent a recurrence from being scheduled.
 * <p>
 * Tasks may opt out of cancellation using the {@code cancellable} constructor parameter. Such tasks will recur until
 * they decide not to by returning -1L.
 * <h3>Task Queues</h3>
 * Tasks are bound to the {@link RealTaskQueue} they are scheduled in. Each queue is sequential and the tasks within it
 * are never executed concurrently. It is an error to use a task in multiple queues.
 */
abstract class Task<T extends Task<T>> {
    final @Nullable String name;
    final boolean cancellable;

    // Guarded by the TaskRunner.
    @Nullable
    RealTaskQueue<T> queue = null;

    Task(final @Nullable String name, final boolean cancellable) {
        this.name = name;
        this.cancellable = cancellable;
    }

    final void initQueue(final @NonNull RealTaskQueue<T> queue) {
        assert queue != null;

        if (this.queue == queue) {
            return;
        }

        assert this.queue == null; // task must be in a single queue
        this.queue = queue;
    }

    static abstract class RunnableTask extends Task<RunnableTask> implements Runnable {
        RunnableTask(@Nullable String name, boolean cancellable) {
            super(name, cancellable);
        }

        @Override
        public String toString() {
            return "RunnableTask{" +
                    "name='" + name + '\'' +
                    ", cancellable=" + cancellable +
                    ", queue=" + queue + '\'' +
                    '}';
        }
    }

    static abstract class ScheduledTask extends Task<ScheduledTask> implements Comparable<ScheduledTask> {
        /**
         * Undefined unless this is in {@link RealTaskQueue#futureTasks}.
         */
        long nextExecuteNanoTime = -1L;

        ScheduledTask(@NonNull String name, boolean cancellable) {
            super(name, cancellable);
        }

        /**
         * @return the delay in nanoseconds until the next execution, or -1L to not reschedule.
         */
        abstract long runOnce();

        @Override
        public int compareTo(final @NonNull ScheduledTask other) {
            assert other != null;

            final var comparison = Long.compare(nextExecuteNanoTime, other.nextExecuteNanoTime);
            if (comparison == 0) {
                return (this == other) ? 0 : 1;
            }
            return comparison;
        }

        @Override
        public String toString() {
            return "ScheduledTask{" +
                    "name='" + name + '\'' +
                    ", cancellable=" + cancellable +
                    ", queue=" + queue + '\'' +
                    ", nextExecuteNanoTime=" + nextExecuteNanoTime +
                    '}';
        }
    }
}
