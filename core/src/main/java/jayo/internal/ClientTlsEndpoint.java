/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.internal;

import jayo.*;
import jayo.tls.Handshake;
import jayo.tls.JssePlatform;
import jayo.tls.TlsEndpoint;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A client-side {@link TlsEndpoint}.
 */
public final class ClientTlsEndpoint implements TlsEndpoint {
    private final @NonNull Endpoint encryptedEndpoint;
    private final @NonNull RealTlsEndpoint impl;

    private Reader reader = null;
    private Writer writer = null;

    private ClientTlsEndpoint(
            final @NonNull Endpoint encryptedEndpoint,
            final @NonNull SSLEngine engine,
            final @NonNull Consumer<@NonNull SSLSession> sessionInitCallback,
            final boolean waitForCloseConfirmation) {
        assert encryptedEndpoint != null;
        assert sessionInitCallback != null;
        assert engine != null;

        this.encryptedEndpoint = encryptedEndpoint;

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
        return impl.shutdownReceived;
    }

    @Override
    public boolean shutdownSent() {
        return impl.shutdownSent;
    }

    @Override
    public void close() {
        impl.close();
    }

    /**
     * Builder of {@link ClientTlsEndpoint}
     */
    public static final class Builder extends RealTlsEndpoint.Builder<ClientBuilder> implements ClientBuilder {
        private final @NonNull SSLEngine engine;

        public Builder() {
            final var jssePlatform = JssePlatform.get();
            final var trustManager = jssePlatform.getDefaultTrustManager();
            final var sslContext = jssePlatform.newSSLContextWithTrustManager(trustManager);
            engine = sslContext.createSSLEngine();
            engine.setUseClientMode(true);
        }

        public Builder(final @NonNull SSLContext sslContext) {
            assert sslContext != null;

            engine = sslContext.createSSLEngine();
            engine.setUseClientMode(true);
        }

        public Builder(final @NonNull SSLEngine engine) {
            assert engine != null;

            this.engine = engine;
        }

        /**
         * The private constructor used by {@link #clone()}.
         */
        private Builder(final @NonNull SSLEngine engine,
                        final @NonNull Consumer<@NonNull SSLSession> sessionInitCallback,
                        final boolean waitForCloseConfirmation) {
            assert engine != null;
            assert sessionInitCallback != null;

            this.engine = engine;
            this.sessionInitCallback = sessionInitCallback;
            this.waitForCloseConfirmation = waitForCloseConfirmation;
        }

        @Override
        @NonNull
        Builder getThis() {
            return this;
        }

        @Override
        public @NonNull TlsEndpoint build(final @NonNull Endpoint encryptedEndpoint) {
            Objects.requireNonNull(encryptedEndpoint);
            return new ClientTlsEndpoint(
                    encryptedEndpoint,
                    engine,
                    sessionInitCallback,
                    waitForCloseConfirmation);
        }

        @Override
        public @NonNull ClientBuilder clone() {
            return new Builder(engine, sessionInitCallback, waitForCloseConfirmation);
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
