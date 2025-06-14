/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.JayoClosedResourceException;
import jayo.JayoException;
import jayo.network.NetworkEndpoint;
import jayo.network.NetworkServer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Map;
import java.util.Objects;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * A {@link NetworkServer} backed by an underlying {@linkplain ServerSocket IO ServerSocket}.
 */
@SuppressWarnings({"unchecked", "RawUseOfParameterized"})
public final class ServerSocketNetworkServer implements NetworkServer {
    private static final System.Logger LOGGER = System.getLogger("jayo.network.ServerSocketNetworkServer");

    private final @NonNull ServerSocket serverSocket;
    private final long defaultReadTimeoutNanos;
    private final long defaultWriteTimeoutNanos;
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions;

    ServerSocketNetworkServer(final @NonNull SocketAddress localAddress,
                              final long defaultReadTimeoutNanos,
                              final long defaultWriteTimeoutNanos,
                              final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
                              final @NonNull Map<@NonNull SocketOption, @Nullable Object> serverSocketOptions,
                              final int maxPendingConnections) {
        assert localAddress != null;
        assert defaultReadTimeoutNanos >= 0L;
        assert defaultWriteTimeoutNanos >= 0L;
        assert socketOptions != null;
        assert serverSocketOptions != null;
        assert maxPendingConnections >= 0;

        try {
            final var serverSocket = new ServerSocket();
            serverSocket.bind(localAddress, maxPendingConnections);

            for (final var serverSocketOption : serverSocketOptions.entrySet()) {
                serverSocket.setOption(serverSocketOption.getKey(), serverSocketOption.getValue());
            }

            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "new ServerSocketNetworkServer bound to {0}{1}provided socket options = {2}",
                        localAddress, System.lineSeparator(), serverSocketOptions);
            }

            this.serverSocket = serverSocket;
        } catch (IOException e) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "new ServerSocketNetworkServer failed to bind to " + localAddress, e);
            }
            throw JayoException.buildJayoException(e);
        }

        this.defaultReadTimeoutNanos = defaultReadTimeoutNanos;
        this.defaultWriteTimeoutNanos = defaultWriteTimeoutNanos;
        this.socketOptions = socketOptions;
    }

    @Override
    public @NonNull NetworkEndpoint accept() {
        try {
            final var socket = serverSocket.accept();
            for (final var socketOption : socketOptions.entrySet()) {
                socket.setOption(socketOption.getKey(), socketOption.getValue());
            }
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "accepted server SocketNetworkEndpoint connected to {0}{1}default read " +
                                "timeout = {2} ns, default write timeout = {3} ns{4}provided socket options = {5}",
                        socket.getRemoteSocketAddress(), System.lineSeparator(), defaultReadTimeoutNanos,
                        defaultWriteTimeoutNanos, System.lineSeparator(), socketOptions);
            }

            return new SocketNetworkEndpoint(socket, defaultReadTimeoutNanos, defaultWriteTimeoutNanos);
        } catch (IOException e) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "ServerSocketNetworkServer failed to accept a client connection", e);
            }
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public @NonNull InetSocketAddress getLocalAddress() {
        throwIfClosed();
        return (InetSocketAddress) serverSocket.getLocalSocketAddress();
    }

    @Override
    public <T> @Nullable T getOption(final @NonNull SocketOption<T> name) {
        Objects.requireNonNull(name);
        try {
            throwIfClosed();
            return serverSocket.getOption(name);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public @NonNull ServerSocket getUnderlying() {
        return serverSocket;
    }

    private void throwIfClosed() {
        if (serverSocket.isClosed()) {
            throw new JayoClosedResourceException();
        }
    }
}
