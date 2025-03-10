/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.bytestring;

import jayo.JayoEOFException;
import jayo.JayoException;
import jayo.internal.RealUtf8;
import jayo.internal.SegmentedUtf8;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * A specific {@link ByteString} that contains UTF-8 encoded bytes, and provides additional features like
 * {@link #length()}, {@link #codePoints()}.
 *
 * @see ByteString
 * @see Ascii
 */
public sealed interface Utf8 extends ByteString permits Ascii, RealUtf8, SegmentedUtf8 {
    /**
     * The empty UTF-8 byte string
     */
    @NonNull
    Utf8 EMPTY = new RealUtf8(new byte[0], true);

    /**
     * @param data a sequence of bytes to be wrapped.
     * @return a new UTF-8 byte string containing a copy of all the UTF-8 bytes of {@code data}.
     */
    static @NonNull Utf8 of(final byte @NonNull ... data) {
        Objects.requireNonNull(data);
        return new RealUtf8(data.clone(), false);
    }

    /**
     * @param offset    the start offset (inclusive) in the {@code data} byte array.
     * @param byteCount the number of bytes to copy.
     * @return a new UTF-8 byte string containing a copy of {@code byteCount} UTF-8 bytes of {@code data} starting at
     * {@code offset}.
     * @throws IndexOutOfBoundsException if {@code offset} or {@code byteCount} is out of range of {@code data} indices.
     */
    static @NonNull Utf8 of(final byte @NonNull [] data,
                            final int offset,
                            final int byteCount) {
        return new RealUtf8(data, offset, byteCount);
    }

    /**
     * @param data a byte buffer from which we will copy the remaining bytes.
     * @return a new UTF-8 byte string containing a copy of the remaining UTF-8 bytes of {@code data}.
     */
    static @NonNull Utf8 of(final @NonNull ByteBuffer data) {
        Objects.requireNonNull(data);
        final var copy = new byte[data.remaining()];
        data.get(copy);
        return new RealUtf8(copy, false);
    }

    /**
     * Encodes {@code string} using UTF-8 and wraps these bytes into a UTF-8 byte string.
     */
    static @NonNull Utf8 encode(final @NonNull String string) {
        Objects.requireNonNull(string);
        return new RealUtf8(string);
    }

    /**
     * Reads {@code byteCount} UTF-8 bytes from {@code in} and wraps them into a UTF-8 byte string.
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
            return new RealUtf8(in.readNBytes(byteCount), false);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * @return the length of this UTF-8 bytes sequence. The length is equal to the number of
     * {@linkplain java.lang.Character Unicode code units} in this UTF-8 bytes sequence.
     * @implNote Result of this method is the same as {@link String#length()} you would get by calling
     * {@code decodeToString().length()}.
     */
    int length();

    /**
     * @return a stream of Unicode code point values from this UTF-8 bytes sequence.
     */
    @NonNull
    IntStream codePoints();

    /**
     * @return a UTF-8 byte string equal to this UTF-8 byte string, but with the bytes 'A' through 'Z' replaced with the
     * corresponding byte in 'a' through 'z'. Returns this UTF-8 byte string if it contains no bytes in 'A' through 'Z'.
     */
    @Override
    @NonNull
    Utf8 toAsciiLowercase();

    /**
     * @return a UTF-8 byte string equal to this UTF-8 byte string, but with the bytes 'a' through 'z' replaced with the
     * corresponding byte in 'A' through 'Z'. Returns this UTF-8 byte string if it contains no bytes in 'a' through 'z'.
     */
    @Override
    @NonNull
    Utf8 toAsciiUppercase();

    /**
     * Returns a UTF-8 byte string that is a subsequence of the bytes of this UTF-8 byte string. The substring begins
     * with the byte at {@code startIndex} and extends to the end of this byte string.
     *
     * @param startIndex the start index (inclusive) of a subsequence to copy.
     * @return the specified substring. If {@code startIndex} is 0, this byte string is returned.
     * @throws IndexOutOfBoundsException if {@code startIndex} is out of range of byte string indices.
     */
    @Override
    @NonNull
    Utf8 substring(final int startIndex);

    /**
     * Returns a UTF-8 byte string that is a subsequence of the bytes of this UTF-8 byte string. The substring begins
     * with the byte at {@code startIndex} and ends at {@code endIndex}.
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
    Utf8 substring(final int startIndex, final int endIndex);

    /**
     * @return either a new String by decoding all the bytes from this byte string using UTF-8, or the cached one if
     * available.The {@link String#length()} of the obtained string will be the {@link #length()} of this UTF-8 byte
     * string.
     */
    @Override
    @NonNull
    String toString();

    /**
     * @param prefix the prefix to check for.
     * @return true if this UTF-8 byte string starts with the {@code prefix}.
     * @implNote Behavior of this method is compatible with {@link String#startsWith(String)}.
     */
    default boolean startsWith(final @NonNull String prefix) {
        Objects.requireNonNull(prefix);
        final var codePointsIterator = codePoints().iterator();
        return prefix.codePoints()
                .allMatch(c -> codePointsIterator.hasNext() && c == codePointsIterator.nextInt());
    }

    /**
     * @param suffix the suffix to check for.
     * @return true if this UTF-8 byte string ends with the {@code suffix}.
     * @implNote Behavior of this method is compatible with {@link String#endsWith(String)}.
     */
    default boolean endsWith(final @NonNull String suffix) {
        Objects.requireNonNull(suffix);
        return endsWith(suffix.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param other the string to find within this UTF-8 byte string.
     * @return the index of {@code other} first occurrence in this byte string, or {@code -1} if it doesn't contain
     * {@code other}.
     * @implNote Behavior of this method is compatible with {@link String#indexOf(String)}.
     */
    default int indexOf(final @NonNull String other) {
        Objects.requireNonNull(other);
        return indexOf(other.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param other      the string to find within this UTF-8 byte string.
     * @param startIndex the start of the range (inclusive) to find {@code other}.
     * @return the index of {@code other} first occurrence in this byte string at or after {@code startIndex}, or
     * {@code -1} if it doesn't contain {@code other}.
     * @throws IllegalArgumentException if {@code startIndex} is negative.
     * @implNote Behavior of this method is compatible with {@link String#indexOf(String, int)}.
     */
    default int indexOf(final @NonNull String other, final int startIndex) {
        Objects.requireNonNull(other);
        return indexOf(other.getBytes(StandardCharsets.UTF_8), startIndex);
    }

    /**
     * @param other the string to find within this UTF-8 byte string.
     * @return the index of {@code other} last occurrence in this byte string, or {@code -1} if it doesn't contain
     * {@code other}.
     * @implNote Behavior of this method is compatible with {@link String#lastIndexOf(String)}.
     */
    default int lastIndexOf(final @NonNull String other) {
        Objects.requireNonNull(other);
        return lastIndexOf(other.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param other      the string to find within this UTF-8 byte string.
     * @param startIndex the start of the range (inclusive) to find {@code other}.
     * @return the index of {@code other} last occurrence in this byte string at or after {@code startIndex}, or
     * {@code -1} if it doesn't contain {@code other}.
     * @throws IllegalArgumentException if {@code startIndex} is negative.
     * @implNote Behavior of this method is compatible with {@link String#lastIndexOf(String, int)}
     */
    default int lastIndexOf(final @NonNull String other, final int startIndex) {
        Objects.requireNonNull(other);
        return lastIndexOf(other.getBytes(StandardCharsets.UTF_8), startIndex);
    }
}
