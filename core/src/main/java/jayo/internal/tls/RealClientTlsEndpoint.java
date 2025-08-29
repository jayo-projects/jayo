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
import jayo.internal.RealTlsEndpoint;
import jayo.tls.ClientHandshakeCertificates;
import jayo.tls.ClientTlsEndpoint;
import jayo.tls.Handshake;
import jayo.tls.TlsEndpoint;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.util.List;
import java.util.Objects;

/**
 * A client-side {@link TlsEndpoint}.
 */
public final class RealClientTlsEndpoint implements ClientTlsEndpoint {
    private final @NonNull Endpoint encryptedEndpoint;
    private final @NonNull ClientHandshakeCertificates handshakeCertificates;
    private final @NonNull RealTlsEndpoint impl;

    private Reader reader = null;
    private Writer writer = null;

    private RealClientTlsEndpoint(
            final @NonNull Endpoint encryptedEndpoint,
            final @NonNull ClientHandshakeCertificates handshakeCertificates,
            final boolean waitForCloseConfirmation,
            final @NonNull SSLEngine engine) {
        assert encryptedEndpoint != null;
        assert handshakeCertificates != null;
        assert engine != null;

        this.encryptedEndpoint = encryptedEndpoint;
        this.handshakeCertificates = handshakeCertificates;

        impl = new RealTlsEndpoint(
                encryptedEndpoint,
                engine,
                waitForCloseConfirmation);
    }

    @Override
    public @NonNull Reader getReader() {
        if (reader == null) {
            reader = Jayo.buffer(new ClientTlsEndpointRawReader(impl));
        }
        return reader;
    }

    @Override
    public @NonNull Writer getWriter() {
        if (writer == null) {
            writer = Jayo.buffer(new ClientTlsEndpointRawWriter(impl));
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
    public @NonNull Endpoint getUnderlying() {
        return encryptedEndpoint;
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
    public void close() {
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
     * Builder of {@link RealClientTlsEndpoint}
     */
    public static final class Builder extends RealTlsEndpoint.Builder<ClientTlsEndpoint.Builder, ClientTlsEndpoint.Parameterizer>
            implements ClientTlsEndpoint.Builder {
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
        public @NonNull ClientTlsEndpoint build(final @NonNull Endpoint encryptedEndpoint) {
            Objects.requireNonNull(encryptedEndpoint);

            final var engine = ((RealHandshakeCertificates) handshakeCertificates).getSslContext().createSSLEngine();
            engine.setUseClientMode(true);
            return new RealClientTlsEndpoint(
                    encryptedEndpoint,
                    handshakeCertificates,
                    waitForCloseConfirmation,
                    engine);
        }

        @Override
        public @NonNull Parameterizer createParameterizer(final @NonNull Endpoint encryptedEndpoint) {
            Objects.requireNonNull(encryptedEndpoint);
            final var engine = ((RealHandshakeCertificates) handshakeCertificates).getSslContext()
                    .createSSLEngine();
            engine.setUseClientMode(true);
            return new Parameterizer(encryptedEndpoint, engine);
        }

        @Override
        public @NonNull Parameterizer createParameterizer(final @NonNull Endpoint encryptedEndpoint,
                                                          final @NonNull String peerHost,
                                                          final int peerPort) {
            Objects.requireNonNull(encryptedEndpoint);
            Objects.requireNonNull(peerHost);
            assert peerPort > 0;

            final var engine = ((RealHandshakeCertificates) handshakeCertificates).getSslContext()
                    .createSSLEngine(peerHost, peerPort);
            engine.setUseClientMode(true);
            return new Parameterizer(encryptedEndpoint, engine);
        }

        @Override
        public @NonNull Builder clone() {
            return new Builder(handshakeCertificates, waitForCloseConfirmation);
        }

        public final class Parameterizer extends RealTlsEndpoint.Parameterizer
                implements ClientTlsEndpoint.Parameterizer {
            private final @NonNull Endpoint encryptedEndpoint;

            private Parameterizer(final @NonNull Endpoint encryptedEndpoint, final @NonNull SSLEngine engine) {
                super(engine);
                assert encryptedEndpoint != null;
                this.encryptedEndpoint = encryptedEndpoint;
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
            public @NonNull ClientTlsEndpoint build() {
                return new RealClientTlsEndpoint(
                        encryptedEndpoint,
                        handshakeCertificates,
                        waitForCloseConfirmation,
                        engine);
            }
        }
    }

    private record ClientTlsEndpointRawReader(@NonNull RealTlsEndpoint impl) implements RawReader {
        @Override
        public long readAtMostTo(final @NonNull Buffer writer, final long byteCount) {
            return impl.readAtMostTo(writer, byteCount);
        }

        @Override
        public void close() {
            impl.close();
        }
    }

    private record ClientTlsEndpointRawWriter(@NonNull RealTlsEndpoint impl) implements RawWriter {
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
