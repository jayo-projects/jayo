/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 * 
 * Forked from Okio (https://github.com/square/okio), original copyright is below
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

package jayo.internal

import jayo.JayoInterruptedIOException
import jayo.JayoTimeoutException
import jayo.cancelScope
import jayo.internal.TestUtil.assumeNotWindows
import jayo.tools.CancelToken
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.time.Duration.Companion.milliseconds

@Tag("no-ci")
class AwaitMonitorTest {
    private val monitor = Object()

    companion object {
        @JvmStatic
        fun parameters(): Stream<Arguments>? {
            return Stream.of(
                Arguments.of(ExecutorFactory.SINGLE_EXECUTOR_FACTORY, "SingleExecutorFactory"),
                Arguments.of(ExecutorFactory.PLATFORM_SCHEDULED_EXECUTOR_FACTORY, "PlatformScheduledExecutorFactory"),
                Arguments.of(ExecutorFactory.VIRTUAL_SCHEDULED_EXECUTOR_FACTORY, "VirtualScheduledExecutorFactory"),
            )
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun signaled(factory: ExecutorFactory) = synchronized(monitor) {
        val scheduledExecutorService = factory.newScheduledExecutorService()
        cancelScope(500.milliseconds) {
            val start = now()
            scheduledExecutorService.schedule(
                {
                    synchronized(monitor) {
                        monitor.notify()
                    }
                },
                100,
                TimeUnit.MILLISECONDS,
            )
            waitUntilNotified(monitor)
            assertElapsed(100.0, start)
        }
        scheduledExecutorService.shutdown()
    }

    @Test
    fun timeout() = synchronized(monitor) {
        assumeNotWindows()
        cancelScope(100.milliseconds) {
            val start = now()
            assertThatThrownBy { waitUntilNotified(monitor) }
                .isInstanceOf(JayoTimeoutException::class.java)
                .hasMessage("timeout")
            assertElapsed(100.0, start)
        }
    }

    @Test
    fun timeoutAlreadyReached() = synchronized(monitor) {
        assumeNotWindows()
        cancelScope(1.milliseconds) {
            val start = now()
            assertThatThrownBy { waitUntilNotified(monitor) }
                .isInstanceOf(JayoTimeoutException::class.java)
                .hasMessage("timeout")
            assertElapsed(0.0, start)
        }
    }

    @Test
    fun threadInterrupted() = synchronized(monitor) {
        assumeNotWindows()
        val start = now()
        Thread.currentThread().interrupt()
        cancelScope {
            assertThatThrownBy { waitUntilNotified(monitor) }
                .isInstanceOf(JayoInterruptedIOException::class.java)
                .isNotInstanceOf(JayoTimeoutException::class.java)
                .hasMessage("current thread is interrupted")
        }
        assertThat(Thread.interrupted()).isTrue
        assertElapsed(25.0, start, true)
    }

    @Test
    fun threadInterruptedOnThrowIfReached() = synchronized<Unit>(monitor) {
        assumeNotWindows()
        Thread.currentThread().interrupt()
        assertThatThrownBy { CancelToken.throwIfReached(null) }
            .isInstanceOf(JayoInterruptedIOException::class.java)
            .isNotInstanceOf(JayoTimeoutException::class.java)
            .hasMessage("current thread is interrupted")
        assertThat(Thread.interrupted()).isTrue
    }

    /** Returns the nanotime in milliseconds as a double for measuring timeouts.  */
    private fun now(): Double {
        return System.nanoTime() / 1000000.0
    }

    /**
     * Fails the test unless the time from start until now is duration, accepting differences in
     * -50..+450 milliseconds.
     */
    private fun assertElapsed(duration: Double, start: Double, extraDiffAllowed: Boolean = false) {
        val offset = if (extraDiffAllowed) within(60.0) else within(25.0)
        assertThat(now() - start - 20.0).isEqualTo(duration, offset)
    }
}
