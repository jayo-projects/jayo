/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.endpoints;

import jayo.exceptions.JayoInterruptedIOException;
import org.jspecify.annotations.NonNull;

import java.net.SocketTimeoutException;
import java.util.Objects;

/**
 * Signals that a timeout has occurred on a socket read or accept.
 * <p>
 * Wraps a {@link SocketTimeoutException} with an unchecked exception.
 */
public final class JayoSocketTimeoutException extends JayoInterruptedIOException {
    public JayoSocketTimeoutException(final @NonNull String message) {
        super(Objects.requireNonNull(message), new SocketTimeoutException(message));
    }

    public JayoSocketTimeoutException(final @NonNull SocketTimeoutException cause) {
        super(Objects.requireNonNull(cause));
    }
}
