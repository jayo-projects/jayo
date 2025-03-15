/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.network.NetworkProtocol;
import jayo.network.NetworkServer;
import jayo.scheduling.TaskRunner;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardProtocolFamily;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.lang.System.Logger.Level.INFO;

@SuppressWarnings("RawUseOfParameterized")
public final class NetworkServerBuilder implements NetworkServer.Builder {
    private static final System.Logger LOGGER = System.getLogger("jayo.network.NetworkServerBuilder");

    private long readTimeoutNanos = 0L;
    private long writeTimeoutNanos = 0L;
    private @Nullable TaskRunner taskRunner = null;
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions = new HashMap<>();
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> serverSocketOptions = new HashMap<>();
    private int maxPendingConnections = 0;
    private @Nullable ProtocolFamily protocolFamily = null;
    private boolean useNio = true;

    @Override
    public @NonNull NetworkServerBuilder readTimeout(final @NonNull Duration readTimeout) {
        Objects.requireNonNull(readTimeout);
        this.readTimeoutNanos = readTimeout.toNanos();
        return this;
    }

    @Override
    public @NonNull NetworkServerBuilder writeTimeout(final @NonNull Duration writeTimeout) {
        Objects.requireNonNull(writeTimeout);
        this.writeTimeoutNanos = writeTimeout.toNanos();
        return this;
    }

    @Override
    public @NonNull NetworkServerBuilder bufferAsync(final @NonNull TaskRunner taskRunner) {
        Objects.requireNonNull(taskRunner);
        this.taskRunner = taskRunner;
        return this;
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

    public @NonNull NetworkServer bindTcp(final @NonNull SocketAddress localAddress) {
        Objects.requireNonNull(localAddress);

        if (useNio) {
            return new ServerSocketChannelNetworkServer(
                    localAddress,
                    readTimeoutNanos,
                    writeTimeoutNanos,
                    taskRunner,
                    socketOptions,
                    serverSocketOptions,
                    maxPendingConnections,
                    protocolFamily);
        }

        return new ServerSocketNetworkServer(
                localAddress,
                readTimeoutNanos,
                writeTimeoutNanos,
                taskRunner,
                socketOptions,
                serverSocketOptions,
                maxPendingConnections);
    }
}
