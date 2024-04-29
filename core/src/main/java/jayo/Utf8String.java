/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo;

import jayo.exceptions.JayoEOFException;
import jayo.exceptions.JayoException;
import jayo.external.NonNegative;
import jayo.internal.RealUtf8String;
import jayo.internal.SegmentedUtf8String;
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
 */
public sealed interface Utf8String extends ByteString permits RealUtf8String, SegmentedUtf8String {
    /**
     * The empty UTF-8 byte string
     */
    @NonNull
    Utf8String EMPTY = new RealUtf8String(new byte[0], true);

    /**
     * @param data a sequence of bytes to be wrapped.
     * @return a new byte string containing a copy of all the bytes of {@code data}.
     */
    static @NonNull Utf8String ofUtf8(final byte... data) {
        return new RealUtf8String(data.clone(), false);
    }

    /**
     * @param offset    the start offset (inclusive) in the {@code data} byte array.
     * @param byteCount the number of bytes to copy.
     * @return a new byte string containing a copy of {@code byteCount} bytes of {@code data} starting at
     * {@code offset}.
     * @throws IndexOutOfBoundsException if {@code offset} or {@code byteCount} is out of range of
     *                                   {@code data} indices.
     */
    static @NonNull Utf8String ofUtf8(final byte @NonNull [] data, final int offset, final int byteCount) {
        return new RealUtf8String(data, offset, byteCount);
    }

    /**
     * @param data a byte buffer from which we will copy the remaining bytes.
     * @return a new byte string containing a copy of the remaining bytes of {@code data}.
     */
    static @NonNull Utf8String ofUtf8(final @NonNull ByteBuffer data) {
        final var copy = new byte[data.remaining()];
        data.get(copy);
        return new RealUtf8String(copy, false);
    }

    /**
     * Reads {@code byteCount} bytes from {@code in} and wraps them into a byte string.
     *
     * @throws JayoEOFException         if {@code in} has fewer than {@code byteCount} bytes to read.
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     */
    static @NonNull Utf8String readUtf8(final @NonNull InputStream in, final @NonNegative int byteCount) {
        if (byteCount < 0) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }

        try {
            return new RealUtf8String(in.readNBytes(byteCount), false);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * Encodes {@code string} using UTF-8 and wraps these bytes into a byte string.
     */
    static @NonNull Utf8String encodeUtf8(final @NonNull String string) {
        return new RealUtf8String(string);
    }

    /**
     * @return the length of this UTF-8 bytes sequence. The length is equal to the number of
     * {@linkplain java.lang.Character Unicode code units} in this UTF-8 bytes sequence.
     * @implNote Result of this method is the same as {@link String#length()} you would get by calling
     * {@code decodeToUtf8().length()}.
     */
    @NonNegative
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
    Utf8String toAsciiLowercase();

    /**
     * @return a UTF-8 byte string equal to this UTF-8 byte string, but with the bytes 'a' through 'z' replaced with the
     * corresponding byte in 'A' through 'Z'. Returns this UTF-8 byte string if it contains no bytes in 'a' through 'z'.
     */
    @Override
    @NonNull
    Utf8String toAsciiUppercase();

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
    Utf8String substring(final @NonNegative int startIndex);

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
    Utf8String substring(final @NonNegative int startIndex, final @NonNegative int endIndex);

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
    default int indexOf(final @NonNull String other, final @NonNegative int startIndex) {
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
    default int lastIndexOf(final @NonNull String other, final @NonNegative int startIndex) {
        Objects.requireNonNull(other);
        return lastIndexOf(other.getBytes(StandardCharsets.UTF_8), startIndex);
    }
}
