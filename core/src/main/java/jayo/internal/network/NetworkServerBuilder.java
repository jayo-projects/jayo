/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.network.NetworkProtocol;
import jayo.network.NetworkServer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketOption;
import java.net.StandardProtocolFamily;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.lang.System.Logger.Level.INFO;

@SuppressWarnings("RawUseOfParameterized")
public final class NetworkServerBuilder implements NetworkServer.Builder {
    private static final System.Logger LOGGER = System.getLogger("jayo.network.NetworkServerBuilder");

    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions;
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> serverSocketOptions;
    private int maxPendingConnections;
    private @Nullable ProtocolFamily protocolFamily;
    private boolean useNio;

    public NetworkServerBuilder() {
        this(new HashMap<>(), new HashMap<>(), 0, null, true);
    }

    /**
     * The private constructor used by {@link #clone()}.
     */
    private NetworkServerBuilder(final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
                                 final @NonNull Map<@NonNull SocketOption, @Nullable Object> serverSocketOptions,
                                 final int maxPendingConnections,
                                 final @Nullable ProtocolFamily protocolFamily,
                                 final boolean useNio) {
        assert socketOptions != null;
        assert serverSocketOptions != null;

        this.socketOptions = socketOptions;
        this.serverSocketOptions = serverSocketOptions;
        this.maxPendingConnections = maxPendingConnections;
        this.protocolFamily = protocolFamily;
        this.useNio = useNio;
    }

    @Override
    public <T> @NonNull NetworkServerBuilder option(final @NonNull SocketOption<T> name, final @Nullable T value) {
        Objects.requireNonNull(name);
        socketOptions.put(name, value);
        return this;
    }

    @Override
    public <T> @NonNull NetworkServerBuilder serverOption(final @NonNull SocketOption<T> name,
                                                          final @Nullable T value) {
        Objects.requireNonNull(name);
        serverSocketOptions.put(name, value);
        return this;
    }

    @Override
    public @NonNull NetworkServerBuilder maxPendingConnections(final int maxPendingConnections) {
        if (maxPendingConnections < 0) {
            throw new IllegalArgumentException("maxPendingConnections < 0: " + maxPendingConnections);
        }
        this.maxPendingConnections = maxPendingConnections;
        return this;
    }

    @Override
    public @NonNull NetworkServerBuilder protocol(final @NonNull NetworkProtocol protocol) {
        Objects.requireNonNull(protocol);
        this.protocolFamily = switch (protocol) {
            case IPv4 -> StandardProtocolFamily.INET;
            case IPv6 -> StandardProtocolFamily.INET6;
        };
        if (!useNio) {
            LOGGER.log(INFO, "Setting a network protocol requires NIO mode, forcing it.");
            useNio = true;
        }
        return this;
    }

    @Override
    public @NonNull NetworkServerBuilder useNio(final boolean useNio) {
        if (!useNio && protocolFamily != null) {
            LOGGER.log(INFO, "You set a network protocol, it requires NIO mode, forcing it.");
            this.useNio = true;
            return this;
        }

        this.useNio = useNio;
        return this;
    }

    @Override
    public @NonNull NetworkServer bindTcp(final @NonNull InetSocketAddress localAddress) {
        Objects.requireNonNull(localAddress);

        if (useNio) {
            return new ServerSocketChannelNetworkServer(
                    localAddress,
                    socketOptions,
                    serverSocketOptions,
                    maxPendingConnections,
                    protocolFamily);
        }

        return new ServerSocketNetworkServer(
                localAddress,
                socketOptions,
                serverSocketOptions,
                maxPendingConnections);
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public @NonNull NetworkServerBuilder clone() {
        return new NetworkServerBuilder(
                socketOptions,
                serverSocketOptions,
                maxPendingConnections,
                protocolFamily,
                useNio);
    }
}
