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
import jayo.internal.tls.TlsExplorer;
import jayo.tls.Handshake;
import jayo.tls.JayoTlsHandshakeCallbackException;
import jayo.tls.JayoTlsHandshakeException;
import jayo.tls.TlsEndpoint;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.*;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

/**
 * A server-side {@link TlsEndpoint}.
 */
public final class ServerTlsEndpoint implements TlsEndpoint {
    private static final System.Logger LOGGER = System.getLogger("jayo.tls.ServerTlsEndpoint");

    private final @NonNull Endpoint encryptedEndpoint;
    private final @NonNull RealTlsEndpoint impl;

    private Reader reader = null;
    private Writer writer = null;

    private ServerTlsEndpoint(
            final @NonNull Endpoint encryptedEndpoint,
            final @NonNull SslContextStrategy sslContextStrategy,
            final @NonNull Function<@NonNull SSLContext, @NonNull SSLEngine> engineFactory,
            final @NonNull Consumer<SSLSession> sessionInitCallback,
            final boolean waitForCloseConfirmation) {
        assert encryptedEndpoint != null;
        assert sslContextStrategy != null;
        assert engineFactory != null;
        assert sessionInitCallback != null;

        this.encryptedEndpoint = encryptedEndpoint;
        try {
            final var sslContext = sslContextStrategy.getSslContext(this::getServerNameIndication);
            // call client code
            final SSLEngine engine;
            try {
                engine = engineFactory.apply(sslContext);
            } catch (Exception e) {
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, "Client threw exception in SSLEngine factory.", e);
                } else if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "Client threw exception in SSLEngine factory: {0}.",
                            e.getMessage());
                }
                throw new JayoTlsHandshakeCallbackException("SSLEngine creation callback failed", e);
            }
            impl = new RealTlsEndpoint(
                    encryptedEndpoint,
                    engine,
                    sessionInitCallback,
                    waitForCloseConfirmation);
        } catch (JayoException e) {
            encryptedEndpoint.close();
            throw e;
        }
    }

    @Override
    public @NonNull Reader getReader() {
        if (reader == null) {
            reader = Jayo.buffer(new ServerTlsEndpointRawReader(impl));
        }
        return reader;
    }

    @Override
    public @NonNull Writer getWriter() {
        if (writer == null) {
            writer = Jayo.buffer(new ServerTlsEndpointRawWriter(impl));
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

    private @Nullable SNIServerName getServerNameIndication() {
        final var serverNames = TlsExplorer.exploreTlsRecord(encryptedEndpoint.getReader().peek());
        final var hostName = serverNames.get(StandardConstants.SNI_HOST_NAME);
        return (hostName instanceof SNIHostName) ? hostName : null;
    }

    private record ServerTlsEndpointRawReader(@NonNull RealTlsEndpoint impl) implements RawReader {
        @Override
        public long readAtMostTo(final @NonNull Buffer writer, final long byteCount) {
            return impl.readAtMostTo(writer, byteCount);
        }

        @Override
        public void close() {
            impl.close();
        }
    }

    private record ServerTlsEndpointRawWriter(@NonNull RealTlsEndpoint impl) implements RawWriter {
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

    private sealed interface SslContextStrategy {
        @FunctionalInterface
        interface SniReader {
            @Nullable
            SNIServerName readSni();
        }

        @NonNull
        SSLContext getSslContext(final @NonNull SniReader sniReader);
    }

    private record SniSslContextStrategy(
            @NonNull Function<@Nullable SNIServerName, @Nullable SSLContext> sniSslCF) implements SslContextStrategy {
        @Override
        public @NonNull SSLContext getSslContext(final @NonNull SniReader sniReader) {
            assert sniReader != null;

            // IO block
            final var nameOpt = sniReader.readSni();
            // call client code
            final SSLContext chosenContext;
            try {
                chosenContext = sniSslCF.apply(nameOpt);
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

            if (chosenContext == null) {
                throw new JayoTlsHandshakeException("No TLS context available for received SNI: " + nameOpt);
            }
            return chosenContext;
        }
    }

    private record FixedSslContextStrategy(@NonNull SSLContext sslContext) implements SslContextStrategy {
        @Override
        public @NonNull SSLContext getSslContext(final @NonNull SniReader sniReader) {
            // Avoid SNI parsing (using the supplied sniReader) when no decision would be made based on it.
            return sslContext;
        }
    }

    private static @NonNull SSLEngine defaultSSLEngineFactory(final @NonNull SSLContext sslContext) {
        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);
        return engine;
    }

    /**
     * Builder of {@link ServerTlsEndpoint}
     */
    public static final class Builder extends RealTlsEndpoint.Builder<ServerBuilder> implements ServerBuilder {
        private final @NonNull SslContextStrategy internalSslContextFactory;
        private @Nullable Function<@NonNull SSLContext, @NonNull SSLEngine> sslEngineFactory = null;

        public Builder(final @NonNull SSLContext sslContext) {
            assert sslContext != null;
            this.internalSslContextFactory = new FixedSslContextStrategy(sslContext);
        }

        public Builder(final @NonNull Function<@Nullable SNIServerName, @Nullable SSLContext> sniSslCF) {
            assert sniSslCF != null;
            this.internalSslContextFactory = new SniSslContextStrategy(sniSslCF);
        }

        /**
         * The private constructor used by {@link #clone()}.
         */
        private Builder(final @NonNull SslContextStrategy internalSslContextFactory,
                        final @Nullable Function<@NonNull SSLContext, @NonNull SSLEngine> sslEngineFactory,
                        final @NonNull Consumer<@NonNull SSLSession> sessionInitCallback,
                        final boolean waitForCloseConfirmation) {
            assert internalSslContextFactory != null;
            assert sessionInitCallback != null;

            this.internalSslContextFactory = internalSslContextFactory;
            this.sslEngineFactory = sslEngineFactory;
            this.sessionInitCallback = sessionInitCallback;
            this.waitForCloseConfirmation = waitForCloseConfirmation;
        }

        @Override
        @NonNull
        ServerBuilder getThis() {
            return this;
        }

        @Override
        public @NonNull ServerBuilder engineFactory(
                final @NonNull Function<@NonNull SSLContext, @NonNull SSLEngine> sslEngineFactory) {
            this.sslEngineFactory = Objects.requireNonNull(sslEngineFactory);
            return this;
        }

        @Override
        public @NonNull TlsEndpoint build(final @NonNull Endpoint encryptedEndpoint) {
            Objects.requireNonNull(encryptedEndpoint);
            return new ServerTlsEndpoint(
                    encryptedEndpoint,
                    internalSslContextFactory,
                    (sslEngineFactory != null) ? sslEngineFactory : ServerTlsEndpoint::defaultSSLEngineFactory,
                    sessionInitCallback,
                    waitForCloseConfirmation);
        }

        @Override
        public @NonNull ServerBuilder clone() {
            return new Builder(internalSslContextFactory, sslEngineFactory, sessionInitCallback, waitForCloseConfirmation);
        }
    }
}
