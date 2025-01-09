/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadFactory;

import static java.lang.System.Logger.Level.INFO;

/**
 * Java 21 utils
 */
final class JavaVersionUtils {
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
    static ThreadFactory threadBuilder(final String prefix) {
        assert prefix != null;
        return Thread.ofVirtual()
                .name(prefix, 0)
                .inheritInheritableThreadLocals(true)
                .factory();
    }

    /**
     * Java 21 has {thread.threadId()} final method
     */
    static long threadId(final Thread thread) {
        assert thread != null;
        return thread.threadId();
    }

    /**
     * There is a problem in SSLEngine, SSLCipher or Cipher because calling {@code engine.unwrap(source, dst)} with a
     * readonly source {@link ByteBuffer} fails in Java 17.
     * <p>
     * This bug is fixed in Java 21 !
     */
    static ByteBuffer asReadOnlyBuffer(final ByteBuffer wrap) {
        assert wrap != null;
        return wrap.asReadOnlyBuffer();
    }
}
