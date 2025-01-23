/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
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

package jayo.internal.scheduling

import jayo.TestLogHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.RejectedExecutionException
import kotlin.test.assertFailsWith

class TaskRunnerTest {
    private val taskFaker = TaskFaker()

    @RegisterExtension
    @JvmField
    val testLogHandler = TestLogHandler(taskFaker.logger)

    private val taskRunner = taskFaker.taskRunner
    private val log = mutableListOf<String>()
    private val redQueue = taskRunner.newScheduledQueue()
    private val blueQueue = taskRunner.newScheduledQueue()
    private val greenQueue = taskRunner.newScheduledQueue()

    @AfterEach
    internal fun tearDown() {
        taskFaker.close()
    }

    @Test
    fun executeDelayed() {
        redQueue.schedule("task", 100.µs) {
            log += "run@${taskFaker.nanoTime}"
            -1L
        }

        taskFaker.advanceUntil(0.µs)
        assertThat(log).containsExactly()

        taskFaker.advanceUntil(99.µs)
        assertThat(log).containsExactly()

        taskFaker.advanceUntil(100.µs)
        assertThat(log).containsExactly("run@100000")

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task",
            "TRACE: Q10000 starting              : task",
            "TRACE: Q10000 finished run in   0 µs: task",
        )
    }

    @Test
    fun executeRepeated() {
        val delays = mutableListOf(50.µs, 150.µs, -1L)
        redQueue.schedule("task", 100.µs) {
            log += "run@${taskFaker.nanoTime}"
            return@schedule delays.removeAt(0)
        }

        taskFaker.advanceUntil(0.µs)
        assertThat(log).containsExactly()

        taskFaker.advanceUntil(100.µs)
        assertThat(log).containsExactly("run@100000")

        taskFaker.advanceUntil(150.µs)
        assertThat(log).containsExactly("run@100000", "run@150000")

        taskFaker.advanceUntil(299.µs)
        assertThat(log).containsExactly("run@100000", "run@150000")

        taskFaker.advanceUntil(300.µs)
        assertThat(log).containsExactly("run@100000", "run@150000", "run@300000")

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task",
            "TRACE: Q10000 starting              : task",
            "TRACE: Q10000 finished run in   0 µs: task",
            "TRACE: Q10000 run again after  50 µs: task",
            "TRACE: Q10000 starting              : task",
            "TRACE: Q10000 finished run in   0 µs: task",
            "TRACE: Q10000 run again after 150 µs: task",
            "TRACE: Q10000 starting              : task",
            "TRACE: Q10000 finished run in   0 µs: task",
        )
    }

    /** Repeat with a delay of 200 but schedule with a delay of 50. The schedule wins. */
    @Test
    fun executeScheduledEarlierReplacesRepeatedLater() {
        val task =
            object : Task.ScheduledTask("task", true) {
                val schedules = mutableListOf(50.µs)
                val delays = mutableListOf(200.µs, -1)

                override fun runOnce(): Long {
                    log += "run@${taskFaker.nanoTime}"
                    if (schedules.isNotEmpty()) {
                        redQueue.schedule(this, schedules.removeAt(0))
                    }
                    return delays.removeAt(0)
                }
            }
        redQueue.schedule(task, 100.µs)

        taskFaker.advanceUntil(0.µs)
        assertThat(log).isEmpty()

        taskFaker.advanceUntil(100.µs)
        assertThat(log).containsExactly("run@100000")

        taskFaker.advanceUntil(150.µs)
        assertThat(log).containsExactly("run@100000", "run@150000")

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task",
            "TRACE: Q10000 starting              : task",
            "TRACE: Q10000 scheduled after  50 µs: task",
            "TRACE: Q10000 finished run in   0 µs: task",
            "TRACE: Q10000 already scheduled     : task",
            "TRACE: Q10000 starting              : task",
            "TRACE: Q10000 finished run in   0 µs: task",
        )
    }

    /** Schedule with a delay of 200 but repeat with a delay of 50. The repeat wins. */
    @Test
    fun executeRepeatedEarlierReplacesScheduledLater() {
        val task =
            object : Task.ScheduledTask("task", true) {
                val schedules = mutableListOf(200.µs)
                val delays = mutableListOf(50.µs, -1L)

                override fun runOnce(): Long {
                    log += "run@${taskFaker.nanoTime}"
                    if (schedules.isNotEmpty()) {
                        redQueue.schedule(this, schedules.removeAt(0))
                    }
                    return delays.removeAt(0)
                }
            }
        redQueue.schedule(task, 100.µs)

        taskFaker.advanceUntil(0.µs)
        assertThat(log).isEmpty()

        taskFaker.advanceUntil(100.µs)
        assertThat(log).containsExactly("run@100000")

        taskFaker.advanceUntil(150.µs)
        assertThat(log).containsExactly("run@100000", "run@150000")

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task",
            "TRACE: Q10000 starting              : task",
            "TRACE: Q10000 scheduled after 200 µs: task",
            "TRACE: Q10000 finished run in   0 µs: task",
            "TRACE: Q10000 run again after  50 µs: task",
            "TRACE: Q10000 starting              : task",
            "TRACE: Q10000 finished run in   0 µs: task",
        )
    }

    @Test
    fun cancelReturnsTruePreventsNextExecution() {
        redQueue.schedule("task", 100.µs) {
            log += "run@${taskFaker.nanoTime}"
            -1L
        }

        taskFaker.advanceUntil(0.µs)
        assertThat(log).isEmpty()

        redQueue.cancelAll()

        taskFaker.advanceUntil(99.µs)
        assertThat(log).isEmpty()

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task",
            "TRACE: Q10000 canceled              : task",
        )
    }

    @Test
    fun cancelReturnsFalseDoesNotCancel() {
        redQueue.schedule(
            object : Task.ScheduledTask("task", false) {
                override fun runOnce(): Long {
                    log += "run@${taskFaker.nanoTime}"
                    return -1L
                }
            },
            100.µs,
        )

        taskFaker.advanceUntil(0.µs)
        assertThat(log).isEmpty()

        redQueue.cancelAll()

        taskFaker.advanceUntil(99.µs)
        assertThat(log).isEmpty()

        taskFaker.advanceUntil(100.µs)
        assertThat(log).containsExactly("run@100000")

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task",
            "TRACE: Q10000 starting              : task",
            "TRACE: Q10000 finished run in   0 µs: task",
        )
    }

    @Test
    fun cancelWhileExecutingPreventsRepeat() {
        redQueue.schedule("task", 100.µs) {
            log += "run@${taskFaker.nanoTime}"
            redQueue.cancelAll()
            return@schedule 100.µs
        }

        taskFaker.advanceUntil(0.µs)
        assertThat(log).isEmpty()

        taskFaker.advanceUntil(100.µs)
        assertThat(log).containsExactly("run@100000")

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task",
            "TRACE: Q10000 starting              : task",
            "TRACE: Q10000 finished run in   0 µs: task",
        )
    }

    @Test
    fun cancelWhileExecutingDoesNothingIfTaskDoesNotRepeat() {
        redQueue.schedule("task", 100.µs) {
            log += "run@${taskFaker.nanoTime}"
            redQueue.cancelAll()
            -1L
        }

        taskFaker.advanceUntil(0.µs)
        assertThat(log).isEmpty()

        taskFaker.advanceUntil(100.µs)
        assertThat(log).containsExactly("run@100000")

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task",
            "TRACE: Q10000 starting              : task",
            "TRACE: Q10000 finished run in   0 µs: task",
        )
    }

    @Test
    fun cancelWhileExecutingDoesNotStopUncancelableTask() {
        redQueue.schedule(
            object : Task.ScheduledTask("task", false) {
                val delays = mutableListOf(50.µs, -1L)

                override fun runOnce(): Long {
                    log += "run@${taskFaker.nanoTime}"
                    redQueue.cancelAll()
                    return delays.removeAt(0)
                }
            },
            100.µs,
        )

        taskFaker.advanceUntil(0.µs)
        assertThat(log).isEmpty()

        taskFaker.advanceUntil(100.µs)
        assertThat(log).containsExactly("run@100000")

        taskFaker.advanceUntil(150.µs)
        assertThat(log).containsExactly("run@100000", "run@150000")

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task",
            "TRACE: Q10000 starting              : task",
            "TRACE: Q10000 finished run in   0 µs: task",
            "TRACE: Q10000 run again after  50 µs: task",
            "TRACE: Q10000 starting              : task",
            "TRACE: Q10000 finished run in   0 µs: task",
        )
    }

    @Test
    fun interruptingCoordinatorAttemptsToCancelsAndSucceeds() {
        redQueue.schedule("task", 100.µs) {
            log += "run@${taskFaker.nanoTime}"
            -1L
        }

        taskFaker.advanceUntil(0.µs)
        assertThat(log).isEmpty()

        taskFaker.interruptCoordinatorThread()

        taskFaker.advanceUntil(0.µs)
        assertThat(log).isEmpty()

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task",
            "TRACE: Q10000 canceled              : task",
        )
    }

    @Test
    fun interruptingCoordinatorAttemptsToCancelsAndFails() {
        redQueue.schedule(
            object : Task.ScheduledTask("task", false) {
                override fun runOnce(): Long {
                    log += "run@${taskFaker.nanoTime}"
                    return -1L
                }
            },
            100.µs,
        )

        taskFaker.advanceUntil(0.µs)
        assertThat(log).isEmpty()

        taskFaker.interruptCoordinatorThread()

        taskFaker.advanceUntil(0.µs)
        assertThat(log).isEmpty()

        taskFaker.advanceUntil(100.µs)
        assertThat(log).containsExactly("run@100000")

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task",
            "TRACE: Q10000 starting              : task",
            "TRACE: Q10000 finished run in   0 µs: task",
        )
    }

    /** Inspect how many runnables have been enqueued. If none then we're truly sequential. */
    @Test
    fun singleQueueIsSerial() {
        redQueue.schedule("task one", 100.µs) {
            log += "one:run@${taskFaker.nanoTime} parallel=${taskFaker.isParallel}"
            -1L
        }

        redQueue.schedule("task two", 100.µs) {
            log += "two:run@${taskFaker.nanoTime} parallel=${taskFaker.isParallel}"
            -1L
        }

        redQueue.schedule("task three", 100.µs) {
            log += "three:run@${taskFaker.nanoTime} parallel=${taskFaker.isParallel}"
            -1L
        }

        taskFaker.advanceUntil(0.µs)
        assertThat(log).isEmpty()

        taskFaker.advanceUntil(100.µs)
        assertThat(log).containsExactly(
            "one:run@100000 parallel=false",
            "two:run@100000 parallel=false",
            "three:run@100000 parallel=false",
        )

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task one",
            "TRACE: Q10000 scheduled after 100 µs: task two",
            "TRACE: Q10000 scheduled after 100 µs: task three",
            "TRACE: Q10000 starting              : task one",
            "TRACE: Q10000 finished run in   0 µs: task one",
            "TRACE: Q10000 starting              : task two",
            "TRACE: Q10000 finished run in   0 µs: task two",
            "TRACE: Q10000 starting              : task three",
            "TRACE: Q10000 finished run in   0 µs: task three",
        )
    }

    /** Inspect how many runnables have been enqueued. If non-zero then we're truly parallel. */
    @Test
    fun differentQueuesAreParallel() {
        redQueue.schedule("task one", 100.µs) {
            log += "one:run@${taskFaker.nanoTime} parallel=${taskFaker.isParallel}"
            -1L
        }

        blueQueue.schedule("task two", 100.µs) {
            log += "two:run@${taskFaker.nanoTime} parallel=${taskFaker.isParallel}"
            -1L
        }

        greenQueue.schedule("task three", 100.µs) {
            log += "three:run@${taskFaker.nanoTime} parallel=${taskFaker.isParallel}"
            -1L
        }

        taskFaker.advanceUntil(0.µs)
        assertThat(log).isEmpty()

        taskFaker.advanceUntil(100.µs)
        assertThat(log).containsExactlyInAnyOrder(
            "one:run@100000 parallel=true",
            "two:run@100000 parallel=true",
            "three:run@100000 parallel=true",
        )

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactlyInAnyOrder(
            "TRACE: Q10000 scheduled after 100 µs: task one",
            "TRACE: Q10001 scheduled after 100 µs: task two",
            "TRACE: Q10002 scheduled after 100 µs: task three",
            "TRACE: Q10000 starting              : task one",
            "TRACE: Q10000 finished run in   0 µs: task one",
            "TRACE: Q10001 starting              : task two",
            "TRACE: Q10001 finished run in   0 µs: task two",
            "TRACE: Q10002 starting              : task three",
            "TRACE: Q10002 finished run in   0 µs: task three",
        )
    }

    /** Test the introspection method [RealTaskQueue.scheduledTasks]. */
    @Test
    fun scheduledTasks() {
        redQueue.schedule("task one", 100.µs) {
            // Do nothing.
            -1L
        }

        redQueue.schedule("task two", 200.µs) {
            // Do nothing.
            -1L
        }

        assertThat(redQueue.scheduledTasks.toString())
            .isEqualTo("[ScheduledTask{name='task one', cancellable=true, queue=Q10000', nextExecuteNanoTime=100000}," +
                    " ScheduledTask{name='task two', cancellable=true, queue=Q10000', nextExecuteNanoTime=200000}]")

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task one",
            "TRACE: Q10000 scheduled after 200 µs: task two",
        )
    }

    /**
     * We don't track the active task in scheduled tasks. This behavior might be a mistake, but it's
     * cumbersome to implement properly because the active task might be a cancel.
     */
    @Test
    fun scheduledTasksDoesNotIncludeRunningTask() {
        val task =
            object : Task.ScheduledTask("task one", true) {
                val schedules = mutableListOf(200.µs)

                override fun runOnce(): Long {
                    if (schedules.isNotEmpty()) {
                        redQueue.schedule(this, schedules.removeAt(0)) // Add it at the end also.
                    }
                    log += "scheduledTasks=${redQueue.scheduledTasks}"
                    return -1L
                }
            }
        redQueue.schedule(task, 100.µs)

        redQueue.schedule("task two", 200.µs) {
            log += "scheduledTasks=${redQueue.scheduledTasks}"
            -1L
        }

        taskFaker.advanceUntil(100.µs)
        assertThat(log).containsExactly(
            "scheduledTasks=[" +
                    "ScheduledTask{name='task two', cancellable=true, queue=Q10000', nextExecuteNanoTime=200000}, " +
                    "ScheduledTask{name='task one', cancellable=true, queue=Q10000', nextExecuteNanoTime=300000}" +
                    "]",
        )

        taskFaker.advanceUntil(200.µs)
        assertThat(log).containsExactly(
            "scheduledTasks=[" +
                    "ScheduledTask{name='task two', cancellable=true, queue=Q10000', nextExecuteNanoTime=200000}, " +
                    "ScheduledTask{name='task one', cancellable=true, queue=Q10000', nextExecuteNanoTime=300000}" +
                    "]",
            "scheduledTasks=[" +
                    "ScheduledTask{name='task one', cancellable=true, queue=Q10000', nextExecuteNanoTime=300000}" +
                    "]",
        )

        taskFaker.advanceUntil(300.µs)
        assertThat(log).containsExactly(
            "scheduledTasks=[" +
                    "ScheduledTask{name='task two', cancellable=true, queue=Q10000', nextExecuteNanoTime=200000}, " +
                    "ScheduledTask{name='task one', cancellable=true, queue=Q10000', nextExecuteNanoTime=300000}" +
                    "]",
            "scheduledTasks=[" +
                    "ScheduledTask{name='task one', cancellable=true, queue=Q10000', nextExecuteNanoTime=300000}" +
                    "]",
            "scheduledTasks=[]",
        )

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task one",
            "TRACE: Q10000 scheduled after 200 µs: task two",
            "TRACE: Q10000 starting              : task one",
            "TRACE: Q10000 scheduled after 200 µs: task one",
            "TRACE: Q10000 finished run in   0 µs: task one",
            "TRACE: Q10000 starting              : task two",
            "TRACE: Q10000 finished run in   0 µs: task two",
            "TRACE: Q10000 starting              : task one",
            "TRACE: Q10000 finished run in   0 µs: task one",
        )
    }

    @Test
    fun taskNameIsUsedForThreadNameWhenRunning() {
        redQueue.execute("lucky task", true) {
            log += "run threadName:${Thread.currentThread().name}"
        }

        taskFaker.advanceUntil(0.µs)
        assertThat(log).containsExactly("run threadName:lucky task")

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after   0 µs: lucky task",
            "TRACE: Q10000 starting              : lucky task",
            "TRACE: Q10000 finished run in   0 µs: lucky task",
        )
    }

    @Test
    fun shutdownSuccessfullyCancelsScheduledTasks() {
        redQueue.schedule("task", 100.µs) {
            log += "run@${taskFaker.nanoTime}"
            -1L
        }

        taskFaker.advanceUntil(0.µs)
        assertThat(log).isEmpty()

        redQueue.shutdown()

        taskFaker.advanceUntil(99.µs)
        assertThat(log).isEmpty()

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task",
            "TRACE: Q10000 canceled              : task",
        )
    }

    @Test
    fun shutdownFailsToCancelsScheduledTasks() {
        redQueue.schedule(
            object : Task.ScheduledTask("task", false) {
                override fun runOnce(): Long {
                    log += "run@${taskFaker.nanoTime}"
                    return 50.µs
                }
            },
            100.µs,
        )

        taskFaker.advanceUntil(0.µs)
        assertThat(log).isEmpty()

        redQueue.shutdown()

        taskFaker.advanceUntil(99.µs)
        assertThat(log).isEmpty()

        taskFaker.advanceUntil(100.µs)
        assertThat(log).containsExactly("run@100000")

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 scheduled after 100 µs: task",
            "TRACE: Q10000 starting              : task",
            "TRACE: Q10000 finished run in   0 µs: task",
        )
    }

    @Test
    fun scheduleDiscardsTaskWhenShutdown() {
        redQueue.shutdown()

        redQueue.schedule("task", 100.µs) {
            // Do nothing.
            -1L
        }

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 schedule canceled (queue is shutdown): task",
        )
    }

    @Test
    fun scheduleThrowsWhenShutdown() {
        redQueue.shutdown()

        assertFailsWith<RejectedExecutionException> {
            redQueue.schedule(
                object : Task.ScheduledTask("task", false) {
                    override fun runOnce(): Long {
                        return -1L
                    }
                },
                100.µs,
            )
        }

        taskFaker.assertNoMoreTasks()

        assertThat(testLogHandler.takeAll()).containsExactly(
            "TRACE: Q10000 schedule failed (queue is shutdown): task",
        )
    }

    @Test
    fun idleLatch() {
        redQueue.schedule("task", 0L) {
            log += "run@${taskFaker.nanoTime}"
            -1L
        }

        val idleLatch = redQueue.idleLatch()
        assertThat(idleLatch.count).isEqualTo(1)

        taskFaker.advanceUntil(0.µs)
        assertThat(log).containsExactly("run@0")

        assertThat(idleLatch.count).isEqualTo(0)
    }

    @Test
    fun multipleCallsToIdleLatchReturnSameInstance() {
        redQueue.schedule("task", 0L) {
            log += "run@${taskFaker.nanoTime}"
            -1L
        }

        val idleLatch1 = redQueue.idleLatch()
        val idleLatch2 = redQueue.idleLatch()
        assertThat(idleLatch2).isSameAs(idleLatch1)
    }

    @Test
    fun cancelAllWhenEmptyDoesNotStartWorkerThread() {
        redQueue.schedule("red task", 100.µs) {
            error("expected to be canceled")
        }
        assertThat(taskFaker.executeCallCount).isEqualTo(1)

        blueQueue.schedule("task", 100.µs) {
            error("expected to be canceled")
        }
        assertThat(taskFaker.executeCallCount).isEqualTo(1)

        redQueue.cancelAll()
        assertThat(taskFaker.executeCallCount).isEqualTo(1)

        blueQueue.cancelAll()
        assertThat(taskFaker.executeCallCount).isEqualTo(1)
    }

    @Test
    fun noMoreThanOneWorkerThreadWaitingToStartAtATime() {
        // Enqueueing the red task starts a thread because the head of the queue changed.
        redQueue.execute("red task", true) {
            log += "red:starting@${taskFaker.nanoTime}"
            taskFaker.sleep(100.µs)
            log += "red:finishing@${taskFaker.nanoTime}"
        }
        assertThat(taskFaker.executeCallCount).isEqualTo(1)

        // Enqueueing the blue task doesn't start a thread because the red one is still starting.
        blueQueue.execute("blue task", true) {
            log += "blue:starting@${taskFaker.nanoTime}"
            taskFaker.sleep(100.µs)
            log += "blue:finishing@${taskFaker.nanoTime}"
        }
        assertThat(taskFaker.executeCallCount).isEqualTo(1)

        // Running the red task starts another thread, so the two can run in parallel.
        taskFaker.runNextTask()
        assertThat(log).containsExactly("red:starting@0")
        assertThat(taskFaker.executeCallCount).isEqualTo(2)

        // Next the blue task starts.
        taskFaker.runNextTask()
        assertThat(log).containsExactly(
            "red:starting@0",
            "blue:starting@0",
        )
        assertThat(taskFaker.executeCallCount).isEqualTo(2)

        // Advance time until the tasks complete.
        taskFaker.advanceUntil(100.µs)
        assertThat(log).containsExactly(
            "red:starting@0",
            "blue:starting@0",
            "red:finishing@100000",
            "blue:finishing@100000",
        )
        taskFaker.assertNoMoreTasks()
        assertThat(taskFaker.executeCallCount).isEqualTo(2)
    }

    @Test
    fun onlyOneCoordinatorWaitingToStartFutureTasks() {
        // Enqueueing the red task starts a coordinator thread.
        redQueue.schedule("red task", 100.µs) {
            log += "red:run@${taskFaker.nanoTime}"
            -1L
        }
        assertThat(taskFaker.executeCallCount).isEqualTo(1)

        // Enqueueing the blue task doesn't need a 2nd coordinator yet.
        blueQueue.schedule("blue task", 200.µs) {
            log += "blue:run@${taskFaker.nanoTime}"
            -1L
        }
        assertThat(taskFaker.executeCallCount).isEqualTo(1)

        // Nothing to do.
        taskFaker.runTasks()
        assertThat(log).isEmpty()

        // At 100.µs, the coordinator runs the red task and starts a thread for the new coordinator.
        taskFaker.advanceUntil(100.µs)
        assertThat(log).containsExactly("red:run@100000")
        assertThat(taskFaker.executeCallCount).isEqualTo(2)

        // At 200.µs, the blue task runs.
        taskFaker.advanceUntil(200.µs)
        assertThat(log).containsExactly("red:run@100000", "blue:run@200000")
        assertThat(taskFaker.executeCallCount).isEqualTo(2)

        taskFaker.assertNoMoreTasks()
    }

    private val Int.µs: Long
        get() = this * 1_000L
}
