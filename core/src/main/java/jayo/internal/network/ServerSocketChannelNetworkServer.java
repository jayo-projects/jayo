/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.JayoException;
import jayo.external.NonNegative;
import jayo.network.NetworkEndpoint;
import jayo.network.NetworkServer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link NetworkServer} backed by an underlying {@linkplain ServerSocketChannel NIO ServerSocketChannel}.
 */
@SuppressWarnings({"unchecked", "RawUseOfParameterized"})
public final class ServerSocketChannelNetworkServer implements NetworkServer {
    private final @NonNull ServerSocketChannel serverSocketChannel;
    private final @NonNegative long defaultReadTimeoutNanos;
    private final @NonNegative long defaultWriteTimeoutNanos;
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions;

    ServerSocketChannelNetworkServer(
            final @NonNull SocketAddress local,
            final @NonNegative long defaultReadTimeoutNanos,
            final @NonNegative long defaultWriteTimeoutNanos,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> serverSocketOptions,
            final @NonNegative int maxPendingConnections,
            final @Nullable ProtocolFamily family) {
        assert local != null;
        assert defaultReadTimeoutNanos >= 0;
        assert defaultWriteTimeoutNanos >= 0;
        assert socketOptions != null;
        assert serverSocketOptions != null;
        assert maxPendingConnections >= 0;

        try {
            final var serverSocketChannel = (family != null)
                    ? ServerSocketChannel.open(family)
                    : ServerSocketChannel.open();
            serverSocketChannel.bind(local, maxPendingConnections);

            for (final var serverSocketOption : serverSocketOptions.entrySet()) {
                serverSocketChannel.setOption(serverSocketOption.getKey(), serverSocketOption.getValue());
            }

            this.serverSocketChannel = serverSocketChannel;
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
            final var socketChannel = serverSocketChannel.accept();
            for (final var socketOption : socketOptions.entrySet()) {
                socketChannel.setOption(socketOption.getKey(), socketOption.getValue());
            }
            return new SocketChannelNetworkEndpoint(socketChannel, defaultReadTimeoutNanos, defaultWriteTimeoutNanos);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public void close() {
        try {
            serverSocketChannel.close();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public @NonNull SocketAddress getLocalAddress() {
        try {
            return serverSocketChannel.getLocalAddress();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public <T> @Nullable T getOption(final @NonNull SocketOption<T> name) {
        Objects.requireNonNull(name);
        try {
            return serverSocketChannel.getOption(name);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public @NonNull ServerSocketChannel getUnderlying() {
        return serverSocketChannel;
    }
}
