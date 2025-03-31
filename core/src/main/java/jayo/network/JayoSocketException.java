/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.network;

import jayo.JayoException;
import org.jspecify.annotations.NonNull;

import java.net.SocketException;
import java.util.Objects;

/**
 * Wraps a {@link SocketException} with an unchecked exception.
 */
public sealed class JayoSocketException extends JayoException permits JayoConnectException {
    public JayoSocketException(final @NonNull SocketException cause) {
        super(Objects.requireNonNull(cause));
    }

    public JayoSocketException(final @NonNull String message) {
        super(Objects.requireNonNull(message), new SocketException(message));
    }

    JayoSocketException(final @NonNull String message, final @NonNull SocketException cause) {
        super(Objects.requireNonNull(message), Objects.requireNonNull(cause));
    }
}
