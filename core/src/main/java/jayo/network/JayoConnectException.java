/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.network;

import org.jspecify.annotations.NonNull;

import java.net.ConnectException;
import java.util.Objects;

/**
 * Wraps a {@link ConnectException} with an unchecked exception.
 */
public final class JayoConnectException extends JayoSocketException {
    public JayoConnectException(final @NonNull ConnectException cause) {
        super(Objects.requireNonNull(cause));
    }

    public JayoConnectException(final @NonNull String message) {
        super(Objects.requireNonNull(message), new ConnectException(message));
    }
}
