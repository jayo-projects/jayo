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

import jayo.scheduler.TaskQueue;
import jayo.scheduler.TaskRunner;
import jayo.scheduler.tools.BasicFifoQueue;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class RealTaskRunner implements TaskRunner {
    private static final System.Logger LOGGER = System.getLogger("jayo.scheduler.TaskRunner");

    private final @NonNull AtomicInteger nextQueueIndex = new AtomicInteger(10000);

    final @NonNull Backend backend;
    final System.@NonNull Logger logger;

    // scheduled runner
    private final @NonNull Runnable scheduledRunnable;
    final @NonNull ReentrantLock scheduledLock = new ReentrantLock();
    final @NonNull Condition scheduledCondition = scheduledLock.newCondition();
    private boolean scheduledCoordinatorWaiting = false;
    private long scheduledCoordinatorWakeUpAt = 0L;

    /**
     * When we need a new thread to run tasks, we call {@link Backend#execute(RealTaskRunner, Runnable)}. A few
     * microseconds later we expect a newly started thread to call {@link Runnable#run()}. We shouldn't request new
     * threads until the already-requested ones are in service, otherwise we might create more threads than we need.
     * <p>
     * We use {@code #scheduledExecuteCallCount} and {@link #scheduledRunCallCount} to defend against starting more
     * threads than we need. Both fields are guarded by {@link #scheduledLock}.
     */
    private int scheduledExecuteCallCount = 0;
    private int scheduledRunCallCount = 0;

    // FIFO runner
    private final @NonNull Runnable runnable;
    final @NonNull ReentrantLock lock = new ReentrantLock();

    /**
     * When we need a new thread to run tasks, we call {@link Backend#execute(RealTaskRunner, Runnable)}. A few
     * microseconds later we expect a newly started thread to call {@link Runnable#run()}. We shouldn't request new
     * threads until the already-requested ones are in service, otherwise we might create more threads than we need.
     * <p>
     * We use {@code #executeCallCount} and {@link #runCallCount} to defend against starting more threads than we need.
     * Both fields are guarded by {@link #lock}.
     */
    private int executeCallCount = 0;
    private int runCallCount = 0;

    /**
     * sequential tasks FIFO ordered.
     */
    final BasicFifoQueue<Task.RunnableTask> futureTasks = BasicFifoQueue.create();
    /**
     * Scheduled tasks ordered by {@link Task.ScheduledTask#nextExecuteNanoTime}.
     */
    final Queue<Task.ScheduledTask> futureScheduledTasks = new PriorityQueue<>();

    public RealTaskRunner(final @NonNull ExecutorService executor) {
        this(new RealBackend(executor), LOGGER);
    }

    RealTaskRunner(final @NonNull Backend backend, final System.@NonNull Logger logger) {
        assert backend != null;
        assert logger != null;

        this.backend = backend;
        this.logger = logger;
        scheduledRunnable = () -> {
            Task.ScheduledTask task;
            scheduledLock.lock();
            try {
                scheduledRunCallCount++;
                task = awaitScheduledTaskToRun();
                if (task == null) {
                    return;
                }
            } finally {
                scheduledLock.unlock();
            }

            final var currentThread = Thread.currentThread();
            final var oldName = currentThread.getName();
            try {
                while (true) {
                    currentThread.setName(task.name);
                    assert task.queue != null;
                    final var delayNanos = TaskLogger.logElapsed(logger, task, task.queue, task::runOnce);
                    // A task ran successfully. Update the execution state and take the next task.
                    scheduledLock.lock();
                    try {
                        afterScheduledRun(task, delayNanos, true);
                        task = awaitScheduledTaskToRun();
                        if (task == null) {
                            return;
                        }
                    } finally {
                        scheduledLock.unlock();
                    }
                }
            } catch (Throwable thrown) {
                // A task failed. Update the execution state and re-throw the exception.
                scheduledLock.lock();
                try {
                    assert task != null;
                    afterScheduledRun(task, -1L, false);
                } finally {
                    scheduledLock.unlock();
                }
                throw thrown;
            } finally {
                currentThread.setName(oldName);
            }
        };

        runnable = () -> {
            Task.RunnableTask task;
            lock.lock();
            try {
                runCallCount++;
                task = awaitTaskToRun();
                if (task == null) {
                    return;
                }
            } finally {
                lock.unlock();
            }

            final var currentThread = Thread.currentThread();
            final var oldName = currentThread.getName();
            try {
                while (true) {
                    currentThread.setName(task.name);
                    task.run();
                    // A task ran successfully. Update the execution state and take the next task.
                    lock.lock();
                    try {
                        afterRun(task, true);
                        task = awaitTaskToRun();
                        if (task == null) {
                            return;
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (Throwable thrown) {
                // A task failed. Update execution state and re-throw the exception.
                lock.lock();
                try {
                    assert task != null;
                    afterRun(task, false);
                } finally {
                    lock.unlock();
                }
                throw thrown;
            } finally {
                currentThread.setName(oldName);
            }
        };
    }

    void kickScheduledCoordinator() {
        if (scheduledCoordinatorWaiting) {
            backend.coordinatorNotify(this);
        } else {
            startAnotherScheduledThread();
        }
    }

    /**
     * Start another thread unless a new thread is already scheduled to start.
     */
    private void startAnotherScheduledThread() {
        if (scheduledExecuteCallCount > scheduledRunCallCount) {
            return; // A thread is still starting.
        }
        scheduledExecuteCallCount++;
        backend.execute(this, scheduledRunnable);
    }

    private <T extends Task<T>> void beforeRun(final @NonNull T task) {
        assert task != null;

        final var queue = task.queue;
        if (queue != null) {
            final var removedTask = queue.futureTasks.poll();
            if (task != removedTask || queue.scheduledTask != task) {
                throw new IllegalStateException("removedTask " + removedTask + " or queue.scheduledTask " +
                        queue.scheduledTask + " != task " + task);
            }
            queue.activeTask = task;
        }

        if (task instanceof Task.RunnableTask runnableTask) {
            if (futureTasks.poll() != runnableTask) {
                throw new IllegalStateException();
            }
            // Also, start another thread if there's more work or scheduling to do.
            if (!futureTasks.isEmpty()) {
                startAnotherThread();
            }
        } else if (task instanceof Task.ScheduledTask scheduledTask) {
            scheduledTask.nextExecuteNanoTime = -1L;
            if (futureScheduledTasks.poll() != scheduledTask) {
                throw new IllegalStateException();
            }
            // Also, start another thread if there's more work or scheduling to do.
            if (!futureScheduledTasks.isEmpty()) {
                startAnotherScheduledThread();
            }
        } else {
            throw new IllegalStateException("Unexpected task type: " + task);
        }
    }

    private void afterScheduledRun(final Task.@NonNull ScheduledTask task,
                                   final long delayNanos,
                                   final boolean completedNormally) {
        assert task != null;

        final var queue = (RealTaskQueue.ScheduledQueue) task.queue;
        if (queue != null) {
            afterScheduledRun(task, delayNanos, queue);
        }

        // If the task crashed, start another thread to run the next task.
        if (!futureScheduledTasks.isEmpty() && !completedNormally) {
            startAnotherScheduledThread();
        }
    }

    private void afterScheduledRun(final Task.@NonNull ScheduledTask task,
                                   final long delayNanos,
                                   final RealTaskQueue.@NonNull ScheduledQueue queue) {
        if (queue.activeTask != task) {
            throw new IllegalStateException("Task queue " + queue.name + " is not active." +
                    " queue.activeTask " + queue.activeTask + " != task " + task);
        }

        final var cancelTask = queue.cancelActiveTask;
        queue.cancelActiveTask = false;
        queue.activeTask = null;

        assert queue.scheduledTask == task;

        final var nextTaskInQueue = queue.futureTasks.peek();
        if (nextTaskInQueue != null) {
            futureScheduledTasks.offer(nextTaskInQueue);
            queue.scheduledTask = nextTaskInQueue;
        } else {
            queue.scheduledTask = null;
        }

        if (delayNanos != -1L && !cancelTask && !queue.shutdown) {
            queue.scheduleAndDecide(task, delayNanos, true);
        }
    }

    /**
     * Returns an immediately executable task for the calling thread to execute, sleeping as necessary until one is
     * ready. If there are no ready task, or if other threads can execute it this will return null. If there is more
     * than a single task ready to execute immediately this will start another thread to handle that work.
     */
    private Task.ScheduledTask awaitScheduledTaskToRun() {
        while (true) {
            final var scheduledTask = futureScheduledTasks.peek();
            if (scheduledTask == null) {
                return null; // Nothing to do.
            }

            final var now = backend.nanoTime();
            final var taskDelayNanos = scheduledTask.nextExecuteNanoTime - now;

            // We have a task ready to go. Run it.
            if (taskDelayNanos <= 0L) {
                beforeRun(scheduledTask);
                return scheduledTask;

                // Notify the coordinator of a task that's coming up soon.
            } else if (scheduledCoordinatorWaiting) {
                if (taskDelayNanos < scheduledCoordinatorWakeUpAt - now) {
                    backend.coordinatorNotify(this);
                }
                return null;

                // No other thread is coordinating. Become the coordinator and wait for this scheduled task!
            } else {
                scheduledCoordinatorWaiting = true;
                scheduledCoordinatorWakeUpAt = now + taskDelayNanos;
                var fullyWaited = false;
                try {
                    fullyWaited = backend.coordinatorWait(this, taskDelayNanos);
                } catch (InterruptedException ignored) {
                    // Will cause all tasks to exit unless more are scheduled!
                    cancelAll();
                } finally {
                    scheduledCoordinatorWaiting = false;
                }
                // wait was fully done, return this scheduled task now ready to go.
                if (fullyWaited && scheduledTask == futureScheduledTasks.peek()) {
                    beforeRun(scheduledTask);
                    return scheduledTask;
                }
            }
        }
    }


    private Task.RunnableTask awaitTaskToRun() {
        // try to peek a runnable task
        final var task = futureTasks.peek();
        if (task == null) {
            return null;
        }

        // We have a task ready to go. Run it.
        beforeRun(task);
        return task;
    }

    /**
     * Start another thread, unless a new thread is already scheduled to start.
     */
    void startAnotherThread() {
        if (executeCallCount > runCallCount) {
            return; // A thread is still starting.
        }
        executeCallCount++;
        backend.execute(this, runnable);
    }

    private void afterRun(final Task.@NonNull RunnableTask task, final boolean completedNormally) {
        assert task != null;

        final var queue = (RealTaskQueue.RunnableQueue) task.queue;
        if (queue != null) {
            afterRun(task, queue);
        }

        // If the task crashed, start another thread to run the next task.
        if (!futureTasks.isEmpty() && !completedNormally) {
            startAnotherThread();
        }
    }

    private void afterRun(final Task.@NonNull RunnableTask task, final RealTaskQueue.@NonNull RunnableQueue queue) {
        if (queue.activeTask != task) {
            throw new IllegalStateException("Task queue " + queue.name + " is not active." +
                    " queue.activeTask " + queue.activeTask + " != task " + task);
        }

        queue.activeTask = null;

        assert queue.scheduledTask == task;

        final var nextTaskInQueue = queue.futureTasks.peek();
        if (nextTaskInQueue != null) {
            futureTasks.offer(nextTaskInQueue);
            queue.scheduledTask = nextTaskInQueue;
        } else {
            queue.scheduledTask = null;
        }
    }

    @Override
    public @NonNull TaskQueue newQueue() {
        return new RealTaskQueue.RunnableQueue(this, "Q" + nextQueueIndex.getAndIncrement());
    }

    @Override
    public RealTaskQueue.@NonNull ScheduledQueue newScheduledQueue() {
        return new RealTaskQueue.ScheduledQueue(this, "Q" + nextQueueIndex.getAndIncrement());
    }

    @Override
    public void execute(final @NonNull String name,
                        final boolean cancellable,
                        final @NonNull Runnable block) {
        assert name != null;
        assert block != null;

        final var task = new Task.RunnableTask(name, cancellable) {
            @Override
            public void run() {
                block.run();
            }
        };

        lock.lock();
        try {
            final var wasEmpty = futureTasks.offer(task);
            if (wasEmpty) {
                startAnotherThread();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void shutdown() {
        scheduledLock.lock();
        lock.lock();
        try {
            cancelAll();
            backend.shutdown();
        } finally {
            lock.unlock();
            scheduledLock.unlock();
        }
    }

    @Override
    public TaskRunner.@NonNull Backend getBackend() {
        return backend;
    }

    private void cancelAll() {
        cancelAll(futureTasks);
        cancelAll(futureScheduledTasks);
    }

    private <T extends Task<T>> void cancelAll(final @NonNull Queue<T> futureTasks) {
        assert futureTasks != null;

        final var tasksIterator = futureTasks.iterator();
        while (tasksIterator.hasNext()) {
            final var task = tasksIterator.next();
            if (task.cancellable) {
                TaskLogger.taskLog(logger, task, task.queue, () -> "canceled");
                tasksIterator.remove();
                if (task.queue != null) {
                    task.queue.futureTasks.remove(task);
                }
            }
        }
    }

    interface Backend extends TaskRunner.Backend {
        void coordinatorNotify(final @NonNull RealTaskRunner taskRunner);

        boolean coordinatorWait(final @NonNull RealTaskRunner taskRunner,
                                final long nanos) throws InterruptedException;

        void execute(final @NonNull RealTaskRunner taskRunner, final @NonNull Runnable runnable);

        void shutdown();
    }

    record RealBackend(@NonNull ExecutorService executor) implements Backend {
        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public @NonNull <T> BlockingQueue<T> decorate(final @NonNull BlockingQueue<T> queue) {
            Objects.requireNonNull(queue);
            return queue;
        }

        @Override
        public void coordinatorNotify(final @NonNull RealTaskRunner taskRunner) {
            assert taskRunner != null;
            taskRunner.scheduledCondition.signal();
        }

        /**
         * Wait a duration in nanoseconds.
         *
         * @return true if wait was fully completed, false if it has been signalled before ending the wait phase.
         */
        @Override
        public boolean coordinatorWait(final @NonNull RealTaskRunner taskRunner,
                                       final long nanos) throws InterruptedException {
            assert taskRunner != null;
            assert nanos > 0;
            return taskRunner.scheduledCondition.awaitNanos(nanos) <= 0;
        }

        @Override
        public void execute(final @NonNull RealTaskRunner taskRunner, final @NonNull Runnable runnable) {
            assert taskRunner != null;
            assert runnable != null;
            executor.execute(runnable);
        }

        @Override
        public void shutdown() {
            executor.shutdown();
        }
    }
}
