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
        Cancellable.run(cancelScope ->
                timeout.withTimeout(0L, node -> {
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    assertFalse(node.exit());
                    return null;
                })
        );
        assertTimedOut();
    }

    @Test
    public void noTimeoutCall() {
        AsyncTimeout timeout = recordingAsyncTimeout();
        // with cancel scope but no timeout
        Cancellable.call(cancelScope ->
                // provide a defaultTimeout to check it is ignored
                timeout.withTimeout(1L, node -> {
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    assertFalse(node.exit());
                    return true;
                })
        );
        assertTimedOut();
    }

    @Test
    public void singleInstanceTimedOutRun() {
        Cancellable.run(Duration.ofMillis(25), cancelScope ->
                a.withTimeout(0L, node -> {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    assertTrue(node.exit());
                    return null;
                })
        );
        assertTimedOut(a);
    }

    @Test
    public void singleInstanceTimedOutCall() {
        Cancellable.call(Duration.ofMillis(25), cancelScope ->
                a.withTimeout(0L, node -> {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    assertTrue(node.exit());
                    return true;
                })
        );
        assertTimedOut(a);
    }

    @Test
    public void singleInstanceNotTimedOutRun() {
        Cancellable.run(Duration.ofMillis(50), cancelScope ->
                b.withTimeout(0L, node -> {
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    assertFalse(node.exit());
                    return true;
                })
        );
        assertTimedOut();
    }

    @Test
    public void instancesAddedAtEnd() {
        Cancellable.run(Duration.ofMillis(100), cancelScope1 ->
                a.withTimeout(0L, nodeA -> {
                    Cancellable.run(Duration.ofMillis(75), cancelScope2 ->
                            b.withTimeout(0L, nodeB -> {
                                Cancellable.run(Duration.ofMillis(50), cancelScope3 ->
                                        c.withTimeout(0L, nodeC -> {
                                            Cancellable.run(Duration.ofMillis(25), cancelScope4 ->
                                                    d.withTimeout(0L, nodeD -> {
                                                        try {
                                                            Thread.sleep(125);
                                                        } catch (InterruptedException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                        assertTrue(nodeD.exit());
                                                        return null;
                                                    })
                                            );
                                            assertTrue(nodeC.exit());
                                            return null;
                                        })
                                );
                                assertTrue(nodeB.exit());
                                return null;
                            })
                    );
                    assertTrue(nodeA.exit());
                    return null;
                })
        );
        assertTimedOut(a, b, c, d);
    }

    @Test
    public void instancesRemovedAtFront() {
        Cancellable.run(cancelScope ->
                a.withTimeout(0L, nodeA -> {
                    b.withTimeout(0L, nodeB -> {
                        c.withTimeout(0L, nodeC -> {
                            d.withTimeout(0L, nodeD -> {
                                assertFalse(nodeD.exit());
                                return null;
                            });
                            assertFalse(nodeC.exit());
                            return null;
                        });
                        assertFalse(nodeB.exit());
                        return null;
                    });
                    assertFalse(nodeA.exit());
                    return null;
                })
        );

        assertTimedOut();
    }

    @Test
    public void reEnter() {
        Cancellable.run(Duration.ofSeconds(1), cancelScope ->
                a.withTimeout(0L, node1 -> {
                    assertFalse(node1.exit());
                    a.withTimeout(0L, node2 -> {
                        assertFalse(node2.exit());
                        return null;
                    });
                    return null;
                })
        );
    }

    @Test
    public void reEnterAfterTimeout() {
        Cancellable.run(Duration.ofSeconds(1), cancelScope ->
                a.withTimeout(0L, node1 -> {
                    try {
                        assertSame(a, timedOut.take()); // take is blocking until timeout occurs
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    assertTrue(node1.exit());
                    a.withTimeout(0L, node2 -> {
                        assertFalse(node2.exit());
                        return null;
                    });
                    return null;
                })
        );
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
            timeout.withTimeout(0L, node -> {
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                assertTrue(node.exit());
                return null;
            });
        });
        assertTimedOut(timeout);
    }

    @Test
    public void shortTimeoutReached() {
        final var timeout = recordingAsyncTimeout();
        Cancellable.run(Duration.ofNanos(1), cancelScope ->
                timeout.withTimeout(0L, node -> {
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    assertTrue(node.exit());
                    return null;
                })
        );
        assertTimedOut(timeout);
    }

    @Test
    public void defaultTimeout() {
        a.withTimeout(25_000_000L /* = 25 millis */, node -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(node.exit());
            return null;
        });
        assertTimedOut(a);
    }

    @Test
    public void defaultTimeoutNoTimeout() {
        a.withTimeout(100_000_000L /* = 100 millis */, node -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertFalse(node.exit());
            return null;
        });
        assertTimedOut();
    }

    @Test
    public void defaultTimeoutThrowsWhenInvalid() {
        assertThatThrownBy(() -> a.withTimeout(-1L, ignored -> null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Asserts which timeouts fired, and in which order.
     */
    private void assertTimedOut(AsyncTimeout... expected) {
        assertEquals(List.of(expected), List.copyOf(timedOut));
    }

    private AsyncTimeout recordingAsyncTimeout() {
        final var asyncTimeoutRef = new AtomicReference<AsyncTimeout>();
        final var asyncTimeout = AsyncTimeout.create(() -> timedOut.add(asyncTimeoutRef.get()));
        asyncTimeoutRef.set(asyncTimeout);
        return asyncTimeout;
    }
}
