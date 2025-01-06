/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.System.Logger.Level.INFO;

/**
 * Java 17 utils
 */
final class JavaVersionUtils {
    private static final System.Logger LOGGER = System.getLogger("jayo.JavaVersionUtils");

    // un-instantiable
    private JavaVersionUtils() {
    }

    static {
        LOGGER.log(INFO, "Using Java 17 compatibility");
    }

    /**
     * Java 17 has no Virtual Thread support, so we use platform threads
     */
    static @NonNull ThreadFactory threadBuilder(final @NonNull String prefix) {
        assert prefix != null;
        return new PlatformThreadFactory(prefix);
    }

    /**
     * Java 17 has no {thread.threadId()} final method
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
        return wrap; // sad !
    }

    private static final class PlatformThreadFactory implements ThreadFactory {
        private final @NonNull String prefix;
        private final @NonNull AtomicLong threadCounter = new AtomicLong();

        private PlatformThreadFactory(final @NonNull String prefix) {
            assert prefix != null;
            this.prefix = prefix;
        }

        @Override
        public @NonNull Thread newThread(final @NonNull Runnable r) {
            final var thread = new Thread(null, r,
                    prefix + threadCounter.incrementAndGet(),
                    0, true);
            thread.setDaemon(true);
            return thread;
        }
    }
}
