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

package jayo.internal;

import jayo.Cancellable;
import jayo.external.AsyncTimeout;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This test uses four timeouts of varying durations: 250ms, 500ms, 750ms and
 * 1000ms, named 'a', 'b', 'c' and 'd'.
 */
@Tag("no-ci")
public final class AsyncTimeoutTest {
    private final BlockingDeque<AsyncTimeout> timedOut = new LinkedBlockingDeque<>();
    private final AsyncTimeout a = recordingAsyncTimeout();
    private final AsyncTimeout b = recordingAsyncTimeout();
    private final AsyncTimeout c = recordingAsyncTimeout();
    private final AsyncTimeout d = recordingAsyncTimeout();

    @Test
    public void noTimeoutRun() {
        AsyncTimeout timeout = recordingAsyncTimeout();
        // with cancel scope but no timeout
        Cancellable.create().run(cancelScope -> {
            timeout.enter(cancelScope);
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertFalse(timeout.exit());
            assertTimedOut();
        });
    }

    @Test
    public void noTimeoutCall() {
        AsyncTimeout timeout = recordingAsyncTimeout();
        // with cancel scope but no timeout
        Cancellable.create().call(cancelScope -> {
            timeout.enter(cancelScope);
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertFalse(timeout.exit());
            assertTimedOut();
            return true;
        });
    }

    @Test
    public void singleInstanceTimedOutRun() {
        Cancellable.runWithTimeout(Duration.ofMillis(25), cancelScope -> {
            a.enter(cancelScope);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(a.exit());
            assertTimedOut(a);
        });
    }

    @Test
    public void singleInstanceTimedOutCall() {
        Cancellable.callWithTimeout(Duration.ofMillis(25), cancelScope -> {
            a.enter(cancelScope);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(a.exit());
            assertTimedOut(a);
            return true;
        });
    }

    @Test
    public void singleInstanceNotTimedOutRun() {
        Cancellable.runWithTimeout(Duration.ofMillis(50), cancelScope -> {
            b.enter(cancelScope);
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            b.exit();
            assertFalse(b.exit());
            assertTimedOut();
        });
    }

    @Test
    public void instancesAddedAtEnd() {
        Cancellable.runWithTimeout(Duration.ofMillis(100), cancelScope1 -> {
            a.enter(cancelScope1);
            b.enter(cancelScope1);
            c.enter(cancelScope1);
            d.enter(cancelScope1);
            Cancellable.runWithTimeout(Duration.ofMillis(75), cancelScope2 ->
                    Cancellable.runWithTimeout(Duration.ofMillis(50), cancelScope3 ->
                            Cancellable.runWithTimeout(Duration.ofMillis(25), cancelScope4 -> {
                        try {
                            Thread.sleep(125);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    })));
        });
        assertTrue(a.exit());
        assertTrue(b.exit());
        assertTrue(c.exit());
        assertTrue(d.exit());
        assertTimedOut(a, b, c, d);
    }

    @Test
    public void instancesRemovedAtFront() {
        Cancellable.create().run(cancelScope -> {
            a.enter(cancelScope);
            b.enter(cancelScope);
            c.enter(cancelScope);
            d.enter(cancelScope);
        });
        assertFalse(a.exit());
        assertFalse(b.exit());
        assertFalse(c.exit());
        assertFalse(d.exit());
        assertTimedOut();
    }

    @Test
    public void doubleEnter() {
        Cancellable.runWithTimeout(Duration.ofMillis(25), cancelScope -> {
            a.enter(cancelScope);
            assertThatThrownBy(() -> a.enter(cancelScope))
                    .isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    public void reEnter() {
        Cancellable.runWithTimeout(Duration.ofSeconds(1), cancelScope -> {
            a.enter(cancelScope);
            assertFalse(a.exit());
            a.enter(cancelScope);
            assertFalse(a.exit());
        });
    }

    @Test
    public void reEnterAfterTimeout() {
        Cancellable.runWithTimeout(Duration.ofSeconds(1), cancelScope -> {
            a.enter(cancelScope);
            try {
                assertSame(a, timedOut.take());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(a.exit());
            a.enter(cancelScope);
            assertFalse(a.exit());
        });
    }

    @Test
    public void deadlineOnly() {
        final var builder = Cancellable.builder();
        builder.deadline(Duration.ofMillis(25));
        builder.build().run(cancelScope -> {
            final var timeout = recordingAsyncTimeout();
            timeout.enter(cancelScope);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(timeout.exit());
            assertTimedOut(timeout);
        });
    }

    @Test
    public void deadlineBeforeTimeout() {
        final var timeout = recordingAsyncTimeout();
        final var builder = Cancellable.builder();
        builder.timeout(Duration.ofMillis(75));
        builder.deadline(Duration.ofMillis(25));
        builder.build().run(cancelScope -> {
            timeout.enter(cancelScope);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(timeout.exit());
            assertTimedOut(timeout);
        });
    }

    @Test
    public void deadlineAfterTimeout() {
        final var timeout = recordingAsyncTimeout();
        final var builder = Cancellable.builder();
        builder.timeout(Duration.ofMillis(25));
        builder.deadline(Duration.ofMillis(75));
        builder.build().run(cancelScope -> {
            timeout.enter(cancelScope);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(timeout.exit());
            assertTimedOut(timeout);
        });
    }

    @Test
    public void deadlineStartsBeforeEnter() {
        final var timeout = recordingAsyncTimeout();
        final var builder = Cancellable.builder();
        builder.deadline(Duration.ofMillis(50));
        builder.build().run(cancelScope -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            timeout.enter(cancelScope);
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(timeout.exit());
            assertTimedOut(timeout);
        });
    }

    @Test
    public void shortDeadlineReached() {
        final var timeout = recordingAsyncTimeout();
        final var builder = Cancellable.builder();
        builder.deadline(Duration.ofNanos(1));
        builder.build().run(cancelScope -> {
            timeout.enter(cancelScope);
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(timeout.exit());
            assertTimedOut(timeout);
        });
    }

    /**
     * Asserts which timeouts fired, and in which order.
     */
    private void assertTimedOut(AsyncTimeout... expected) {
        assertEquals(List.of(expected), List.copyOf(timedOut));
    }

    private AsyncTimeout recordingAsyncTimeout() {
        AtomicReference<AsyncTimeout> asyncTimeoutRef = new AtomicReference<>();
        final var asyncTimeout = AsyncTimeout.create(() -> timedOut.add(asyncTimeoutRef.get()));
        asyncTimeoutRef.set(asyncTimeout);
        return asyncTimeout;
    }
}
