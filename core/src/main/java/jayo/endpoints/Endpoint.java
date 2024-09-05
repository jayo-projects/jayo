/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.endpoints;

import jayo.RawReader;
import jayo.RawWriter;
import org.jspecify.annotations.NonNull;

import java.net.Socket;
import java.nio.channels.SocketChannel;

/**
 * Your endpoint on an I/O connection between you and one or several peer(s).
 * <p>
 * An endpoint is plugged to an open connection, you can read incoming data thanks to {@link #getReader()} and write
 * data thanks to {@link #getWriter()}.
 * <p>
 * An endpoint is either open or closed. An endpoint is open upon creation, and once the
 * {@linkplain #getUnderlying() underlying} IO resource is closed it remains closed. After an endpoint is closed, any
 * further attempt to invoke an I/O operation upon it will cause a {@link JayoClosedEndpointException} to be thrown.
 * <p>
 * Note: <b>A file is not an endpoint.</b>
 */
public interface Endpoint {
    /**
     * @return a raw reader that reads incoming data from the I/O connection.
     * @throws jayo.exceptions.JayoException if an I/O error occurs when creating the raw reader.
     */
    @NonNull
    RawReader getReader();

    /**
     * @return a raw writer that writes data into the I/O connection.
     * @throws jayo.exceptions.JayoException if an I/O error occurs when creating the raw writer.
     */
    @NonNull
    RawWriter getWriter();

    /**
     * @return the underlying IO resource. For example a {@link Socket}, a {@link SocketChannel} or even another
     * {@link Endpoint}.
     */
    @NonNull
    Object getUnderlying();
}
