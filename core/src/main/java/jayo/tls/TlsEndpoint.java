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
import jayo.Reader;
import jayo.Writer;
import jayo.internal.RealTlsEndpoint;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.util.List;

/**
 * A TLS (Transport Layer Security) endpoint, either the client-side or server-side end of a TLS connection between two
 * peers. {@link TlsEndpoint} guarantee that the TLS connection is established and its <b>initial handshake was done</b>
 * upon creation.
 * <p>
 * {@link TlsEndpoint} implementations delegate all cryptographic operations to the standard JDK's existing
 * {@linkplain SSLEngine TLS/SSL Engine}; effectively hiding it behind an easy-to-use streaming API based on Jayo's
 * reader and writer, that allows to secure JVM applications with minimal added complexity.
 * <p>
 * Note that this is an API adapter, not a cryptographic implementation: except for a few bytes that are parsed at
 * the beginning of the connection in server mode, to look for the SNI, the whole protocol implementation is done by the
 * {@link SSLEngine}.
 * <p>
 * Please read the {@link #shutdown()} javadoc for a detailed explanation of the TLS shutdown phase.
 *
 * @see <a href="https://www.ibm.com/docs/en/sdk-java-technology/8?topic=sslengine-">Java SSLEngine documentation</a>
 * @see ClientTlsEndpoint
 * @see ServerTlsEndpoint
 */
public sealed interface TlsEndpoint extends Endpoint permits ClientTlsEndpoint, ServerTlsEndpoint {

    /**
     * @return a reader that reads decrypted plaintext data from this TLS endpoint.
     */
    @Override
    @NonNull
    Reader getReader();

    /**
     * @return a writer to write plaintext data to be encrypted by this TLS endpoint.
     */
    @Override
    @NonNull
    Writer getWriter();

    /**
     * @return the {@linkplain SSLSession TLS session} of the TLS connection.
     */
    @NonNull
    SSLSession getSession();

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
     * The exact behavior can be configured using {@link Builder#waitForCloseConfirmation(boolean)}.
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
     * The abstract builder used to create a {@link TlsEndpoint} instance.
     */
    sealed interface Builder<T extends Builder<T, U>, U extends Parameterizer> extends Cloneable
            permits RealTlsEndpoint.Builder, ClientTlsEndpoint.Builder, ServerTlsEndpoint.Builder {
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

        /**
         * Create a new {@linkplain TlsEndpoint.Parameterizer TLS parameterizer} using no advisory peer information, it
         * requires an existing {@link Endpoint} for encrypted bytes (typically, but not necessarily associated with a
         * network socket).
         *
         * @see #createParameterizer(Endpoint, String, int)
         */
        @NonNull
        U createParameterizer(final @NonNull Endpoint encryptedEndpoint);

        /**
         * Create a new {@linkplain TlsEndpoint.Parameterizer TLS parameterizer} using advisory peer information, it
         * requires an existing {@link Endpoint} for encrypted bytes (typically, but not necessarily associated with a
         * network socket).
         * <p>
         * Applications using this method are providing hints for an internal session reuse strategy.
         * <p>
         * Some cipher suites (such as Kerberos) require remote hostname information, in which case peerHost needs to be
         * specified.
         *
         * @param peerHost the non-authoritative name of the host.
         * @param peerPort the non-authoritative port.
         */
        @NonNull
        U createParameterizer(final @NonNull Endpoint encryptedEndpoint,
                              final @NonNull String peerHost,
                              final int peerPort);

        /**
         * @return a deep copy of this builder.
         */
        @NonNull
        T clone();
    }

    sealed interface Parameterizer permits RealTlsEndpoint.Parameterizer, ClientTlsEndpoint.Parameterizer,
            ServerTlsEndpoint.Parameterizer {
        /**
         * @return the list of enabled {@linkplain Protocol protocols} (http/1.1, quic, etc.) for
         * <a href="https://tools.ietf.org/html/draft-ietf-tls-applayerprotoneg">ALPN</a> selection. These protocols
         * describe how HTTP messages are framed.
         */
        @NonNull
        List<@NonNull Protocol> getEnabledProtocols();

        /**
         * Sets the list of enabled {@linkplain Protocol protocols} (http/1.1, quic, etc.) for
         * <a href="https://tools.ietf.org/html/draft-ietf-tls-applayerprotoneg">ALPN</a> selection. These protocols
         * describe how HTTP messages are framed.
         */
        void setEnabledProtocols(final @NonNull List<@NonNull Protocol> protocols);

        /**
         * @return the list of enabled {@linkplain TlsVersion TLS versions} (TLSv1.3, TLSv1.2 etc.) to secure the
         * connection. The chosen TLS version will vary, depending on protocols and cipher suites negotiated with the
         * peer.
         */
        @NonNull
        List<@NonNull TlsVersion> getEnabledTlsVersions();

        /**
         * Sets the list of enabled {@linkplain TlsVersion TLS versions} (TLSv1.3, TLSv1.2 etc.) to secure the
         * connection. The chosen TLS version will vary, depending on protocols and cipher suites negotiated with the
         * peer.
         */
        void setEnabledTlsVersions(final @NonNull List<@NonNull TlsVersion> tlsVersions);

        /**
         * @return the list of supported {@linkplain CipherSuite TLS cipher suites} by this platform.
         */
        @NonNull
        List<@NonNull CipherSuite> getSupportedCipherSuites();

        /**
         * @return the list of enabled {@linkplain CipherSuite TLS cipher suites}, it must be a sub-list of
         * {@link #getSupportedCipherSuites()}.
         */
        @NonNull
        List<@NonNull CipherSuite> getEnabledCipherSuites();

        /**
         * Sets the list of enabled {@linkplain CipherSuite TLS cipher suites}, it must be a sub-list of
         * {@link #getSupportedCipherSuites()}.
         */
        void setEnabledCipherSuites(final @NonNull List<@NonNull CipherSuite> cipherSuites);
    }
}
