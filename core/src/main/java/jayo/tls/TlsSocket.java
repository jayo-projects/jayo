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

import jayo.*;
import jayo.internal.AbstractTlsSocket;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.util.List;

/**
 * A TLS (Transport Layer Security) socket, either the client-side or server-side end of a TLS connection between two
 * peers. {@link TlsSocket} guarantee that the TLS connection is established and the <b>initial handshake was done</b>
 * upon creation.
 * <p>
 * {@link TlsSocket} implementation delegate all cryptographic operations to the standard JDK's existing
 * {@linkplain SSLEngine TLS/SSL Engine}; effectively hiding it behind an easy-to-use API based on
 * {@linkplain Socket Jayo Socket}, that allows to secure JVM applications with minimal added complexity.
 * <p>
 * Note that this is an API adapter, not a cryptographic implementation: except for a few bytes that are parsed at
 * the beginning of the connection in server mode to look for the SNI, the whole protocol implementation is done by the
 * {@link SSLEngine}.
 * <p>
 * Please read the {@link #shutdown()} javadoc for a detailed explanation of the TLS shutdown phase.
 *
 * @see <a href="https://www.ibm.com/docs/en/sdk-java-technology/8?topic=sslengine-">Java SSLEngine documentation</a>
 * @see ClientTlsSocket
 * @see ServerTlsSocket
 */
public sealed interface TlsSocket extends RawSocket permits ClientTlsSocket, ServerTlsSocket, AbstractTlsSocket {

    /**
     * @return a reader that reads decrypted plaintext data from this TLS socket.
     */
    @Override
    @NonNull
    RawReader getReader();

    /**
     * @return a writer to write plaintext data to be encrypted by this TLS socket.
     */
    @Override
    @NonNull
    RawWriter getWriter();

    /**
     * @return the {@link SSLSession} of the TLS connection, that holds parameters of the ongoing TLS session.
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
     * "close notify" alerts) must be performed so that the peers stay synchronized.
     * <p>
     * This class supports both uni- and bidirectional shutdown by its 2-step behavior, using this method.
     * <p>
     * When this is the first party to send the "close notify" alert, this method will only send the alert, set the
     * {@link #isShutdownSent()} flag and return {@code false}. If a unidirectional shutdown is enough, this first
     * call is enough. To complete the bidirectional shutdown handshake, This method must be called again. The second
     * call will wait for the peer's "close notify" shutdown alert. On success, the second call will return
     * {@code true}.
     * <p>
     * If the peer already sent the "close notify" alert, and it was already processed implicitly inside a read
     * operation, the {@link #isShutdownReceived()} flag is already set. This method will then send the "close notify"
     * alert, set the {@link #isShutdownSent()} flag and immediately return {@code true}. It is therefore recommended
     * to check the return value of this method and call it again if the bidirectional shutdown is not yet complete.
     * <p>
     * Note that despite not being mandated by the specification, a proper TLS close is important to prevent truncation
     * attacks. It consists, essentially, of an adversary introducing TCP FIN segments to trick on party to ignore
     * the final bytes of a secure stream. For more details, see
     * <a href="https://hal.inria.fr/hal-01102013">the original paper</a>.
     *
     * @return {@code true} if the closing of this TLS connection is fully finished.
     */
    boolean shutdown();

    /**
     * @return {@code true} if this side of the TLS connection has already received the close notification.
     * @see #shutdown()
     */
    boolean isShutdownReceived();

    /**
     * @return {@code true} if this side of the TLS connection has already sent the close notification.
     * @see #shutdown()
     */
    boolean isShutdownSent();

    /**
     * Cancels the underlying socket. This method first does some form of best-effort TLS shutdown if not already done.
     * The exact behavior can be configured using {@link Builder#waitForCloseConfirmation(boolean)}.
     * <p>
     * The default behavior mimics what happens in a normal (that is, non-layered)
     * {@link javax.net.ssl.SSLSocket#close()}.
     * <p>
     * For finer control of the TLS shutdown procedure, use {@link #shutdown()}.
     *
     * @throws JayoException if the underlying socket throws an IO Exception during cancel. Exceptions thrown during
     *                       any previous TLS close are not propagated.
     * @see #shutdown()
     */
    @Override
    void cancel();

    /**
     * The abstract builder used to create a {@link TlsSocket} instance.
     */
    sealed interface Builder<T extends Builder<T, U>, U extends Parameterizer>
            permits AbstractTlsSocket.Builder, ClientTlsSocket.Builder, ServerTlsSocket.Builder {
        /**
         * Whether to wait for TLS close confirmation when calling {@link TlsSocket#cancel()}. Default is {@code false}
         * to not wait and cancel immediately. The proper closing procedure can then be triggered at any moment using
         * {@link TlsSocket#shutdown()}.
         * <p>
         * Setting this to {@code true} will block (potentially until it times out, or indefinitely) the cancel
         * operation until the peer confirms the close on their side (sending a "close notify" alert). In this case it
         * emulates the behavior of {@linkplain javax.net.ssl.SSLSocket SSLSocket} when used in layered mode (and
         * without autoClose).
         * <p>
         * Even when this behavior is enabled, the cancel operation will not propagate any exception thrown during the
         * TLS close exchange and just proceed to cancel the underlying socket.
         *
         * @see TlsSocket#shutdown()
         */
        @NonNull
        T waitForCloseConfirmation(final boolean waitForCloseConfirmation);

        /**
         * Create a new {@linkplain TlsSocket.Parameterizer TLS parameterizer} using no advisory peer information. It
         * requires an existing {@link Socket} for encrypted bytes (typically, but not necessarily associated with a
         * network socket).
         *
         * @see #createParameterizer(RawSocket, String, int)
         */
        @NonNull
        U createParameterizer(final @NonNull RawSocket encryptedSocket);

        /**
         * Create a new {@linkplain TlsSocket.Parameterizer TLS parameterizer} using advisory peer information. It
         * requires an existing {@link Socket} for encrypted bytes (typically, but not necessarily associated with a
         * network socket).
         * <p>
         * Applications using this method are providing hints for an internal session reuse strategy.
         * <p>
         * Some cipher suites (such as Kerberos) require remote peer information, in which case {@code peerHost} needs
         * to be specified.
         *
         * @param peerHost the non-authoritative name of the host.
         * @param peerPort the non-authoritative port.
         */
        @NonNull
        U createParameterizer(final @NonNull RawSocket encryptedSocket,
                              final @NonNull String peerHost,
                              final int peerPort);
    }

    sealed interface Parameterizer permits AbstractTlsSocket.Parameterizer, ClientTlsSocket.Parameterizer,
            ServerTlsSocket.Parameterizer {
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
         * @return the list of enabled {@linkplain TlsVersion TLS versions} (TLSv1.3, TLSv1.2, etc.) to secure the
         * connection. The chosen TLS version will vary, depending on protocols and cipher suites negotiated with the
         * peer.
         */
        @NonNull
        List<@NonNull TlsVersion> getEnabledTlsVersions();

        /**
         * Sets the list of enabled {@linkplain TlsVersion TLS versions} (TLSv1.3, TLSv1.2, etc.) to secure the
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
         * @return the list of enabled {@linkplain CipherSuite TLS cipher suites}, it is a sub-list of
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
