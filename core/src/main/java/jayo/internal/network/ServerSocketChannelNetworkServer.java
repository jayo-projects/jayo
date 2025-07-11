/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.JayoException;
import jayo.network.NetworkEndpoint;
import jayo.network.NetworkServer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.util.Map;
import java.util.Objects;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * A {@link NetworkServer} backed by an underlying {@linkplain ServerSocketChannel NIO ServerSocketChannel}.
 */
@SuppressWarnings({"unchecked", "RawUseOfParameterized"})
public final class ServerSocketChannelNetworkServer implements NetworkServer {
    private static final System.Logger LOGGER = System.getLogger("jayo.network.ServerSocketChannelNetworkServer");

    private final @NonNull ServerSocketChannel serverSocketChannel;
    private final long defaultReadTimeoutNanos;
    private final long defaultWriteTimeoutNanos;
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions;

    ServerSocketChannelNetworkServer(
            final @NonNull SocketAddress localAddress,
            final long defaultReadTimeoutNanos,
            final long defaultWriteTimeoutNanos,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> serverSocketOptions,
            final int maxPendingConnections,
            final @Nullable ProtocolFamily family) {
        assert localAddress != null;
        assert defaultReadTimeoutNanos >= 0;
        assert defaultWriteTimeoutNanos >= 0;
        assert socketOptions != null;
        assert serverSocketOptions != null;
        assert maxPendingConnections >= 0;

        try {
            final var serverSocketChannel = (family != null)
                    ? ServerSocketChannel.open(family)
                    : ServerSocketChannel.open();
            serverSocketChannel.bind(localAddress, maxPendingConnections);

            for (final var serverSocketOption : serverSocketOptions.entrySet()) {
                serverSocketChannel.setOption(serverSocketOption.getKey(), serverSocketOption.getValue());
            }
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "new ServerSocketChannelNetworkServer bound to {0}, protocol family = {1}{2}" +
                                "provided socket options = {3}",
                        localAddress, family, System.lineSeparator(), serverSocketOptions);
            }

            this.serverSocketChannel = serverSocketChannel;
        } catch (IOException e) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "new ServerSocketChannelNetworkServer failed to bind to " + localAddress, e);
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
            final var socketChannel = serverSocketChannel.accept();
            for (final var socketOption : socketOptions.entrySet()) {
                socketChannel.setOption(socketOption.getKey(), socketOption.getValue());
            }
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "accepted server SocketChannelNetworkEndpoint connected to {0}{1}default " +
                                "read timeout = {2} ns, default write timeout = {3} ns{4}provided socket options = {5}",
                        socketChannel.getRemoteAddress(), System.lineSeparator(), defaultReadTimeoutNanos,
                        defaultWriteTimeoutNanos, System.lineSeparator(), socketOptions);
            }

            return new SocketChannelNetworkEndpoint(socketChannel, defaultReadTimeoutNanos, defaultWriteTimeoutNanos);
        } catch (IOException e) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "ServerSocketChannelNetworkServer failed to accept a client connection", e);
            }
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
    public @NonNull InetSocketAddress getLocalAddress() {
        try {
            return (InetSocketAddress) serverSocketChannel.getLocalAddress();
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
