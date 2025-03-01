/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.network;

import jayo.Endpoint;
import jayo.JayoClosedResourceException;
import jayo.internal.network.NetworkServerConfig;
import jayo.internal.network.ServerSocketChannelNetworkServer;
import jayo.internal.network.ServerSocketNetworkServer;
import jayo.scheduling.TaskRunner;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Duration;

/**
 * A server backed by an underlying network server socket. This socket is <b>guaranteed to be bound</b>.
 * <p>
 * The {@link #getLocalAddress()} method returns the local address that the socket is bound to, the
 * {@link #getOption(SocketOption)} method is used to query socket options and the {@link #getUnderlying()} method
 * returns the underlying socket itself : an {@linkplain java.net.ServerSocket IO ServerSocket} or a
 * {@linkplain java.nio.channels.ServerSocketChannel NIO ServerSocketChannel}.
 * <p>
 * Please read {@link Endpoint} javadoc for the endpoint rationale.
 */
public sealed interface NetworkServer extends Closeable
        permits ServerSocketChannelNetworkServer, ServerSocketNetworkServer {
    /**
     * @return a new TCP {@link NetworkServer} backed by an underlying
     * {@linkplain java.nio.channels.ServerSocketChannel NIO ServerSocketChannel} bound to the provided
     * {@code localAddress} socket address.
     * <p>
     * This method uses default configuration, with no read/write timeouts, no {@linkplain SocketOption socket options}
     * for the server and its accepted sockets, and no max pending connections set on the underlying server socket.
     * <p>
     * If you need specific options, please use {@link #bindTcp(SocketAddress, Config)}  instead.
     * @throws jayo.JayoException If an I/O error occurs.
     */
    static @NonNull NetworkServer bindTcp(final @NonNull SocketAddress localAddress) {
        return bindTcp(localAddress, configForNIO());
    }

    /**
     * @return a new TCP {@link NetworkServer} bound to the provided {@code localAddress} socket address.
     * <p>
     * This method uses the provided {@code config} configuration, which can be used to configure read/write timeouts
     * and the {@linkplain SocketOption socket options} for the server and its accepted sockets, and the max pending
     * connections set on the underlying server socket.
     * @throws jayo.JayoException If an I/O error occurs.
     */
    static @NonNull NetworkServer bindTcp(final @NonNull SocketAddress localAddress, final @NonNull Config<?> config) {
        return ((NetworkServerConfig<?>) config).bind(localAddress);
    }

    /**
     * @return a {@link NetworkServer} configuration based on {@code java.nio.channels}.
     */
    static @NonNull NioConfig configForNIO() {
        return new NetworkServerConfig.Nio();
    }

    /**
     * @return a {@link NetworkServer} configuration based on {@code java.io}.
     */
    static @NonNull IoConfig configForIO() {
        return new NetworkServerConfig.Io();
    }

    /**
     * @return a new server-side {@link NetworkEndpoint} that was created after an incoming client request. The server
     * loop task blocks until a request is received and then the resulting connection is established or an error occurs.
     * <h3>Timeouts</h3>
     * <ul>
     *     <li>The specified {@linkplain Config#readTimeout(Duration) read timeout value} will be used as default for
     *     each read operation of the {@linkplain jayo.Endpoint#getReader() Endpoint's reader}.
     *     <li>The specified {@linkplain Config#writeTimeout(Duration) write timeout value} will be used as default for
     *     each write operation of the {@linkplain jayo.Endpoint#getWriter() Endpoint's writer}.
     * </ul>
     * A timeout of zero is interpreted as an infinite timeout.
     * @throws UnsupportedOperationException If one of the socket options you set with
     *                                       {@link Config#option(SocketOption, Object)} is not supported by the
     *                                       network endpoint.
     * @throws IllegalArgumentException      If one of the socket options' value you set with
     *                                       {@link Config#option(SocketOption, Object)} is not a valid value for
     *                                       this socket option.
     * @throws JayoClosedResourceException   If this network server was closed when waiting for an incoming client
     *                                       request.
     * @throws jayo.JayoException            If an I/O error occurs.
     */
    @NonNull
    NetworkEndpoint accept();

    /**
     * Closes this network server.
     * <p>
     * Any thread currently blocked in {@link #accept()} will throw a {@link JayoClosedResourceException}.
     * <p>
     * If this server endpoint is already closed then invoking this method has no effect.
     *
     * @throws jayo.JayoException If an I/O error occurs.
     */
    void close();

    /**
     * @return the local address that this network server's underlying socket is bound to.
     * @throws JayoClosedResourceException If this network endpoint is closed.
     * @throws jayo.JayoException          If an I/O error occurs.
     */
    @NonNull
    InetSocketAddress getLocalAddress();

    /**
     * @param <T>  The type of the socket option value
     * @param name The socket option
     * @return The value of the socket option. A value of {@code null} may be a valid value for some socket options.
     * @throws UnsupportedOperationException If the socket option is not supported by this channel
     * @throws JayoClosedResourceException   If this network endpoint is closed.
     * @throws jayo.JayoException            If an I/O error occurs.
     * @see java.net.StandardSocketOptions
     */
    <T> @Nullable T getOption(final @NonNull SocketOption<T> name);

    /**
     * @return the underlying IO resource. For example a {@linkplain java.net.ServerSocket IO Socket} or a
     * {@linkplain java.nio.channels.ServerSocketChannel NIO ServerSocketChannel}.
     */
    @NonNull
    Object getUnderlying();

    /**
     * The configuration used to create a {@link NetworkServer}.
     */
    sealed interface Config<T extends Config<T>> permits IoConfig, NioConfig, NetworkServerConfig {
        /**
         * Sets the default read timeout of all read operations of the
         * {@linkplain NetworkServer#accept() accepted network endpoints} by the {@link NetworkServer}
         * built using this configuration. Default is zero. A timeout of zero is interpreted as an infinite timeout.
         */
        @NonNull
        T readTimeout(final @NonNull Duration readTimeout);

        /**
         * Sets the default write timeout of all write operations of the
         * {@linkplain NetworkServer#accept() accepted network endpoints} by the {@link NetworkServer}
         * built using this configuration. Default is zero. A timeout of zero is interpreted as an infinite timeout.
         */
        @NonNull
        T writeTimeout(final @NonNull Duration writeTimeout);

        /**
         * Read and write operations on the {@linkplain NetworkServer#accept() accepted network endpoints} by the
         * {@link NetworkServer} built using this configuration are seamlessly processed <b>asynchronously</b> in
         * distinct runnable tasks using the provided {@code taskRunner}.
         */
        @NonNull
        T bufferAsync(final @NonNull TaskRunner taskRunner);

        /**
         * Sets the value of a socket option to set on the
         * {@linkplain NetworkServer#accept() accepted network endpoints} by the {@link NetworkServer} built using this
         * configuration.
         *
         * @param <U>   The type of the socket option value
         * @param name  The socket option
         * @param value The value of the socket option. A value of {@code null} may be a valid value for some socket
         *              options.
         * @see java.net.StandardSocketOptions
         */
        <U> @NonNull T option(final @NonNull SocketOption<U> name, final @Nullable U value);

        /**
         * Sets the value of a socket option to set on the {@link NetworkServer} built using this configuration.
         *
         * @param <U>   The type of the socket option value
         * @param name  The socket option
         * @param value The value of the socket option. A value of {@code null} may be a valid value for some socket
         *              options.
         * @see java.net.StandardSocketOptions
         */
        <U> @NonNull T serverOption(final @NonNull SocketOption<U> name, final @Nullable U value);

        /**
         * Sets the maximum number of pending connections on the {@link NetworkServer} built by using this
         * configuration. Default is zero. If the value is zero, an implementation specific default is used.
         */
        @NonNull
        T maxPendingConnections(final int maxPendingConnections);
    }

    /**
     * The configuration used to create a {@link NetworkServer} based on {@code java.nio.channels}.
     */
    sealed interface NioConfig extends Config<NioConfig> permits NetworkServerConfig.Nio {
        /**
         * Sets the {@link NetworkProtocol network protocol} to use when opening the underlying NIO server sockets. The
         * default protocol is platform (and possibly configuration) dependent and therefore unspecified.
         *
         * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html#Ipv4IPv6">
         * java.net.preferIPv4Stack</a> system property
         */
        @NonNull
        NioConfig protocol(final @NonNull NetworkProtocol protocol);
    }

    /**
     * The configuration used to create a {@link NetworkServer} based on {@code java.io}
     */
    sealed interface IoConfig extends Config<IoConfig> permits NetworkServerConfig.Io {
    }
}
