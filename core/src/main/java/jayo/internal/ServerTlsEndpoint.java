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
import jayo.Endpoint;
import jayo.JayoClosedResourceException;
import jayo.JayoEOFException;
import jayo.external.NonNegative;
import jayo.tls.JayoTlsHandshakeCallbackException;
import jayo.tls.JayoTlsHandshakeException;
import jayo.tls.TlsEndpoint;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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
    private final @NonNull SslContextStrategy sslContextStrategy;
    private final @NonNull Function<@NonNull SSLContext, @NonNull SSLEngine> engineFactory;
    private final @NonNull Consumer<@NonNull SSLSession> sessionInitCallback;
    private final boolean waitForCloseConfirmation;

    private final @NonNull Lock initLock = new ReentrantLock();

    private final @NonNull RealReader encryptedReader;

    private volatile boolean sniRead = false;
    private @Nullable SSLContext sslContext = null;
    private @Nullable RealTlsEndpoint impl = null;

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
            READER = l.findVarHandle(ServerTlsEndpoint.class, "reader", RawReader.class);
            WRITER = l.findVarHandle(ServerTlsEndpoint.class, "writer", RawWriter.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

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
        this.sslContextStrategy = sslContextStrategy;
        this.engineFactory = engineFactory;
        this.sessionInitCallback = sessionInitCallback;
        this.waitForCloseConfirmation = waitForCloseConfirmation;
        encryptedReader = new RealReader(encryptedEndpoint.getReader());
    }

    @Override
    public @NonNull RawReader getReader() {
        var reader = this.reader;
        if (reader == null) {
            reader = new ServerTlsEndpointRawReader(this);
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
            writer = new ServerTlsEndpointRawWriter(this);
            if (!WRITER.compareAndSet(this, null, writer)) {
                writer = this.writer;
            }
        }
        return writer;
    }

    @Override
    public void renegotiate() {
        if (!sniRead) {
            try {
                initEngine();
            } catch (JayoEOFException e) {
                throw new JayoClosedResourceException();
            }
        }
        assert impl != null;
        impl.renegotiate();
    }

    @Override
    public void handshake() {
        if (!sniRead) {
            try {
                initEngine();
            } catch (JayoEOFException e) {
                throw new JayoClosedResourceException();
            }
        }
        assert impl != null;
        impl.handshake();
    }

    @Override
    public @Nullable SSLContext getSslContext() {
        return sslContext;
    }

    @Override
    public @Nullable SSLEngine getSslEngine() {
        if (impl != null) {
            return impl.engine;
        }
        return null;
    }

    @Override
    public @NonNull Endpoint getUnderlying() {
        return encryptedEndpoint;
    }

    @Override
    public boolean shutdown() {
        return impl != null && impl.shutdown();
    }

    @Override
    public boolean shutdownReceived() {
        return impl != null && impl.shutdownReceived;
    }

    @Override
    public boolean shutdownSent() {
        return impl != null && impl.shutdownSent;
    }

    @Override
    public void close() {
        if (impl != null) {
            impl.close();
        }
        encryptedEndpoint.close();
    }

    private void initEngine() {
        initLock.lock();
        try {
            if (!sniRead) {
                sslContext = sslContextStrategy.getSslContext(this::getServerNameIndication);
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
                        encryptedReader,
                        sessionInitCallback,
                        waitForCloseConfirmation);
                sniRead = true;
            }
        } finally {
            initLock.unlock();
        }
    }

    private @Nullable SNIServerName getServerNameIndication() {
        final var serverNames = TlsExplorer.exploreTlsRecord(encryptedReader.peek());
        final var hostName = serverNames.get(StandardConstants.SNI_HOST_NAME);
        return (hostName instanceof SNIHostName) ? hostName : null;
    }

    private record ServerTlsEndpointRawReader(@NonNull ServerTlsEndpoint serverTlsEndpoint) implements RawReader {
        @Override
        public long readAtMostTo(final @NonNull Buffer writer, final @NonNegative long byteCount) {
            if (!serverTlsEndpoint.sniRead) {
                try {
                    serverTlsEndpoint.initEngine();
                } catch (JayoEOFException e) {
                    return -1;
                }
            }
            assert serverTlsEndpoint.impl != null;
            return serverTlsEndpoint.impl.readAtMostTo(writer, byteCount);
        }

        @Override
        public void close() {
            serverTlsEndpoint.close();
        }
    }

    private record ServerTlsEndpointRawWriter(@NonNull ServerTlsEndpoint serverTlsEndpoint) implements RawWriter {
        @Override
        public void write(final @NonNull Buffer reader, final @NonNegative long byteCount) {
            if (!serverTlsEndpoint.sniRead) {
                try {
                    serverTlsEndpoint.initEngine();
                } catch (JayoEOFException e) {
                    throw new JayoClosedResourceException();
                }
            }
            assert serverTlsEndpoint.impl != null;
            serverTlsEndpoint.impl.write(reader, byteCount);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            serverTlsEndpoint.close();
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

        public Builder(final @NonNull Endpoint encryptedEndpoint, final @NonNull SSLContext sslContext) {
            super(encryptedEndpoint);
            assert sslContext != null;
            this.internalSslContextFactory = new FixedSslContextStrategy(sslContext);
        }

        public Builder(final @NonNull Endpoint encryptedEndpoint,
                       final @NonNull Function<@Nullable SNIServerName, @Nullable SSLContext> sniSslCF) {
            super(encryptedEndpoint);
            assert sniSslCF != null;
            this.internalSslContextFactory = new SniSslContextStrategy(sniSslCF);
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
        public @NonNull TlsEndpoint build() {
            return new ServerTlsEndpoint(
                    encryptedEndpoint,
                    internalSslContextFactory,
                    (sslEngineFactory != null) ? sslEngineFactory : ServerTlsEndpoint::defaultSSLEngineFactory,
                    sessionInitCallback,
                    waitForCloseConfirmation);
        }
    }
}
