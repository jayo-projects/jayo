/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.network;

import jayo.JayoException;
import org.jspecify.annotations.NonNull;

import java.net.UnknownHostException;
import java.util.Objects;

/**
 * Wraps a {@link UnknownHostException} with an unchecked exception.
 */
public final class JayoUnknownHostException extends JayoException {
    public JayoUnknownHostException(final @NonNull String message) {
        super(Objects.requireNonNull(message), new UnknownHostException(message));
    }

    public JayoUnknownHostException(final @NonNull UnknownHostException cause) {
        super(Objects.requireNonNull(cause));
    }
}
