/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.endpoints;

import jayo.JayoException;
import org.jspecify.annotations.NonNull;

import java.net.SocketException;
import java.util.Objects;

/**
 * Wraps a {@link SocketException} with an unchecked exception.
 */
public final class JayoEndpointException extends JayoException {
    public JayoEndpointException(final @NonNull SocketException cause) {
        super(Objects.requireNonNull(cause));
    }
}
