/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.network;

import jayo.Endpoint;
import jayo.JayoClosedResourceException;
import jayo.internal.network.NetworkEndpointBuilder;
import jayo.internal.network.SocketChannelNetworkEndpoint;
import jayo.internal.network.SocketNetworkEndpoint;
import jayo.internal.network.SocksNetworkEndpoint;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.time.Duration;

/**
 * A network endpoint is either the client-side or server-side end of a socket-based connection between two peers.
 * {@link NetworkEndpoint} guarantee that its underlying socket is <b>open and connected</b> to a peer upon creation.
 * <p>
 * The {@link #getLocalAddress()} method returns the local address that the socket is bound to, the
 * {@link #getPeerAddress()} method returns the peer address to which the socket is connected, the
 * {@link #getOption(SocketOption)} method is used to query socket options and the {@link #getUnderlying()} method
 * returns the underlying socket itself: an {@linkplain java.net.Socket IO Socket} or a
 * {@linkplain java.nio.channels.SocketChannel NIO SocketChannel}.
 * <p>
 * Note: {@link NetworkEndpoint} honors timeout. When a read or write operation times out, the socket is asynchronously
 * closed by a watchdog thread.
 *
 * @see Endpoint
 */
public sealed interface NetworkEndpoint extends Endpoint
        permits SocketChannelNetworkEndpoint, SocketNetworkEndpoint, SocksNetworkEndpoint {
    /**
     * @return a new client-side TCP {@link NetworkEndpoint} backed by an underlying
     * {@linkplain java.nio.channels.SocketChannel NIO SocketChannel} connected to the server using the provided
     * {@code peerAddress} socket address.
     * <p>
     * This method uses default configuration, with no connect/read/write timeouts, no
     * {@linkplain SocketOption socket options} set on the underlying network socket and no proxy.
     * <p>
     * If you need any specific configuration, please use {@link #builder()} instead.
     * @throws jayo.JayoException If an I/O error occurs.
     */
    static @NonNull NetworkEndpoint connectTcp(final @NonNull InetSocketAddress peerAddress) {
        return builder().connectTcp(peerAddress);
    }

    /**
     * @return a client-side {@link NetworkEndpoint} builder.
     */
    static @NonNull Builder builder() {
        return new NetworkEndpointBuilder();
    }

    /**
     * @return the local socket address that this network endpoint's underlying socket is bound to.
     * @throws JayoClosedResourceException If this network endpoint is closed.
     * @throws jayo.JayoException          If an I/O error occurs.
     */
    @NonNull
    InetSocketAddress getLocalAddress();

    /**
     * @return the peer socket address to which this network endpoint's underlying socket is connected.
     * @throws JayoClosedResourceException If this network endpoint is closed.
     * @throws jayo.JayoException          If an I/O error occurs.
     */
    @NonNull
    InetSocketAddress getPeerAddress();

    /**
     * @return the {@link Proxy.Socks} that is used as intermediary between this and the peer, if any.
     */
    default Proxy.@Nullable Socks getProxy() {
        return null;
    }

    /**
     * @param <T>  The type of the socket option value.
     * @param name The socket option.
     * @return The value of the socket option. A value of {@code null} may be a valid value for some socket options.
     * @throws UnsupportedOperationException If this network endpoint does not support the socket option.
     * @throws JayoClosedResourceException   If this network endpoint is closed.
     * @throws jayo.JayoException            If an I/O error occurs.
     * @see java.net.StandardSocketOptions
     */
    <T> @Nullable T getOption(final @NonNull SocketOption<T> name);

    /**
     * The builder used to create a client-side {@link NetworkEndpoint}.
     */
    sealed interface Builder extends Cloneable permits NetworkEndpointBuilder {
        /**
         * Sets the timeout for establishing the connection to the peer, including the proxy initialization if one is
         * used. Default is zero. A timeout of zero is interpreted as an infinite timeout.
         */
        @NonNull
        Builder connectTimeout(final @NonNull Duration connectTimeout);

        /**
         * Sets the default read timeout that will apply on each low-level read operation of the network endpoint.
         * Default is zero. A timeout of zero is interpreted as an infinite timeout.
         */
        @NonNull
        Builder readTimeout(final @NonNull Duration readTimeout);

        /**
         * Sets the default write timeout that will apply on each low-level write operation of the network endpoint.
         * Default is zero. A timeout of zero is interpreted as an infinite timeout.
         */
        @NonNull
        Builder writeTimeout(final @NonNull Duration writeTimeout);

        /**
         * Sets the value of a socket option to set on the network endpoint.
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
         * @return a new client-side TCP {@link NetworkEndpoint} connected to the server using the provided
         * {@code peerAddress} socket address.
         * @throws jayo.JayoException If an I/O error occurs.
         */
        @NonNull
        NetworkEndpoint connectTcp(final @NonNull InetSocketAddress peerAddress);

        /**
         * @return a new client-side TCP {@link NetworkEndpoint} connected to the server using the provided
         * {@code peerAddress} socket address using the provided Socks {@code proxy} as intermediary between this and
         * the peer server.
         * @throws jayo.JayoException If an I/O error occurs.
         */
        @NonNull
        NetworkEndpoint connectTcp(final @NonNull InetSocketAddress peerAddress, final Proxy.@NonNull Socks proxy);

        /**
         * @return a deep copy of this builder.
         */
        @NonNull
        Builder clone();
    }
}
