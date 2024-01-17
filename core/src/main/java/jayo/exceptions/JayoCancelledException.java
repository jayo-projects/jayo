/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.exceptions;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import jayo.Cancellable;

import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Exception indicating that the result of a cancellable task cannot be retrieved because the task was cancelled.
 *
 * @see Cancellable
 */
public sealed class JayoCancelledException extends CancellationException permits JayoTimeoutException {
    public JayoCancelledException(final @NonNull String message) {
        super(Objects.requireNonNull(message));
    }

    /**
     * Returns the cause of this exception.
     *
     * @return the {@code IOException} which is the cause of this exception.
     */
    @Override
    public @Nullable JayoException getCause() {
        return (JayoException) super.getCause();
    }

    public void initJayoCause(final @NonNull JayoException cause) {
        super.initCause(Objects.requireNonNull(cause));
    }
}
