/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.endpoints;

import jayo.internal.RealSocketEndpoint;
import org.jspecify.annotations.NonNull;

import java.net.Socket;
import java.util.Objects;

/**
 * An endpoint bound to an underlying {@link Socket}.
 * <p>
 * This socket must be {@linkplain Socket#isConnected() connected} and not {@linkplain Socket#isClosed() closed} on
 * {@link SocketEndpoint} creation.
 * <p>
 * Please read {@link Endpoint} javadoc for endpoint rationale.
 */
public sealed interface SocketEndpoint extends Endpoint permits RealSocketEndpoint {
    /**
     * @return an endpoint bound to the provided {@link Socket}. This socket must be
     * {@linkplain Socket#isConnected() connected} and not {@linkplain Socket#isClosed() closed}.
     * <p>
     * Prefer this over using {@code Jayo.reader(socket.getInputStream())} and
     * {@code Jayo.writer(socket.getOutputStream())} because this endpoint honors timeouts. When a read or write
     * operation times out, the underlying socket is asynchronously closed by a watchdog thread.
     * @throws IllegalArgumentException if the socket is not {@linkplain Socket#isConnected() connected} or is
     *                                  {@linkplain Socket#isClosed() closed}.
     */
    static @NonNull SocketEndpoint from(final @NonNull Socket socket) {
        Objects.requireNonNull(socket);
        if (!socket.isConnected()) {
            throw new IllegalArgumentException("Socket is not connected");
        }
        if (socket.isClosed()) {
            throw new IllegalArgumentException("Socket is closed");
        }
        return new RealSocketEndpoint(socket);
    }

    /**
     * @return the underlying {@link Socket}.
     */
    @NonNull
    Socket getUnderlying();
}
