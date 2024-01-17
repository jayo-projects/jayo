/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.exceptions;

import org.jspecify.annotations.NonNull;
import jayo.Cancellable;

import java.util.Objects;

/**
 * Exception indicating that a cancellable task has reached deadline, or that an I/O operation has timed out.
 *
 * @see Cancellable
 */
public final class JayoTimeoutException extends JayoCancelledException {
    public JayoTimeoutException(final @NonNull String message) {
        super(Objects.requireNonNull(message));
    }
}
