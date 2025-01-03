/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo;

import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.Objects;

/**
 * Exception thrown when an attempt is made to invoke or complete an I/O operation upon endpoint that is closed, or at
 * least closed to that operation.
 * <p>
 * We also wrap a {@linkplain java.nio.channels.ClosedChannelException ClosedChannelException} or a
 * {@linkplain java.net.SocketException SocketException} when its message is <i>"Socket is closed"</i> or any
 * {@link IOException} when its message is <i>"Broken Pipe"</i> with this unchecked exception.
 */
public final class JayoClosedEndpointException extends JayoException {
    public JayoClosedEndpointException() {
        super(new IOException());
    }

    public JayoClosedEndpointException(final @NonNull IOException cause) {
        super(Objects.requireNonNull(cause));
    }
}
