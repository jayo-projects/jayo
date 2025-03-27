/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.tls;

import jayo.Endpoint;
import jayo.internal.tls.RealClientTlsEndpoint;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * The client-side TLS (Transport Layer Security) end of a TLS connection between two peers. {@link ClientTlsEndpoint}
 * guarantee that the TLS connection is established and its <b>initial handshake was done</b> upon creation.
 *
 * @see TlsEndpoint
 * @see ServerTlsEndpoint
 */
public sealed interface ClientTlsEndpoint extends TlsEndpoint permits RealClientTlsEndpoint {
    /**
     * Create a new default client-side TLS endpoint, it requires an existing {@link Endpoint} for encrypted bytes
     * (typically, but not necessarily associated with a network socket). A system default
     * {@link ClientHandshakeCertificates} will be used to secure TLS connections.
     * <p>
     * If you need any specific configuration, please use {@link #builder(ClientHandshakeCertificates)} instead.
     */
    static @NonNull ClientTlsEndpoint create(final @NonNull Endpoint encryptedEndpoint) {
        Objects.requireNonNull(encryptedEndpoint);
        return builder(ClientHandshakeCertificates.create())
                .build(encryptedEndpoint);
    }

    /**
     * Create a new {@link Builder} for a client-side TLS endpoint using the provided
     * {@link ClientHandshakeCertificates}.
     */
    static @NonNull Builder builder(final @NonNull ClientHandshakeCertificates handshakeCertificates) {
        Objects.requireNonNull(handshakeCertificates);
        return new RealClientTlsEndpoint.Builder(handshakeCertificates);
    }

    @NonNull
    ClientHandshakeCertificates getHandshakeCertificates();

    /**
     * The builder used to create a {@link ClientTlsEndpoint} instance.
     */
    sealed interface Builder extends TlsEndpoint.Builder<Builder> permits RealClientTlsEndpoint.Builder {
        @NonNull
        ClientHandshakeCertificates getHandshakeCertificates();

        /**
         * Create a new {@linkplain ClientTlsEndpoint client-side TLS endpoint}, it requires an existing
         * {@link Endpoint} for encrypted bytes (typically, but not necessarily associated with a network socket).
         */
        @NonNull
        ClientTlsEndpoint build(final @NonNull Endpoint encryptedEndpoint);
    }
}
