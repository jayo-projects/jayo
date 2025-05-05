/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.bytestring;

import jayo.JayoEOFException;
import jayo.JayoException;
import jayo.internal.RealAscii;
import jayo.internal.SegmentedAscii;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * A specific {@link ByteString} that contains ASCII encoded bytes, and is also a {@link CharSequence}.
 *
 * @see ByteString
 * @see Utf8
 */
public sealed interface Ascii extends Utf8, CharSequence permits RealAscii, SegmentedAscii {
    /**
     * The empty ASCII byte string
     */
    @NonNull
    Ascii EMPTY = new RealAscii(new byte[0]);

    /**
     * @param data a sequence of bytes to be wrapped.
     * @return a new ASCII byte string containing a copy of all the ASCII bytes of {@code data}.
     */
    static @NonNull Ascii of(final byte @NonNull ... data) {
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
    static @NonNull Ascii of(final byte @NonNull [] data,
                             final int offset,
                             final int byteCount) {
        Objects.requireNonNull(data);
        return new RealAscii(data, offset, byteCount);
    }

    /**
     * @param data a byte buffer from which we will copy the remaining bytes.
     * @return a new ASCII byte string containing a copy of the {@linkplain ByteBuffer#remaining() remaining} ASCII
     * bytes of {@code data}.
     */
    static @NonNull Ascii of(final @NonNull ByteBuffer data) {
        Objects.requireNonNull(data);
        final var copy = new byte[data.remaining()];
        data.get(copy);
        return new RealAscii(copy);
    }

    /**
     * Encodes {@code string} using ASCII and wraps these bytes into an ASCII byte string.
     */
    static @NonNull Ascii encode(final @NonNull String string) {
        Objects.requireNonNull(string);
        return new RealAscii(string);
    }

    /**
     * Reads {@code byteCount} ASCII bytes from {@code in} stream and wraps them into an ASCII byte string.
     *
     * @throws JayoEOFException         if {@code in} has fewer than {@code byteCount} bytes to read.
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     */
    static @NonNull Ascii read(final @NonNull InputStream in, final int byteCount) {
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

    /**
     * @return an ASCII byte string equal to this ASCII byte string, but with the bytes 'A' through 'Z' replaced with
     * the corresponding byte in 'a' through 'z'. Returns this ASCII byte string if it contains no bytes in the 'A'
     * through 'Z' range.
     */
    @Override
    @NonNull
    Ascii toAsciiLowercase();

    /**
     * @return an ASCII byte string equal to this ASCII byte string, but with the bytes 'a' through 'z' replaced with
     * the corresponding byte in 'A' through 'Z'. Returns this ASCII byte string if it contains no bytes in the 'a'
     * through 'z' range.
     */
    @Override
    @NonNull
    Ascii toAsciiUppercase();

    /**
     * Returns an ASCII byte string that is a subsequence of this ASCII byte string. The substring begins with the byte
     * at {@code startIndex} and extends to the end of this byte string.
     *
     * @param startIndex the start index (inclusive) of a subsequence to copy.
     * @return the specified substring. If {@code startIndex} is 0, this byte string is returned.
     * @throws IndexOutOfBoundsException if {@code startIndex} is out of range of byte string indices.
     */
    @Override
    @NonNull
    Ascii substring(final int startIndex);

    /**
     * Returns an ASCII byte string that is a subsequence of this ASCII byte string. The substring begins with the byte
     * at {@code startIndex} and ends at {@code endIndex}.
     *
     * @param startIndex the start index (inclusive) of a subsequence to copy.
     * @param endIndex   the end index (exclusive) of a subsequence to copy.
     * @return the specified substring. If {@code startIndex} is 0 and {@code endIndex} is the size of this byte string,
     * this byte string is returned.
     * @throws IndexOutOfBoundsException if {@code startIndex} or {@code endIndex} is out of range of byte string
     *                                   indices.
     * @throws IllegalArgumentException  if {@code startIndex > endIndex}.
     */
    @Override
    @NonNull
    Ascii substring(final int startIndex, final int endIndex);

    @Override
    @NonNull
    IntStream codePoints();

    @Override
    boolean isEmpty();

    @Override
    default @NonNull IntStream chars() {
        return codePoints(); // in ASCII encoding, chars are the same as code points (no surrogates).
    }
}
