/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.network;

import jayo.JayoClosedResourceException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.time.Duration;

/**
 * A raw network socket is either the client-side or server-side end of a socket-based connection between two peers.
 * {@link RawNetworkSocket} guarantee that its underlying socket is <b>open</b> upon creation.
 */
public sealed interface RawNetworkSocket permits NetworkSocket, NetworkSocket.Unconnected {
    /**
     * @return the local socket address that this network socket's underlying socket is bound to.
     * @throws JayoClosedResourceException If this network socket is closed.
     * @throws jayo.JayoException          If an I/O error occurs.
     */
    @NonNull
    InetSocketAddress getLocalAddress();

    /**
     * @return the timeout that applies on each low-level read operation of this network socket.
     */
    @NonNull
    Duration getReadTimeout();

    /**
     * @return the timeout that applies on each low-level write operation of this network socket.
     */
    @NonNull
    Duration getWriteTimeout();

    /**
     * @param <T>  The type of the socket option value.
     * @param name The socket option.
     * @return The value of the socket option. A value of {@code null} may be a valid value for some socket options.
     * @throws UnsupportedOperationException If this network socket does not support the socket option.
     * @throws JayoClosedResourceException   If this network socket is closed.
     * @throws jayo.JayoException            If an I/O error occurs.
     * @see java.net.StandardSocketOptions
     */
    <T> @Nullable T getOption(final @NonNull SocketOption<T> name);

    /**
     * Fail any in-flight and future operations. This operation may be called by any thread at any time.
     */
    void cancel();
}
