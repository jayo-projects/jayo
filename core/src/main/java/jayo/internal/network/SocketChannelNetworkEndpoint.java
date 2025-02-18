/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.JayoException;
import jayo.RawReader;
import jayo.RawWriter;
import jayo.internal.CancellableUtils;
import jayo.internal.GatheringByteChannelRawWriter;
import jayo.internal.ReadableByteChannelRawReader;
import jayo.internal.RealAsyncTimeout;
import jayo.network.NetworkEndpoint;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.SocketChannel;
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
            final @NonNull SocketAddress peerAddress,
            final long connectTimeoutNanos,
            final long defaultReadTimeoutNanos,
            final long defaultWriteTimeoutNanos,
            final @NonNull Map<@NonNull SocketOption, @Nullable Object> socketOptions,
            final @Nullable ProtocolFamily family) {
        assert peerAddress != null;
        assert connectTimeoutNanos >= 0;
        assert defaultReadTimeoutNanos >= 0L;
        assert defaultWriteTimeoutNanos >= 0L;
        assert socketOptions != null;

        try {
            // SocketChannel defaults to blocking-mode, that's what we want
            final var socketChannel = (family != null) ? SocketChannel.open(family) : SocketChannel.open();

            final var asyncTimeout = buildAsyncTimeout(socketChannel, defaultReadTimeoutNanos,
                    defaultWriteTimeoutNanos);
            final var cancelToken = CancellableUtils.getCancelToken();
            asyncTimeout.withTimeoutOrDefault(cancelToken, connectTimeoutNanos, () -> {
                try {
                    socketChannel.connect(peerAddress);
                    return null;
                } catch (IOException e) {
                    throw JayoException.buildJayoException(e);
                }
            });

            for (final var socketOption : socketOptions.entrySet()) {
                socketChannel.setOption(socketOption.getKey(), socketOption.getValue());
            }

            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "new client SocketChannelNetworkEndpoint connected to {0}{1}protocol family " +
                                "= {2}, default read timeout = {3} ns, default write timeout = {4} ns{5}provided " +
                                "socket options = {6}",
                        peerAddress, System.lineSeparator(), family, defaultReadTimeoutNanos, defaultWriteTimeoutNanos,
                        System.lineSeparator(), socketOptions);
            }

            return new SocketChannelNetworkEndpoint(
                    socketChannel,
                    asyncTimeout);
        } catch (IOException e) {
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG,
                        "new client SocketChannelNetworkEndpoint failed to connect to " + peerAddress, e);
            }
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
            READER = l.findVarHandle(SocketChannelNetworkEndpoint.class, "reader", RawReader.class);
            WRITER = l.findVarHandle(SocketChannelNetworkEndpoint.class, "writer", RawWriter.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

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
    public @NonNull RawReader getReader() {
        var reader = this.reader;
        if (reader == null) {
            reader = asyncTimeout.reader(new ReadableByteChannelRawReader(socketChannel));
            if (!READER.compareAndSet(this, null, reader)) {
                reader = this.reader;
            }
        }
        return reader;
    }

    @Override
    public @NonNull RawWriter getWriter() {
        var writer = this.writer;
        if (writer == null) {
            writer = asyncTimeout.writer(new GatheringByteChannelRawWriter(socketChannel));
            if (!WRITER.compareAndSet(this, null, writer)) {
                writer = this.writer;
            }
        }
        return writer;
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
    public @NonNull SocketAddress getLocalAddress() {
        try {
            return socketChannel.getLocalAddress();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public @NonNull SocketAddress getPeerAddress() {
        try {
            return socketChannel.getRemoteAddress();
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
