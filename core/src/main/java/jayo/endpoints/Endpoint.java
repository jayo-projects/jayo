/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.endpoints;

import jayo.RawReader;
import jayo.RawWriter;
import jayo.internal.RealSocketEndpoint;
import org.jspecify.annotations.NonNull;

import java.io.Closeable;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.Objects;

/**
 * Your endpoint on an I/O connection between you and one or several peer(s).
 * <p>
 * An endpoint is plugged to an open connection, you can read incoming data thanks to {@link #getReader()} and write
 * data thanks to {@link #getWriter()}.
 * <p>
 * An endpoint is either open or closed. An endpoint is open upon creation, and once closed it remains closed. After an
 * endpoint is closed, any further attempt to invoke an I/O operation upon it will cause a
 * {@link JayoClosedEndpointException} to be thrown.
 * <p>
 * Note: <b>A file is not an endpoint.</b>
 */
public interface Endpoint extends Closeable {
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
     * @return a raw reader that reads incoming data from the I/O connection.
     * @throws jayo.exceptions.JayoException if an I/O error occurs when creating the raw reader.
     * @implSpec the {@linkplain RawReader#close() close} method of this reader must call the {@link #close()} method
     * of this endpoint.
     */
    @NonNull
    RawReader getReader();

    /**
     * @return a raw writer that writes data into the I/O connection.
     * @throws jayo.exceptions.JayoException if an I/O error occurs when creating the raw writer.
     * @implSpec the {@linkplain RawWriter#close() close} method of this writer must call the {@link #close()} method
     * of this endpoint.
     */
    @NonNull
    RawWriter getWriter();

    /**
     * Closes this endpoint.
     * <p>
     * After an endpoint is closed, any further attempt to invoke I/O operations upon it will cause a
     * {@link JayoClosedEndpointException} to be thrown.
     * <p>
     * If this endpoint is already closed then invoking this method has no effect.
     *
     * @throws jayo.exceptions.JayoException If an I/O error occurs during the closing phase.
     */
    void close();

    /**
     * @return the underlying IO resource. For example a {@link Socket}, a {@link SocketChannel} or even another
     * {@link Endpoint}.
     */
    @NonNull
    Object getUnderlying();
}
