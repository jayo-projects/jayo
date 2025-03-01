/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.network.NetworkEndpoint;
import jayo.network.NetworkProtocol;
import jayo.network.SocksProxy;
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

@SuppressWarnings("RawUseOfParameterized")
public abstract sealed class NetworkEndpointConfig<T extends NetworkEndpoint.Config<T>>
        implements NetworkEndpoint.Config<T> {
    private @Nullable Duration connectTimeout = null;
    private long readTimeoutNanos = 0L;
    private long writeTimeoutNanos = 0L;
    private @Nullable TaskRunner taskRunner = null;
    private final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions = new HashMap<>();

    @Override
    public final @NonNull T connectTimeout(final @NonNull Duration connectTimeout) {
        Objects.requireNonNull(connectTimeout);
        this.connectTimeout = connectTimeout;
        return getThis();
    }

    @Override
    public final @NonNull T readTimeout(final @NonNull Duration readTimeout) {
        Objects.requireNonNull(readTimeout);
        readTimeoutNanos = readTimeout.toNanos();
        return getThis();
    }

    @Override
    public final @NonNull T writeTimeout(final @NonNull Duration writeTimeout) {
        Objects.requireNonNull(writeTimeout);
        writeTimeoutNanos = writeTimeout.toNanos();
        return getThis();
    }

    @Override
    public final @NonNull T bufferAsync(final @NonNull TaskRunner taskRunner) {
        Objects.requireNonNull(taskRunner);
        this.taskRunner = taskRunner;
        return getThis();
    }

    @Override
    public final <U> @NonNull T option(final @NonNull SocketOption<U> name, final @Nullable U value) {
        Objects.requireNonNull(name);
        socketOptions.put(name, value);
        return getThis();
    }

    public final @NonNull NetworkEndpoint connect(final @NonNull InetSocketAddress peerAddress,
                                                  final @Nullable SocksProxy proxy) {
        assert peerAddress != null;
        return connectInternal(peerAddress, proxy, connectTimeout, readTimeoutNanos, writeTimeoutNanos, socketOptions,
                taskRunner);
    }

    abstract @NonNull T getThis();

    abstract @NonNull NetworkEndpoint connectInternal(
            final @NonNull InetSocketAddress peerAddress,
            final @Nullable SocksProxy proxy,
            final @Nullable Duration connectTimeout,
            final long defaultReadTimeoutNanos,
            final long defaultWriteTimeoutNanos,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
            final @Nullable TaskRunner taskRunner);

    public static final class Nio extends NetworkEndpointConfig<NetworkEndpoint.NioConfig>
            implements NetworkEndpoint.NioConfig {
        private @Nullable ProtocolFamily family = null;

        @Override
        public @NonNull Nio protocol(final @NonNull NetworkProtocol protocol) {
            Objects.requireNonNull(protocol);
            this.family = switch (protocol) {
                case IPv4 -> StandardProtocolFamily.INET;
                case IPv6 -> StandardProtocolFamily.INET6;
            };
            return this;
        }

        @Override
        @NonNull
        Nio getThis() {
            return this;
        }

        @Override
        @NonNull
        NetworkEndpoint connectInternal(final @NonNull InetSocketAddress peerAddress,
                                        final @Nullable SocksProxy proxy,
                                        final @Nullable Duration connectTimeout,
                                        final long defaultReadTimeoutNanos,
                                        final long defaultWriteTimeoutNanos,
                                        final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
                                        final @Nullable TaskRunner taskRunner) {
            assert peerAddress != null;
            assert socketOptions != null;
            return SocketChannelNetworkEndpoint.connect(
                    peerAddress,
                    connectTimeout,
                    defaultReadTimeoutNanos,
                    defaultWriteTimeoutNanos,
                    taskRunner,
                    proxy,
                    socketOptions,
                    family);
        }
    }

    public static final class Io extends NetworkEndpointConfig<NetworkEndpoint.IoConfig>
            implements NetworkEndpoint.IoConfig {
        @Override
        @NonNull
        Io getThis() {
            return this;
        }

        @Override
        @NonNull
        NetworkEndpoint connectInternal(final @NonNull InetSocketAddress peerAddress,
                                        final @Nullable SocksProxy proxy,
                                        final @Nullable Duration connectTimeout,
                                        final long defaultReadTimeoutNanos,
                                        final long defaultWriteTimeoutNanos,
                                        final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
                                        final @Nullable TaskRunner taskRunner) {
            assert peerAddress != null;
            assert socketOptions != null;
            return SocketNetworkEndpoint.connect(
                    peerAddress,
                    connectTimeout,
                    defaultReadTimeoutNanos,
                    defaultWriteTimeoutNanos,
                    taskRunner,
                    proxy,
                    socketOptions);
        }
    }
}
