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
import jayo.tls.*;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.util.Objects;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

/**
 * A client-side {@link TlsEndpoint}.
 */
public final class RealClientTlsEndpoint implements ClientTlsEndpoint {
    private static final System.Logger LOGGER = System.getLogger("jayo.tls.ClientTlsEndpoint");

    private final @NonNull Endpoint encryptedEndpoint;
    private final @NonNull ClientHandshakeCertificates handshakeCertificates;
    private final @NonNull RealTlsEndpoint impl;

    private Reader reader = null;
    private Writer writer = null;

    private RealClientTlsEndpoint(
            final @NonNull Endpoint encryptedEndpoint,
            final @NonNull ClientHandshakeCertificates handshakeCertificates,
            final @NonNull Consumer<@NonNull SSLEngine> sslEngineCustomizer,
            final @NonNull Consumer<@NonNull SSLSession> sessionInitCallback,
            final boolean waitForCloseConfirmation) {
        assert encryptedEndpoint != null;
        assert handshakeCertificates != null;
        assert sslEngineCustomizer != null;
        assert sessionInitCallback != null;

        this.encryptedEndpoint = encryptedEndpoint;
        this.handshakeCertificates = handshakeCertificates;

        final var engine = ((RealHandshakeCertificates) handshakeCertificates).sslContext().createSSLEngine();
        engine.setUseClientMode(true);

        // call customizer
        try {
            sslEngineCustomizer.accept(engine);
        } catch (Exception e) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "Client threw exception in SSLEngine customizer.", e);
            } else if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "Client threw exception in SSLEngine customizer: {0}.", e.getMessage());
            }
            throw new JayoTlsHandshakeCallbackException("SSLEngine customizer failed", e);
        }

        impl = new RealTlsEndpoint(
                encryptedEndpoint,
                engine,
                sessionInitCallback,
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
    public boolean shutdownReceived() {
        return impl.shutdownReceived();
    }

    @Override
    public boolean shutdownSent() {
        return impl.shutdownSent();
    }

    @Override
    public void close() {
        impl.close();
    }

    @Override
    public @NonNull ClientHandshakeCertificates getHandshakeCertificates() {
        return handshakeCertificates;
    }

    /**
     * Builder of {@link RealClientTlsEndpoint}
     */
    public static final class Builder extends RealTlsEndpoint.Builder<ClientTlsEndpoint.Builder>
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
                        final @NonNull Consumer<@NonNull SSLEngine> sslEngineCustomizer,
                        final @NonNull Consumer<@NonNull SSLSession> sessionInitCallback,
                        final boolean waitForCloseConfirmation) {
            assert handshakeCertificates != null;
            assert sslEngineCustomizer != null;
            assert sessionInitCallback != null;

            this.handshakeCertificates = handshakeCertificates;
            this.sslEngineCustomizer = sslEngineCustomizer;
            this.sessionInitCallback = sessionInitCallback;
            this.waitForCloseConfirmation = waitForCloseConfirmation;
        }

        @Override
        protected @NonNull Builder getThis() {
            return this;
        }

        @Override
        public @NonNull ClientTlsEndpoint build(final @NonNull Endpoint encryptedEndpoint) {
            Objects.requireNonNull(encryptedEndpoint);
            return new RealClientTlsEndpoint(
                    encryptedEndpoint,
                    handshakeCertificates,
                    sslEngineCustomizer,
                    sessionInitCallback,
                    waitForCloseConfirmation);
        }

        @Override
        public @NonNull Builder clone() {
            return new Builder(handshakeCertificates, sslEngineCustomizer, sessionInitCallback,
                    waitForCloseConfirmation);
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
        public void write(final @NonNull Buffer reader, final long byteCount) {
            impl.write(reader, byteCount);
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
