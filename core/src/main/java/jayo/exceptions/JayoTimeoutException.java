/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.exceptions;

import org.jspecify.annotations.NonNull;

import java.net.SocketTimeoutException;
import java.util.Objects;

/**
 * Signals that a timeout has occurred or a deadline has been reached.
 */
public final class JayoTimeoutException extends JayoInterruptedIOException {
    public JayoTimeoutException(final @NonNull String message) {
        super(Objects.requireNonNull(message));
    }

    public JayoTimeoutException(final @NonNull SocketTimeoutException cause) {
        super(Objects.requireNonNull(cause));
    }
}
