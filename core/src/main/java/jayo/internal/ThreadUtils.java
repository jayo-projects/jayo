/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;

import java.lang.Thread.Builder;
import java.util.Objects;

import static java.lang.System.Logger.Level.INFO;

/**
 * Virtual thread utils for JDK 21
 */
public final class ThreadUtils {
    // un-instantiable
    private ThreadUtils() {
    }

    private static final System.Logger LOGGER = System.getLogger("o.u.d.ThreadUtils");

    static {
        LOGGER.log(INFO, "Jayo will use virtual threads");
    }

    /* Visible for tests only */
    static @NonNull Thread newThread(final @NonNull Runnable task) {
        return threadBuilder("").unstarted(task);
    }

    public static @NonNull Builder threadBuilder(final @NonNull String prefix) {
        Objects.requireNonNull(prefix);
        return Thread.ofVirtual()
                .name(prefix, 0)
                .inheritInheritableThreadLocals(true);
    }
}
