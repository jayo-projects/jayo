/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.network;

import jayo.JayoException;
import org.jspecify.annotations.NonNull;

import java.net.UnknownServiceException;
import java.util.Objects;

/**
 * Wraps an {@link UnknownServiceException} with an unchecked exception.
 */
public final class JayoUnknownServiceException extends JayoException {
    public JayoUnknownServiceException(final @NonNull UnknownServiceException cause) {
        super(Objects.requireNonNull(cause));
    }

    public JayoUnknownServiceException(final @NonNull String message) {
        super(Objects.requireNonNull(message), new UnknownServiceException(message));
    }
}
