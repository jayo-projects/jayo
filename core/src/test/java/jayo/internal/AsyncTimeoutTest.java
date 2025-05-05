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
import jayo.tools.AsyncTimeout;
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
 * This test uses four timeouts of varying durations: 250ms, 500ms, 750ms and 1000ms, named 'a', 'b', 'c' and 'd'.
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
        Cancellable.run(cancelScope -> {
            timeout.enter(0L);
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
        Cancellable.call(cancelScope -> {
            // provide a defaultTimeout to check it is ignored
            timeout.enter(1L);
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
        Cancellable.run(Duration.ofMillis(25), cancelScope -> {
            a.enter(0L);
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
        Cancellable.call(Duration.ofMillis(25), cancelScope -> {
            a.enter(0L);
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
        Cancellable.run(Duration.ofMillis(50), cancelScope -> {
            b.enter(0L);
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
        Cancellable.run(Duration.ofMillis(100), cancelScope1 -> {
            a.enter(0L);
            b.enter(0L);
            c.enter(0L);
            d.enter(0L);
            Cancellable.run(Duration.ofMillis(75), cancelScope2 ->
                    Cancellable.run(Duration.ofMillis(50), cancelScope3 ->
                            Cancellable.run(Duration.ofMillis(25), cancelScope4 -> {
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
        Cancellable.run(cancelScope -> {
            a.enter(0L);
            b.enter(0L);
            c.enter(0L);
            d.enter(0L);
        });
        assertFalse(a.exit());
        assertFalse(b.exit());
        assertFalse(c.exit());
        assertFalse(d.exit());
        assertTimedOut();
    }

    @Test
    public void doubleEnter() {
        Cancellable.run(Duration.ofMillis(25), cancelScope -> {
            a.enter(0L);
            assertThatThrownBy(() -> a.enter(0L))
                    .isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    public void reEnter() {
        Cancellable.run(Duration.ofSeconds(1), cancelScope -> {
            a.enter(0L);
            assertFalse(a.exit());
            a.enter(0L);
            assertFalse(a.exit());
        });
    }

    @Test
    public void reEnterAfterTimeout() {
        Cancellable.run(Duration.ofSeconds(1), cancelScope -> {
            a.enter(0L);
            try {
                assertSame(a, timedOut.take());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(a.exit());
            a.enter(0L);
            assertFalse(a.exit());
        });
    }

    @Test
    public void timeout() {
        Cancellable.run(Duration.ofMillis(25), cancelScope -> {
            final var timeout = recordingAsyncTimeout();
            timeout.enter(0L);
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
    public void timeoutStartsBeforeEnter() {
        final var timeout = recordingAsyncTimeout();
        Cancellable.run(Duration.ofMillis(50), cancelScope -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            timeout.enter(0L);
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
    public void shortTimeoutReached() {
        final var timeout = recordingAsyncTimeout();
        Cancellable.run(Duration.ofNanos(1), cancelScope -> {
            timeout.enter(0L);
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
    public void defaultTimeout() {
        a.enter(25_000_000L);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        assertTrue(a.exit());
        assertTimedOut(a);
    }

    @Test
    public void defaultTimeoutNoTimeout() {
        a.enter(100_000_000L);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        assertFalse(a.exit());
        assertTimedOut();
    }

    @Test
    public void defaultTimeoutThrowsWhenInvalid() {
        assertThatThrownBy(() -> a.enter(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Asserts which timeouts fired, and in which order.
     */
    private void assertTimedOut(AsyncTimeout... expected) {
        assertEquals(List.of(expected), List.copyOf(timedOut));
    }

    private AsyncTimeout recordingAsyncTimeout() {
        AtomicReference<AsyncTimeout> asyncTimeoutRef = new AtomicReference<>();
        final var asyncTimeout = AsyncTimeout.
                create(0L, 0L, () -> timedOut.add(asyncTimeoutRef.get()));
        asyncTimeoutRef.set(asyncTimeout);
        return asyncTimeout;
    }
}
