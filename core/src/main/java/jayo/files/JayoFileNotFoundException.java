/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.files;

import jayo.JayoException;
import org.jspecify.annotations.NonNull;

import java.io.FileNotFoundException;
import java.nio.file.NoSuchFileException;
import java.util.Objects;

/**
 * Wraps a {@link FileNotFoundException} or a {@link NoSuchFileException} with an unchecked exception.
 */
public final class JayoFileNotFoundException extends JayoException {
    @SuppressWarnings("unused")
    public JayoFileNotFoundException(final @NonNull String message) {
        super(Objects.requireNonNull(message), new FileNotFoundException(message));
    }

    public JayoFileNotFoundException(final @NonNull FileNotFoundException cause) {
        super(Objects.requireNonNull(cause));
    }

    public JayoFileNotFoundException(final @NonNull NoSuchFileException cause) {
        super(Objects.requireNonNull(cause));
    }
}
