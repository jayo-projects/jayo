/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.exceptions;

import org.jspecify.annotations.NonNull;

import java.io.EOFException;
import java.util.Objects;

/**
 * Wraps a {@link EOFException} with an unchecked exception.
 */
public final class JayoEOFException extends JayoException {
    public JayoEOFException() {
        super(new EOFException());
    }
    
    public JayoEOFException(final @NonNull String message) {
        super(Objects.requireNonNull(message), new EOFException(message));
    }

    public JayoEOFException(final @NonNull EOFException cause) {
        super(Objects.requireNonNull(cause));
    }
}
