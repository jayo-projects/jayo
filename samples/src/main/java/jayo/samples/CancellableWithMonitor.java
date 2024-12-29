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

package jayo.samples;

import jayo.CancelScope;
import jayo.Cancellable;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class CancellableWithMonitor {

    private static final class Dice {
        private final Random random = new Random();
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(0);
        private int total = 0;

        private synchronized void roll() {
            int roll = random.nextInt(1, 7);
            System.out.println("Rolled: " + roll);
            total += roll;
            notifyAll();
        }

        public void rollAtFixedRate(int period, TimeUnit timeUnit) {
            scheduler.scheduleAtFixedRate(this::roll, period, period, timeUnit);
        }

        public synchronized void awaitTotal(CancelScope cancelScope, int toReach) {
            while (total < toReach) {
                cancelScope.waitUntilNotified(this);
                System.out.println("A roll was done, total: " + total);
            }
            scheduler.shutdownNow();
            System.out.println("Finished ! Expected: " + toReach + ", final total: " + total);
        }
    }

    public static void main(String... args) throws Exception {
        Dice dice = new Dice();
        // start the game ! Will throw the dice every 500 millis
        dice.rollAtFixedRate(500, TimeUnit.MILLISECONDS);
        // wait until total dice rolls reaches 42
        Cancellable.create().run(cancelScope -> dice.awaitTotal(cancelScope, 42));
    }
}
