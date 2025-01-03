/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.JayoClosedEndpointException;
import jayo.JayoException;
import jayo.external.NonNegative;
import jayo.network.NetworkEndpoint;
import jayo.network.NetworkServer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link NetworkServer} backed by an underlying {@linkplain ServerSocket IO ServerSocket}.
 */
@SuppressWarnings({"unchecked", "RawUseOfParameterized"})
public final class ServerSocketNetworkServer implements NetworkServer {
    private final @NonNull ServerSocket serverSocket;
    private final @NonNegative long defaultReadTimeoutNanos;
    private final @NonNegative long defaultWriteTimeoutNanos;
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions;

    ServerSocketNetworkServer(final @NonNull SocketAddress local,
                              final @NonNegative long defaultReadTimeoutNanos,
                              final @NonNegative long defaultWriteTimeoutNanos,
                              final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
                              final @NonNull Map<@NonNull SocketOption, @Nullable Object> serverSocketOptions,
                              final @NonNegative int maxPendingConnections) {
        assert local != null;
        assert defaultReadTimeoutNanos >= 0L;
        assert defaultWriteTimeoutNanos >= 0L;
        assert socketOptions != null;
        assert serverSocketOptions != null;
        assert maxPendingConnections >= 0;

        try {
            final var serverSocket = new ServerSocket();
            serverSocket.bind(local, maxPendingConnections);

            for (final var serverSocketOption : serverSocketOptions.entrySet()) {
                serverSocket.setOption(serverSocketOption.getKey(), serverSocketOption.getValue());
            }

            this.serverSocket = serverSocket;
        } catch (IOException e) {
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
            return new SocketNetworkEndpoint(socket, defaultReadTimeoutNanos, defaultWriteTimeoutNanos);
        } catch (IOException e) {
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
    public @NonNull SocketAddress getLocalAddress() {
        throwIfClosed();
        return serverSocket.getLocalSocketAddress();
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
            throw new JayoClosedEndpointException();
        }
    }
}
