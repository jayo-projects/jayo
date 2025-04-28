/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;

import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.System.Logger.Level.INFO;

/**
 * Java 17 utils
 */
public final class JavaVersionUtils {
    private static final System.Logger LOGGER = System.getLogger("jayo.JavaVersionUtils");

    // un-instantiable
    private JavaVersionUtils() {
    }

    static {
        LOGGER.log(INFO, "Using Java 17 compatibility");
    }

    /**
     * Java 17 has no virtual Thread support, so we use platform threads.
     */
    public static @NonNull ThreadFactory threadFactory(final @NonNull String prefix, final boolean isDaemon) {
        assert prefix != null;
        return new PlatformThreadFactory(prefix, isDaemon);
    }

    /**
     * Java 17 has no virtual Thread support, so we use pooled platform threads through {@link ThreadPoolExecutor} with
     * our {@link #threadFactory(String, boolean)}.
     */
    public static @NonNull ExecutorService executorService(final @NonNull String prefix, final boolean isDaemon) {
        assert prefix != null;
        return new ThreadPoolExecutor(
                0, // corePoolSize
                Integer.MAX_VALUE, // maximumPoolSize
                60L, TimeUnit.SECONDS, // keepAliveTime
                new SynchronousQueue<>(),
                threadFactory(prefix, isDaemon)
        );
    }

    /**
     * Java 17 has no {executor.close()}, we add it here.
     */
    public static void close(final @NonNull ExecutorService executor) {
        assert executor != null;

        var terminated = executor.isTerminated();
        if (!terminated) {
            executor.shutdown();
            var interrupted = false;
            while (!terminated) {
                try {
                    terminated = executor.awaitTermination(1L, TimeUnit.DAYS);
                } catch (InterruptedException e) {
                    if (!interrupted) {
                        executor.shutdownNow();
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Java 17 has no {@code thread.threadId()} final method.
     */
    static long threadId(final @NonNull Thread thread) {
        assert thread != null;
        return thread.getId();
    }

    /**
     * There is a problem in SSLEngine, SSLCipher or Cipher because calling {@code engine.unwrap(source, dst)} with a
     * readonly source {@link ByteBuffer} fails in Java 17.
     */
    static @NonNull ByteBuffer asReadOnlyBuffer(final @NonNull ByteBuffer wrap) {
        assert wrap != null;
        return wrap; // truly sad!
    }

    private static final class PlatformThreadFactory implements ThreadFactory {
        private final @NonNull String prefix;
        private final boolean isDaemon;
        private final @NonNull AtomicLong threadCounter = new AtomicLong();

        private PlatformThreadFactory(final @NonNull String prefix, final boolean isDaemon) {
            assert prefix != null;

            this.prefix = prefix;
            this.isDaemon = isDaemon;
        }

        @Override
        public @NonNull Thread newThread(final @NonNull Runnable runnable) {
            assert runnable != null;

            final var thread = new Thread(
                    null,
                    runnable,
                    prefix + threadCounter.incrementAndGet(),
                    0,
                    true);
            thread.setDaemon(isDaemon);
            return thread;
        }
    }
}
