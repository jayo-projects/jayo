/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.JayoException;
import jayo.RawReader;
import jayo.RawWriter;
import jayo.external.NonNegative;
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

import static java.lang.System.Logger.Level.WARNING;

/**
 * A {@link NetworkEndpoint} backed by an underlying {@linkplain SocketChannel NIO SocketChannel}.
 */
public final class SocketChannelNetworkEndpoint implements NetworkEndpoint {
    private static final System.Logger LOGGER_TIMEOUT =
            System.getLogger("jayo.network.SocketChannelNetworkEndpoint");

    @SuppressWarnings({"unchecked", "RawUseOfParameterized"})
    static @NonNull NetworkEndpoint connect(
            final @NonNull SocketAddress peerAddress,
            final @NonNegative long connectTimeoutNanos,
            final @NonNegative long defaultReadTimeoutNanos,
            final @NonNegative long defaultWriteTimeoutNanos,
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

            final var asyncTimeout = buildAsyncTimeout(socketChannel);
            final var cancelToken = CancellableUtils.getCancelToken(connectTimeoutNanos);
            if (cancelToken != null) {
                asyncTimeout.withTimeout(cancelToken, () -> {
                    try {
                        socketChannel.connect(peerAddress);
                    } catch (IOException e) {
                        throw JayoException.buildJayoException(e);
                    }
                    return null;
                });
            } else {
                socketChannel.connect(peerAddress);
            }

            for (final var socketOption : socketOptions.entrySet()) {
                socketChannel.setOption(socketOption.getKey(), socketOption.getValue());
            }

            return new SocketChannelNetworkEndpoint(
                    socketChannel,
                    defaultReadTimeoutNanos,
                    defaultWriteTimeoutNanos,
                    asyncTimeout);
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
                LOGGER_TIMEOUT.log(WARNING, "Failed to close timed out socket channel " + socketChannel, e);
            }
        });
    }

    private final @NonNull SocketChannel socketChannel;
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
            READER = l.findVarHandle(SocketChannelNetworkEndpoint.class, "reader", RawReader.class);
            WRITER = l.findVarHandle(SocketChannelNetworkEndpoint.class, "writer", RawWriter.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    SocketChannelNetworkEndpoint(final @NonNull SocketChannel socketChannel,
                                 final @NonNegative long defaultReadTimeoutNanos,
                                 final @NonNegative long defaultWriteTimeoutNanos) {
        this(socketChannel, defaultReadTimeoutNanos, defaultWriteTimeoutNanos, buildAsyncTimeout(socketChannel));
    }

    private SocketChannelNetworkEndpoint(final @NonNull SocketChannel socketChannel,
                                         final @NonNegative long defaultReadTimeoutNanos,
                                         final @NonNegative long defaultWriteTimeoutNanos,
                                         final @NonNull RealAsyncTimeout asyncTimeout) {
        assert socketChannel != null;
        assert defaultReadTimeoutNanos >= 0L;
        assert defaultWriteTimeoutNanos >= 0L;
        assert asyncTimeout != null;

        this.socketChannel = socketChannel;
        this.defaultReadTimeoutNanos = defaultReadTimeoutNanos;
        this.defaultWriteTimeoutNanos = defaultWriteTimeoutNanos;
        this.asyncTimeout = asyncTimeout;
    }

    @Override
    public @NonNull RawReader getReader() {
        var reader = this.reader;
        if (reader == null) {
            reader = asyncTimeout.reader(new ReadableByteChannelRawReader(socketChannel), defaultReadTimeoutNanos);
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
            writer = asyncTimeout.writer(new GatheringByteChannelRawWriter(socketChannel), defaultWriteTimeoutNanos);
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
