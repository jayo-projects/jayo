/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.JayoException;
import jayo.internal.network.RealSocksProxy;
import jayo.internal.network.SocksNetworkSocket;
import jayo.network.NetworkSocket;
import jayo.network.Proxy;
import jayo.tools.CancelToken;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketOption;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.WARNING;
import static jayo.internal.Utils.TIMEOUT_WRITE_SIZE;

/**
 * A {@link NetworkSocket} backed by an underlying {@linkplain SocketChannel NIO SocketChannel}.
 */
public final class SocketChannelNetworkSocket extends AbstractNetworkSocket {
    private static final System.Logger LOGGER = System.getLogger("jayo.network.SocketChannelNetworkSocket");

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

    public SocketChannelNetworkSocket(final @NonNull SocketChannel socketChannel) {
        this(socketChannel, buildAsyncTimeout(socketChannel));
    }

    private SocketChannelNetworkSocket(final @NonNull SocketChannel socketChannel,
                                       final @NonNull RealAsyncTimeout timeout) {
        super(timeout);
        assert socketChannel != null;

        this.socketChannel = socketChannel;
    }

    @Override
    public void cancel() {
        try {
            socketChannel.close();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public boolean isOpen() {
        return socketChannel.isOpen() && closeBits.get() == 0;
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
    public void setReadTimeout(final @NonNull Duration readTimeout) {
        Objects.requireNonNull(readTimeout);
        readTimeoutNanos = readTimeout.toNanos();
    }

    @Override
    public @NonNull SocketChannel getUnderlying() {
        return socketChannel;
    }

    @Override
    int read(final @NonNull Segment dstTail, final int toRead) throws IOException {
        assert dstTail != null;
        return socketChannel.read(dstTail.asByteBuffer(dstTail.limit, toRead));
    }

    @Override
    void shutdownInput() throws IOException {
        if (!socketChannel.isOpen()) {
            return; // Nothing to do.
        }
        socketChannel.shutdownInput();
    }

    @Override
    void write(final @NonNull RealBuffer src,
               final long byteCount,
               final @Nullable RealCancelToken cancelToken) {
        assert src != null;

        var remaining = byteCount;
        while (remaining > 0L) {
            /*
             * Don't write more than 4 full segments (~67 KiB) of data at a time. Otherwise, slow connections may suffer
             * timeouts even when they're making (slow) progress. Without this, writing a single 1 MiB buffer may never
             * succeed on a sufficiently slow connection.
             */
            final var toWrite = Math.min(remaining, TIMEOUT_WRITE_SIZE);
            writeToSocketChannel(src, toWrite, cancelToken);
            remaining -= toWrite;
        }
    }

    private void writeToSocketChannel(final @NonNull RealBuffer src,
                                      final long byteCount,
                                      final @Nullable RealCancelToken cancelToken) {
        assert src != null;

        src.withHeadsAsByteBuffers(byteCount, sources -> {
            var remaining = byteCount;
            final var firstSourceIndex = new Wrapper.Int(); // index of the first source in the array of sources with remaining bytes to write
            while (true) {
                CancelToken.throwIfReached(cancelToken);
                final var written = timeout.withTimeout(cancelToken, () -> {
                    try {
                        return socketChannel.write(sources, firstSourceIndex.value,
                                sources.length - firstSourceIndex.value);
                    } catch (IOException e) {
                        throw JayoException.buildJayoException(e);
                    }
                });
                remaining -= written;
                if (remaining == 0) {
                    break; // done
                }

                // we must ignore the X first fully written byteArrays in the next iteration's writing call
                firstSourceIndex.value = (int) Arrays.stream(sources)
                        .takeWhile(byteBuffer -> !byteBuffer.hasRemaining())
                        .count();
            }
            return byteCount;
        });
    }

    @Override
    void shutdownOutput() throws IOException {
        if (!socketChannel.isOpen()) {
            return; // Nothing to do.
        }
        socketChannel.shutdownOutput();
    }

    public static final class Unconnected implements NetworkSocket.Unconnected {
        private final @Nullable Duration connectTimeout;
        private final @NonNull SocketChannel socketChannel;

        @SuppressWarnings({"unchecked", "RawUseOfParameterized"})
        public Unconnected(final @Nullable Duration connectTimeout,
                           final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
                           final @Nullable ProtocolFamily family) {
            assert socketOptions != null;

            this.connectTimeout = connectTimeout;
            try {
                // SocketChannel defaults to blocking-mode, that's precisely what we want
                final var socketChannel = (family != null) ? SocketChannel.open(family) : SocketChannel.open();

                for (final var socketOption : socketOptions.entrySet()) {
                    socketChannel.setOption(socketOption.getKey(), socketOption.getValue());
                }

                this.socketChannel = socketChannel;
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
        }

        @Override
        public @NonNull NetworkSocket connect(@NonNull InetSocketAddress peerAddress) {
            Objects.requireNonNull(peerAddress);
            return connectPrivate(peerAddress, null);
        }

        @Override
        public @NonNull NetworkSocket connect(@NonNull InetSocketAddress peerAddress, Proxy.@NonNull Socks proxy) {
            Objects.requireNonNull(peerAddress);
            Objects.requireNonNull(proxy);
            return connectPrivate(peerAddress, proxy);
        }

        private @NonNull NetworkSocket connectPrivate(final @NonNull InetSocketAddress peerAddress,
                                                      final Proxy.@Nullable Socks proxy) {
            assert peerAddress != null;

            final var asyncTimeout = buildAsyncTimeout(socketChannel);
            final NetworkSocket networkSocket;
            if (connectTimeout != null) {
                final var cancelToken = new RealCancelToken(connectTimeout.toNanos());
                networkSocket = asyncTimeout.withTimeout(cancelToken, () ->
                        connect(peerAddress, asyncTimeout, proxy));
            } else {
                networkSocket = connect(peerAddress, asyncTimeout, proxy);
            }

            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "new client SocketChannelNetworkSocket connected to {0}", peerAddress);
            }

            return networkSocket;
        }

        private @NonNull NetworkSocket connect(final @NonNull InetSocketAddress peerAddress,
                                               final @NonNull RealAsyncTimeout asyncTimeout,
                                               final Proxy.@Nullable Socks proxy) {
            try {
                if (proxy != null) {
                    // connect to the proxy and use it to reach peer
                    socketChannel.connect(new InetSocketAddress(proxy.getHost(), proxy.getPort()));
                    final var proxyNetEndpoint = new SocketChannelNetworkSocket(socketChannel, asyncTimeout);
                    if (!(proxy instanceof RealSocksProxy socksProxy)) {
                        throw new IllegalArgumentException("proxy is not a RealSocksProxy");
                    }
                    return new SocksNetworkSocket(socksProxy, proxyNetEndpoint, peerAddress);
                }
                // connect to peer
                socketChannel.connect(peerAddress);
                return new SocketChannelNetworkSocket(socketChannel, asyncTimeout);
            } catch (IOException e) {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG,
                            "new client SocketChannelNetworkSocket failed to connect to " + peerAddress, e);
                }
                throw JayoException.buildJayoException(e);
            }
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
        public <T> @Nullable T getOption(final @NonNull SocketOption<T> name) {
            Objects.requireNonNull(name);
            try {
                return socketChannel.getOption(name);
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
        }

        @Override
        public void cancel() {
            try {
                socketChannel.close();
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
        }
    }
}
