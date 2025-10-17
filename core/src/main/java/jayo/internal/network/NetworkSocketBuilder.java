/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.internal.IoSocketNetworkSocket;
import jayo.internal.SocketChannelNetworkSocket;
import jayo.network.NetworkProtocol;
import jayo.network.NetworkSocket;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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

    private @Nullable Duration connectTimeout = null;
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions = new HashMap<>();
    private @Nullable ProtocolFamily protocolFamily = null;
    private boolean useNio = true;

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
    public NetworkSocket.@NonNull Unconnected openTcp() {
        if (useNio) {
            return new SocketChannelNetworkSocket.Unconnected(connectTimeout, socketOptions, protocolFamily);
        }

        return new IoSocketNetworkSocket.Unconnected(connectTimeout, socketOptions);
    }
}
