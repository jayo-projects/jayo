/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.external.NonNegative;
import jayo.network.NetworkEndpoint;
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
public abstract sealed class NetworkEndpointBuilder<T extends NetworkEndpoint.Builder<T>>
        implements NetworkEndpoint.Builder<T> {
    private @NonNegative long connectTimeoutNanos = 0L;
    private @NonNegative long readTimeoutNanos = 0L;
    private @NonNegative long writeTimeoutNanos = 0L;
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions = new HashMap<>();

    @Override
    public final @NonNull T connectTimeout(final @NonNull Duration connectTimeout) {
        Objects.requireNonNull(connectTimeout);
        this.connectTimeoutNanos = connectTimeout.toNanos();
        return getThis();
    }

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
    public final @NonNull NetworkEndpoint connect(final @NonNull SocketAddress peerAddress) {
        Objects.requireNonNull(peerAddress);
        return connectInternal(peerAddress, connectTimeoutNanos, readTimeoutNanos, writeTimeoutNanos, socketOptions);
    }

    abstract @NonNull T getThis();

    abstract @NonNull NetworkEndpoint connectInternal(
            final @NonNull SocketAddress peerAddress,
            final @NonNegative long connectTimeoutNanos,
            final @NonNegative long defaultReadTimeoutNanos,
            final @NonNegative long defaultWriteTimeoutNanos,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions);

    public static final class Nio extends NetworkEndpointBuilder<NetworkEndpoint.NioBuilder>
            implements NetworkEndpoint.NioBuilder {
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
        NetworkEndpoint connectInternal(final @NonNull SocketAddress peerAddress,
                                        final @NonNegative long connectTimeoutNanos,
                                        final @NonNegative long defaultReadTimeoutNanos,
                                        final @NonNegative long defaultWriteTimeoutNanos,
                                        final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions) {
            assert peerAddress != null;
            return SocketChannelNetworkEndpoint.connect(
                    peerAddress,
                    connectTimeoutNanos,
                    defaultReadTimeoutNanos,
                    defaultWriteTimeoutNanos,
                    socketOptions,
                    family);
        }
    }

    public static final class Io extends NetworkEndpointBuilder<NetworkEndpoint.IoBuilder>
            implements NetworkEndpoint.IoBuilder {
        @Override
        @NonNull
        Io getThis() {
            return this;
        }

        @Override
        @NonNull
        NetworkEndpoint connectInternal(final @NonNull SocketAddress peerAddress,
                                        final @NonNegative long connectTimeoutNanos,
                                        final @NonNegative long defaultReadTimeoutNanos,
                                        final @NonNegative long defaultWriteTimeoutNanos,
                                        final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions) {
            assert peerAddress != null;
            return SocketNetworkEndpoint.connect(
                    peerAddress,
                    connectTimeoutNanos,
                    defaultReadTimeoutNanos,
                    defaultWriteTimeoutNanos,
                    socketOptions);
        }
    }
}
