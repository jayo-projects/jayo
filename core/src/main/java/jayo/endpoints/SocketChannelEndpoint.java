/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.endpoints;

import jayo.internal.RealSocketChannelEndpoint;
import org.jspecify.annotations.NonNull;

import java.nio.channels.SocketChannel;
import java.util.Objects;

/**
 * An endpoint bound to an underlying {@link SocketChannel}.
 * <p>
 * This socket must be {@linkplain SocketChannel#isConnected() connected} and {@linkplain SocketChannel#isOpen() open}
 * on {@link SocketChannelEndpoint} creation.
 * <p>
 * Please read {@link Endpoint} javadoc for endpoint rationale.
 */
public sealed interface SocketChannelEndpoint extends Endpoint permits RealSocketChannelEndpoint {
    /**
     * @return an endpoint bound to the provided {@link SocketChannel}. This socket must be
     * {@linkplain SocketChannel#isConnected() connected} and {@linkplain SocketChannel#isOpen() open}.
     * <p>
     * Prefer this over using {@code Jayo.reader(socketChannel)} and {@code Jayo.writer(socketChannel)} because this
     * endpoint honors timeouts. When a read or write operation times out, the underlying socket channel is
     * asynchronously closed by a watchdog thread.
     * @throws IllegalArgumentException if the socket channel is not {@linkplain SocketChannel#isConnected() connected}
     *                                  or not {@linkplain SocketChannel#isOpen() open}.
     */
    static @NonNull SocketChannelEndpoint from(final @NonNull SocketChannel socketChannel) {
        Objects.requireNonNull(socketChannel);
        if (!socketChannel.isOpen()) {
            throw new IllegalArgumentException("Socket channel is closed");
        }
        if (!socketChannel.isConnected()) {
            throw new IllegalArgumentException("Socket channel is not connected");
        }
        return new RealSocketChannelEndpoint(socketChannel);
    }

    /**
     * @return the underlying {@link SocketChannel}.
     */
    @NonNull
    SocketChannel getUnderlying();
}
