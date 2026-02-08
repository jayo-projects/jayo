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
 * Java 25 utils
 */
@SuppressWarnings("unused")
public final class JavaVersionUtils {
    private static final System.Logger LOGGER = System.getLogger("jayo.JavaVersionUtils");

    // un-instantiable
    private JavaVersionUtils() {
    }

    static {
        LOGGER.log(INFO, """
                
                Jayo runs in Java 25 mode :
                 ☑ virtual threads
                 ☑ scoped value""".stripIndent());
    }

    /**
     * The internal executor for Jayo subprojects like jayo-http.
     * <p>
     * Java 21+ has virtual Thread support, so we use them through
     * {@link Executors#newThreadPerTaskExecutor(ThreadFactory)} with our {@link #threadFactory(String)}.
     */
    public static @NonNull ExecutorService executorService(final @NonNull String prefix) {
        assert prefix != null;
        return Executors.newThreadPerTaskExecutor(threadFactory(prefix));
    }

    /**
     * The internal thread factory for jayo (core) and its subprojects like jayo-http.
     * <p>
     * Java 21+ has virtual Thread support, so we use them. All virtual threads are daemon threads.
     */
    public static @NonNull ThreadFactory threadFactory(final @NonNull String prefix) {
        assert prefix != null;
        return Thread.ofVirtual()
                .name(prefix, 0)
                .inheritInheritableThreadLocals(false)
                .factory();
    }

    private static final ScopedValue<CancellationContext> CANCELLATION_CONTEXT = ScopedValue.newInstance();

    public static @Nullable RealCancelToken getCancelToken() {
        return CANCELLATION_CONTEXT.isBound() ? CANCELLATION_CONTEXT.get().getCancelToken() : null;
    }

    public static void runCancellable(final @NonNull RealCancelToken cancelToken,
                                      final @NonNull Consumer<CancelScope> block) {
        assert cancelToken != null;
        assert block != null;

        if (CANCELLATION_CONTEXT.isBound()) {
            final var cancellationContext = CANCELLATION_CONTEXT.get();
            cancellationContext.addCancelToken(cancelToken);
            try {
                block.accept(cancelToken);
                return;
            } finally {
                cancelToken.finished = true;
            }
        }

        final var cancellationContext = new CancellationContext(cancelToken);
        ScopedValue.where(CANCELLATION_CONTEXT, cancellationContext).run(() -> block.accept(cancelToken));
    }

    public static <T> T callCancellable(final @NonNull RealCancelToken cancelToken,
                                        final @NonNull Function<CancelScope, T> block) {
        assert cancelToken != null;
        assert block != null;

        if (CANCELLATION_CONTEXT.isBound()) {
            final var cancellationContext = CANCELLATION_CONTEXT.get();
            cancellationContext.addCancelToken(cancelToken);
            try {
                return block.apply(cancelToken);
            } finally {
                cancelToken.finished = true;
            }
        }

        final var cancellationContext = new CancellationContext(cancelToken);
        return ScopedValue.where(CANCELLATION_CONTEXT, cancellationContext).call(() -> block.apply(cancelToken));
    }

    /**
     * Java 21+ has the {@code thread.threadId()} final method.
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
