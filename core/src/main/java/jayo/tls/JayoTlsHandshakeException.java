/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.tls;

import org.jspecify.annotations.NonNull;

import javax.net.ssl.SSLHandshakeException;
import java.util.Objects;

/**
 * Wraps a {@link SSLHandshakeException} with an unchecked exception.
 */
public sealed class JayoTlsHandshakeException extends JayoTlsException permits JayoTlsHandshakeCallbackException {
    public JayoTlsHandshakeException(final @NonNull String message) {
        super(Objects.requireNonNull(message), new SSLHandshakeException(message));
    }

    public JayoTlsHandshakeException(final @NonNull SSLHandshakeException cause) {
        super(Objects.requireNonNull(cause));
    }
}
