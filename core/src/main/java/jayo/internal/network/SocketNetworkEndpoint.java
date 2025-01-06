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

import jayo.JayoClosedResourceException;
import jayo.JayoException;
import jayo.RawReader;
import jayo.RawWriter;
import jayo.external.NonNegative;
import jayo.internal.CancellableUtils;
import jayo.internal.InputStreamRawReader;
import jayo.internal.OutputStreamRawWriter;
import jayo.internal.RealAsyncTimeout;
import jayo.network.NetworkEndpoint;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.Map;
import java.util.Objects;

import static java.lang.System.Logger.Level.WARNING;

/**
 * A {@link NetworkEndpoint} backed by an underlying {@linkplain Socket IO Socket}.
 */
public final class SocketNetworkEndpoint implements NetworkEndpoint {
    private static final System.Logger LOGGER_TIMEOUT = System.getLogger("jayo.network.SocketNetworkEndpoint");

    @SuppressWarnings({"unchecked", "RawUseOfParameterized"})
    static @NonNull NetworkEndpoint connect(
            final @NonNull SocketAddress peerAddress,
            final @NonNegative long connectTimeoutNanos,
            final @NonNegative long defaultReadTimeoutNanos,
            final @NonNegative long defaultWriteTimeoutNanos,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions
    ) {
        assert peerAddress != null;
        assert connectTimeoutNanos >= 0L;
        assert defaultReadTimeoutNanos >= 0L;
        assert defaultWriteTimeoutNanos >= 0L;
        assert socketOptions != null;

        final var socket = new Socket();
        try {
            final var asyncTimeout = buildAsyncTimeout(socket);
            final var cancelToken = CancellableUtils.getCancelToken(connectTimeoutNanos);
            if (cancelToken != null) {
                asyncTimeout.withTimeout(cancelToken, () -> {
                    try {
                        socket.connect(peerAddress);
                    } catch (IOException e) {
                        throw JayoException.buildJayoException(e);
                    }
                    return null;
                });
            } else {
                socket.connect(peerAddress);
            }

            for (final var socketOption : socketOptions.entrySet()) {
                socket.setOption(socketOption.getKey(), socketOption.getValue());
            }

            return new SocketNetworkEndpoint(socket, defaultReadTimeoutNanos, defaultWriteTimeoutNanos, asyncTimeout);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @NonNull
    private static RealAsyncTimeout buildAsyncTimeout(final @NonNull Socket socket) {
        assert socket != null;
        return new RealAsyncTimeout(() -> {
            try {
                socket.close();
            } catch (Exception e) {
                LOGGER_TIMEOUT.log(WARNING, "Failed to close timed out socket " + socket, e);
            }
        });
    }

    private final @NonNull Socket socket;
    private final @NonNull RealAsyncTimeout asyncTimeout;
    private final @NonNegative long defaultReadTimeoutNanos;
    private final @NonNegative long defaultWriteTimeoutNanos;

    @SuppressWarnings("FieldMayBeFinal")
    private volatile RawReader reader = null;
    @SuppressWarnings("FieldMayBeFinal")
    private volatile RawWriter writer = null;

    // VarHandle mechanics
    private static final VarHandle READER;
    private static final VarHandle WRITER;

    static {
        try {
            final var l = MethodHandles.lookup();
            READER = l.findVarHandle(SocketNetworkEndpoint.class, "reader", RawReader.class);
            WRITER = l.findVarHandle(SocketNetworkEndpoint.class, "writer", RawWriter.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    SocketNetworkEndpoint(final @NonNull Socket socket,
                          final @NonNegative long defaultReadTimeoutNanos,
                          final @NonNegative long defaultWriteTimeoutNanos) {
        this(socket, defaultReadTimeoutNanos, defaultWriteTimeoutNanos, buildAsyncTimeout(socket));
    }

    private SocketNetworkEndpoint(final @NonNull Socket socket,
                                  final @NonNegative long defaultReadTimeoutNanos,
                                  final @NonNegative long defaultWriteTimeoutNanos,
                                  final @NonNull RealAsyncTimeout asyncTimeout) {
        assert socket != null;
        assert defaultReadTimeoutNanos >= 0L;
        assert defaultWriteTimeoutNanos >= 0L;
        assert asyncTimeout != null;

        this.socket = socket;
        this.defaultReadTimeoutNanos = defaultReadTimeoutNanos;
        this.defaultWriteTimeoutNanos = defaultWriteTimeoutNanos;
        this.asyncTimeout = asyncTimeout;
    }

    @Override
    public @NonNull RawReader getReader() {
        var reader = this.reader;
        try {
            // always get the input stream from socket that does some checks
            final var in = socket.getInputStream();
            if (reader == null) {
                reader = asyncTimeout.reader(new InputStreamRawReader(in), defaultReadTimeoutNanos);
                if (!READER.compareAndSet(this, null, reader)) {
                    reader = this.reader;
                }
            }
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
        return reader;
    }

    @Override
    public @NonNull RawWriter getWriter() {
        var writer = this.writer;
        try {
            // always get the output stream from socket that does some checks
            final var out = socket.getOutputStream();
            if (writer == null) {
                writer = asyncTimeout.writer(new OutputStreamRawWriter(out), defaultWriteTimeoutNanos);
                if (!WRITER.compareAndSet(this, null, writer)) {
                    writer = this.writer;
                }
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
    public @NonNull SocketAddress getLocalAddress() {
        throwIfClosed();
        return socket.getLocalSocketAddress();
    }

    @Override
    public @NonNull SocketAddress getPeerAddress() {
        throwIfClosed();
        return socket.getRemoteSocketAddress();
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
