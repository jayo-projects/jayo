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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo;

import org.jspecify.annotations.NonNull;
import jayo.exceptions.JayoCancelledException;
import jayo.internal.RealCancelToken;

import java.util.concurrent.locks.Condition;

/**
 * Defines a scope for cancellable blocks. Every <b>cancellable builder</b> (like
 * {@linkplain Cancellable#withTimeout withTimeout}, {@linkplain Cancellable#withCancellable executeCancellable},
 * etc.) creates a {@code CancelScope} implementation that is available to manually {@link #cancel} code execution, or
 * {@link #shield()} its content from outside existing cancellations.
 */
public sealed interface CancelScope permits RealCancelToken {
    /**
     * Triggers an immediate cancellation.
     */
    void cancel();

    /**
     * Allow to protect the rest of this cancellable scope from outside existing cancellations, if any.
     */
    void shield();

    /**
     * Waits on {@code condition} until it is signaled. Throws a {@link JayoCancelledException} if either the thread
     * is interrupted or if this cancel scope elapses before {@code condition} is signaled.
     * The caller must hold the lock that condition is bound to.
     * <p>
     * Here's a sample class that uses {@code awaitSignal()} to await a specific state. Note that the call is made
     * within a loop to avoid unnecessary waiting and to mitigate spurious notifications.
     * <pre>
     * {@code
     * public final class Dice {
     *   private final Random random = new Random();
     *   private int latestTotal;
     *
     *   private final Lock lock = new ReentrantLock();
     *   private final Condition condition = lock.newCondition();
     *
     *   public void roll() {
     *     lock.withLock {
     *       latestTotal = 2 + random.nextInt(6) + random.nextInt(6);
     *       System.out.println("Rolled " + latestTotal);
     *       condition.signalAll();
     *     }
     *   }
     *
     *   public void rollAtFixedRate(final int period, TimeUnit timeUnit) {
     *     Executors.newScheduledThreadPool(0).scheduleAtFixedRate(new Runnable() {
     *       public void run() {
     *         roll();
     *       }
     *     }, 0, period, timeUnit);
     *   }
     *
     *   public void awaitTotal(CancelScope cancelScope, final int total) {
     *     lock.withLock {
     *       while (latestTotal != total) {
     *         cancelScope.awaitSignal(this);
     *       }
     *     }
     *   }
     * }
     * }
     * </pre>
     */
    void awaitSignal(final @NonNull Condition condition);

    /**
     * Waits on {@code monitor} until it is notified. Throws {@link JayoCancelledException} if either the thread is
     * interrupted or if this cancel scope elapses before {@code monitor} is notified. The caller must be synchronized
     * on {@code monitor}.
     * <p>
     * Here's a sample class that uses {@code waitUntilNotified()} to await a specific state. Note that the call is
     * made within a loop to avoid unnecessary waiting and to mitigate spurious notifications.
     * <pre>
     * {@code
     * public final class Dice {
     *   private final Random random = new Random();
     *   private int latestTotal;
     *
     *   public synchronized void roll() {
     *     latestTotal = 2 + random.nextInt(6) + random.nextInt(6);
     *     System.out.println("Rolled " + latestTotal);
     *     notifyAll();
     *   }
     *
     *   public void rollAtFixedRate(final int period, TimeUnit timeUnit) {
     *     Executors.newScheduledThreadPool(0).scheduleAtFixedRate(new Runnable() {
     *       public void run() {
     *         roll();
     *       }
     *     }, 0, period, timeUnit);
     *   }
     *
     *   public synchronized void awaitTotal(CancelScope cancelScope, final int total) {
     *     while (latestTotal != total) {
     *       cancelScope.waitUntilNotified(this);
     *     }
     *   }
     * }
     * }
     * </pre>
     *
     * @apiNote Prefer using {@code j.u.c.locks.Lock} over synchronized blocks for a better virtual thread integration.
     * @see #awaitSignal(Condition)
     */
    void waitUntilNotified(final @NonNull Object monitor);
}
