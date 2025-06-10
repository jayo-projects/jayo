/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
 *
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.internal.network;

import jayo.*;
import jayo.internal.InputStreamRawReader;
import jayo.internal.OutputStreamRawWriter;
import jayo.internal.RealAsyncTimeout;
import jayo.network.NetworkEndpoint;
import jayo.network.Proxy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketOption;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.WARNING;

/**
 * A {@link NetworkEndpoint} backed by an underlying {@linkplain Socket IO Socket}.
 */
public final class SocketNetworkEndpoint implements NetworkEndpoint {
    private static final System.Logger LOGGER = System.getLogger("jayo.network.SocketNetworkEndpoint");

    @SuppressWarnings({"unchecked", "RawUseOfParameterized"})
    static @NonNull NetworkEndpoint connect(
            final @NonNull InetSocketAddress peerAddress,
            final @Nullable Duration connectTimeout,
            final long readTimeoutNanos,
            final long writeTimeoutNanos,
            final Proxy.@Nullable Socks proxy,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions
    ) {
        assert peerAddress != null;
        assert readTimeoutNanos >= 0L;
        assert writeTimeoutNanos >= 0L;
        assert socketOptions != null;

        final var socket = new Socket();
        try {
            for (final var socketOption : socketOptions.entrySet()) {
                socket.setOption(socketOption.getKey(), socketOption.getValue());
            }

            final var asyncTimeout = buildAsyncTimeout(socket, readTimeoutNanos, writeTimeoutNanos);
            final var networkEndpoint = connect(socket, peerAddress, connectTimeout, asyncTimeout, proxy);

            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "new client SocketNetworkEndpoint connected to {0}{1}default read timeout =" +
                                " {2} ns, default write timeout = {3} ns{4}provided socket options = {5}",
                        peerAddress, System.lineSeparator(), readTimeoutNanos, writeTimeoutNanos,
                        System.lineSeparator(), socketOptions);
            }

            return networkEndpoint;
        } catch (IOException e) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "new client SocketNetworkEndpoint failed to connect to " + peerAddress, e);
            }
            throw JayoException.buildJayoException(e);
        }
    }

    private static NetworkEndpoint connect(final @NonNull Socket socket,
                                           final @NonNull InetSocketAddress peerAddress,
                                           final @Nullable Duration connectTimeout,
                                           final @NonNull RealAsyncTimeout asyncTimeout,
                                           final Proxy.@Nullable Socks proxy) {
        try {
            if (proxy != null) {
                // connect to the proxy and use it to reach peer
                connect(socket, new InetSocketAddress(proxy.getHost(), proxy.getPort()), connectTimeout);
                final var proxyNetEndpoint = new SocketNetworkEndpoint(socket, asyncTimeout);
                if (!(proxy instanceof RealSocksProxy socksProxy)) {
                    throw new IllegalArgumentException("proxy is not a RealSocksProxy");
                }
                return new SocksNetworkEndpoint(socksProxy, proxyNetEndpoint, peerAddress);
            }
            // connect to peer
            connect(socket, peerAddress, connectTimeout);
            return new SocketNetworkEndpoint(socket, asyncTimeout);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    private static void connect(final @NonNull Socket socket,
                                final @NonNull InetSocketAddress inetSocketAddress,
                                final @Nullable Duration connectTimeout) throws IOException {
        if (connectTimeout != null) {
            final var connectTimeoutMillis = connectTimeout.toMillis();
            if (connectTimeoutMillis > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("connect timeout in millis is too large, should be <= Integer.MAX_VALUE");
            }
            socket.connect(inetSocketAddress, (int) connectTimeoutMillis);
        } else {
            socket.connect(inetSocketAddress);
        }
    }

    @NonNull
    private static RealAsyncTimeout buildAsyncTimeout(final @NonNull Socket socket,
                                                      final long defaultReadTimeoutNanos,
                                                      final long defaultWriteTimeoutNanos) {
        assert socket != null;
        return new RealAsyncTimeout(defaultReadTimeoutNanos, defaultWriteTimeoutNanos, () -> {
            try {
                socket.close();
            } catch (Exception e) {
                LOGGER.log(WARNING, "Failed to close timed out socket " + socket, e);
            }
        });
    }

    private final @NonNull Socket socket;
    private final @NonNull RealAsyncTimeout asyncTimeout;

    private Reader reader = null;
    private Writer writer = null;

    SocketNetworkEndpoint(final @NonNull Socket socket,
                          final long defaultReadTimeoutNanos,
                          final long defaultWriteTimeoutNanos) {
        this(socket, buildAsyncTimeout(socket, defaultReadTimeoutNanos, defaultWriteTimeoutNanos));
    }

    private SocketNetworkEndpoint(final @NonNull Socket socket, final @NonNull RealAsyncTimeout asyncTimeout) {
        assert socket != null;
        assert asyncTimeout != null;

        this.socket = socket;
        this.asyncTimeout = asyncTimeout;
    }

    @Override
    public @NonNull Reader getReader() {
        try {
            // always get the input stream from socket that does some checks
            final var in = socket.getInputStream();
            if (reader == null) {
                final var rawReader = asyncTimeout.reader(new InputStreamRawReader(in));
                reader = Jayo.buffer(rawReader);
            }
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
        return reader;
    }

    @Override
    public @NonNull Writer getWriter() {
        try {
            // always get the output stream from socket that does some checks
            final var out = socket.getOutputStream();
            if (writer == null) {
                final var rawWriter = asyncTimeout.writer(new OutputStreamRawWriter(out));
                writer = Jayo.buffer(rawWriter);
            }
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
        return writer;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed() && !socket.isInputShutdown() && !socket.isOutputShutdown();
    }

    @Override
    public @NonNull InetSocketAddress getLocalAddress() {
        throwIfClosed();
        return (InetSocketAddress) socket.getLocalSocketAddress();
    }

    @Override
    public @NonNull InetSocketAddress getPeerAddress() {
        throwIfClosed();
        return (InetSocketAddress) socket.getRemoteSocketAddress();
    }

    @Override
    public <T> @Nullable T getOption(final @NonNull SocketOption<T> name) {
        Objects.requireNonNull(name);
        try {
            throwIfClosed();
            return socket.getOption(name);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public @NonNull Socket getUnderlying() {
        return socket;
    }

    private void throwIfClosed() {
        if (socket.isClosed()) {
            throw new JayoClosedResourceException();
        }
    }
}
