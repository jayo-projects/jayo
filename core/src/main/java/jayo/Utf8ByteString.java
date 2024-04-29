/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo;

import jayo.exceptions.JayoEOFException;
import jayo.exceptions.JayoException;
import jayo.external.NonNegative;
import jayo.internal.RealUtf8ByteString;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * A specific type of {@link ByteString} that contains exclusively UTF-8 encoded bytes, it also implements
 * {@link CharSequence}.
 *
 * @see ByteString
 */
public sealed interface Utf8ByteString extends ByteString, CharSequence permits RealUtf8ByteString {
    /**
     * The empty UTF-8 byte string
     */
    @NonNull
    Utf8ByteString EMPTY = new RealUtf8ByteString(new byte[0], true);

    /**
     * @param data a sequence of bytes to be wrapped.
     * @return a new byte string containing a copy of all the bytes of {@code data}.
     */
    static @NonNull Utf8ByteString ofUtf8(final byte... data) {
        return new RealUtf8ByteString(data.clone(), false);
    }

    /**
     * @param offset    the start offset (inclusive) in the {@code data} byte array.
     * @param byteCount the number of bytes to copy.
     * @return a new byte string containing a copy of {@code byteCount} bytes of {@code data} starting at
     * {@code offset}.
     * @throws IndexOutOfBoundsException if {@code offset} or {@code byteCount} is out of range of
     *                                   {@code data} indices.
     */
    static @NonNull Utf8ByteString ofUtf8(final byte @NonNull [] data, final int offset, final int byteCount) {
        return new RealUtf8ByteString(data, offset, byteCount);
    }

    /**
     * @param data a byte buffer from which we will copy the remaining bytes.
     * @return a new byte string containing a copy of the remaining bytes of {@code data}.
     */
    static @NonNull Utf8ByteString ofUtf8(final @NonNull ByteBuffer data) {
        final var copy = new byte[data.remaining()];
        data.get(copy);
        return new RealUtf8ByteString(copy, false);
    }

    /**
     * Reads {@code byteCount} bytes from {@code in} and wraps them into a byte string.
     *
     * @throws JayoEOFException         if {@code in} has fewer than {@code byteCount} bytes to read.
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     */
    static @NonNull Utf8ByteString readUtf8(final @NonNull InputStream in, final @NonNegative int byteCount) {
        if (byteCount < 0) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }

        try {
            return new RealUtf8ByteString(in.readNBytes(byteCount), false);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * Encodes {@code string} using UTF-8 and wraps these bytes into a byte string.
     */
    static @NonNull Utf8ByteString encodeUtf8(final @NonNull String string) {
        return new RealUtf8ByteString(string);
    }

    /**
     * @return a string containing the characters in this sequence in the same order as this sequence. The length of the
     * string will be the length of this sequence.
     */
    @Override
    @NonNull
    String toString();

    @Override
    boolean isEmpty();
}
