/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.tls;

import jayo.Endpoint;
import jayo.JayoException;
import jayo.RawReader;
import jayo.RawWriter;
import jayo.internal.ClientTlsEndpoint;
import jayo.internal.RealTlsEndpoint;
import jayo.internal.ServerTlsEndpoint;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A TLS (Transport Layer Security) endpoint, either the client-side or server-side end of a TLS connection between two
 * peers. {@link TlsEndpoint} guarantee that the TLS connection is initialized and its <b>initial handshake was done</b>
 * upon creation.
 * <p>
 * {@link TlsEndpoint} implementations delegate all cryptographic operations to the standard JDK's existing
 * {@linkplain SSLEngine TLS/SSL Engine}; effectively hiding it behind an easy-to-use streaming API based on Jayo's
 * reader and writer, that allows to secure JVM applications with minimal added complexity.
 * <p>
 * Note that this is an API adapter, not a cryptographic implementation: except for a few bytes that are parsed at
 * the beginning of the connection, to look for the SNI, the whole protocol implementation is done by the SSLEngine.
 * <p>
 * A TLS endpoint is created by using one of its {@code create*} for client or server side. They require an existing
 * {@link Endpoint} for encrypted bytes (typically, but not necessarily associated with a network socket); and a
 * {@link SSLEngine} or a {@link SSLContext}.
 * <p>
 * Please read the {@link #shutdown()} javadoc for a detailed explanation of the TLS shutdown phase.
 *
 * @see <a href="https://www.ibm.com/docs/en/sdk-java-technology/8?topic=sslengine-">Java SSLEngine documentation</a>
 */
public sealed interface TlsEndpoint extends Endpoint permits ClientTlsEndpoint, ServerTlsEndpoint {
    /**
     * Create a new {@link ClientConfig} for a client-side TLS endpoint, it requires an existing {@link Endpoint} for
     * encrypted bytes (typically, but not necessarily associated with a network socket); and the provided
     * {@link SSLContext}. This SSL context will be used to create a {@link SSLEngine} configured in client mode.
     * <p>
     * This method uses default configuration, with no session init callback and without waiting for TLS peer
     * confirmation on close.
     * <p>
     * If you need specific options, please use {@link #createClient(Endpoint, SSLContext, ClientConfig)} instead.
     *
     * @param encryptedEndpoint a reference to the underlying {@link Endpoint} for encrypted bytes.
     * @param sslContext        the {@link SSLContext} to be used.
     * @see JssePlatform#newSSLContext()
     * @see HandshakeCertificates#sslContext()
     */
    static @NonNull TlsEndpoint createClient(final @NonNull Endpoint encryptedEndpoint,
                                             final @NonNull SSLContext sslContext) {
        return createClient(encryptedEndpoint, sslContext, configForClient());
    }

    /**
     * Create a new {@link ClientConfig} for a client-side TLS endpoint, it requires an existing {@link Endpoint} for
     * encrypted bytes (typically, but not necessarily associated with a network socket); and the provided
     * {@link SSLContext}. This SSL context will be used to create a {@link SSLEngine} configured in client mode.
     * <p>
     * This method uses the provided {@code config} configuration, which can be used to configure a session init
     * callback and to force waiting for TLS peer confirmation on close.
     *
     * @param encryptedEndpoint a reference to the underlying {@link Endpoint} for encrypted bytes.
     * @param sslContext        the {@link SSLContext} to be used.
     * @see JssePlatform#newSSLContext()
     * @see HandshakeCertificates#sslContext()
     */
    static @NonNull TlsEndpoint createClient(final @NonNull Endpoint encryptedEndpoint,
                                             final @NonNull SSLContext sslContext,
                                             final @NonNull ClientConfig config) {
        Objects.requireNonNull(encryptedEndpoint);
        Objects.requireNonNull(sslContext);
        return ((ClientTlsEndpoint.Config) config).build(encryptedEndpoint, sslContext);
    }

    /**
     * Create a new {@link ClientConfig} for a client-side TLS endpoint, it requires an existing {@link Endpoint} for
     * encrypted bytes (typically, but not necessarily associated with a network socket); and the provided
     * {@link SSLEngine}.
     * <p>
     * This method uses default configuration, with no session init callback and without waiting for TLS peer
     * confirmation on close.
     * <p>
     * If you need specific options, please use {@link #createClient(Endpoint, SSLEngine, ClientConfig)} instead.
     *
     * @param encryptedEndpoint a reference to the underlying {@link Endpoint} for encrypted bytes.
     * @param engine            the {@link SSLEngine} to be used.
     */
    static @NonNull TlsEndpoint createClient(final @NonNull Endpoint encryptedEndpoint,
                                             final @NonNull SSLEngine engine) {
        return createClient(encryptedEndpoint, engine, configForClient());
    }

    /**
     * Create a new {@link ClientConfig} for a client-side TLS endpoint, it requires an existing {@link Endpoint} for
     * encrypted bytes (typically, but not necessarily associated with a network socket); and the provided
     * {@link SSLEngine}.
     * <p>
     * This method uses the provided {@code config} configuration, which can be used to configure a session init
     * callback and to force waiting for TLS peer confirmation on close.
     *
     * @param encryptedEndpoint a reference to the underlying {@link Endpoint} for encrypted bytes.
     * @param engine            the {@link SSLEngine} to be used.
     */
    static @NonNull TlsEndpoint createClient(final @NonNull Endpoint encryptedEndpoint,
                                             final @NonNull SSLEngine engine,
                                             final @NonNull ClientConfig config) {
        Objects.requireNonNull(encryptedEndpoint);
        Objects.requireNonNull(engine);
        if (!engine.getUseClientMode()) {
            throw new IllegalArgumentException("The provided SSL engine must use client mode");
        }
        return ((ClientTlsEndpoint.Config) config).build(encryptedEndpoint, engine);
    }

    /**
     * @return a client-side {@link TlsEndpoint} configuration.
     */
    static @NonNull ClientConfig configForClient() {
        return new ClientTlsEndpoint.Config();
    }


    /**
     * Create a new {@link ServerConfig} for a server-side TLS endpoint, it requires an existing {@link Endpoint} for
     * encrypted bytes (typically, but not necessarily associated with a network socket); and the provided
     * {@link SSLContext}. This SSL context will be used to create a {@link SSLEngine} configured in server mode.
     * <p>
     * This method uses default configuration, with no engine factory, no session init callback and without waiting for
     * TLS peer confirmation on close.
     * <p>
     * If you need specific options, please use {@link #createServer(Endpoint, SSLContext, ServerConfig)} instead.
     *
     * @param encryptedEndpoint a reference to the underlying {@link Endpoint} for encrypted bytes.
     * @param sslContext        the fixed SSL context (and so the correct certificate) to be used.
     * @see JssePlatform#newSSLContext()
     * @see HandshakeCertificates#sslContext()
     */
    static @NonNull TlsEndpoint createServer(final @NonNull Endpoint encryptedEndpoint,
                                             final @NonNull SSLContext sslContext) {
        return createServer(encryptedEndpoint, sslContext, configForServer());
    }

    /**
     * Create a new {@link ServerConfig} for a server-side TLS endpoint, it requires an existing {@link Endpoint} for
     * encrypted bytes (typically, but not necessarily associated with a network socket); and the provided
     * {@link SSLContext}. This SSL context will be used to create a {@link SSLEngine} configured in server mode.
     * <p>
     * This method uses the provided {@code config} configuration, which can be used to configure an engine factory, a
     * session init callback and to force waiting for TLS peer confirmation on close.
     *
     * @param encryptedEndpoint a reference to the underlying {@link Endpoint} for encrypted bytes.
     * @param sslContext        the fixed SSL context (and so the correct certificate) to be used.
     * @see JssePlatform#newSSLContext()
     * @see HandshakeCertificates#sslContext()
     */
    static @NonNull TlsEndpoint createServer(final @NonNull Endpoint encryptedEndpoint,
                                             final @NonNull SSLContext sslContext,
                                             final @NonNull ServerConfig config) {
        Objects.requireNonNull(encryptedEndpoint);
        Objects.requireNonNull(sslContext);
        return ((ServerTlsEndpoint.Config) config).build(encryptedEndpoint, sslContext);
    }

    /**
     * Create a new {@link ServerConfig} for a server-side TLS endpoint, it requires an existing {@link Endpoint} for
     * encrypted bytes (typically, but not necessarily associated with a network socket); and a custom
     * {@link SSLContext} factory, which will be used to create the SSL context, as a function of the SNI received at
     * the TLS connection start. This SSL context will then be used to create a {@link SSLEngine} configured in server
     * mode.
     * <p>
     * This method uses default configuration, with no engine factory, no session init callback and without waiting for
     * TLS peer confirmation on close.
     * <p>
     * If you need specific options, please use {@link #createServer(Endpoint, ServerConfig, Function)}  instead.
     *
     * @param encryptedEndpoint a reference to the underlying {@link Endpoint} for encrypted bytes.
     * @param sslContextFactory a function to select the correct SSL context (and so the correct certificate) based on
     *                          the optional SNI server name provided by the client. A {@code null} SNI server name
     *                          means that the client did not send a SNI server name. Returning {@code null} indicates
     *                          that no SSL context is supplied and the TLS connection would then be aborted by throwing
     *                          a {@linkplain JayoTlsHandshakeException JayoSSLHandshakeException}.
     * @implNote Due to limitations of {@link SSLEngine}, configuring a server-side {@link TlsEndpoint} to select the
     * {@link SSLContext} based on the SNI value implies parsing the first TLS frame (ClientHello) independently of the
     * SSLEngine.
     * @see <a href="https://tools.ietf.org/html/rfc6066#section-3">Server Name Indication</a>
     * @see JssePlatform#newSSLContext()
     * @see HandshakeCertificates#sslContext()
     */
    static @NonNull TlsEndpoint createServer(
            final @NonNull Endpoint encryptedEndpoint,
            final @NonNull Function<@Nullable SNIServerName, @Nullable SSLContext> sslContextFactory) {
        return createServer(encryptedEndpoint, configForServer(), sslContextFactory);
    }

    /**
     * Create a new {@link ServerConfig} for a server-side TLS endpoint, it requires an existing {@link Endpoint} for
     * encrypted bytes (typically, but not necessarily associated with a network socket); and a custom
     * {@link SSLContext} factory, which will be used to create the SSL context, as a function of the SNI received at
     * the TLS connection start. This SSL context will then be used to create a {@link SSLEngine} configured in server
     * mode.
     * <p>
     * This method uses the provided {@code config} configuration, which can be used to configure an engine factory, a
     * session init callback and to force waiting for TLS peer confirmation on close.
     *
     * @param encryptedEndpoint a reference to the underlying {@link Endpoint} for encrypted bytes.
     * @param sslContextFactory a function to select the correct SSL context (and so the correct certificate) based on
     *                          the optional SNI server name provided by the client. A {@code null} SNI server name
     *                          means that the client did not send a SNI server name. Returning {@code null} indicates
     *                          that no SSL context is supplied and the TLS connection would then be aborted by throwing
     *                          a {@linkplain JayoTlsHandshakeException JayoSSLHandshakeException}.
     * @implNote Due to limitations of {@link SSLEngine}, configuring a server-side {@link TlsEndpoint} to select the
     * {@link SSLContext} based on the SNI value implies parsing the first TLS frame (ClientHello) independently of the
     * SSLEngine.
     * @see <a href="https://tools.ietf.org/html/rfc6066#section-3">Server Name Indication</a>
     * @see JssePlatform#newSSLContext()
     * @see HandshakeCertificates#sslContext()
     */
    static @NonNull TlsEndpoint createServer(
            final @NonNull Endpoint encryptedEndpoint,
            final @NonNull ServerConfig config,
            final @NonNull Function<@Nullable SNIServerName, @Nullable SSLContext> sslContextFactory) {
        Objects.requireNonNull(encryptedEndpoint);
        Objects.requireNonNull(sslContextFactory);
        return ((ServerTlsEndpoint.Config) config).build(encryptedEndpoint, sslContextFactory);
    }

    /**
     * @return a server-side {@link TlsEndpoint} configuration.
     */
    static @NonNull ServerConfig configForServer() {
        return new ServerTlsEndpoint.Config();
    }

    /**
     * @return a raw reader that reads decrypted plaintext data from this TLS endpoint.
     */
    @Override
    @NonNull
    RawReader getReader();

    /**
     * @return a raw writer to write plaintext data to be encrypted by this TLS endpoint.
     */
    @Override
    @NonNull
    RawWriter getWriter();

    /**
     * @return the result of the initial handshake on this TLS connection.
     */
    @NonNull
    Handshake getHandshake();

    /**
     * Shuts down the TLS connection. This method emulates the behavior of OpenSSL's
     * <a href="https://wiki.openssl.org/index.php/Manual:SSL_shutdown(3)">SSL_shutdown()</a>.
     * <p>
     * The shutdown procedure consists of two steps: the sending of the "close notify" shutdown alert and the reception
     * of the peer's "close notify". According to the TLS standard, it is acceptable for an application to only send its
     * shutdown alert and then close the underlying connection without waiting for the peer's response. When the
     * underlying connection shall be used for more communications, the complete shutdown procedure (bidirectional
     * "close notify" alerts) must be performed, so that the peers stay synchronized.
     * <p>
     * This class supports both uni- and bidirectional shutdown by its 2-step behavior, using this method.
     * <p>
     * When this is the first party to send the "close notify" alert, this method will only send the alert, set the
     * {@link #shutdownSent()} flag and return {@code false}. If a unidirectional shutdown is enough, this first
     * call is sufficient. In order to complete the bidirectional shutdown handshake, This method must be called again.
     * The second call will wait for the peer's "close notify" shutdown alert. On success, the second call will return
     * {@code true}.
     * <p>
     * If the peer already sent the "close notify" alert, and it was already processed implicitly inside a read
     * operation, the {@link #shutdownReceived()} flag is already set. This method will then send the "close notify"
     * alert, set the {@link #shutdownSent()} flag and immediately return {@code true}. It is therefore recommended
     * to check the return value of this method and call it again, if the bidirectional shutdown is not yet complete.
     * <p>
     * Note that despite not being mandated by the specification, a proper TLS close is important to prevent truncation
     * attacks, which consists, essentially, of an adversary introducing TCP FIN segments to trick on party to ignore
     * the final bytes of a secure stream. For more details, see
     * <a href="https://hal.inria.fr/hal-01102013">the original paper</a>.
     *
     * @return {@code true} if the closing of this TLS connection is finished.
     */
    boolean shutdown();

    /**
     * @return {@code true} if this side of the TLS connection has already received the close notification.
     * @see #shutdown()
     */
    boolean shutdownReceived();

    /**
     * @return {@code true} if this side of the TLS connection has already sent the close notification.
     * @see #shutdown()
     */
    boolean shutdownSent();

    /**
     * Closes the underlying endpoint. This method first does some form of best-effort TLS close if not already done.
     * The exact behavior can be configured using {@link Config#waitForCloseConfirmation(boolean)}.
     * <p>
     * The default behavior mimics what happens in a normal (that is, non-layered)
     * {@link javax.net.ssl.SSLSocket#close()}.
     * <p>
     * For finer control of the TLS close, use {@link #shutdown()}.
     *
     * @throws JayoException if the underlying endpoint throws an IO Exception during close. Exceptions thrown during
     *                       any previous TLS close are not propagated.
     */
    @Override
    void close();

    /**
     * @return the underlying {@link Endpoint} that read and write encrypted data.
     */
    @Override
    @NonNull
    Endpoint getUnderlying();

    /**
     * The abstract configuration used to create a {@link TlsEndpoint} instance.
     */
    sealed interface Config<T extends Config<T>> permits RealTlsEndpoint.Config, ClientConfig, ServerConfig {
        /**
         * Register a callback function to be executed when the TLS session is established (or re-established). The
         * supplied function will run in the same thread as the rest of the handshake, so it should ideally run as fast
         * as possible.
         *
         * @see Handshake#get(SSLSession)
         */
        @NonNull
        T sessionInitCallback(final @NonNull Consumer<@NonNull SSLSession> sessionInitCallback);

        /**
         * Whether to wait for TLS close confirmation when calling {@code close()} on this TLS endpoint or its
         * {@linkplain TlsEndpoint#getReader() reader} or {@linkplain TlsEndpoint#getWriter() writer}. Default is
         * {@code false} to not wait and close immediately. The proper closing procedure can then be triggered at any
         * moment using {@link TlsEndpoint#shutdown()}.
         * <p>
         * Setting this to {@code true} will block (potentially until it times out, or indefinitely) the close operation
         * until the counterpart confirms the close on their side (sending a close_notify alert). In this case it
         * emulates the behavior of {@linkplain javax.net.ssl.SSLSocket SSLSocket} when used in layered mode (and
         * without autoClose).
         * <p>
         * Even when this behavior is enabled, the close operation will not propagate any exception thrown during the
         * TLS close exchange and just proceed to close the underlying reader or writer.
         *
         * @see TlsEndpoint#shutdown()
         */
        @NonNull
        T waitForCloseConfirmation(final boolean waitForCloseConfirmation);
    }

    /**
     * The configuration used to create a client-side {@link TlsEndpoint} instance.
     */
    sealed interface ClientConfig extends Config<ClientConfig> permits ClientTlsEndpoint.Config {
    }

    /**
     * The configuration used to create a server-side {@link TlsEndpoint} instance.
     */
    sealed interface ServerConfig extends Config<ServerConfig> permits ServerTlsEndpoint.Config {
        /**
         * Sets the custom function that builds a {@link SSLEngine} from the {@link SSLContext} when it will be
         * available during handshake.
         */
        @NonNull
        ServerConfig engineFactory(final @NonNull Function<@NonNull SSLContext, @NonNull SSLEngine> sslEngineFactory);
    }
}
