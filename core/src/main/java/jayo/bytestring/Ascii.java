/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.bytestring;

import jayo.JayoEOFException;
import jayo.JayoException;
import jayo.internal.RealAscii;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.stream.IntStream;

public sealed interface Ascii extends Utf8, CharSequence permits RealAscii {
    /**
     * The empty ASCII byte string
     */
    @NonNull
    Utf8 EMPTY = new RealAscii(new byte[0]);

    /**
     * @param data a sequence of bytes to be wrapped.
     * @return a new ASCII byte string containing a copy of all the ASCII bytes of {@code data}.
     */
    static @NonNull Utf8 of(final byte @NonNull ... data) {
        Objects.requireNonNull(data);
        return new RealAscii(data.clone());
    }

    /**
     * @param offset    the start offset (inclusive) in the {@code data} byte array.
     * @param byteCount the number of bytes to copy.
     * @return a new ASCII byte string containing a copy of {@code byteCount} ASCII bytes of {@code data} starting at
     * {@code offset}.
     * @throws IndexOutOfBoundsException if {@code offset} or {@code byteCount} is out of range of {@code data} indices.
     */
    static @NonNull Utf8 of(final byte @NonNull [] data,
                            final int offset,
                            final int byteCount) {
        Objects.requireNonNull(data);
        return new RealAscii(data, offset, byteCount);
    }

    /**
     * @param data a byte buffer from which we will copy the remaining bytes.
     * @return a new ASCII byte string containing a copy of the remaining ASCII bytes of {@code data}.
     */
    static @NonNull Utf8 of(final @NonNull ByteBuffer data) {
        Objects.requireNonNull(data);
        final var copy = new byte[data.remaining()];
        data.get(copy);
        return new RealAscii(copy);
    }

    /**
     * Encodes {@code string} using ASCII and wraps these bytes into an ASCII byte string.
     */
    static @NonNull Utf8 encode(final @NonNull String string) {
        Objects.requireNonNull(string);
        return new RealAscii(string);
    }

    /**
     * Reads {@code byteCount} ASCII bytes from {@code in} and wraps them into an ASCII byte string.
     *
     * @throws JayoEOFException         if {@code in} has fewer than {@code byteCount} bytes to read.
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     */
    static @NonNull Utf8 read(final @NonNull InputStream in, final int byteCount) {
        Objects.requireNonNull(in);
        if (byteCount < 0) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }

        try {
            return new RealAscii(in.readNBytes(byteCount));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    @NonNull IntStream codePoints();

    @Override
    boolean isEmpty();
}
