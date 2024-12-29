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

package jayo.samples

import jayo.CancelScope
import jayo.cancelScope
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.system.measureTimeMillis

private class DiceWithLock {
    private val random = Random()
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(0)
    private val lock: Lock = ReentrantLock()
    private val condition: Condition = lock.newCondition()
    private var total = 0

    private fun roll() {
        lock.withLock {
            val roll = random.nextInt(1, 7)
            println("Rolled: $roll")
            total += roll
            condition.signalAll()
        }
    }

    fun rollAtFixedRate(period: Int, timeUnit: TimeUnit) {
        scheduler.scheduleAtFixedRate({ this.roll() }, period.toLong(), period.toLong(), timeUnit)
    }

    fun awaitTotal(cancelScope: CancelScope, toReach: Int) {
        val elapsed = measureTimeMillis {
            while (total < toReach) {
                lock.withLock {
                    cancelScope.awaitSignal(condition)
                    println("A roll was done, total: $total")
                }
            }
        }
        scheduler.shutdownNow()
        println("Finished in $elapsed ms ! Expected: $toReach, final total: $total")
    }
}

fun main() {
    val dice = DiceWithLock()
    // start the game ! Will throw the dice every 500 millis
    dice.rollAtFixedRate(500, TimeUnit.MILLISECONDS)
    // wait until total dice rolls reaches 42
    cancelScope { dice.awaitTotal(this, 42) }
}