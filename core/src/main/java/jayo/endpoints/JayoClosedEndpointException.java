/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.endpoints;

import jayo.JayoException;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;

/**
 * Wraps a {@link ClosedChannelException} or a {@link SocketException} with "Socket is closed" message with an unchecked
 * exception.
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
