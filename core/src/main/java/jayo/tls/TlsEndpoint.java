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

import jayo.JayoException;
import jayo.RawReader;
import jayo.RawWriter;
import jayo.endpoints.Endpoint;
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
 * peers.
 * <p>
 * Instances that implement this interface delegate all cryptographic operations to the standard Java TLS/SSL
 * implementation: {@link SSLEngine}; effectively hiding it behind an easy-to-use streaming API based on Jayo's reader
 * and writer, that allows to secure JVM applications with minimal added complexity.
 * <p>
 * Note that this is an API adapter, not a cryptographic implementation: except for a few bytes that are parsed at
 * the beginning of the connection, to look for the SNI, the whole protocol implementation is done by the SSLEngine.
 * Both the SSLContext and SSLEngine are supplied by the client; these classes are the ones responsible for protocol
 * configuration, including hostname validation, client-side authentication, etc.
 * <p>
 * A TLS endpoint is created by using one of its builders for client or server side. They require an existing
 * {@link Endpoint} for encrypted bytes (typically, but not necessarily obtained from a
 * {@linkplain java.net.Socket Socket} or a {@linkplain java.nio.channels.SocketChannel SocketChannel}); and a
 * {@link SSLEngine} or a {@link SSLContext}.
 * <ul>
 *     <li>Please read {@link #handshake()}
 *     <li>Please read {@link #shutdown()} javadoc for a detailed explanation of the TLS shutdown phase.
 * </ul>
 *
 * @see <a href="https://www.ibm.com/docs/en/sdk-java-technology/8?topic=sslengine-">Java SSLEngine documentation</a>
 */
public sealed interface TlsEndpoint extends Endpoint permits ClientTlsEndpoint, ServerTlsEndpoint {
    /**
     * Create a new {@link ClientBuilder} for a client-side TLS endpoint, it requires an existing {@link Endpoint} for
     * encrypted bytes (typically, but not necessarily obtained from a {@linkplain java.net.Socket Socket} or a
     * {@linkplain java.nio.channels.SocketChannel SocketChannel}); and the provided {@link SSLContext}. This SSL
     * context will be used to create a {@link SSLEngine} configured in client mode.
     *
     * @param encryptedEndpoint a reference to the underlying {@link Endpoint} for encrypted bytes.
     * @param sslContext        the {@link SSLContext} to be used.
     */
    static @NonNull ClientBuilder clientBuilder(final @NonNull Endpoint encryptedEndpoint,
                                                final @NonNull SSLContext sslContext) {
        Objects.requireNonNull(encryptedEndpoint);
        Objects.requireNonNull(sslContext);
        return new ClientTlsEndpoint.Builder(encryptedEndpoint, sslContext);
    }

    /**
     * Create a new {@link ClientBuilder} for a client-side TLS endpoint, it requires an existing {@link Endpoint} for
     * encrypted bytes (typically, but not necessarily obtained from a {@linkplain java.net.Socket Socket} or a
     * {@linkplain java.nio.channels.SocketChannel SocketChannel}); and the provided {@link SSLEngine}.
     *
     * @param encryptedEndpoint a reference to the underlying {@link Endpoint} for encrypted bytes.
     * @param engine            the {@link SSLEngine} to be used.
     */
    static @NonNull ClientBuilder clientBuilder(final @NonNull Endpoint encryptedEndpoint,
                                                final @NonNull SSLEngine engine) {
        Objects.requireNonNull(encryptedEndpoint);
        Objects.requireNonNull(engine);
        if (!engine.getUseClientMode()) {
            throw new IllegalArgumentException("The provided SSL engine must uses client mode");
        }
        return new ClientTlsEndpoint.Builder(encryptedEndpoint, engine);
    }

    /**
     * Create a new {@link ServerBuilder} for a server-side TLS endpoint, it requires an existing {@link Endpoint} for
     * encrypted bytes (typically, but not necessarily obtained from a {@linkplain java.net.Socket Socket} or a
     * {@linkplain java.nio.channels.SocketChannel SocketChannel}); and the provided {@link SSLContext}. This SSL
     * context will be used to create a {@link SSLEngine} configured in server mode.
     *
     * @param encryptedEndpoint a reference to the underlying {@link Endpoint} for encrypted bytes.
     * @param sslContext        the fixed {@link SSLContext} to be used.
     */
    static @NonNull ServerBuilder serverBuilder(final @NonNull Endpoint encryptedEndpoint,
                                                final @NonNull SSLContext sslContext) {
        Objects.requireNonNull(encryptedEndpoint);
        Objects.requireNonNull(sslContext);
        return new ServerTlsEndpoint.Builder(encryptedEndpoint, sslContext);
    }

    /**
     * Create a new {@link ServerBuilder} for a server-side TLS endpoint, it requires an existing {@link Endpoint} for
     * encrypted bytes (typically, but not necessarily obtained from a {@linkplain java.net.Socket Socket} or a
     * {@linkplain java.nio.channels.SocketChannel SocketChannel}); and a custom {@link SSLContext} factory, which will
     * be used to create the SSL context, as a function of the SNI received at the TLS connection start. This SSL
     * context will then be used to create a {@link SSLEngine} configured in server mode.
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
     */
    static @NonNull ServerBuilder serverBuilder(
            final @NonNull Endpoint encryptedEndpoint,
            final @NonNull Function<@Nullable SNIServerName, @Nullable SSLContext> sslContextFactory) {
        Objects.requireNonNull(encryptedEndpoint);
        Objects.requireNonNull(sslContextFactory);
        return new ServerTlsEndpoint.Builder(encryptedEndpoint, sslContextFactory);
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
     * Forces the initial TLS handshake. Calling this method is usually not needed, as a handshake will happen
     * automatically when doing the first read operation on {@link #getReader()} or the first write operation on
     * {@link #getWriter()}. Calling this method after the initial handshake has been done has no effect.
     * <p>
     * This method may be invoked at any time. If another thread has already initiated a read, write, or handshaking
     * operation upon this TLS connection, however, then an invocation of this method will block until this ongoing
     * operation is complete because of the locks that prevent concurrent calls.
     *
     * @throws JayoTlsException              if the {@link SSLEngine} or the TLS mechanism on top of it failed.
     * @throws JayoException if another IO Exception occurred.
     */
    void handshake();

    /**
     * Initiates a handshake (initial or renegotiation) on this TLS connection. This method is usually not needed for
     * the initial handshake, as a handshake will happen automatically when doing the first read operation on
     * {@link #getReader()} or the first write operation on {@link #getWriter()}.
     * <p>
     * Note that <b>renegotiation is a problematic feature of the TLS protocol, therefore it was removed in TLS 1.3</b>,
     * that should only be initiated at a quiet point of the protocol.
     * <p>
     * This method may be invoked at any time. If another thread has already initiated a read, write, or handshaking
     * operation upon this TLS connection, however, then an invocation of this method will block until this ongoing
     * operation is complete because of the locks that prevent concurrent calls.
     *
     * @throws JayoTlsException              if the {@link SSLEngine} or the TLS mechanism on top of it failed.
     * @throws JayoException if another IO Exception occurred.
     */
    void renegotiate();

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
     * The exact behavior can be configured using {@link Builder#waitForCloseConfirmation(boolean)}.
     * <p>
     * The default behavior mimics what happens in a normal (that is, non-layered)
     * {@link javax.net.ssl.SSLSocket#close()}.
     * <p>
     * For finer control of the TLS close, use {@link #shutdown()}.
     *
     * @throws JayoException if the underlying endpoint throws an IO Exception during close. Exceptions
     *                                       thrown during any previous TLS close are not propagated.
     */
    @Override
    void close();

    /**
     * @return the {@link SSLContext} if present, or {@code null} if unknown. That can happen in client-side if a
     * {@link SSLEngine} was provided to the builder; or in server-side when the TLS connection has not been
     * initialized, or before the SNI is received.
     */
    @Nullable
    SSLContext getSslContext();

    /**
     * @return the {@link SSLEngine} if present, or {@code null} if unknown. That can happen in server-side when
     * the TLS connection has not been initialized, or before the SNI is received.
     */
    @Nullable
    SSLEngine getSslEngine();

    /**
     * @return the underlying {@link Endpoint} that read and write encrypted data.
     */
    @Override
    @NonNull
    Endpoint getUnderlying();

    /**
     * The abstract builder used to create a {@link TlsEndpoint} instance.
     */
    sealed interface Builder<T extends Builder<T>> permits RealTlsEndpoint.Builder, ClientBuilder, ServerBuilder {
        /**
         * Register a callback function to be executed when the TLS session is established (or re-established). The
         * supplied function will run in the same thread as the rest of the handshake, so it should ideally run as fast
         * as possible.
         */
        @NonNull
        T sessionInitCallback(final @NonNull Consumer<SSLSession> sessionInitCallback);

        /**
         * Whether to wait for TLS close confirmation when calling {@code close()} on the
         * {@link TlsEndpoint#getReader()} or the {@link TlsEndpoint#getWriter()}. Default is
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

        @NonNull
        TlsEndpoint build();
    }

    /**
     * The builder used to create a client-side {@link TlsEndpoint} instance.
     */
    sealed interface ClientBuilder extends Builder<ClientBuilder> permits ClientTlsEndpoint.Builder {
    }

    /**
     * The builder used to create a server-side {@link TlsEndpoint} instance.
     */
    sealed interface ServerBuilder extends Builder<ServerBuilder> permits ServerTlsEndpoint.Builder {
        /**
         * The custom function that builds a {@link SSLEngine} from the {@link SSLContext} when it will be available
         * during handshake.
         */
        @NonNull
        ServerBuilder engineFactory(final @NonNull Function<@NonNull SSLContext, @NonNull SSLEngine> sslEngineFactory);
    }
}
