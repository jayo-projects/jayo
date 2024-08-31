/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.exceptions;

import org.jspecify.annotations.NonNull;

import java.net.ProtocolException;
import java.util.Objects;

/**
 * Wraps a {@link ProtocolException} with an unchecked exception.
 */
public final class JayoProtocolException extends JayoException {
    public JayoProtocolException(final @NonNull String message) {
        super(Objects.requireNonNull(message), new ProtocolException(message));
    }

    public JayoProtocolException(final @NonNull ProtocolException cause) {
        super(Objects.requireNonNull(cause));
    }
}
