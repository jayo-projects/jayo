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

import jayo.cancelScope
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.nanoseconds

class TimeoutTest {

    companion object {
        val smallerNanos = TimeUnit.MILLISECONDS.toNanos(500L).nanoseconds
        val biggerNanos = TimeUnit.MILLISECONDS.toNanos(1500L).nanoseconds
    }

    @Test
    fun noTimeout() {
        val result = cancelScope { 42 }
        assertThat(result).isEqualTo(42)
    }

    @Test
    fun intersectWithPrefersSmallerDeadline() {
        var cancelToken: RealCancelToken?
        cancelScope(smallerNanos) {
            val expectedDeadlineNanoTime = System.nanoTime() + smallerNanos.inWholeNanoseconds
            cancelToken = JavaVersionUtils.getCancelToken()
            assertThat(cancelToken!!.deadlineNanoTime).isCloseTo(expectedDeadlineNanoTime, offset(1_000_000))
            cancelScope(biggerNanos) {
                cancelToken = JavaVersionUtils.getCancelToken()
                assertThat(cancelToken!!.deadlineNanoTime).isCloseTo(expectedDeadlineNanoTime, offset(1_000_000))
            }
            cancelToken = JavaVersionUtils.getCancelToken()
            assertThat(cancelToken!!.deadlineNanoTime).isCloseTo(expectedDeadlineNanoTime, offset(1_000_000))
        }

        cancelToken = JavaVersionUtils.getCancelToken()
        assertThat(cancelToken).isNull()

        cancelScope(biggerNanos) {
            val expectedDeadlineNanoTime = System.nanoTime() + biggerNanos.inWholeNanoseconds
            cancelToken = JavaVersionUtils.getCancelToken()
            assertThat(cancelToken!!.deadlineNanoTime).isCloseTo(expectedDeadlineNanoTime, offset(350_000))
            cancelScope(smallerNanos) {
                val expectedDeadlineNanoTime2 = System.nanoTime() + smallerNanos.inWholeNanoseconds
                cancelToken = JavaVersionUtils.getCancelToken()
                assertThat(cancelToken!!.deadlineNanoTime).isCloseTo(expectedDeadlineNanoTime2, offset(100_000))
            }
            cancelToken = JavaVersionUtils.getCancelToken()
            assertThat(cancelToken!!.deadlineNanoTime).isCloseTo(expectedDeadlineNanoTime, offset(1_000_000))
        }
    }

    @Test
    fun intersectWithPrefersNonZeroDeadline() {
        var cancelToken: RealCancelToken?
        cancelScope {
            cancelToken = JavaVersionUtils.getCancelToken()
            assertThat(cancelToken!!.deadlineNanoTime).isEqualTo(0L)
            cancelScope(biggerNanos) {
                val expectedDeadlineNanoTime = System.nanoTime() + biggerNanos.inWholeNanoseconds
                cancelToken = JavaVersionUtils.getCancelToken()
                assertThat(cancelToken!!.deadlineNanoTime).isCloseTo(expectedDeadlineNanoTime, offset(350_000))
            }
            cancelToken = JavaVersionUtils.getCancelToken()
            assertThat(cancelToken!!.deadlineNanoTime).isEqualTo(0L)
        }
    }

    @Test
    fun shield() {
        var cancelToken: RealCancelToken?
        cancelScope(biggerNanos) {
            cancelToken = JavaVersionUtils.getCancelToken()
            assertThat(cancelToken!!.shielded).isFalse()
            shield()
            cancelToken = JavaVersionUtils.getCancelToken()
            assertThat(cancelToken).isNull()
        }
    }
}
