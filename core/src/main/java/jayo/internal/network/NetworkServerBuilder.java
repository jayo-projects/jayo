/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.external.NonNegative;
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
public abstract sealed class NetworkServerBuilder<T extends NetworkServer.Builder<T>>
        implements NetworkServer.Builder<T> {
    private @NonNegative long readTimeoutNanos = 0L;
    private @NonNegative long writeTimeoutNanos = 0L;
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions = new HashMap<>();
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> serverSocketOptions = new HashMap<>();
    private @NonNegative int maxPendingConnections = 0;

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
    public final @NonNull T maxPendingConnections(final @NonNegative int maxPendingConnections) {
        if (maxPendingConnections < 0) {
            throw new IllegalArgumentException("maxPendingConnections < 0: " + maxPendingConnections);
        }
        this.maxPendingConnections = maxPendingConnections;
        return getThis();
    }

    @Override
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
            final @NonNegative long defaultReadTimeoutNanos,
            final @NonNegative long defaultWriteTimeoutNanos,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> serverSocketOptions,
            final @NonNegative int maxPendingConnections);

    public static final class Nio extends NetworkServerBuilder<NetworkServer.NioBuilder>
            implements NetworkServer.NioBuilder {
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
                final @NonNegative long defaultReadTimeoutNanos,
                final @NonNegative long defaultWriteTimeoutNanos,
                final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
                final @NonNull Map<@NonNull SocketOption, @Nullable Object> serverSocketOptions,
                final @NonNegative int maxPendingConnections) {
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

    public static final class Io extends NetworkServerBuilder<NetworkServer.IoBuilder>
            implements NetworkServer.IoBuilder {
        @Override
        @NonNull
        Io getThis() {
            return this;
        }

        @Override
        @NonNull
        NetworkServer bindInternal(
                final @NonNull SocketAddress localAddress,
                final @NonNegative long defaultReadTimeoutNanos,
                final @NonNegative long defaultWriteTimeoutNanos,
                final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
                final @NonNull Map<@NonNull SocketOption, @Nullable Object> serverSocketOptions,
                final @NonNegative int maxPendingConnections) {
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
