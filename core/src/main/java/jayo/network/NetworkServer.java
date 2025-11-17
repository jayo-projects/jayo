/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.network;

import jayo.JayoClosedResourceException;
import jayo.Socket;
import jayo.internal.network.NetworkServerBuilder;
import jayo.internal.network.ServerSocketChannelNetworkServer;
import jayo.internal.network.ServerSocketNetworkServer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.time.Duration;

/**
 * A server backed by an underlying network server socket. This socket is <b>guaranteed to be bound</b>.
 * <p>
 * The {@link #getLocalAddress()} method returns the local address that the socket is bound to, the
 * {@link #getOption(SocketOption)} method is used to query socket options and the {@link #getUnderlying()} method
 * returns the underlying socket itself: an {@linkplain java.net.ServerSocket IO ServerSocket} or a
 * {@linkplain java.nio.channels.ServerSocketChannel NIO ServerSocketChannel}.
 * <p>
 * Call {@link #accept()} to block until a request is received and the resulting connection is established.
 * <p>
 * Please read {@link Socket} javadoc for the socket rationale.
 */
public sealed interface NetworkServer extends AutoCloseable
        permits ServerSocketChannelNetworkServer, ServerSocketNetworkServer {
    /**
     * @return a new TCP {@link NetworkServer} backed by an underlying
     * {@linkplain java.nio.channels.ServerSocketChannel NIO ServerSocketChannel} bound to the provided
     * {@code localAddress} socket address.
     * <p>
     * This method uses default configuration, with no read/write timeouts, no {@linkplain SocketOption socket options}
     * for the server and its accepted sockets, and no max pending connections set on the underlying server socket.
     * <p>
     * If you need specific options, please use {@link #builder()} instead.
     * @throws jayo.JayoException If an I/O error occurs.
     */
    static @NonNull NetworkServer bindTcp(final @NonNull InetSocketAddress localAddress) {
        return builder().bindTcp(localAddress);
    }

    /**
     * @return a {@link NetworkServer} builder.
     */
    static @NonNull Builder builder() {
        return new NetworkServerBuilder();
    }

    /**
     * @return a new server-side {@link NetworkSocket} that was created after an incoming client request. The
     * executing thread blocks until a request is received and the resulting connection is established or an error
     * occurs.
     * @throws UnsupportedOperationException If one of the socket options you set with
     *                                       {@link Builder#option(SocketOption, Object)} is not supported by the
     *                                       network socket.
     * @throws IllegalArgumentException      If one of the socket options' value you set with
     *                                       {@link Builder#option(SocketOption, Object)} is not a valid value for
     *                                       this socket option.
     * @throws JayoClosedResourceException   If this network server was closed when waiting for an incoming client
     *                                       request.
     * @throws jayo.JayoException            If an I/O error occurs.
     */
    @NonNull
    NetworkSocket accept();

    /**
     * Closes this network server.
     * <p>
     * Any thread currently blocked in {@link #accept()} will throw a {@link JayoClosedResourceException}.
     * <p>
     * It is safe to close a network server more than once, but only the first call has an effect.
     *
     * @throws jayo.JayoException If an I/O error occurs.
     */
    void close();

    /**
     * @return the local address that this network server's underlying socket is bound to.
     * @throws JayoClosedResourceException If this network server is closed.
     * @throws jayo.JayoException          If an I/O error occurs.
     */
    @NonNull
    InetSocketAddress getLocalAddress();

    /**
     * @param <T>  The type of the socket option value.
     * @param name The socket option.
     * @return The value of the socket option. A value of {@code null} may be a valid value for some socket options.
     * @throws UnsupportedOperationException If this network server does not support the socket option.
     * @throws JayoClosedResourceException   If this network server is closed.
     * @throws jayo.JayoException            If an I/O error occurs.
     * @see java.net.StandardSocketOptions
     */
    <T> @Nullable T getOption(final @NonNull SocketOption<T> name);

    /**
     * @return the underlying IO resource. For example, a {@linkplain java.net.ServerSocket IO ServerSocket} or a
     * {@linkplain java.nio.channels.ServerSocketChannel NIO ServerSocketChannel}.
     */
    @NonNull
    Object getUnderlying();

    /**
     * The builder used to create a {@link NetworkServer}.
     */
    sealed interface Builder extends Cloneable permits NetworkServerBuilder {
        /**
         * Sets the default read timeout of all read operations of the
         * {@linkplain NetworkServer#accept() accepted network sockets}. Default is zero. A timeout of zero is
         * interpreted as an infinite timeout.
         * <p>
         * Note: after the socket is accepted, you can change the read timeout at any time by calling
         * {@link NetworkSocket#setReadTimeout(Duration)}.
         */
        @NonNull
        Builder readTimeout(final @NonNull Duration readTimeout);

        /**
         * Sets the default write timeout of all write operations of the
         * {@linkplain NetworkServer#accept() accepted network sockets}. Default is zero. A timeout of zero is
         * interpreted as an infinite timeout.
         * <p>
         * Note: after the socket is accepted, you can change the read timeout at any time by calling
         * {@link NetworkSocket#setReadTimeout(Duration)}.
         */
        @NonNull
        Builder writeTimeout(final @NonNull Duration writeTimeout);

        /**
         * Sets the value of a socket option to set on the
         * {@linkplain NetworkServer#accept() accepted network sockets}.
         *
         * @param <T>   The type of the socket option value
         * @param name  The socket option
         * @param value The value of the socket option. A value of {@code null} may be a valid value for some socket
         *              options.
         * @see java.net.StandardSocketOptions
         */
        <T> @NonNull Builder option(final @NonNull SocketOption<T> name, final @Nullable T value);

        /**
         * Sets the value of a socket option to set on the {@link NetworkServer} that will be built using this
         * builder.
         *
         * @param <T>   The type of the socket option value
         * @param name  The socket option
         * @param value The value of the socket option. A value of {@code null} may be a valid value for some socket
         *              options.
         * @see java.net.StandardSocketOptions
         */
        <T> @NonNull Builder serverOption(final @NonNull SocketOption<T> name, final @Nullable T value);

        /**
         * Sets the maximum number of pending connections on the {@link NetworkServer} that will be built using this
         * builder. Default is zero. If the value is zero, an implementation-specific default is used.
         */
        @NonNull
        Builder maxPendingConnections(final int maxPendingConnections);

        /**
         * Sets the {@link NetworkProtocol network protocol} to use when opening the underlying NIO server sockets:
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
         * If true, the underlying server sockets will be Java NIO ones. If false, they will be Java IO ones. Default is
         * {@code true}.
         */
        @NonNull
        Builder useNio(final boolean useNio);

        /**
         * @return a deep copy of this builder.
         */
        @NonNull
        Builder clone();

        /**
         * @return a new TCP {@link NetworkServer} bound to the provided {@code localAddress} socket address.
         * @throws jayo.JayoException If an I/O error occurs.
         */
        @NonNull
        NetworkServer bindTcp(final @NonNull InetSocketAddress localAddress);
    }
}
