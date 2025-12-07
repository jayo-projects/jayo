/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.tls;

import jayo.RawSocket;
import jayo.Socket;
import jayo.internal.tls.RealServerTlsSocket;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import java.util.Objects;
import java.util.function.Function;

/**
 * The server-side TLS (Transport Layer Security) end of a TLS connection between two peers. {@link ServerTlsSocket}
 * guarantee that the TLS connection is established and the <b>initial handshake was done</b> upon creation.
 *
 * @see TlsSocket
 * @see ClientTlsSocket
 */
public sealed interface ServerTlsSocket extends TlsSocket permits RealServerTlsSocket {
    /**
     * Create a new {@link Builder} for a server-side TLS socket using the provided {@link ServerHandshakeCertificates}.
     */
    static @NonNull Builder builder(final @NonNull ServerHandshakeCertificates handshakeCertificates) {
        Objects.requireNonNull(handshakeCertificates);
        return new RealServerTlsSocket.Builder(handshakeCertificates);
    }

    /**
     * Create a new {@link Builder} for a server-side TLS socket using a custom {@link ServerHandshakeCertificates}
     * factory, which will be used to create the handshake certificates as a function of the SNI received at the TLS
     * connection start.
     *
     * @param handshakeCertificatesFactory a function to select the correct {@link ServerHandshakeCertificates} based on
     *                                     the optional SNI server name provided by the client. A {@code null} SNI
     *                                     server name means that the client did not send a SNI server name. Returning
     *                                     {@code null} indicates that no server certificate is supplied and the TLS
     *                                     connection would then be aborted by throwing a
     *                                     {@link JayoTlsHandshakeException}.
     * @implNote Due to limitations of {@link SSLEngine}, configuring a {@link ServerTlsSocket} to select the
     * {@link ServerHandshakeCertificates} based on the SNI value implies parsing the first TLS frame (ClientHello)
     * independently of the {@link SSLEngine}.
     * @see <a href="https://tools.ietf.org/html/rfc6066#section-3">Server Name Indication</a>
     */
    static @NonNull Builder builder(
            final @NonNull Function<@Nullable SNIServerName, @Nullable ServerHandshakeCertificates>
                    handshakeCertificatesFactory) {
        Objects.requireNonNull(handshakeCertificatesFactory);
        return new RealServerTlsSocket.Builder(handshakeCertificatesFactory);
    }

    @NonNull
    ServerHandshakeCertificates getHandshakeCertificates();

    /**
     * The builder used to create a {@link ServerTlsSocket} instance.
     */
    sealed interface Builder extends TlsSocket.Builder<Builder, Parameterizer> permits RealServerTlsSocket.Builder {
        /**
         * Create a new {@linkplain ServerTlsSocket server-side TLS socket}, it requires an existing
         * {@link Socket} for encrypted bytes (typically, but not necessarily associated with a network socket).
         * <p>
         * If you need TLS parameterization, please use {@link #createParameterizer(RawSocket)} or
         * {@link #createParameterizer(RawSocket, String, int)} instead.
         */
        @NonNull
        ServerTlsSocket build(final @NonNull RawSocket encryptedSocket);
    }

    sealed interface Parameterizer extends TlsSocket.Parameterizer
            permits RealServerTlsSocket.Builder.Parameterizer {
        /**
         * Create a new {@linkplain ServerTlsSocket server-side TLS socket}.
         */
        @NonNull
        ServerTlsSocket build();
    }
}
