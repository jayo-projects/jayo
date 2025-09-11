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

import jayo.*;
import jayo.internal.RealTlsSocket;
import jayo.tls.*;
import jayo.tls.ClientTlsSocket;
import jayo.tls.TlsSocket;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.util.List;
import java.util.Objects;

/**
 * A client-side {@link TlsSocket}.
 */
public final class RealClientTlsSocket implements ClientTlsSocket {
    private final @NonNull Socket encryptedSocket;
    private final @NonNull ClientHandshakeCertificates handshakeCertificates;
    private final @NonNull RealTlsSocket impl;

    private Reader reader = null;
    private Writer writer = null;

    private RealClientTlsSocket(
            final @NonNull Socket encryptedSocket,
            final @NonNull ClientHandshakeCertificates handshakeCertificates,
            final boolean waitForCloseConfirmation,
            final @NonNull SSLEngine engine) {
        assert encryptedSocket != null;
        assert handshakeCertificates != null;
        assert engine != null;

        this.encryptedSocket = encryptedSocket;
        this.handshakeCertificates = handshakeCertificates;

        impl = new RealTlsSocket(
                encryptedSocket,
                engine,
                waitForCloseConfirmation);
    }

    @Override
    public @NonNull Reader getReader() {
        if (reader == null) {
            reader = Jayo.buffer(new ClientTlsSocketRawReader(impl));
        }
        return reader;
    }

    @Override
    public @NonNull Writer getWriter() {
        if (writer == null) {
            writer = Jayo.buffer(new ClientTlsSocketRawWriter(impl));
        }
        return writer;
    }

    @Override
    public @NonNull SSLSession getSession() {
        return impl.getSession();
    }

    @Override
    public @NonNull Handshake getHandshake() {
        return impl.getHandshake();
    }

    @Override
    public @NonNull Socket getUnderlying() {
        return encryptedSocket;
    }

    @Override
    public boolean shutdown() {
        return impl.shutdown();
    }

    @Override
    public boolean isShutdownReceived() {
        return impl.shutdownReceived;
    }

    @Override
    public boolean isShutdownSent() {
        return impl.shutdownSent;
    }

    @Override
    public void cancel() {
        impl.close();
    }

    @Override
    public boolean isOpen() {
        return impl.isOpen();
    }

    @Override
    public @NonNull ClientHandshakeCertificates getHandshakeCertificates() {
        return handshakeCertificates;
    }

    /**
     * Builder of {@link RealClientTlsSocket}
     */
    public static final class Builder extends RealTlsSocket.Builder<ClientTlsSocket.Builder, ClientTlsSocket.Parameterizer>
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

        public final class Parameterizer extends RealTlsSocket.Parameterizer
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

    private record ClientTlsSocketRawReader(@NonNull RealTlsSocket impl) implements RawReader {
        @Override
        public long readAtMostTo(final @NonNull Buffer writer, final long byteCount) {
            return impl.readAtMostTo(writer, byteCount);
        }

        @Override
        public void close() {
            impl.close();
        }
    }

    private record ClientTlsSocketRawWriter(@NonNull RealTlsSocket impl) implements RawWriter {
        @Override
        public void writeFrom(final @NonNull Buffer source, final long byteCount) {
            impl.write(source, byteCount);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            impl.close();
        }
    }
}
