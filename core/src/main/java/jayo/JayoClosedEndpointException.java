/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo;

import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;

/**
 * Exception thrown when an attempt is made to invoke or complete an I/O operation upon endpoint that is closed, or at
 * least closed to that operation.
 * <p>
 * It can also wrap a {@link ClosedChannelException} or a {@link SocketException} when its message is
 * <i>"Socket is closed"</i> with this unchecked exception.
 */
public final class JayoClosedEndpointException extends JayoException {
    public JayoClosedEndpointException() {
        super(new IOException());
    }

    public JayoClosedEndpointException(final @NonNull ClosedChannelException cause) {
        super(Objects.requireNonNull(cause));
    }

    public JayoClosedEndpointException(final @NonNull SocketException cause) {
        super(Objects.requireNonNull(cause));
    }
}
