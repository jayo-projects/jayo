/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.network;

import jayo.JayoClosedResourceException;
import jayo.RawSocket;
import jayo.Socket;
import jayo.internal.AbstractNetworkSocket;
import jayo.internal.IoSocketNetworkSocket;
import jayo.internal.SocketChannelNetworkSocket;
import jayo.internal.network.NetworkSocketBuilder;
import jayo.internal.network.SocksNetworkSocket;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.time.Duration;
import java.util.function.UnaryOperator;

/**
 * A network socket is either the client-side or server-side end of a socket-based connection between two peers.
 * {@link NetworkSocket} guarantee that its underlying socket is <b>open and connected</b> to a peer upon creation.
 * <p>
 * The {@link #getLocalAddress()} method returns the local address that the socket is bound to, the
 * {@link #getPeerAddress()} method returns the peer address to which the socket is connected, the
 * {@link #getOption(SocketOption)} method is used to query socket options and the {@link #getUnderlying()} method
 * returns the underlying socket itself: an {@linkplain java.net.Socket IO Socket} or a
 * {@linkplain java.nio.channels.SocketChannel NIO SocketChannel}.
 * <p>
 * Note: {@link NetworkSocket} honors timeout. When a read or write operation times out, the socket is asynchronously
 * closed by a watchdog thread. By default, no read nor write timeout is set, use {@link #setReadTimeout(Duration)} and
 * {@link #setWriteTimeout(Duration)} methods to set them.
 */
public sealed interface NetworkSocket extends RawNetworkSocket, RawSocket
        permits AbstractNetworkSocket, SocksNetworkSocket {
    /**
     * @return a new client-side TCP {@link NetworkSocket} backed by an underlying
     * {@linkplain java.nio.channels.SocketChannel NIO SocketChannel} connected to the server using the provided
     * {@code peerAddress} socket address.
     * <p>
     * This method uses default configuration, with no connect/read/write timeouts, no
     * {@linkplain SocketOption socket options} set on the underlying network socket and no proxy.
     * <p>
     * If you need any specific configuration, please use {@link #builder()} instead.
     * @throws jayo.JayoException If an I/O error occurs.
     */
    static @NonNull NetworkSocket connectTcp(final @NonNull InetSocketAddress peerAddress) {
        return builder().openTcp().connect(peerAddress);
    }

    /**
     * @return a client-side {@link NetworkSocket} builder.
     */
    static @NonNull Builder builder() {
        return new NetworkSocketBuilder();
    }

    /**
     * @return the peer socket address to which this network socket's underlying socket is connected.
     * @throws JayoClosedResourceException If this network socket is closed.
     * @throws jayo.JayoException          If an I/O error occurs.
     */
    @NonNull
    InetSocketAddress getPeerAddress();

    /**
     * Sets the timeout that will apply on each low-level read operation of this network socket. A timeout of zero is
     * interpreted as an infinite timeout.
     *
     * @see NetworkSocket.Builder#readTimeout(Duration)
     * @see NetworkServer.Builder#readTimeout(Duration)
     */
    void setReadTimeout(final @NonNull Duration readTimeout);

    /**
     * Sets the timeout that will apply on each low-level write operation of this network socket. A timeout of zero is
     * interpreted as an infinite timeout.
     *
     * @see NetworkSocket.Builder#writeTimeout(Duration)
     * @see NetworkServer.Builder#writeTimeout(Duration)
     */
    void setWriteTimeout(final @NonNull Duration writeTimeout);

    /**
     * @return the {@link Proxy.Socks} that is used as intermediary between this and the peer, if any.
     */
    default Proxy.@Nullable Socks getProxy() {
        return null;
    }

    /**
     * @return the underlying resource. For example, a {@linkplain java.net.Socket IO Socket}, a
     * {@linkplain java.nio.channels.SocketChannel NIO SocketChannel} or another {@link Socket} / {@link RawSocket}.
     */
    @NonNull
    Object getUnderlying();

    /**
     * The builder used to create a client-side {@link NetworkSocket}.
     */
    sealed interface Builder extends Cloneable permits NetworkSocketBuilder {
        /**
         * Sets the timeout for establishing the connection to the peer, including the proxy initialization if one is
         * used. Default is zero. A timeout of zero is interpreted as an infinite timeout.
         */
        @NonNull
        Builder connectTimeout(final @NonNull Duration connectTimeout);

        /**
         * @return the currently configured timeout for establishing the connection to the peer, including the proxy
         * initialization if one is used.
         * @see #connectTimeout(Duration)
         */
        @NonNull
        Duration getConnectTimeout();

        /**
         * Sets the default read timeout that will apply on each low-level read operation of the network socket.
         * Default is zero. A timeout of zero is interpreted as an infinite timeout.
         * <p>
         * Note: after the socket is created, you can change the read timeout at any time by calling
         * {@linkplain NetworkSocket#setReadTimeout(Duration) NetworkSocket.setReadTimeout(Duration)}.
         */
        @NonNull
        Builder readTimeout(final @NonNull Duration readTimeout);

        /**
         * @return the currently configured default read timeout that will apply on each low-level read operation of the
         * network socket.
         * <p>
         * Note: after the socket is created, you can change the read timeout at any time by calling
         * {@linkplain NetworkSocket#setReadTimeout(Duration) NetworkSocket.setReadTimeout(Duration)}.
         * @see #readTimeout(Duration)
         */
        @NonNull
        Duration getReadTimeout();

        /**
         * Sets the default write timeout that will apply on each low-level write operation of the network socket.
         * Default is zero. A timeout of zero is interpreted as an infinite timeout.
         * <p>
         * Note: after the socket is created, you can change the write timeout at any time by calling
         * {@linkplain NetworkSocket#setWriteTimeout(Duration) NetworkSocket.setWriteTimeout(Duration)}.
         */
        @NonNull
        Builder writeTimeout(final @NonNull Duration writeTimeout);

        /**
         * @return the currently configured default write timeout that will apply on each low-level write operation of
         * the network socket.
         * Note: after the socket is created, you can change the write timeout at any time by calling
         * {@linkplain NetworkSocket#setWriteTimeout(Duration) NetworkSocket.setWriteTimeout(Duration)}.
         * @see #writeTimeout(Duration)
         */
        @NonNull
        Duration getWriteTimeout();

        /**
         * Sets the value of a socket option to set on the network socket.
         *
         * @param <T>   The type of the socket option value.
         * @param name  The socket option.
         * @param value The value of the socket option. A value of {@code null} may be a valid value for some socket
         *              options.
         * @see java.net.StandardSocketOptions
         */
        <T> @NonNull Builder option(final @NonNull SocketOption<T> name, final @Nullable T value);

        /**
         * Sets the {@link NetworkProtocol network protocol} to use when opening the underlying NIO sockets:
         * {@code IPv4} or {@code IPv6}. The default protocol is platform (and possibly configuration) dependent and
         * therefore unspecified.
         * <p>
         * This option <b>is only available for Java NIO</b>, so Java NIO mode is forced when this parameter is set!
         *
         * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html#Ipv4IPv6">
         * java.net.preferIPv4Stack</a> system property
         */
        @NonNull
        Builder protocol(final @NonNull NetworkProtocol protocol);

        /**
         * If true, the underlying socket will be a Java NIO one. If false, it will be a Java IO one. Default is
         * {@code true}.
         */
        @NonNull
        Builder useNio(final boolean useNio);

        /**
         * This method exists mainly to support debugging or testing. You may also use it to redirect to another peer
         * address. For debugging purpose, debug then return the argument address.
         *
         * @param peerAddressModifier a function to select the {@link InetSocketAddress} to connect to, based on the
         *                            initially targeted peer address.
         */
        @NonNull
        Builder onConnect(final @NonNull UnaryOperator<@NonNull InetSocketAddress> peerAddressModifier);

        /**
         * @return a deep copy of this builder.
         */
        @NonNull
        Builder clone();

        /**
         * @return a new {@linkplain Unconnected unconnected client-side TCP socket}.
         * @throws jayo.JayoException If an I/O error occurs.
         */
        @NonNull
        Unconnected openTcp();
    }

    /**
     * An unconnected client-side {@link RawNetworkSocket}.
     */
    sealed interface Unconnected extends RawNetworkSocket
            permits IoSocketNetworkSocket.Unconnected, SocketChannelNetworkSocket.Unconnected {
        /**
         * @return a new client-side {@link NetworkSocket} connected to the server using the provided
         * {@code peerAddress} socket address.
         * @throws jayo.JayoException If an I/O error occurs.
         */
        @NonNull
        NetworkSocket connect(final @NonNull InetSocketAddress peerAddress);

        /**
         * @return a new client-side TCP {@link NetworkSocket} connected to the server using the provided
         * {@code peerAddress} socket address using the provided Socks {@code proxy} as intermediary between this and
         * the peer server.
         * @throws jayo.JayoException If an I/O error occurs.
         */
        @NonNull
        NetworkSocket connect(final @NonNull InetSocketAddress peerAddress, final Proxy.@NonNull Socks proxy);
    }
}
