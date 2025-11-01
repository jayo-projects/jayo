/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo;

import org.jspecify.annotations.NonNull;

/**
 * A pair of streams for interactive communication with a peer using an I/O connection.
 * <p>
 * Send data to the peer by writing to {@linkplain #getWriter() writer}, and read data from the peer by reading from
 * {@linkplain #getReader() reader}.
 * <p>
 * This can be implemented by a plain TCP socket. It can also be layered to add features like security (as in a TLS
 * socket) or connectivity (as in a proxy socket).
 * <p>
 * Closing the {@linkplain #getReader() reader} does not impact the {@linkplain #getWriter() writer}, and vice
 * versa.
 * <p>
 * You must close the reader and the writer to ensure you release the resources held by this socket. If you're using
 * both from the same thread, you can do that with a try with resource block:
 * <pre>
 * {@code
 * try (Reader reader = socket.getReader(); Writer writer = socket.getWriter()) {
 *   readAndWrite(reader, writer)
 * }
 * }
 * </pre>
 * A socket is open upon creation until the reader or the writer is closed, or the socket is
 * {@linkplain #cancel() canceled}. Once canceled, any further attempt to read, write or flush will fail immediately
 * with a {@linkplain JayoException JayoException}.
 *
 * @see Jayo#closeQuietly(RawSocket)
 */
public interface Socket extends RawSocket {
    /**
     * @return a reader that reads incoming data from the I/O connection.
     * @throws JayoException if an I/O error occurs.
     */
    @Override
    @NonNull
    Reader getReader();

    /**
     * @return a writer that writes data into the I/O connection.
     * @throws JayoException if an I/O error occurs.
     */
    @Override
    @NonNull
    Writer getWriter();

    /**
     * @return {@code true} if this socket is open, ensuring that neither {@linkplain #getReader() reader} nor
     * {@linkplain #getWriter() writer} are closed and this socket is not {@linkplain #cancel() canceled}.
     */
    boolean isOpen();

    /**
     * @return the underlying resource. For example, a {@linkplain java.net.Socket IO Socket}, a
     * {@linkplain java.nio.channels.SocketChannel NIO SocketChannel} or another {@link Socket} / {@link RawSocket}.
     */
    @NonNull
    Object getUnderlying();
}
