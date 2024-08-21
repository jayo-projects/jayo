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

import jayo.Buffer;
import jayo.RawReader;
import jayo.RawWriter;
import jayo.endpoints.Endpoint;
import jayo.external.NonNegative;
import jayo.tls.TlsEndpoint;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Consumer;

/**
 * A client-side {@link TlsEndpoint}.
 */
public final class ClientTlsEndpoint implements TlsEndpoint {
    private final @NonNull Endpoint encryptedEndpoint;
    private final @Nullable SSLContext sslContext;
    private final @NonNull RealTlsEndpoint impl;

    @SuppressWarnings("FieldMayBeFinal")
    private volatile RawReader reader = null;
    @SuppressWarnings("FieldMayBeFinal")
    private volatile RawWriter writer = null;

    // VarHandle mechanics
    private static final VarHandle READER;
    private static final VarHandle WRITER;

    static {
        try {
            final var l = MethodHandles.lookup();
            READER = l.findVarHandle(ClientTlsEndpoint.class, "reader", RawReader.class);
            WRITER = l.findVarHandle(ClientTlsEndpoint.class, "writer", RawWriter.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ClientTlsEndpoint(
            final @NonNull Endpoint encryptedEndpoint,
            final @Nullable SSLContext sslContext,
            final @NonNull SSLEngine engine,
            final @NonNull Consumer<@NonNull SSLSession> sessionInitCallback,
            final boolean waitForCloseConfirmation) {
        assert encryptedEndpoint != null;
        assert sessionInitCallback != null;
        assert engine != null;

        this.encryptedEndpoint = encryptedEndpoint;
        this.sslContext = sslContext;

        impl = new RealTlsEndpoint(
                encryptedEndpoint,
                engine,
                null,
                sessionInitCallback,
                waitForCloseConfirmation);
    }

    @Override
    public @NonNull RawReader getReader() {
        var reader = this.reader;
        if (reader == null) {
            reader = new ClientTlsEndpointRawReader(impl);
            if (!READER.compareAndSet(this, null, reader)) {
                reader = this.reader;
            }
        }
        return reader;
    }

    @Override
    public @NonNull RawWriter getWriter() {
        var writer = this.writer;
        if (writer == null) {
            writer = new ClientTlsEndpointRawWriter(impl);
            if (!WRITER.compareAndSet(this, null, writer)) {
                writer = this.writer;
            }
        }
        return writer;
    }

    @Override
    public void renegotiate() {
        impl.renegotiate();
    }

    @Override
    public void handshake() {
        impl.handshake();
    }

    @Override
    public @Nullable SSLContext getSslContext() {
        return sslContext;
    }

    @Override
    public @NonNull SSLEngine getSslEngine() {
        return impl.engine;
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
        private final @Nullable SSLContext sslContext;
        private final @NonNull SSLEngine engine;

        public Builder(final @NonNull Endpoint encryptedEndpoint, final @NonNull SSLContext sslContext) {
            super(encryptedEndpoint);
            assert sslContext != null;

            this.sslContext = sslContext;
            engine = sslContext.createSSLEngine();
            engine.setUseClientMode(true);
        }

        public Builder(final @NonNull Endpoint encryptedEndpoint, final @NonNull SSLEngine engine) {
            super(encryptedEndpoint);
            assert engine != null;

            this.sslContext = null;
            this.engine = engine;
        }

        @Override
        @NonNull
        Builder getThis() {
            return this;
        }

        @Override
        public @NonNull TlsEndpoint build() {
            return new ClientTlsEndpoint(
                    encryptedEndpoint,
                    sslContext,
                    engine,
                    sessionInitCallback,
                    waitForCloseConfirmation);
        }
    }

    private record ClientTlsEndpointRawReader(@NonNull RealTlsEndpoint impl) implements RawReader {
        @Override
        public long readAtMostTo(final @NonNull Buffer writer, final @NonNegative long byteCount) {
            return impl.readAtMostTo(writer, byteCount);
        }

        @Override
        public void close() {
            impl.close();
        }
    }

    private record ClientTlsEndpointRawWriter(@NonNull RealTlsEndpoint impl) implements RawWriter {
        @Override
        public void write(final @NonNull Buffer reader, final @NonNegative long byteCount) {
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
