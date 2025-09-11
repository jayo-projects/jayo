/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.internal.IoSocketNetworkSocket;
import jayo.internal.SocketChannelNetworkSocket;
import jayo.network.NetworkProtocol;
import jayo.network.NetworkSocket;
import jayo.network.Proxy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketOption;
import java.net.StandardProtocolFamily;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.lang.System.Logger.Level.INFO;

@SuppressWarnings("RawUseOfParameterized")
public final class NetworkSocketBuilder implements NetworkSocket.Builder {
    private static final System.Logger LOGGER = System.getLogger("jayo.network.NetworkSocketBuilder");

    private @Nullable Duration connectTimeout;
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions;
    private @Nullable ProtocolFamily protocolFamily;
    private boolean useNio;

    public NetworkSocketBuilder() {
        this(null, new HashMap<>(), null, true);
    }

    /**
     * The private constructor used by {@link #clone()}.
     */
    private NetworkSocketBuilder(final @Nullable Duration connectTimeout,
                                 final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
                                 final @Nullable ProtocolFamily protocolFamily,
                                 final boolean useNio) {
        assert socketOptions != null;

        this.connectTimeout = connectTimeout;
        this.socketOptions = socketOptions;
        this.protocolFamily = protocolFamily;
        this.useNio = useNio;
    }

    @Override
    public @NonNull NetworkSocketBuilder connectTimeout(final @NonNull Duration connectTimeout) {
        Objects.requireNonNull(connectTimeout);
        this.connectTimeout = connectTimeout;
        return this;
    }

    @Override
    public <T> @NonNull NetworkSocketBuilder option(final @NonNull SocketOption<T> name, final @Nullable T value) {
        Objects.requireNonNull(name);
        socketOptions.put(name, value);
        return this;
    }

    @Override
    public @NonNull NetworkSocketBuilder protocol(final @NonNull NetworkProtocol protocol) {
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
    public @NonNull NetworkSocketBuilder useNio(final boolean useNio) {
        if (!useNio && protocolFamily != null) {
            LOGGER.log(INFO, "You set a network protocol, it requires NIO mode, forcing it.");
            this.useNio = true;
            return this;
        }

        this.useNio = useNio;
        return this;
    }

    @Override
    public @NonNull NetworkSocket connectTcp(final @NonNull InetSocketAddress peerAddress) {
        Objects.requireNonNull(peerAddress);
        return connectTcpPrivate(peerAddress, null);
    }

    @Override
    public @NonNull NetworkSocket connectTcp(final @NonNull InetSocketAddress peerAddress,
                                             final Proxy.@NonNull Socks proxy) {
        Objects.requireNonNull(peerAddress);
        Objects.requireNonNull(proxy);
        return connectTcpPrivate(peerAddress, proxy);
    }

    private @NonNull NetworkSocket connectTcpPrivate(final @NonNull InetSocketAddress peerAddress,
                                                     final Proxy.@Nullable Socks proxy) {
        assert peerAddress != null;

        if (useNio) {
            return SocketChannelNetworkSocket.connect(
                    peerAddress,
                    connectTimeout,
                    proxy,
                    socketOptions,
                    protocolFamily);
        }

        return IoSocketNetworkSocket.connect(
                peerAddress,
                connectTimeout,
                proxy,
                socketOptions);
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public @NonNull NetworkSocketBuilder clone() {
        return new NetworkSocketBuilder(
                connectTimeout,
                socketOptions,
                protocolFamily,
                useNio);
    }
}
