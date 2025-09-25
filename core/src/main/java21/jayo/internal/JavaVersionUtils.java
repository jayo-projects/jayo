/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.CancelScope;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.ref.Cleaner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.System.Logger.Level.INFO;

/**
 * Java 21 utils
 */
@SuppressWarnings("unused")
public final class JavaVersionUtils {
    private static final System.Logger LOGGER = System.getLogger("jayo.JavaVersionUtils");

    // un-instantiable
    private JavaVersionUtils() {
    }

    static {
        LOGGER.log(INFO, """
     
     Jayo runs in Java 21 mode :
      ☑ virtual threads,
      ☒ scoped value""".stripIndent());
    }

    /**
     * The internal executor for Jayo subprojects like jayo-http.
     * <p>
     * Java 21 has virtual Thread support, so we use them through
     * {@link Executors#newThreadPerTaskExecutor(ThreadFactory)} with our {@link #threadFactory(String, boolean)}.
     */
    public static @NonNull ExecutorService executorService(final @NonNull String prefix, final boolean isDaemon) {
        assert prefix != null;
        return Executors.newThreadPerTaskExecutor(threadFactory(prefix, isDaemon));
    }

    /**
     * The internal thread factory for jayo (core) and its subprojects like jayo-http.
     * <p>
     * Java 21 has virtual Thread support, so we use them.
     *
     * @implNote the {@code isDaemon} parameter is ignored, virtual threads are always daemon threads.
     */
    public static @NonNull ThreadFactory threadFactory(final @NonNull String prefix, final boolean isDaemon) {
        assert prefix != null;
        return Thread.ofVirtual()
                .name(prefix, 0)
                .inheritInheritableThreadLocals(false)
                .factory();
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
     * Java 21 has the {@code executor.close()} method, we just call it.
     */
    public static void close(@NonNull ExecutorService executor) {
        assert executor != null;
        executor.close();
    }

    /**
     * Java 21 has the {@code thread.threadId()} final method.
     */
    static long threadId(final @NonNull Thread thread) {
        assert thread != null;
        return thread.threadId();
    }

    /**
     * @return a Cleaner that uses a virtual thread factory.
     */
    static @NonNull Cleaner cleaner() {
        final var cleanerThreadFactory = Thread.ofVirtual()
                .name("Cleaner-", 0)
                .factory();
        return Cleaner.create(cleanerThreadFactory);
    }
}
