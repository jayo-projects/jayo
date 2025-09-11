/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.tls;

import jayo.Socket;
import jayo.internal.tls.RealClientTlsSocket;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SNIServerName;
import java.util.List;
import java.util.Objects;

/**
 * The client-side TLS (Transport Layer Security) end of a TLS connection between two peers. {@link ClientTlsSocket}
 * guarantee that the TLS connection is established and the <b>initial handshake was done</b> upon creation.
 *
 * @see TlsSocket
 * @see ServerTlsSocket
 */
public sealed interface ClientTlsSocket extends TlsSocket permits RealClientTlsSocket {
    /**
     * Create a new default client-side TLS socket. It requires an existing {@link Socket} for encrypted bytes
     * (typically, but not necessarily associated with a network socket). A system default
     * {@link ClientHandshakeCertificates} will be used to secure TLS connections.
     * <p>
     * If you need any specific configuration, please use {@link #builder(ClientHandshakeCertificates)} instead.
     */
    static @NonNull ClientTlsSocket create(final @NonNull Socket encryptedSocket) {
        Objects.requireNonNull(encryptedSocket);
        return builder(ClientHandshakeCertificates.create())
                .build(encryptedSocket);
    }

    /**
     * Create a new {@link Builder} for a client-side TLS socket using the provided
     * {@link ClientHandshakeCertificates}.
     */
    static @NonNull Builder builder(final @NonNull ClientHandshakeCertificates handshakeCertificates) {
        Objects.requireNonNull(handshakeCertificates);
        return new RealClientTlsSocket.Builder(handshakeCertificates);
    }

    @NonNull
    ClientHandshakeCertificates getHandshakeCertificates();

    /**
     * The builder used to create a {@link ClientTlsSocket} instance.
     */
    sealed interface Builder extends TlsSocket.Builder<Builder, Parameterizer> permits RealClientTlsSocket.Builder {
        @NonNull
        ClientHandshakeCertificates getHandshakeCertificates();

        /**
         * Create a new {@linkplain ClientTlsSocket client-side TLS socket}, it requires an existing
         * {@link Socket} for encrypted bytes (typically, but not necessarily associated with a network socket).
         * <p>
         * If you need TLS parameterization, please use {@link #createParameterizer(Socket)} or
         * {@link #createParameterizer(Socket, String, int)} instead.
         */
        @NonNull
        ClientTlsSocket build(final @NonNull Socket encryptedSocket);
    }

    sealed interface Parameterizer extends TlsSocket.Parameterizer
            permits RealClientTlsSocket.Builder.Parameterizer {
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
         * Create a new {@linkplain ClientTlsSocket client-side TLS socket}.
         */
        @NonNull
        ClientTlsSocket build();
    }
}
