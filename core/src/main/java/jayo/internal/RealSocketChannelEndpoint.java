/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.RawReader;
import jayo.RawWriter;
import jayo.endpoints.SocketChannelEndpoint;
import jayo.exceptions.JayoException;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.channels.SocketChannel;
import java.util.Objects;

import static java.lang.System.Logger.Level.WARNING;

public final class RealSocketChannelEndpoint implements SocketChannelEndpoint {
    private static final System.Logger LOGGER_TIMEOUT = System.getLogger("jayo.SocketChannelEndpoint");
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
            READER = l.findVarHandle(RealSocketChannelEndpoint.class, "reader", RawReader.class);
            WRITER = l.findVarHandle(RealSocketChannelEndpoint.class, "writer", RawWriter.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public RealSocketChannelEndpoint(final @NonNull SocketChannel socketChannel) {
        this.socketChannel = Objects.requireNonNull(socketChannel);
        asyncTimeout = new RealAsyncTimeout(() -> {
            try {
                socketChannel.close();
            } catch (Exception e) {
                LOGGER_TIMEOUT.log(WARNING, "Failed to close timed out socket channel " + socketChannel, e);
            }
        });
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
        final var cancelToken = CancellableUtils.getCancelToken();
        if (cancelToken != null) {
            asyncTimeout.withTimeout(cancelToken, () -> {
                closePrivate();
                return null;
            });
            return;
        }
        closePrivate();
    }

    private void closePrivate() {
        try {
            socketChannel.close();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public @NonNull SocketChannel getUnderlying() {
        return socketChannel;
    }
}
