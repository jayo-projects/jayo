/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.network.NetworkServer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("RawUseOfParameterized")
public abstract sealed class NetworkServerConfig<T extends NetworkServer.Config<T>>
        implements NetworkServer.Config<T> {
    private long readTimeoutNanos = 0L;
    private long writeTimeoutNanos = 0L;
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions = new HashMap<>();
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> serverSocketOptions = new HashMap<>();
    private int maxPendingConnections = 0;

    @Override
    public final @NonNull T readTimeout(final @NonNull Duration readTimeout) {
        Objects.requireNonNull(readTimeout);
        this.readTimeoutNanos = readTimeout.toNanos();
        return getThis();
    }

    @Override
    public final @NonNull T writeTimeout(final @NonNull Duration writeTimeout) {
        Objects.requireNonNull(writeTimeout);
        this.writeTimeoutNanos = writeTimeout.toNanos();
        return getThis();
    }

    @Override
    public final <U> @NonNull T option(final @NonNull SocketOption<U> name, final @Nullable U value) {
        Objects.requireNonNull(name);
        socketOptions.put(name, value);
        return getThis();
    }

    @Override
    public final <U> @NonNull T serverOption(final @NonNull SocketOption<U> name, final @Nullable U value) {
        Objects.requireNonNull(name);
        serverSocketOptions.put(name, value);
        return getThis();
    }

    @Override
    public final @NonNull T maxPendingConnections(final int maxPendingConnections) {
        if (maxPendingConnections < 0) {
            throw new IllegalArgumentException("maxPendingConnections < 0: " + maxPendingConnections);
        }
        this.maxPendingConnections = maxPendingConnections;
        return getThis();
    }

    public final @NonNull NetworkServer bind(final @NonNull SocketAddress localAddress) {
        Objects.requireNonNull(localAddress);
        return bindInternal(localAddress,
                readTimeoutNanos,
                writeTimeoutNanos,
                socketOptions,
                serverSocketOptions,
                maxPendingConnections);
    }

    abstract @NonNull T getThis();

    abstract @NonNull NetworkServer bindInternal(
            final @NonNull SocketAddress localAddress,
            final long defaultReadTimeoutNanos,
            final long defaultWriteTimeoutNanos,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> serverSocketOptions,
            final int maxPendingConnections);

    public static final class Nio extends NetworkServerConfig<NetworkServer.NioConfig>
            implements NetworkServer.NioConfig {
        private @Nullable ProtocolFamily family = null;

        @Override
        public @NonNull Nio protocolFamily(final @NonNull ProtocolFamily family) {
            this.family = Objects.requireNonNull(family);
            return this;
        }

        @Override
        @NonNull
        Nio getThis() {
            return this;
        }

        @Override
        @NonNull
        NetworkServer bindInternal(
                final @NonNull SocketAddress localAddress,
                final long defaultReadTimeoutNanos,
                final long defaultWriteTimeoutNanos,
                final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
                final @NonNull Map<@NonNull SocketOption, @Nullable Object> serverSocketOptions,
                final int maxPendingConnections) {
            assert localAddress != null;

            return new ServerSocketChannelNetworkServer(
                    localAddress,
                    defaultReadTimeoutNanos,
                    defaultWriteTimeoutNanos,
                    socketOptions,
                    serverSocketOptions,
                    maxPendingConnections,
                    family);
        }
    }

    public static final class Io extends NetworkServerConfig<NetworkServer.IoConfig>
            implements NetworkServer.IoConfig {
        @Override
        @NonNull
        Io getThis() {
            return this;
        }

        @Override
        @NonNull
        NetworkServer bindInternal(
                final @NonNull SocketAddress localAddress,
                final long defaultReadTimeoutNanos,
                final long defaultWriteTimeoutNanos,
                final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
                final @NonNull Map<@NonNull SocketOption, @Nullable Object> serverSocketOptions,
                final int maxPendingConnections) {
            assert localAddress != null;

            return new ServerSocketNetworkServer(
                    localAddress,
                    defaultReadTimeoutNanos,
                    defaultWriteTimeoutNanos,
                    socketOptions,
                    serverSocketOptions,
                    maxPendingConnections);
        }
    }
}
