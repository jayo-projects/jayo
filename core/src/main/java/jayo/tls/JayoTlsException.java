/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.tls;

import jayo.JayoException;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.util.Objects;

/**
 * Wraps a {@link SSLException} with an unchecked exception.
 */
public sealed class JayoTlsException extends JayoException
        permits JayoTlsHandshakeCallbackException, JayoTlsHandshakeException {
    public JayoTlsException(final @NonNull String message) {
        super(Objects.requireNonNull(message), new SSLException(message));
    }

    public JayoTlsException(final @NonNull SSLException cause) {
        super(Objects.requireNonNull(cause));
    }

    JayoTlsException(final @NonNull String message, final @NonNull SSLHandshakeException cause) {
        super(Objects.requireNonNull(message), Objects.requireNonNull(cause));
    }
}
