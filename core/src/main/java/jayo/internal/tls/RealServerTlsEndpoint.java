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
public final class RealServerTlsEndpoint implements ServerTlsEndpoint {
    private static final System.Logger LOGGER = System.getLogger("jayo.tls.ServerTlsEndpoint");

    private final @NonNull Endpoint encryptedEndpoint;
    private final @NonNull ServerHandshakeCertificates handshakeCertificates;
    private final @NonNull RealTlsEndpoint impl;

    private Reader reader = null;
    private Writer writer = null;

    private RealServerTlsEndpoint(
            final @NonNull Endpoint encryptedEndpoint,
            final @NonNull HandshakeCertificatesStrategy handshakeCertificatesStrategy,
            final @NonNull Function<@NonNull SSLContext, @NonNull SSLEngine> engineFactory,
            final @NonNull Consumer<SSLSession> sessionInitCallback,
            final boolean waitForCloseConfirmation) {
        assert encryptedEndpoint != null;
        assert handshakeCertificatesStrategy != null;
        assert engineFactory != null;
        assert sessionInitCallback != null;

        this.encryptedEndpoint = encryptedEndpoint;
        try {
            handshakeCertificates =
                    handshakeCertificatesStrategy.getHandshakeCertificates(this::getServerNameIndication);
            final var context = ((RealHandshakeCertificates) handshakeCertificates).sslContext();
            // call client code
            final SSLEngine engine;
            try {
                engine = engineFactory.apply(context);
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
    public @NonNull ServerHandshakeCertificates getHandshakeCertificates() {
        return handshakeCertificates;
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

    private static @NonNull SSLEngine defaultSSLEngineFactory(final @NonNull SSLContext sslContext) {
        assert sslContext != null;
        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);
        return engine;
    }

    /**
     * Builder of {@link RealServerTlsEndpoint}
     */
    public static final class Builder extends RealTlsEndpoint.Builder<ServerTlsEndpoint.Builder>
            implements ServerTlsEndpoint.Builder {
        private final @NonNull HandshakeCertificatesStrategy internalHandshakeCertificatesFactory;


        public Builder(final @NonNull ServerHandshakeCertificates handshakeCertificates) {
            assert handshakeCertificates != null;
            this.internalHandshakeCertificatesFactory = new FixedHandshakeCertificatesStrategy(handshakeCertificates);
        }

        public Builder(final @NonNull Function<@Nullable SNIServerName, @Nullable ServerHandshakeCertificates>
                               handshakeCertificatesFactory) {
            assert handshakeCertificatesFactory != null;
            this.internalHandshakeCertificatesFactory = new SniHandshakeCertificatesStrategy(handshakeCertificatesFactory);
        }

        /**
         * The private constructor used by {@link #clone()}.
         */
        private Builder(final @NonNull HandshakeCertificatesStrategy internalHandshakeCertificatesFactory,
                        final @Nullable Function<@NonNull SSLContext, @NonNull SSLEngine> sslEngineFactory,
                        final @NonNull Consumer<@NonNull SSLSession> sessionInitCallback,
                        final boolean waitForCloseConfirmation) {
            assert internalHandshakeCertificatesFactory != null;
            assert sessionInitCallback != null;

            this.internalHandshakeCertificatesFactory = internalHandshakeCertificatesFactory;
            this.sslEngineFactory = sslEngineFactory;
            this.sessionInitCallback = sessionInitCallback;
            this.waitForCloseConfirmation = waitForCloseConfirmation;
        }

        @Override
        protected @NonNull Builder getThis() {
            return this;
        }

        @Override
        public @NonNull ServerTlsEndpoint build(final @NonNull Endpoint encryptedEndpoint) {
            Objects.requireNonNull(encryptedEndpoint);
            return new RealServerTlsEndpoint(
                    encryptedEndpoint,
                    internalHandshakeCertificatesFactory,
                    (sslEngineFactory != null) ? sslEngineFactory : RealServerTlsEndpoint::defaultSSLEngineFactory,
                    sessionInitCallback,
                    waitForCloseConfirmation);
        }

        @Override
        public @NonNull Builder clone() {
            return new Builder(internalHandshakeCertificatesFactory, sslEngineFactory, sessionInitCallback, waitForCloseConfirmation);
        }
    }
}
