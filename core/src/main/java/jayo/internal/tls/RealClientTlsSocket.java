/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.internal.tls;

import jayo.Socket;
import jayo.internal.AbstractTlsSocket;
import jayo.tls.ClientHandshakeCertificates;
import jayo.tls.ClientTlsSocket;
import jayo.tls.TlsSocket;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import java.util.List;
import java.util.Objects;

/**
 * A client-side {@link TlsSocket}.
 */
public final class RealClientTlsSocket extends AbstractTlsSocket implements ClientTlsSocket {
    private final @NonNull ClientHandshakeCertificates handshakeCertificates;

    private RealClientTlsSocket(
            final @NonNull Socket encryptedSocket,
            final @NonNull ClientHandshakeCertificates handshakeCertificates,
            final boolean waitForCloseConfirmation,
            final @NonNull SSLEngine engine) {
        super(encryptedSocket, engine, waitForCloseConfirmation);
        assert handshakeCertificates != null;

        this.handshakeCertificates = handshakeCertificates;
    }

    @Override
    public @NonNull ClientHandshakeCertificates getHandshakeCertificates() {
        return handshakeCertificates;
    }

    /**
     * Builder of {@link RealClientTlsSocket}
     */
    public static final class Builder extends AbstractTlsSocket.Builder<ClientTlsSocket.Builder, ClientTlsSocket.Parameterizer>
            implements ClientTlsSocket.Builder {
        private final @NonNull ClientHandshakeCertificates handshakeCertificates;

        public Builder(final @NonNull ClientHandshakeCertificates handshakeCertificates) {
            assert handshakeCertificates != null;
            this.handshakeCertificates = handshakeCertificates;
        }

        /**
         * The private constructor used by {@link #clone()}.
         */
        private Builder(final @NonNull ClientHandshakeCertificates handshakeCertificates,
                        final boolean waitForCloseConfirmation) {
            assert handshakeCertificates != null;

            this.handshakeCertificates = handshakeCertificates;
            this.waitForCloseConfirmation = waitForCloseConfirmation;
        }

        @Override
        public @NonNull ClientHandshakeCertificates getHandshakeCertificates() {
            return handshakeCertificates;
        }

        @Override
        protected @NonNull Builder getThis() {
            return this;
        }

        @Override
        public @NonNull ClientTlsSocket build(final @NonNull Socket encryptedSocket) {
            Objects.requireNonNull(encryptedSocket);

            final var engine = ((RealHandshakeCertificates) handshakeCertificates).getSslContext().createSSLEngine();
            engine.setUseClientMode(true);
            return new RealClientTlsSocket(
                    encryptedSocket,
                    handshakeCertificates,
                    waitForCloseConfirmation,
                    engine);
        }

        @Override
        public @NonNull Parameterizer createParameterizer(final @NonNull Socket encryptedSocket) {
            Objects.requireNonNull(encryptedSocket);
            final var engine = ((RealHandshakeCertificates) handshakeCertificates).getSslContext()
                    .createSSLEngine();
            engine.setUseClientMode(true);
            return new Parameterizer(encryptedSocket, engine);
        }

        @Override
        public @NonNull Parameterizer createParameterizer(final @NonNull Socket encryptedSocket,
                                                          final @NonNull String peerHost,
                                                          final int peerPort) {
            Objects.requireNonNull(encryptedSocket);
            Objects.requireNonNull(peerHost);
            assert peerPort > 0;

            final var engine = ((RealHandshakeCertificates) handshakeCertificates).getSslContext()
                    .createSSLEngine(peerHost, peerPort);
            engine.setUseClientMode(true);
            return new Parameterizer(encryptedSocket, engine);
        }

        @Override
        public @NonNull Builder clone() {
            return new Builder(handshakeCertificates, waitForCloseConfirmation);
        }

        public final class Parameterizer extends AbstractTlsSocket.Parameterizer
                implements ClientTlsSocket.Parameterizer {
            private final @NonNull Socket encryptedSocket;

            private Parameterizer(final @NonNull Socket encryptedSocket, final @NonNull SSLEngine engine) {
                super(engine);
                assert encryptedSocket != null;
                this.encryptedSocket = encryptedSocket;
            }

            @Override
            public @NonNull List<@NonNull SNIServerName> getServerNames() {
                final var serverNames = engine.getSSLParameters().getServerNames();
                return (serverNames != null) ? serverNames : List.of();
            }

            @Override
            public void setServerNames(@NonNull List<@NonNull SNIServerName> serverNames) {
                Objects.requireNonNull(serverNames);

                final var sslParameters = engine.getSSLParameters();
                sslParameters.setServerNames(serverNames);
                engine.setSSLParameters(sslParameters);
            }

            @Override
            public @NonNull ClientTlsSocket build() {
                return new RealClientTlsSocket(
                        encryptedSocket,
                        handshakeCertificates,
                        waitForCloseConfirmation,
                        engine);
            }
        }
    }
}
