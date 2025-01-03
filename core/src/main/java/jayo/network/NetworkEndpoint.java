/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.network;

import jayo.Endpoint;
import jayo.internal.network.NetworkEndpointBuilder;
import jayo.internal.network.SocketChannelNetworkEndpoint;
import jayo.internal.network.SocketNetworkEndpoint;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Duration;

/**
 * An endpoint backed by an underlying network socket. This socket is <b>guaranteed to be connected</b> to a peer.
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
public sealed interface NetworkEndpoint extends Endpoint permits SocketChannelNetworkEndpoint, SocketNetworkEndpoint {
    /**
     * @return a new client-side TCP {@link NetworkEndpoint} backed by an underlying
     * {@linkplain java.nio.channels.SocketChannel NIO SocketChannel} connected to the server using the provided
     * {@code peerAddress} socket address. The connection blocks until established or an error occurs.
     * <p>
     * This method uses default configuration, with no connect/read/write timeouts and no
     * {@linkplain SocketOption socket options} set on the underlying network socket.
     * <p>
     * If you need specific options, please use {@link #builderForNIO()} or {@link #builderForIO()} instead.
     * @throws jayo.JayoException If an I/O error occurs.
     */
    static @NonNull NetworkEndpoint connectTcp(final @NonNull SocketAddress peerAddress) {
        return builderForNIO().connect(peerAddress);
    }

    /**
     * @return a client-side {@link NetworkEndpoint} builder based on {@code java.nio.channels}.
     */
    static @NonNull NioBuilder builderForNIO() {
        return new NetworkEndpointBuilder.Nio();
    }

    /**
     * @return a client-side {@link NetworkEndpoint} builder based on {@code java.io}.
     */
    static @NonNull IoBuilder builderForIO() {
        return new NetworkEndpointBuilder.Io();
    }

    /**
     * @return the local address that this network endpoint's underlying socket is bound to.
     * @throws jayo.JayoClosedEndpointException If this network endpoint is closed.
     * @throws jayo.JayoException               If an I/O error occurs.
     */
    @NonNull
    SocketAddress getLocalAddress();

    /**
     * @return the peer address to which this network endpoint's underlying socket is connected.
     * @throws jayo.JayoClosedEndpointException If this network endpoint is closed.
     * @throws jayo.JayoException               If an I/O error occurs.
     */
    @NonNull
    SocketAddress getPeerAddress();

    /**
     * @param <T>  The type of the socket option value
     * @param name The socket option
     * @return The value of the socket option. A value of {@code null} may be a valid value for some socket options.
     * @throws UnsupportedOperationException    If the socket option is not supported by this channel
     * @throws jayo.JayoClosedEndpointException If this network endpoint is closed.
     * @throws jayo.JayoException               If an I/O error occurs.
     * @see java.net.StandardSocketOptions
     */
    <T> @Nullable T getOption(final @NonNull SocketOption<T> name);

    /**
     * The abstract configuration used to create a client-side {@link NetworkEndpoint}.
     */
    sealed interface Builder<T extends Builder<T>>
            permits IoBuilder, NioBuilder, NetworkEndpointBuilder {
        /**
         * Sets the connect timeout used in the {@link #connect(SocketAddress)} method. Default is zero. A timeout of
         * zero is interpreted as an infinite timeout.
         */
        @NonNull
        T connectTimeout(final @NonNull Duration connectTimeout);

        /**
         * Sets the default read timeout of all read operations of the network endpoints produced by this builder.
         * Default is zero. A timeout of zero is interpreted as an infinite timeout.
         */
        @NonNull
        T readTimeout(final @NonNull Duration readTimeout);

        /**
         * Sets the default write timeout of all write operations of the network endpoints produced by this builder.
         * Default is zero. A timeout of zero is interpreted as an infinite timeout.
         */
        @NonNull
        T writeTimeout(final @NonNull Duration writeTimeout);

        /**
         * Sets the value of a socket option to set on the network endpoints produced by this builder.
         *
         * @param <U>   The type of the socket option value
         * @param name  The socket option
         * @param value The value of the socket option. A value of {@code null} may be a valid value for some socket
         *              options.
         * @see java.net.StandardSocketOptions
         */
        <U> @NonNull T option(final @NonNull SocketOption<U> name, final @Nullable U value);

        /**
         * @return a new client-side {@link NetworkEndpoint} connected to the server using the provided
         * {@code peerAddress} socket address. The connection blocks until established or an error occurs.
         * <h3>Timeouts</h3>
         * <ul>
         *     <li>The specified {@linkplain #connectTimeout(Duration) connect timeout value} is used in this method for
         *     the connect operation.
         *     <li>The specified {@linkplain #readTimeout(Duration) read timeout value} will be used as default for each
         *     read operation made by the {@linkplain Endpoint#getReader() Endpoint's reader}.
         *     <li>The specified {@linkplain #writeTimeout(Duration) write timeout value} will be used as default for
         *     each write operation made by the {@linkplain Endpoint#getWriter() Endpoint's writer}.
         * </ul>
         * A timeout of zero is interpreted as an infinite timeout.
         * @throws UnsupportedOperationException If one of the socket options you set with
         *                                       {@link #option(SocketOption, Object)} is not supported by the
         *                                       network endpoint.
         * @throws IllegalArgumentException      If one of the socket options' value you set with
         *                                       {@link #option(SocketOption, Object)} is not a valid value for this
         *                                       socket option.
         * @throws jayo.JayoException            If an I/O error occurs.
         */
        @NonNull
        NetworkEndpoint connect(final @NonNull SocketAddress peerAddress);
    }

    /**
     * The configuration used to create a client-side {@link NetworkEndpoint} based on {@code java.nio.channels}.
     */
    sealed interface NioBuilder extends Builder<NioBuilder> permits NetworkEndpointBuilder.Nio {
        /**
         * Sets the {@link ProtocolFamily protocol family} to use when opening the underlying NIO sockets. The default
         * protocol family is platform (and possibly configuration) dependent and therefore unspecified.
         *
         * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html#Ipv4IPv6">
         * java.net.preferIPv4Stack</a> system property
         */
        @NonNull
        NioBuilder protocolFamily(final @NonNull ProtocolFamily family);
    }

    /**
     * The configuration used to create a client-side {@link NetworkEndpoint} based on {@code java.io}.
     */
    sealed interface IoBuilder extends Builder<IoBuilder> permits NetworkEndpointBuilder.Io {
    }
}
