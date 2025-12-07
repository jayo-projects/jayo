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
public interface RawSocket {
    /**
     * @return a reader that reads incoming data from the I/O connection.
     * @throws JayoException if an I/O error occurs.
     */
    @NonNull
    RawReader getReader();

    /**
     * @return a writer that writes data into the I/O connection.
     * @throws JayoException if an I/O error occurs.
     */
    @NonNull
    RawWriter getWriter();

    /**
     * Fail any in-flight and future operations. After canceling:
     * <ul>
     * <li>Any attempt to write or flush {@linkplain #getWriter() writer} will fail immediately with a
     * {@link JayoException}.
     * <li>Any attempt to read {@linkplain #getReader() reader} will fail immediately with a {@link JayoException}.
     * </ul>
     * Closing the reader and the writer will complete normally even after a socket has been canceled.
     * <p>
     * This operation may be called by any thread at any time. It is safe to call concurrently while operating on the
     * reader or the writer.
     */
    void cancel();

    /**
     * @return {@code true} if this socket is open, ensuring that neither {@linkplain #getReader() reader} nor
     * {@linkplain #getWriter() writer} are closed and this socket is not {@linkplain #cancel() canceled}.
     */
    boolean isOpen();
}
