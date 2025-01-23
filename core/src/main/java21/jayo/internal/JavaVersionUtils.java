/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadFactory;

import static java.lang.System.Logger.Level.INFO;

/**
 * Java 21 utils
 */
public final class JavaVersionUtils {
    private static final System.Logger LOGGER = System.getLogger("jayo.JavaVersionUtils");

    // un-instantiable
    private JavaVersionUtils() {
    }

    static {
        LOGGER.log(INFO, "Using Java 21 compatibility, virtual threads in use !");
    }

    /**
     * Java 21 has Virtual Thread support, so we use them
     */
    public static @NonNull ThreadFactory threadFactory(final @NonNull String prefix) {
        assert prefix != null;
        return Thread.ofVirtual()
                .name(prefix, 0)
                .inheritInheritableThreadLocals(true)
                .factory();
    }

    /**
     * Java 21 has {thread.threadId()} final method
     */
    static long threadId(final @NonNull Thread thread) {
        assert thread != null;
        return thread.threadId();
    }

    /**
     * There was a problem in SSLEngine, SSLCipher or Cipher because calling {@code engine.unwrap(source, dst)} with a
     * readonly source {@link ByteBuffer} failed in Java 17.
     * <p>
     * This bug is fixed in Java 21, nice !
     */
    static @NonNull ByteBuffer asReadOnlyBuffer(final @NonNull ByteBuffer wrap) {
        assert wrap != null;
        return wrap.asReadOnlyBuffer();
    }
}
