/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.tls;

import org.jspecify.annotations.NonNull;

import javax.net.ssl.SSLException;
import java.util.Objects;

/**
 * Thrown during {@link TlsEndpoint} handshake to indicate that a user-supplied function threw an exception.
 */
public final class JayoTlsHandshakeCallbackException extends JayoTlsException {
    public JayoTlsHandshakeCallbackException(String message, Throwable throwable) {
        super(new TlsHandshakeCallbackException(message, throwable));
    }

    public static final class TlsHandshakeCallbackException extends SSLException {
        public TlsHandshakeCallbackException(final @NonNull String message, final @NonNull Throwable throwable) {
            super(Objects.requireNonNull(message), Objects.requireNonNull(throwable));
        }
    }
}
