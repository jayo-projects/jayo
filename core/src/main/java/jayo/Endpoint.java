/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo;

import org.jspecify.annotations.NonNull;

import java.io.Closeable;

/**
 * Your endpoint on an I/O connection between you and a peer.
 * <p>
 * An endpoint is plugged to an open connection, you can read incoming data thanks to {@link #getReader()} and write
 * data thanks to {@link #getWriter()}.
 * <p>
 * An endpoint is either open or closed. An endpoint is open upon creation, and once closed, it remains closed. After an
 * endpoint is closed, any further attempt to invoke an I/O operation upon it will cause a
 * {@link JayoClosedResourceException} to be thrown.
 * <p>
 * Note: <b>A file is not an endpoint.</b>
 */
public interface Endpoint extends Closeable {
    /**
     * @return a reader that reads incoming data from the I/O connection.
     * @throws JayoException if an I/O error occurs when creating the raw reader.
     */
    @NonNull
    Reader getReader();

    /**
     * @return a writer that writes data into the I/O connection.
     * @throws JayoException if an I/O error occurs when creating the raw writer.
     */
    @NonNull
    Writer getWriter();

    /**
     * Closes this endpoint and releases the resources held by its {@linkplain #getReader() reader} and
     * {@linkplain #getWriter() writer}, that also pushes all buffered data to their final destination. Trying to read
     * or write to a closed endpoint will throw a {@link JayoClosedResourceException}.
     * <p>
     * It is safe to close an endpoint more than once, but only the first call has an effect.
     *
     * @throws JayoException if an I/O error occurs during the closing phase.
     */
    void close();

    /**
     * @return {@code true} if this endpoint is open.
     * @see #close()
     */
    boolean isOpen();

    /**
     * @return the underlying IO resource. For example, a {@linkplain java.net.Socket IO Socket}, a
     * {@linkplain java.nio.channels.SocketChannel NIO SocketChannel} or even another {@link Endpoint}.
     */
    @NonNull
    Object getUnderlying();
}
