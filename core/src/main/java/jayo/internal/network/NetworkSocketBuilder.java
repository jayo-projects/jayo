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

import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketOption;
import java.net.StandardProtocolFamily;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import static java.lang.System.Logger.Level.INFO;

@SuppressWarnings("RawUseOfParameterized")
public final class NetworkSocketBuilder implements NetworkSocket.Builder {
    private static final System.Logger LOGGER = System.getLogger("jayo.network.NetworkSocketBuilder");

    private @NonNull Duration connectTimeout;
    private long readTimeoutNanos;
    private long writeTimeoutNanos;
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions;
    private @Nullable ProtocolFamily protocolFamily;
    private boolean useNio;
    private @Nullable UnaryOperator<@NonNull InetSocketAddress> peerAddressModifier;

    public NetworkSocketBuilder() {
        this(
                /*connectTimeout*/ Duration.ZERO,
                /*readTimeoutNanos*/ 0L,
                /*writeTimeoutNanos*/ 0L,
                /*socketOptions*/ new HashMap<>(),
                /*protocolFamily*/ null,
                /*useNio*/ true,
                /*peerAddressModifier*/ null);
    }

    private NetworkSocketBuilder(final @NonNull Duration connectTimeout,
                                 final long readTimeoutNanos,
                                 final long writeTimeoutNanos,
                                 final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
                                 final @Nullable ProtocolFamily protocolFamily,
                                 final boolean useNio,
                                 final @Nullable UnaryOperator<@NonNull InetSocketAddress> peerAddressModifier) {
        assert connectTimeout != null;
        assert socketOptions != null;

        this.connectTimeout = connectTimeout;
        this.readTimeoutNanos = readTimeoutNanos;
        this.writeTimeoutNanos = writeTimeoutNanos;
        this.socketOptions = socketOptions;
        this.protocolFamily = protocolFamily;
        this.useNio = useNio;
        this.peerAddressModifier = peerAddressModifier;
    }

    @Override
    public @NonNull NetworkSocketBuilder connectTimeout(final @NonNull Duration connectTimeout) {
        this.connectTimeout = Objects.requireNonNull(connectTimeout);
        return this;
    }

    @Override
    public @NonNull Duration getConnectTimeout() {
        return connectTimeout;
    }

    @Override
    public @NonNull NetworkSocketBuilder readTimeout(final @NonNull Duration readTimeout) {
        Objects.requireNonNull(readTimeout);
        this.readTimeoutNanos = readTimeout.toNanos();
        return this;
    }

    @Override
    public @NonNull Duration getReadTimeout() {
        return Duration.ofNanos(readTimeoutNanos);
    }

    @Override
    public @NonNull NetworkSocketBuilder writeTimeout(final @NonNull Duration writeTimeout) {
        Objects.requireNonNull(writeTimeout);
        this.writeTimeoutNanos = writeTimeout.toNanos();
        return this;
    }

    @Override
    public @NonNull Duration getWriteTimeout() {
        return Duration.ofNanos(writeTimeoutNanos);
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
    public @NonNull NetworkSocketBuilder onConnect(
            final @NonNull UnaryOperator<@NonNull InetSocketAddress> peerAddressModifier) {
        this.peerAddressModifier = Objects.requireNonNull(peerAddressModifier);
        return this;
    }

    @Override
    public NetworkSocket.@NonNull Unconnected openTcp() {
        if (useNio) {
            return new SocketChannelNetworkSocket.Unconnected(connectTimeout, readTimeoutNanos, writeTimeoutNanos,
                    socketOptions, protocolFamily, peerAddressModifier);
        }

        return new IoSocketNetworkSocket.Unconnected(connectTimeout, readTimeoutNanos, writeTimeoutNanos,
                socketOptions, peerAddressModifier);
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public @NonNull NetworkSocketBuilder clone() {
        return new NetworkSocketBuilder(
                connectTimeout,
                readTimeoutNanos,
                writeTimeoutNanos,
                new HashMap<>(socketOptions),
                protocolFamily,
                useNio,
                peerAddressModifier);
    }
}
