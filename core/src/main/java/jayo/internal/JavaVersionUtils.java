/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.CancelScope;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.ref.Cleaner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

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
        LOGGER.log(INFO, """
     
     Jayo runs in Java 17 mode :
      ☒ virtual threads,
      ☒ scoped value""".stripIndent());
    }

    /**
     * The internal executor for Jayo subprojects like jayo-http.
     * <p>
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
     * The internal thread factory for jayo (core) and its subprojects like jayo-http.
     * <p>
     * Java 17 has no virtual Thread support, so we use platform threads.
     */
    public static @NonNull ThreadFactory threadFactory(final @NonNull String prefix, final boolean isDaemon) {
        assert prefix != null;
        return new PlatformThreadFactory(prefix, isDaemon);
    }

    private static final class PlatformThreadFactory implements ThreadFactory {
        private final @NonNull String prefix;
        private final boolean isDaemon;
        private final @NonNull AtomicInteger threadCounter = new AtomicInteger();

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
                    prefix + threadCounter.getAndIncrement(),
                    0L,
                    false);
            thread.setDaemon(isDaemon);
            return thread;
        }
    }

    private static final ThreadLocal<CancellationContext> CANCELLATION_CONTEXT = new ThreadLocal<>();

    public static @Nullable RealCancelToken getCancelToken() {
        final var cancellationContext = CANCELLATION_CONTEXT.get();
        return (cancellationContext != null) ? cancellationContext.getCancelToken() : null;
    }

    public static void runCancellable(final @NonNull RealCancelToken cancelToken,
                                      final @NonNull Consumer<CancelScope> block) {
        assert cancelToken != null;
        assert block != null;

        var cancellationContext = CANCELLATION_CONTEXT.get();
        if (cancellationContext != null) {
            cancellationContext.addCancelToken(cancelToken);
            try {
                block.accept(cancelToken);
                return;
            } finally {
                cancelToken.finished = true;
            }
        }

        cancellationContext = new CancellationContext(cancelToken);
        CANCELLATION_CONTEXT.set(cancellationContext);
        try {
            block.accept(cancelToken);
        } finally {
            CANCELLATION_CONTEXT.remove();
        }
    }

    public static <T> T callCancellable(final @NonNull RealCancelToken cancelToken,
                                 final @NonNull Function<CancelScope, T> block) {
        assert cancelToken != null;
        assert block != null;

        var cancellationContext = CANCELLATION_CONTEXT.get();
        if (cancellationContext != null) {
            cancellationContext.addCancelToken(cancelToken);
            try {
                return block.apply(cancelToken);
            } finally {
                cancelToken.finished = true;
            }
        }

        cancellationContext = new CancellationContext(cancelToken);
        CANCELLATION_CONTEXT.set(cancellationContext);
        try {
            return block.apply(cancelToken);
        } finally {
            CANCELLATION_CONTEXT.remove();
        }
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
     * @return a default Cleaner.
     */
    static @NonNull Cleaner cleaner() {
        return Cleaner.create();
    }
}
