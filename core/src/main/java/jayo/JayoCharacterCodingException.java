/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo;

import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.Objects;

/**
 * Unchecked IO exception, semantically identical as
 * {@linkplain java.nio.charset.CharacterCodingException CharacterCodingException}.
 */
public final class JayoCharacterCodingException extends JayoException {
    public JayoCharacterCodingException(final @NonNull String message) {
        super(Objects.requireNonNull(message), new IOException(message));
    }
}
