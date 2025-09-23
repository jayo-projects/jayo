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

package jayo.internal;

import jayo.JayoClosedResourceException;
import jayo.JayoException;
import jayo.internal.network.RealSocksProxy;
import jayo.internal.network.SocksNetworkSocket;
import jayo.network.NetworkSocket;
import jayo.network.Proxy;
import jayo.tools.CancelToken;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketOption;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.WARNING;

/**
 * A {@link NetworkSocket} backed by an underlying {@linkplain Socket IO Socket}.
 */
public final class IoSocketNetworkSocket extends AbstractNetworkSocket {
    private static final System.Logger LOGGER = System.getLogger("jayo.network.AbstractNetworkSocket");

    @SuppressWarnings({"unchecked", "RawUseOfParameterized"})
    public static @NonNull NetworkSocket connect(
            final @NonNull InetSocketAddress peerAddress,
            final @Nullable Duration connectTimeout,
            final Proxy.@Nullable Socks proxy,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions
    ) {
        assert peerAddress != null;
        assert socketOptions != null;

        final var socket = new Socket();
        try {
            for (final var socketOption : socketOptions.entrySet()) {
                socket.setOption(socketOption.getKey(), socketOption.getValue());
            }

            final var asyncTimeout = buildAsyncTimeout(socket);
            final var networkSocket = connect(socket, peerAddress, connectTimeout, asyncTimeout, proxy);

            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "new client AbstractNetworkSocket connected to {0}, socket options = {1}",
                        peerAddress, socketOptions);
            }

            return networkSocket;
        } catch (IOException e) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "new client AbstractNetworkSocket failed to connect to " + peerAddress, e);
            }
            throw JayoException.buildJayoException(e);
        }
    }

    private static NetworkSocket connect(final @NonNull Socket socket,
                                         final @NonNull InetSocketAddress peerAddress,
                                         final @Nullable Duration connectTimeout,
                                         final @NonNull RealAsyncTimeout asyncTimeout,
                                         final Proxy.@Nullable Socks proxy) {
        assert socket != null;
        assert peerAddress != null;
        assert asyncTimeout != null;

        try {
            if (proxy != null) {
                // connect to the proxy and use it to reach peer
                connect(socket, new InetSocketAddress(proxy.getHost(), proxy.getPort()), connectTimeout);
                final var proxyNetEndpoint = new IoSocketNetworkSocket(socket, asyncTimeout);
                if (!(proxy instanceof RealSocksProxy socksProxy)) {
                    throw new IllegalArgumentException("proxy is not a RealSocksProxy");
                }
                return new SocksNetworkSocket(socksProxy, proxyNetEndpoint, peerAddress);
            }
            // connect to peer
            connect(socket, peerAddress, connectTimeout);
            return new IoSocketNetworkSocket(socket, asyncTimeout);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    private static void connect(final @NonNull Socket socket,
                                final @NonNull InetSocketAddress inetSocketAddress,
                                final @Nullable Duration connectTimeout) throws IOException {
        assert socket != null;
        assert inetSocketAddress != null;

        if (connectTimeout != null) {
            final var connectTimeoutMillis = getTimeoutAsMillis(connectTimeout);
            socket.connect(inetSocketAddress, (int) connectTimeoutMillis);
        } else {
            socket.connect(inetSocketAddress);
        }
    }

    private static int getTimeoutAsMillis(final @NonNull Duration timeout) {
        assert timeout != null;

        final var timeoutMillis = timeout.toMillis();
        if (timeoutMillis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("The timeout in millis is too large, should be <= Integer.MAX_VALUE");
        }
        return (int) timeoutMillis;
    }

    @NonNull
    private static RealAsyncTimeout buildAsyncTimeout(final @NonNull Socket socket) {
        assert socket != null;
        return new RealAsyncTimeout(() -> {
            try {
                socket.close();
            } catch (Exception e) {
                LOGGER.log(WARNING, "Failed to close timed out socket " + socket, e);
            }
        });
    }

    private final @NonNull Socket socket;
    private final @NonNull InputStream in;
    private final @NonNull OutputStream out;

    public IoSocketNetworkSocket(final @NonNull Socket socket) {
        this(socket, buildAsyncTimeout(socket));
    }

    private IoSocketNetworkSocket(final @NonNull Socket socket, final @NonNull RealAsyncTimeout timeout) {
        super(timeout);
        assert socket != null;

        this.socket = socket;
        try {
            in = socket.getInputStream();
            out = socket.getOutputStream();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public void cancel() {
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

    private void throwIfClosed() {
        if (socket.isClosed()) {
            throw new JayoClosedResourceException();
        }
    }

    @Override
    public void setReadTimeout(final @NonNull Duration readTimeout) {
        Objects.requireNonNull(readTimeout);

        final var readTimeoutMillis = getTimeoutAsMillis(readTimeout);
        try {
            socket.setSoTimeout(readTimeoutMillis);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }

        readTimeoutNanos = readTimeout.toNanos();
    }

    @Override
    public @NonNull Socket getUnderlying() {
        return socket;
    }

    @Override
    int read(final @NonNull Segment dstTail, final int toRead) throws IOException {
        assert dstTail != null;
        return in.read(dstTail.data, dstTail.limit, toRead);
    }

    @Override
    void shutdownInput() throws IOException {
        if (socket.isClosed() || socket.isInputShutdown()) {
            return; // Nothing to do.
        }
        try {
            socket.shutdownInput();
        } catch (SocketException se) {
            // avoid a rare closing race condition
            if (!se.getMessage().equals("Socket is closed")) {
                throw se;
            }
        }
    }

    @Override
    void write(final @NonNull RealBuffer src,
               final long byteCount,
               final @Nullable RealCancelToken cancelToken) {
        assert src != null;

        var remaining = byteCount;
        while (remaining > 0L) {
            CancelToken.throwIfReached(cancelToken);
            final var head = src.head;
            assert head != null;
            final var toWrite = (int) Math.min(remaining, head.limit - head.pos);
            timeout.withTimeout(cancelToken, ignored -> {
                try {
                    out.write(head.data, head.pos, toWrite);
                    return null;
                } catch (IOException e) {
                    throw JayoException.buildJayoException(e);
                }
            });

            head.pos += toWrite;
            src.byteSize -= toWrite;
            remaining -= toWrite;

            if (head.pos == head.limit) {
                src.head = head.pop();
                SegmentPool.recycle(head);
            }
        }
    }

    @Override
    void flush() throws IOException {
        out.flush();
    }

    @Override
    void shutdownOutput() throws IOException {
        if (socket.isClosed() || socket.isOutputShutdown()) {
            return; // Nothing to do.
        }
        try {
            out.flush();
            socket.shutdownOutput();
        } catch (SocketException se) {
            // avoid a rare closing race condition
            if (!se.getMessage().equals("Socket is closed")) {
                throw se;
            }
        }
    }
}
