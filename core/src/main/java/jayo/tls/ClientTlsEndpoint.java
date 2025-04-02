/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.tls;

import jayo.Endpoint;
import jayo.internal.tls.RealClientTlsEndpoint;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SNIServerName;
import java.util.List;
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
    sealed interface Builder extends TlsEndpoint.Builder<Builder, Parameterizer> permits RealClientTlsEndpoint.Builder {
        @NonNull
        ClientHandshakeCertificates getHandshakeCertificates();

        /**
         * Create a new {@linkplain ClientTlsEndpoint client-side TLS endpoint}, it requires an existing
         * {@link Endpoint} for encrypted bytes (typically, but not necessarily associated with a network socket).
         * <p>
         * If you need TLS parameterization, please use {@link #createParameterizer(Endpoint)} or
         * {@link #createParameterizer(Endpoint, String, int)} instead.
         */
        @NonNull
        ClientTlsEndpoint build(final @NonNull Endpoint encryptedEndpoint);
    }

    sealed interface Parameterizer extends TlsEndpoint.Parameterizer
            permits RealClientTlsEndpoint.Builder.Parameterizer {
        /**
         * @return the list containing all {@linkplain SNIServerName SNI server names} of the Server Name Indication
         * (SNI) parameter.
         */
        @NonNull
        List<@NonNull SNIServerName> getServerNames();

        /**
         * Sets the list containing all {@linkplain SNIServerName SNI server names} of the Server Name Indication
         * (SNI) parameter.
         */
        void setServerNames(final @NonNull List<@NonNull SNIServerName> serverNames);

        /**
         * Create a new {@linkplain ClientTlsEndpoint client-side TLS endpoint}.
         */
        @NonNull
        ClientTlsEndpoint build();
    }
}
