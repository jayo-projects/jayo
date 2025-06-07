/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

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
        LOGGER.log(INFO, "Using Java 21 compatibility with virtual threads!");
    }

    /**
     * Java 21 has virtual Thread support, so we use them.
     *
     * @implNote the {@code isDaemon} parameter is ignored, virtual threads are always daemon threads.
     */
    public static @NonNull ThreadFactory threadFactory(final @NonNull String prefix, final boolean isDaemon) {
        assert prefix != null;
        return Thread.ofVirtual()
                .name(prefix, 0)
                .inheritInheritableThreadLocals(true)
                .factory();
    }

    /**
     * Java 21 has virtual Thread support, so we use them through
     * {@link Executors#newThreadPerTaskExecutor(ThreadFactory)} with our {@link #threadFactory(String, boolean)}.
     */
    public static @NonNull ExecutorService executorService(final @NonNull String prefix, final boolean isDaemon) {
        assert prefix != null;
        return Executors.newThreadPerTaskExecutor(threadFactory(prefix, isDaemon));
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
}
