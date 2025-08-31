/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.*;
import jayo.internal.GatheringByteChannelRawWriter;
import jayo.internal.ReadableByteChannelRawReader;
import jayo.internal.RealAsyncTimeout;
import jayo.internal.RealAsyncTimeout.RawReaderWithTimeout;
import jayo.internal.RealAsyncTimeout.RawWriterWithTimeout;
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
            final Proxy.@Nullable Socks proxy,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
            final @Nullable ProtocolFamily family) {
        assert peerAddress != null;
        assert socketOptions != null;

        try {
            // SocketChannel defaults to blocking-mode, that's precisely what we want
            final var socketChannel = (family != null) ? SocketChannel.open(family) : SocketChannel.open();

            for (final var socketOption : socketOptions.entrySet()) {
                socketChannel.setOption(socketOption.getKey(), socketOption.getValue());
            }

            final var asyncTimeout = buildAsyncTimeout(socketChannel);
            final NetworkEndpoint networkEndpoint;
            if (connectTimeout != null) {
                final var cancelToken = new RealCancelToken(connectTimeout.toNanos());
                networkEndpoint = asyncTimeout.withTimeout(cancelToken, () ->
                        connect(socketChannel, peerAddress, asyncTimeout, proxy));
            } else {
                networkEndpoint = connect(socketChannel, peerAddress, asyncTimeout, proxy);
            }

            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "new client SocketChannelNetworkEndpoint connected to {0}, protocol family " +
                        "= {1}, socket options = {2}", peerAddress, family, socketOptions);
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
    private static RealAsyncTimeout buildAsyncTimeout(final @NonNull SocketChannel socketChannel) {
        assert socketChannel != null;
        return new RealAsyncTimeout(() -> {
            try {
                socketChannel.close();
            } catch (Exception e) {
                LOGGER.log(WARNING, "Failed to close timed out socket channel " + socketChannel, e);
            }
        });
    }

    private final @NonNull SocketChannel socketChannel;

    final @NonNull RawReaderWithTimeout rawReader;
    private Reader reader = null;
    final @NonNull RawWriterWithTimeout rawWriter;
    private Writer writer = null;

    SocketChannelNetworkEndpoint(final @NonNull SocketChannel socketChannel) {
        this(socketChannel, buildAsyncTimeout(socketChannel));
    }

    private SocketChannelNetworkEndpoint(final @NonNull SocketChannel socketChannel,
                                         final @NonNull RealAsyncTimeout asyncTimeout) {
        assert socketChannel != null;
        assert asyncTimeout != null;

        this.socketChannel = socketChannel;
        this.rawReader = asyncTimeout.reader(new ReadableByteChannelRawReader(socketChannel));
        this.rawWriter = asyncTimeout.writer(new GatheringByteChannelRawWriter(socketChannel));
    }

    @Override
    public @NonNull Reader getReader() {
        if (reader == null) {
            setReader(rawReader);
        }
        return reader;
    }

    void setReader(final @NonNull RawReader rawReader) {
        assert rawReader != null;
        reader = Jayo.buffer(rawReader);
    }

    @Override
    public @NonNull Writer getWriter() {
        if (writer == null) {
            setWriter(rawWriter);
        }
        return writer;
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
    public @NonNull Duration getReadTimeout() {
        return rawReader.getTimeout();
    }

    @Override
    public void setReadTimeout(final @NonNull Duration readTimeout) {
        rawReader.setTimeout(readTimeout);
    }

    @Override
    public @NonNull Duration getWriteTimeout() {
        return rawWriter.getTimeout();
    }

    @Override
    public void setWriteTimeout(final @NonNull Duration writeTimeout) {
        rawWriter.setTimeout(writeTimeout);
    }

    @Override
    public @NonNull SocketChannel getUnderlying() {
        return socketChannel;
    }
}
