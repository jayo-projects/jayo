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

package jayo.scheduler.internal

import jayo.scheduler.TaskRunner
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.lang.Thread.UncaughtExceptionHandler
import java.time.Duration
import java.util.concurrent.*

/**
 * Integration test to confirm that [RealTaskRunner] works with a real backend. Business logic is all exercised by
 * [TaskRunnerTest].
 *
 * This test is doing real sleeping with tolerances of 250 ms. Hopefully that's enough for even the busiest of CI
 * servers.
 */
class TaskRunnerRealBackendTest {
    private val log = LinkedBlockingDeque<String>()

    private val loggingUncaughtExceptionHandler =
        UncaughtExceptionHandler { _, throwable ->
            log.put("uncaught exception: $throwable")
        }

    private val threadFactory =
        ThreadFactory { runnable ->
            Thread(runnable, "TaskRunnerRealBackendTest").apply {
                isDaemon = true
                uncaughtExceptionHandler = loggingUncaughtExceptionHandler
            }
        }

    private val executor = Executors.newCachedThreadPool(threadFactory)
    private val taskRunner = TaskRunner.create(executor)

    @AfterEach
    fun tearDown() {
        taskRunner.shutdown(Duration.ZERO)
        executor.shutdown()
    }

    @Test
    fun testQueueSchedule() {
        val queue = taskRunner.newScheduledQueue()
        assertThat(queue.name).isEqualTo("Q10000")
        val t1 = System.nanoTime() / 1e6

        val delays = mutableListOf(TimeUnit.MILLISECONDS.toNanos(1000), -1L)
        queue.schedule("task", TimeUnit.MILLISECONDS.toNanos(750)) {
            log.put("runOnce delays.size=${delays.size}")
            return@schedule delays.removeAt(0)
        }

        assertThat(log.take()).isEqualTo("runOnce delays.size=2")
        val t2 = System.nanoTime() / 1e6 - t1
        assertThat(t2).isCloseTo(750.0, offset(250.0))

        assertThat(log.take()).isEqualTo("runOnce delays.size=1")
        val t3 = System.nanoTime() / 1e6 - t1
        assertThat(t3).isCloseTo(1750.0, offset(250.0))
        assertThat(log).isEmpty()

        queue.shutdown()
    }

    @Test
    fun testQueueExecute() {
        val queue = taskRunner.newQueue()
        val t1 = System.nanoTime() / 1e6

        queue.execute("task", true) {
            log.put("runOnce")
        }

        assertThat(log.take()).isEqualTo("runOnce")
        val t2 = System.nanoTime() / 1e6 - t1
        assertThat(t2).isCloseTo(0.0, offset(250.0))
        assertThat(log).isEmpty()

        queue.shutdown()
    }

    @Test
    fun testSingleExecute() {
        val t1 = System.nanoTime() / 1e6

        taskRunner.execute(true) {
            log.put("runOnce")
        }

        assertThat(log.take()).isEqualTo("runOnce")
        val t2 = System.nanoTime() / 1e6 - t1
        assertThat(t2).isCloseTo(0.0, offset(250.0))
        assertThat(log).isEmpty()
    }

    @Test
    fun taskFailsWithUncheckedException() {
        val queue = taskRunner.newScheduledQueue()
        queue.schedule("task", TimeUnit.MILLISECONDS.toNanos(100)) {
            log.put("failing task running")
            throw RuntimeException("boom!")
        }

        queue.schedule("task", TimeUnit.MILLISECONDS.toNanos(200)) {
            log.put("normal task running")
            return@schedule -1L
        }

        queue.idleLatch().await(500, TimeUnit.MILLISECONDS)

        assertThat(log.take()).isEqualTo("failing task running")
        assertThat(log.take()).isEqualTo("uncaught exception: java.lang.RuntimeException: boom!")
        assertThat(log.take()).isEqualTo("normal task running")
        assertThat(log).isEmpty()

        queue.shutdown()
    }

    @Tag("no-ci")
    @Test
    fun idleLatchAfterShutdown() {
        val queue = taskRunner.newQueue()
        queue.execute("task", true) {
            Thread.sleep(100)
            taskRunner.shutdown(Duration.ofMillis(100))
        }

        assertThat(queue.idleLatch().count).isEqualTo(1)
        assertThat(queue.idleLatch().await(400L, TimeUnit.MILLISECONDS)).isFalse()
        assertThatThrownBy {
            assertThat(queue.idleLatch().count).isEqualTo(0)
        }.isInstanceOf(RejectedExecutionException::class.java)
        assertThat(log).isEmpty()

        queue.shutdown()
    }

    @Test
    fun queueWait() {
        val queue = taskRunner.newQueue()
        val countDownLatch = CountDownLatch(2)

        queue.execute("task1", true) {
            Thread.sleep(200)
            countDownLatch.countDown()
        }
        queue.execute("task2", true) {
            Thread.sleep(200)
            countDownLatch.countDown()
        }

        val result = countDownLatch.await(500, TimeUnit.MILLISECONDS)
        assertThat(result).isTrue
    }

    @Test
    fun queueShutdown() {
        val queue = taskRunner.newQueue()
        val countDownLatch = CountDownLatch(2)

        queue.execute("task1", true) {
            Thread.sleep(200)
            countDownLatch.countDown()
        }
        queue.execute("task2", true) {
            Thread.sleep(200)
            countDownLatch.countDown()
        }

        queue.shutdown()

        val result = countDownLatch.await(500, TimeUnit.MILLISECONDS)
        assertThat(result).isFalse
    }
}
