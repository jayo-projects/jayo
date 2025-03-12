/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.network;

import jayo.Endpoint;
import jayo.JayoClosedResourceException;
import jayo.internal.network.NetworkEndpointConfig;
import jayo.internal.network.SocketChannelNetworkEndpoint;
import jayo.internal.network.SocketNetworkEndpoint;
import jayo.internal.network.SocksNetworkEndpoint;
import jayo.scheduling.TaskRunner;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.time.Duration;
import java.util.Objects;

/**
 * A network endpoint, either the client-side or server-side end of a socket based connection between two peers.
 * {@link NetworkEndpoint} guarantee that its underlying socket is <b>open and connected</b> to a peer upon creation.
 * <p>
 * The {@link #getLocalAddress()} method returns the local address that the socket is bound to, the
 * {@link #getPeerAddress()} method returns the peer address to which the socket is connected, the
 * {@link #getOption(SocketOption)} method is used to query socket options and the {@link #getUnderlying()} method
 * returns the underlying socket itself : an {@linkplain java.net.Socket IO Socket} or a
 * {@linkplain java.nio.channels.SocketChannel NIO SocketChannel}.
 * <p>
 * Network endpoints honors timeout. When a read or write operation times out, the socket is asynchronously closed by a
 * watchdog thread.
 * <p>
 * Please read {@link Endpoint} javadoc for the endpoint rationale.
 */
public sealed interface NetworkEndpoint extends Endpoint
        permits SocketChannelNetworkEndpoint, SocketNetworkEndpoint, SocksNetworkEndpoint {
    /**
     * @return a new client-side TCP {@link NetworkEndpoint} backed by an underlying
     * {@linkplain java.nio.channels.SocketChannel NIO SocketChannel} connected to the server using the provided
     * {@code peerAddress} socket address.
     * <p>
     * This method uses default configuration, with no connect/read/write timeouts and no
     * {@linkplain SocketOption socket options} set on the underlying network socket.
     * <p>
     * If you need specific options, please use {@link #connectTcp(InetSocketAddress, Config)} or
     * {@link #connectTcp(InetSocketAddress, Proxy.Socks, Config)} instead.
     * @throws jayo.JayoException If an I/O error occurs.
     */
    static @NonNull NetworkEndpoint connectTcp(final @NonNull InetSocketAddress peerAddress) {
        return connectTcp(peerAddress, configForNIO());
    }

    /**
     * @return a new client-side TCP {@link NetworkEndpoint} connected to the server using the provided
     * {@code peerAddress} socket address.
     * <p>
     * This method uses the provided {@code config} configuration, which can be used to configure connect/read/write
     * timeouts and the {@linkplain SocketOption socket options} to set on the underlying network socket.
     * @throws jayo.JayoException If an I/O error occurs.
     */
    static @NonNull NetworkEndpoint connectTcp(final @NonNull InetSocketAddress peerAddress,
                                               final @NonNull Config<?> config) {
        Objects.requireNonNull(peerAddress);
        Objects.requireNonNull(config);
        return ((NetworkEndpointConfig<?>) config).connect(peerAddress, null);
    }

    /**
     * @return a new client-side TCP {@link NetworkEndpoint} connected to the server using the provided
     * {@code peerAddress} socket address using the provided {@code proxy} as intermediary between this and the peer
     * server.
     * <p>
     * This method uses the provided {@code config} configuration, which can be used to configure connect/read/write
     * timeouts and the {@linkplain SocketOption socket options} to set on the underlying network socket.
     * @throws jayo.JayoException If an I/O error occurs.
     */
    static @NonNull NetworkEndpoint connectTcp(final @NonNull InetSocketAddress peerAddress,
                                               final Proxy.@NonNull Socks proxy,
                                               final @NonNull Config<?> config) {
        Objects.requireNonNull(peerAddress);
        Objects.requireNonNull(proxy);
        Objects.requireNonNull(config);
        return ((NetworkEndpointConfig<?>) config).connect(peerAddress, proxy);
    }

    /**
     * @return a client-side {@link NetworkEndpoint} configuration based on {@code java.nio.channels}.
     */
    static @NonNull NioConfig configForNIO() {
        return new NetworkEndpointConfig.Nio();
    }

    /**
     * @return a client-side {@link NetworkEndpoint} configuration based on {@code java.io}.
     */
    static @NonNull IoConfig configForIO() {
        return new NetworkEndpointConfig.Io();
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
     * @throws UnsupportedOperationException If the socket option is not supported by this channel.
     * @throws JayoClosedResourceException   If this network endpoint is closed.
     * @throws jayo.JayoException            If an I/O error occurs.
     * @see java.net.StandardSocketOptions
     */
    <T> @Nullable T getOption(final @NonNull SocketOption<T> name);

    /**
     * The abstract configuration used to create a client-side {@link NetworkEndpoint}.
     */
    sealed interface Config<T extends Config<T>>
            permits IoConfig, NioConfig, NetworkEndpointConfig {
        /**
         * Sets the timeout for establishing the connection to the peer, including the proxy initialization if one is
         * used. Default is zero. A timeout of zero is interpreted as an infinite timeout.
         */
        @NonNull
        T connectTimeout(final @NonNull Duration connectTimeout);

        /**
         * Sets the default read timeout that will apply on each low-level read operation of the underlying socket of
         * the network endpoints that uses this configuration. Default is zero. A timeout of zero is interpreted as an
         * infinite timeout.
         */
        @NonNull
        T readTimeout(final @NonNull Duration readTimeout);

        /**
         * Sets the default write timeout that will apply on each low-level write operation of the underlying socket of
         * the network endpoints that uses this configuration. Default is zero. A timeout of zero is interpreted as an
         * infinite timeout.
         */
        @NonNull
        T writeTimeout(final @NonNull Duration writeTimeout);

        /**
         * Read and write operations on the underlying socket of the network endpoints that uses this configuration are
         * seamlessly processed <b>asynchronously</b> in distinct runnable tasks using the provided {@code taskRunner}.
         */
        @NonNull
        T bufferAsync(final @NonNull TaskRunner taskRunner);

        /**
         * Sets the value of a socket option to set on the network endpoints that uses this configuration.
         *
         * @param <U>   The type of the socket option value.
         * @param name  The socket option.
         * @param value The value of the socket option. A value of {@code null} may be a valid value for some socket
         *              options.
         * @see java.net.StandardSocketOptions
         */
        <U> @NonNull T option(final @NonNull SocketOption<U> name, final @Nullable U value);
    }

    /**
     * The configuration used to create a client-side {@link NetworkEndpoint} based on {@code java.nio.channels}.
     */
    sealed interface NioConfig extends Config<NioConfig> permits NetworkEndpointConfig.Nio {
        /**
         * Sets the {@link NetworkProtocol network protocol} to use when opening the underlying NIO sockets. The default
         * protocol is platform (and possibly configuration) dependent and therefore unspecified.
         *
         * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html#Ipv4IPv6">
         * java.net.preferIPv4Stack</a> system property
         */
        @NonNull
        NioConfig protocol(final @NonNull NetworkProtocol protocol);
    }

    /**
     * The configuration used to create a client-side {@link NetworkEndpoint} based on {@code java.io}.
     */
    sealed interface IoConfig extends Config<IoConfig> permits NetworkEndpointConfig.Io {
    }
}
