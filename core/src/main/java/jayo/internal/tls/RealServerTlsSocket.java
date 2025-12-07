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

import jayo.Jayo;
import jayo.JayoException;
import jayo.RawSocket;
import jayo.Socket;
import jayo.internal.AbstractTlsSocket;
import jayo.tls.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.StandardConstants;
import java.util.Objects;
import java.util.function.Function;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

/**
 * A server-side {@link TlsSocket}.
 */
public final class RealServerTlsSocket extends AbstractTlsSocket implements ServerTlsSocket {
    private static final System.Logger LOGGER = System.getLogger("jayo.tls.ServerTlsSocket");

    private final @NonNull ServerHandshakeCertificates handshakeCertificates;

    private RealServerTlsSocket(
            final @NonNull Socket encryptedSocket,
            final @NonNull ServerHandshakeCertificates handshakeCertificates,
            final boolean waitForCloseConfirmation,
            final @NonNull SSLEngine engine) {
        super(encryptedSocket, engine, waitForCloseConfirmation);
        assert handshakeCertificates != null;

        this.handshakeCertificates = handshakeCertificates;
    }

    @Override
    public @NonNull ServerHandshakeCertificates getHandshakeCertificates() {
        return handshakeCertificates;
    }

    private sealed interface HandshakeCertificatesStrategy {
        @FunctionalInterface
        interface SniReader {
            @Nullable
            SNIServerName readSni();
        }

        @NonNull
        ServerHandshakeCertificates getHandshakeCertificates(final @NonNull SniReader sniReader);
    }

    private record SniHandshakeCertificatesStrategy(
            @NonNull Function<@Nullable SNIServerName, @Nullable ServerHandshakeCertificates>
            handshakeCertificatesFactory
    ) implements HandshakeCertificatesStrategy {
        @Override
        public @NonNull ServerHandshakeCertificates getHandshakeCertificates(final @NonNull SniReader sniReader) {
            assert sniReader != null;

            // IO block
            final var nameOpt = sniReader.readSni();
            // call client code
            final ServerHandshakeCertificates chosenHandshakeCertificates;
            try {
                chosenHandshakeCertificates = handshakeCertificatesFactory.apply(nameOpt);
            } catch (Exception e) {
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, "Client code threw exception during evaluation of server name indication.",
                            e);
                } else if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG,
                            "Client code threw exception during evaluation of server name indication: {0}.",
                            e.getMessage());
                }
                throw new JayoTlsHandshakeCallbackException("SNI callback failed", e);
            }

            if (chosenHandshakeCertificates == null) {
                throw new JayoTlsHandshakeException("No TLS context available for received SNI: " + nameOpt);
            }
            return chosenHandshakeCertificates;
        }
    }

    private record FixedHandshakeCertificatesStrategy(
            @NonNull ServerHandshakeCertificates handshakeCertificates
    ) implements HandshakeCertificatesStrategy {
        @Override
        public @NonNull ServerHandshakeCertificates getHandshakeCertificates(final @NonNull SniReader sniReader) {
            // Avoid SNI parsing (using the supplied sniReader) when no decision would be made based on it.
            return handshakeCertificates;
        }
    }

    /**
     * Builder of {@link RealServerTlsSocket}
     */
    public static final class Builder extends AbstractTlsSocket.Builder<ServerTlsSocket.Builder, ServerTlsSocket.Parameterizer>
            implements ServerTlsSocket.Builder {
        private final @NonNull HandshakeCertificatesStrategy handshakeCertificatesStrategy;

        public Builder(final @NonNull ServerHandshakeCertificates handshakeCertificates) {
            assert handshakeCertificates != null;
            this.handshakeCertificatesStrategy = new FixedHandshakeCertificatesStrategy(handshakeCertificates);
        }

        public Builder(final @NonNull Function<@Nullable SNIServerName, @Nullable ServerHandshakeCertificates>
                               handshakeCertificatesFactory) {
            assert handshakeCertificatesFactory != null;
            this.handshakeCertificatesStrategy = new SniHandshakeCertificatesStrategy(handshakeCertificatesFactory);
        }

        /**
         * The private constructor used by {@link #clone()}.
         */
        private Builder(final @NonNull HandshakeCertificatesStrategy internalHandshakeCertificatesFactory,
                        final boolean waitForCloseConfirmation) {
            assert internalHandshakeCertificatesFactory != null;

            this.handshakeCertificatesStrategy = internalHandshakeCertificatesFactory;
            this.waitForCloseConfirmation = waitForCloseConfirmation;
        }

        @Override
        protected @NonNull Builder getThis() {
            return this;
        }

        @Override
        public @NonNull ServerTlsSocket build(final @NonNull RawSocket encryptedSocket) {
            Objects.requireNonNull(encryptedSocket);

            final var socket = Jayo.buffer(encryptedSocket);

            final var handshakeCertificates = handshakeCertificatesStrategy.getHandshakeCertificates(() ->
                    getServerNameIndication(socket));

            final var engine = ((RealHandshakeCertificates) handshakeCertificates).getSslContext().createSSLEngine();
            engine.setUseClientMode(false);
            return new RealServerTlsSocket(
                    socket,
                    handshakeCertificates,
                    waitForCloseConfirmation,
                    engine);
        }

        @Override
        public @NonNull Parameterizer createParameterizer(final @NonNull RawSocket encryptedSocket) {
            Objects.requireNonNull(encryptedSocket);

            final var socket = Jayo.buffer(encryptedSocket);

            final var handshakeCertificates = handshakeCertificatesStrategy.getHandshakeCertificates(() ->
                    getServerNameIndication(socket));
            final var engine = ((RealHandshakeCertificates) handshakeCertificates).getSslContext()
                    .createSSLEngine();
            engine.setUseClientMode(false);
            return new Parameterizer(socket, handshakeCertificates, engine);
        }

        @Override
        public @NonNull Parameterizer createParameterizer(final @NonNull RawSocket encryptedSocket,
                                                          final @NonNull String peerHost,
                                                          final int peerPort) {
            Objects.requireNonNull(encryptedSocket);
            Objects.requireNonNull(peerHost);
            assert peerPort > 0;

            final var socket = Jayo.buffer(encryptedSocket);

            final var handshakeCertificates = handshakeCertificatesStrategy.getHandshakeCertificates(() ->
                    getServerNameIndication(socket));
            final var engine = ((RealHandshakeCertificates) handshakeCertificates).getSslContext()
                    .createSSLEngine(peerHost, peerPort);
            engine.setUseClientMode(false);
            return new Parameterizer(socket, handshakeCertificates, engine);
        }

        private @Nullable SNIServerName getServerNameIndication(final @NonNull Socket encryptedSocket) {
            try {
                final var serverNames = TlsExplorer.exploreTlsRecord(encryptedSocket.getReader().peek());
                final var hostName = serverNames.get(StandardConstants.SNI_HOST_NAME);
                return (hostName instanceof SNIHostName) ? hostName : null;
            } catch (JayoException e) {
                encryptedSocket.cancel();
                throw e;
            }
        }

        @Override
        public @NonNull Builder clone() {
            return new Builder(handshakeCertificatesStrategy, waitForCloseConfirmation);
        }

        public final class Parameterizer extends AbstractTlsSocket.Parameterizer
                implements ServerTlsSocket.Parameterizer {
            private final @NonNull Socket encryptedSocket;
            private final @NonNull ServerHandshakeCertificates handshakeCertificates;

            private Parameterizer(final @NonNull Socket encryptedSocket,
                                  final @NonNull ServerHandshakeCertificates handshakeCertificates,
                                  final @NonNull SSLEngine engine) {
                super(engine);
                assert encryptedSocket != null;
                assert handshakeCertificates != null;

                this.encryptedSocket = encryptedSocket;
                this.handshakeCertificates = handshakeCertificates;
            }

            @Override
            public @NonNull ServerTlsSocket build() {
                Objects.requireNonNull(encryptedSocket);
                return new RealServerTlsSocket(
                        encryptedSocket,
                        handshakeCertificates,
                        waitForCloseConfirmation,
                        engine);
            }
        }
    }
}
