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
            var node = timeout.enter(0L);
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertFalse(timeout.exit(node));
            assertTimedOut();
        });
    }

    @Test
    public void noTimeoutCall() {
        AsyncTimeout timeout = recordingAsyncTimeout();
        // with cancel scope but no timeout
        Cancellable.call(cancelScope -> {
            // provide a defaultTimeout to check it is ignored
            var node = timeout.enter(1L);
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertFalse(timeout.exit(node));
            assertTimedOut();
            return true;
        });
    }

    @Test
    public void singleInstanceTimedOutRun() {
        Cancellable.run(Duration.ofMillis(25), cancelScope -> {
            var node = a.enter(0L);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(a.exit(node));
            assertTimedOut(a);
        });
    }

    @Test
    public void singleInstanceTimedOutCall() {
        Cancellable.call(Duration.ofMillis(25), cancelScope -> {
            var node = a.enter(0L);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(a.exit(node));
            assertTimedOut(a);
            return true;
        });
    }

    @Test
    public void singleInstanceNotTimedOutRun() {
        Cancellable.run(Duration.ofMillis(50), cancelScope -> {
            var node = b.enter(0L);
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertFalse(b.exit(node));
            assertTimedOut();
        });
    }

    @Test
    public void instancesAddedAtEnd() {
        Cancellable.run(Duration.ofMillis(100), cancelScope1 -> {
            var nodeA = a.enter(0L);
            var nodeB = b.enter(0L);
            var nodeC = c.enter(0L);
            var nodeD = d.enter(0L);
            Cancellable.run(Duration.ofMillis(75), cancelScope2 ->
                    Cancellable.run(Duration.ofMillis(50), cancelScope3 ->
                            Cancellable.run(Duration.ofMillis(25), cancelScope4 -> {
                                try {
                                    Thread.sleep(125);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            })));
            assertTrue(a.exit(nodeA));
            assertTrue(b.exit(nodeB));
            assertTrue(c.exit(nodeC));
            assertTrue(d.exit(nodeD));
            assertTimedOut(a, b, c, d);
        });
    }

    @Test
    public void instancesRemovedAtFront() {
        Cancellable.run(cancelScope -> {
            var nodeA = a.enter(0L);
            var nodeB = b.enter(0L);
            var nodeC = c.enter(0L);
            var nodeD = d.enter(0L);
            assertFalse(a.exit(nodeA));
            assertFalse(b.exit(nodeB));
            assertFalse(c.exit(nodeC));
            assertFalse(d.exit(nodeD));
            assertTimedOut();
        });
    }

    @Test
    public void reEnter() {
        Cancellable.run(Duration.ofSeconds(1), cancelScope -> {
            var node = a.enter(0L);
            assertFalse(a.exit(node));
            node = a.enter(0L);
            assertFalse(a.exit(node));
        });
    }

    @Test
    public void reEnterAfterTimeout() {
        Cancellable.run(Duration.ofSeconds(1), cancelScope -> {
            var node = a.enter(0L);
            try {
                assertSame(a, timedOut.take());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(a.exit(node));
            node = a.enter(0L);
            assertFalse(a.exit(node));
        });
    }

    @Test
    public void timeout() {
        Cancellable.run(Duration.ofMillis(25), cancelScope -> {
            final var timeout = recordingAsyncTimeout();
            var node = timeout.enter(0L);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(timeout.exit(node));
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
            var node = timeout.enter(0L);
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(timeout.exit(node));
            assertTimedOut(timeout);
        });
    }

    @Test
    public void shortTimeoutReached() {
        final var timeout = recordingAsyncTimeout();
        Cancellable.run(Duration.ofNanos(1), cancelScope -> {
            var node = timeout.enter(0L);
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(timeout.exit(node));
            assertTimedOut(timeout);
        });
    }

    @Test
    public void defaultTimeout() {
        var node = a.enter(25_000_000L);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        assertTrue(a.exit(node));
        assertTimedOut(a);
    }

    @Test
    public void defaultTimeoutNoTimeout() {
        var node = a.enter(100_000_000L);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        assertFalse(a.exit(node));
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
        final var asyncTimeout = AsyncTimeout.create(() -> timedOut.add(asyncTimeoutRef.get()));
        asyncTimeoutRef.set(asyncTimeout);
        return asyncTimeout;
    }
}
