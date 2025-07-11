/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.*;
import jayo.internal.GatheringByteChannelRawWriter;
import jayo.internal.ReadableByteChannelRawReader;
import jayo.internal.RealAsyncTimeout;
import jayo.internal.RealCancelToken;
import jayo.network.NetworkEndpoint;
import jayo.network.Proxy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketOption;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.WARNING;

/**
 * A {@link NetworkEndpoint} backed by an underlying {@linkplain SocketChannel NIO SocketChannel}.
 */
public final class SocketChannelNetworkEndpoint implements NetworkEndpoint {
    private static final System.Logger LOGGER = System.getLogger("jayo.network.SocketChannelNetworkEndpoint");

    @SuppressWarnings({"unchecked", "RawUseOfParameterized"})
    static @NonNull NetworkEndpoint connect(
            final @NonNull InetSocketAddress peerAddress,
            final @Nullable Duration connectTimeout,
            final long readTimeoutNanos,
            final long writeTimeoutNanos,
            final Proxy.@Nullable Socks proxy,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
            final @Nullable ProtocolFamily family) {
        assert peerAddress != null;
        assert readTimeoutNanos >= 0L;
        assert writeTimeoutNanos >= 0L;
        assert socketOptions != null;

        try {
            // SocketChannel defaults to blocking-mode, that's precisely what we want
            final var socketChannel = (family != null) ? SocketChannel.open(family) : SocketChannel.open();

            for (final var socketOption : socketOptions.entrySet()) {
                socketChannel.setOption(socketOption.getKey(), socketOption.getValue());
            }

            final var asyncTimeout = buildAsyncTimeout(socketChannel, readTimeoutNanos, writeTimeoutNanos);
            final NetworkEndpoint networkEndpoint;
            if (connectTimeout != null) {
                final var cancelToken = new RealCancelToken(connectTimeout.toNanos());
                networkEndpoint = asyncTimeout.withTimeout(cancelToken, () ->
                        connect(socketChannel, peerAddress, asyncTimeout, proxy));
            } else {
                networkEndpoint = connect(socketChannel, peerAddress, asyncTimeout, proxy);
            }

            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "new client SocketChannelNetworkEndpoint connected to {0}{1}protocol family " +
                                "= {2}, default read timeout = {3} ns, default write timeout = {4} ns{5}provided " +
                                "socket options = {6}",
                        peerAddress, System.lineSeparator(), family, readTimeoutNanos, writeTimeoutNanos,
                        System.lineSeparator(), socketOptions);
            }

            return networkEndpoint;
        } catch (IOException e) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG,
                        "new client SocketChannelNetworkEndpoint failed to connect to " + peerAddress, e);
            }
            throw JayoException.buildJayoException(e);
        }
    }

    private static @NonNull NetworkEndpoint connect(final @NonNull SocketChannel socketChannel,
                                                    final @NonNull InetSocketAddress peerAddress,
                                                    final @NonNull RealAsyncTimeout asyncTimeout,
                                                    final Proxy.@Nullable Socks proxy) {
        try {
            if (proxy != null) {
                // connect to the proxy and use it to reach peer
                socketChannel.connect(new InetSocketAddress(proxy.getHost(), proxy.getPort()));
                final var proxyNetEndpoint = new SocketChannelNetworkEndpoint(socketChannel, asyncTimeout);
                if (!(proxy instanceof RealSocksProxy socksProxy)) {
                    throw new IllegalArgumentException("proxy is not a RealSocksProxy");
                }
                return new SocksNetworkEndpoint(socksProxy, proxyNetEndpoint, peerAddress);
            }
            // connect to peer
            socketChannel.connect(peerAddress);
            return new SocketChannelNetworkEndpoint(socketChannel, asyncTimeout);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @NonNull
    private static RealAsyncTimeout buildAsyncTimeout(final @NonNull SocketChannel socketChannel,
                                                      final long defaultReadTimeoutNanos,
                                                      final long defaultWriteTimeoutNanos) {
        assert socketChannel != null;
        return new RealAsyncTimeout(defaultReadTimeoutNanos, defaultWriteTimeoutNanos, () -> {
            try {
                socketChannel.close();
            } catch (Exception e) {
                LOGGER.log(WARNING, "Failed to close timed out socket channel " + socketChannel, e);
            }
        });
    }

    private final @NonNull SocketChannel socketChannel;
    private final @NonNull RealAsyncTimeout asyncTimeout;

    private Reader reader = null;
    private Writer writer = null;

    SocketChannelNetworkEndpoint(final @NonNull SocketChannel socketChannel,
                                 final long defaultReadTimeoutNanos,
                                 final long defaultWriteTimeoutNanos) {
        this(socketChannel, buildAsyncTimeout(socketChannel, defaultReadTimeoutNanos, defaultWriteTimeoutNanos));
    }

    private SocketChannelNetworkEndpoint(final @NonNull SocketChannel socketChannel,
                                         final @NonNull RealAsyncTimeout asyncTimeout) {
        assert socketChannel != null;
        assert asyncTimeout != null;

        this.socketChannel = socketChannel;
        this.asyncTimeout = asyncTimeout;
    }

    @Override
    public @NonNull Reader getReader() {
        if (reader == null) {
            final var rawReader = buildRawReader();
            setReader(rawReader);
        }
        return reader;
    }

    @NonNull
    RawReader buildRawReader() {
        return asyncTimeout.reader(new ReadableByteChannelRawReader(socketChannel));
    }

    void setReader(final @NonNull RawReader rawReader) {
        assert rawReader != null;
        reader = Jayo.buffer(rawReader);
    }

    @Override
    public @NonNull Writer getWriter() {
        if (writer == null) {
            final var rawWriter = buildRawWriter();
            setWriter(rawWriter);
        }
        return writer;
    }

    @NonNull
    RawWriter buildRawWriter() {
        return asyncTimeout.writer(new GatheringByteChannelRawWriter(socketChannel));
    }

    void setWriter(final @NonNull RawWriter rawWriter) {
        assert rawWriter != null;
        writer = Jayo.buffer(rawWriter);
    }

    @Override
    public void close() {
        try {
            socketChannel.close();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public boolean isOpen() {
        return socketChannel.isOpen();
    }

    @Override
    public @NonNull InetSocketAddress getLocalAddress() {
        try {
            return (InetSocketAddress) socketChannel.getLocalAddress();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public @NonNull InetSocketAddress getPeerAddress() {
        try {
            return (InetSocketAddress) socketChannel.getRemoteAddress();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public <T> @Nullable T getOption(final @NonNull SocketOption<T> name) {
        Objects.requireNonNull(name);
        try {
            return socketChannel.getOption(name);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public @NonNull SocketChannel getUnderlying() {
        return socketChannel;
    }
}
