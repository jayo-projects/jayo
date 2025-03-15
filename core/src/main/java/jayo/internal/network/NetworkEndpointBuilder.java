/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.network.NetworkEndpoint;
import jayo.network.NetworkProtocol;
import jayo.network.Proxy;
import jayo.scheduling.TaskRunner;
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
public final class NetworkEndpointBuilder implements NetworkEndpoint.Builder {
    private static final System.Logger LOGGER = System.getLogger("jayo.network.NetworkEndpointBuilder");

    private @Nullable Duration connectTimeout = null;
    private long readTimeoutNanos = 0L;
    private long writeTimeoutNanos = 0L;
    private @Nullable TaskRunner taskRunner = null;
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions = new HashMap<>();
    private @Nullable ProtocolFamily protocolFamily = null;
    private boolean useNio = true;

    @Override
    public @NonNull NetworkEndpointBuilder connectTimeout(final @NonNull Duration connectTimeout) {
        Objects.requireNonNull(connectTimeout);
        this.connectTimeout = connectTimeout;
        return this;
    }

    @Override
    public @NonNull NetworkEndpointBuilder readTimeout(final @NonNull Duration readTimeout) {
        Objects.requireNonNull(readTimeout);
        readTimeoutNanos = readTimeout.toNanos();
        return this;
    }

    @Override
    public @NonNull NetworkEndpointBuilder writeTimeout(final @NonNull Duration writeTimeout) {
        Objects.requireNonNull(writeTimeout);
        writeTimeoutNanos = writeTimeout.toNanos();
        return this;
    }

    @Override
    public @NonNull NetworkEndpointBuilder bufferAsync(final @NonNull TaskRunner taskRunner) {
        Objects.requireNonNull(taskRunner);
        this.taskRunner = taskRunner;
        return this;
    }

    @Override
    public <T> @NonNull NetworkEndpointBuilder option(final @NonNull SocketOption<T> name, final @Nullable T value) {
        Objects.requireNonNull(name);
        socketOptions.put(name, value);
        return this;
    }

    @Override
    public @NonNull NetworkEndpointBuilder protocol(final @NonNull NetworkProtocol protocol) {
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
    public @NonNull NetworkEndpointBuilder useNio(final boolean useNio) {
        if (!useNio && protocolFamily != null) {
            LOGGER.log(INFO, "You set a network protocol, it requires NIO mode, forcing it.");
            this.useNio = true;
            return this;
        }

        this.useNio = useNio;
        return this;
    }

    @Override
    public @NonNull NetworkEndpoint connectTcp(final @NonNull InetSocketAddress peerAddress) {
        Objects.requireNonNull(peerAddress);
        return connectTcpPrivate(peerAddress, null);
    }

    @Override
    public @NonNull NetworkEndpoint connectTcp(final @NonNull InetSocketAddress peerAddress,
                                               final Proxy.@NonNull Socks proxy) {
        Objects.requireNonNull(peerAddress);
        Objects.requireNonNull(proxy);
        return connectTcpPrivate(peerAddress, proxy);
    }

    private @NonNull NetworkEndpoint connectTcpPrivate(final @NonNull InetSocketAddress peerAddress,
                                                       final Proxy.@Nullable Socks proxy) {
        assert peerAddress != null;

        if (useNio) {
            return SocketChannelNetworkEndpoint.connect(
                    peerAddress,
                    connectTimeout,
                    readTimeoutNanos,
                    writeTimeoutNanos,
                    taskRunner,
                    proxy,
                    socketOptions,
                    protocolFamily);
        }

        return SocketNetworkEndpoint.connect(
                peerAddress,
                connectTimeout,
                readTimeoutNanos,
                writeTimeoutNanos,
                taskRunner,
                proxy,
                socketOptions);
    }
}
