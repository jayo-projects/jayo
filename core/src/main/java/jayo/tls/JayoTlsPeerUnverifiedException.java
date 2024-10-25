/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.tls;

import org.jspecify.annotations.NonNull;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.util.Objects;

/**
 * Wraps a {@link SSLPeerUnverifiedException} with an unchecked exception.
 */
public final class JayoTlsPeerUnverifiedException extends JayoTlsException {
    public JayoTlsPeerUnverifiedException(final @NonNull String message) {
        super(Objects.requireNonNull(message), new SSLPeerUnverifiedException(message));
    }

    public JayoTlsPeerUnverifiedException(final @NonNull SSLPeerUnverifiedException cause) {
        super(Objects.requireNonNull(cause));
    }
}
