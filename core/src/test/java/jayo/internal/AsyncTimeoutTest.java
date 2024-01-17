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

package jayo.internal;

import org.junit.jupiter.api.Test;
import jayo.Cancellable;
import jayo.external.AsyncTimeout;

import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This test uses four timeouts of varying durations: 250ms, 500ms, 750ms and
 * 1000ms, named 'a', 'b', 'c' and 'd'.
 */
public final class AsyncTimeoutTest {
    private final BlockingDeque<AsyncTimeout> timedOut = new LinkedBlockingDeque<>();
    private final AsyncTimeout a = recordingAsyncTimeout();
    private final AsyncTimeout b = recordingAsyncTimeout();
    private final AsyncTimeout c = recordingAsyncTimeout();
    private final AsyncTimeout d = recordingAsyncTimeout();

    @Test
    public void zeroTimeoutIsNoTimeout() {
        AsyncTimeout timeout = recordingAsyncTimeout();
        // with cancel scope but no timeout
        Cancellable.withCancellable(cancelScope -> {
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
    public void zeroTimeoutIsNoTimeoutFunction() {
        AsyncTimeout timeout = recordingAsyncTimeout();
        // with cancel scope but no timeout
        Cancellable.withCancellable(cancelScope -> {
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
    public void singleInstanceTimedOut() {
        Cancellable.withTimeout(25, TimeUnit.MILLISECONDS, cancelScope -> {
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
    public void singleInstanceTimedOutFunction() {
        Cancellable.withTimeout(25, TimeUnit.MILLISECONDS, cancelScope -> {
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
    public void singleInstanceNotTimedOut() {
        Cancellable.withTimeout(50, TimeUnit.MILLISECONDS, cancelScope -> {
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
        Cancellable.withTimeout(100, TimeUnit.MILLISECONDS, cancelScope1 -> {
            a.enter(cancelScope1);
            b.enter(cancelScope1);
            c.enter(cancelScope1);
            d.enter(cancelScope1);
            Cancellable.withTimeout(75, TimeUnit.MILLISECONDS, cancelScope2 -> {
                Cancellable.withTimeout(50, TimeUnit.MILLISECONDS, cancelScope3 -> {
                    Cancellable.withTimeout(25, TimeUnit.MILLISECONDS, cancelScope4 -> {
                        try {
                            Thread.sleep(125);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
                });
            });
        });
        assertTrue(a.exit());
        assertTrue(b.exit());
        assertTrue(c.exit());
        assertTrue(d.exit());
        assertTimedOut(a, b, c, d);
    }

    @Test
    public void instancesRemovedAtFront() {
        Cancellable.withCancellable(cancelScope -> {
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
        Cancellable.withTimeout(25, TimeUnit.MILLISECONDS, cancelScope -> {
            a.enter(cancelScope);
            assertThatThrownBy(() -> a.enter(cancelScope))
                    .isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    public void reEnter() {
        Cancellable.withTimeout(1, TimeUnit.SECONDS, cancelScope -> {
            a.enter(cancelScope);
            assertFalse(a.exit());
            a.enter(cancelScope);
            assertFalse(a.exit());
        });
    }

    @Test
    public void reEnterAfterTimeout() {
        Cancellable.withTimeout(1, TimeUnit.SECONDS, cancelScope -> {
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
        new Cancellable.Builder()
                .deadline(25, TimeUnit.MILLISECONDS)
                .build()
                .executeCancellable(cancelScope -> {
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
        new Cancellable.Builder()
                .timeout(75, TimeUnit.MILLISECONDS)
                .deadline(25, TimeUnit.MILLISECONDS)
                .build()
                .executeCancellable(cancelScope -> {
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
        new Cancellable.Builder()
                .timeout(25, TimeUnit.MILLISECONDS)
                .deadline(75, TimeUnit.MILLISECONDS)
                .build()
                .executeCancellable(cancelScope -> {
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
        new Cancellable.Builder()
                .deadline(50, TimeUnit.MILLISECONDS)
                .build()
                .executeCancellable(cancelScope -> {
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
        new Cancellable.Builder()
                .deadline(1, TimeUnit.NANOSECONDS)
                .build()
                .executeCancellable(cancelScope -> {
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

//    @Test
//    public void wrappedSinkTimesOut() {
//        Sink sink = new ForwardingSink(new JayoBuffer(false)) {
//            @Override
//            public long write(final @NonNull Buffer source, final long byteCount) {
//                try {
//                    Thread.sleep(50);
//                    return byteCount;
//                } catch (InterruptedException e) {
//                    throw new AssertionError();
//                }
//            }
//        };
//        AsyncTimeout timeout = new AsyncTimeout();
//        timeout.timeout(25, TimeUnit.MILLISECONDS);
//        Sink timeoutSink = timeout.sink(sink);
//        Buffer data = new JayoBuffer(false).writeUtf8("a");
//        assertThatThrownBy(() -> timeoutSink.write(data, 1))
//                .isInstanceOf(JayoInterruptedIOException.class);
//    }
//
//    @Test
//    public void wrappedSinkFlushTimesOut() throws Exception {
//        Sink sink = new ForwardingSink(new JayoBuffer(false)) {
//            @Override
//            public void flush() {
//                try {
//                    Thread.sleep(50);
//                } catch (InterruptedException e) {
//                    throw new AssertionError();
//                }
//            }
//        };
//        AsyncTimeout timeout = new AsyncTimeout();
//        timeout.timeout(25, TimeUnit.MILLISECONDS);
//        Sink timeoutSink = timeout.sink(sink);
//        assertThatThrownBy(timeoutSink::flush)
//                .isInstanceOf(JayoInterruptedIOException.class);
//    }
//
//    @Test
//    public void wrappedSinkCloseTimesOut() throws Exception {
//        Sink sink = new ForwardingSink(new JayoBuffer(false)) {
//            @Override
//            public void close() {
//                try {
//                    Thread.sleep(50);
//                } catch (InterruptedException e) {
//                    throw new AssertionError();
//                }
//            }
//        };
//        AsyncTimeout timeout = new AsyncTimeout();
//        timeout.timeout(25, TimeUnit.MILLISECONDS);
//        Sink timeoutSink = timeout.sink(sink);
//        assertThatThrownBy(timeoutSink::close)
//                .isInstanceOf(JayoInterruptedIOException.class);
//    }
//
//    @Test
//    public void wrappedSourceTimesOut() {
//        Source source = new ForwardingSource(new JayoBuffer(false)) {
//            @Override
//            public long read(final @NonNull Buffer sink, final long byteCount) {
//                try {
//                    Thread.sleep(50);
//                    return -1;
//                } catch (InterruptedException e) {
//                    throw new AssertionError();
//                }
//            }
//        };
//        AsyncTimeout timeout = new AsyncTimeout();
//        timeout.timeout(25, TimeUnit.MILLISECONDS);
//        Source timeoutSource = timeout.source(source);
//        assertThatThrownBy(() -> timeoutSource.read(new JayoBuffer(false), 0))
//                .isInstanceOf(JayoInterruptedIOException.class);
//    }
//
//    @Test
//    public void wrappedSourceCloseTimesOut() {
//        Source source = new ForwardingSource(new JayoBuffer(false)) {
//            @Override
//            public void close() {
//                try {
//                    Thread.sleep(50);
//                } catch (InterruptedException e) {
//                    throw new AssertionError();
//                }
//            }
//        };
//        AsyncTimeout timeout = new AsyncTimeout();
//        timeout.timeout(25, TimeUnit.MILLISECONDS);
//        Source timeoutSource = timeout.source(source);
//        assertThatThrownBy(timeoutSource::close)
//                .isInstanceOf(JayoInterruptedIOException.class);
//    }
//
//    @Test
//    public void wrappedThrowsWithTimeout() {
//        Sink sink = new ForwardingSink(new JayoBuffer(false)) {
//            @Override
//            public long write(final @NonNull Buffer source, final long byteCount) {
//                try {
//                    Thread.sleep(50);
//                    throw new JayoException("exception and timeout");
//                } catch (InterruptedException e) {
//                    throw new AssertionError();
//                }
//            }
//        };
//        AsyncTimeout timeout = new AsyncTimeout();
//        timeout.timeout(25, TimeUnit.MILLISECONDS);
//        Sink timeoutSink = timeout.sink(sink);
//        Buffer data = new JayoBuffer(false).writeUtf8("a");
//        assertThatThrownBy(() -> timeoutSink.write(data, 1))
//                .isInstanceOf(JayoInterruptedIOException.class)
//                .hasMessage("timeout")
//                .hasRootCauseMessage("exception and timeout");
//    }
//
//    @Test
//    public void wrappedThrowsWithoutTimeout() {
//        Sink sink = new ForwardingSink(new JayoBuffer(false)) {
//            @Override
//            public long write(final @NonNull Buffer source, final long byteCount) {
//                throw new JayoException("no timeout occurred");
//            }
//        };
//        AsyncTimeout timeout = new AsyncTimeout();
//        timeout.timeout(25, TimeUnit.MILLISECONDS);
//        Sink timeoutSink = timeout.sink(sink);
//        Buffer data = new JayoBuffer(false).writeUtf8("a");
//        assertThatThrownBy(() -> timeoutSink.write(data, 1))
//                .isInstanceOf(JayoException.class)
//                .hasMessage("no timeout occurred");
//    }
//
//    /**
//     * We had a bug where writing a very large buffer would fail with an
//     * unexpected timeout because although the sink was making steady forward
//     * progress, doing it all as a single write caused a timeout.
//     */
////    @Disabled("Flaky")
//    @Test
//    public void sinkSplitsLargeWrites() {
//        byte[] data = new byte[512 * 1024];
//        Random dice = new Random(0);
//        dice.nextBytes(data);
//        final Buffer source = bufferWithRandomSegmentLayout(dice, data);
//        final Buffer target = new JayoBuffer(false);
//
//        Sink sink = new ForwardingSink(new JayoBuffer(false)) {
//            @Override
//            public void write(final @NonNull Buffer source, final long byteCount) {
//                try {
//                    Thread.sleep(byteCount / 5000); // ~500 KiB/s.
//                    target.write(source, byteCount);
//                } catch (InterruptedException e) {
//                    throw new AssertionError();
//                }
//            }
//        };
//
//        // Timeout after 250 ms of inactivity.
//        AsyncTimeout timeout = new AsyncTimeout();
//        timeout.timeout(25, TimeUnit.MILLISECONDS);
//        Sink timeoutSink = timeout.sink(sink);
//
//        // Transmit 500 KiB of data, which should take ~1 second. But expect no timeout!
//        timeoutSink.write(source, source.getSize());
//
//        // The data should all have arrived.
//        assertEquals(ByteString.of(data), target.readByteString());
//    }

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
