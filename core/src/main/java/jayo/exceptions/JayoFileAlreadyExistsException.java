/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.exceptions;

import org.jspecify.annotations.NonNull;

import java.nio.file.FileAlreadyExistsException;
import java.util.Objects;

/**
 * Wraps a {@link FileAlreadyExistsException} with an unchecked exception.
 */
public final class JayoFileAlreadyExistsException extends JayoException {
    public JayoFileAlreadyExistsException(final @NonNull FileAlreadyExistsException cause) {
        super(Objects.requireNonNull(cause));
    }
}
