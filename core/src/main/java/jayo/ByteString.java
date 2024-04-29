/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio) and kotlinx-io (https://github.com/Kotlin/kotlinx-io), original
 * copyrights are below
 *
 * Copyright 2017-2023 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENCE file.
 *
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo;

import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import jayo.exceptions.JayoEOFException;
import jayo.exceptions.JayoException;
import jayo.external.NonNegative;
import jayo.internal.RealByteString;
import jayo.internal.SegmentedByteString;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static jayo.internal.Base64Utils.decodeBase64ToArray;

/**
 * An immutable wrapper around a sequence of bytes providing {@link String} like functionality.
 * <p>
 * ByteString allows treating binary data as a value and passing it to other functions without worrying about data
 * modification. This class facilitates various operations on binary data, like comparison or testing for subsequence
 * inclusion.
 * <p>
 * ByteString is a good fit for untyped binary data that could not be represented as {@link String}, like hashes,
 * payload of network packets, encrypted data, etc.
 * <p>
 * <b>Immutability is guaranteed:</b> ByteString copies data on creation as well as on conversion back to
 * {@code byte[]}, thus guaranteeing that subsequent modification of source data or data returned from
 * {@link #toByteArray()} won't mutate the byte string itself.
 * @see Utf8String a UTF-8 specific implementation of {@code ByteString}
 */
public sealed interface ByteString extends Serializable, Comparable<ByteString>
        permits RealByteString, SegmentedByteString, Utf8String {
    /**
     * The empty byte string
     */
    @NonNull
    ByteString EMPTY = new RealByteString(new byte[0]);

    /**
     * @param data a sequence of bytes to be wrapped.
     * @return a new byte string containing a copy of all the bytes of {@code data}.
     */
    static @NonNull ByteString of(final byte... data) {
        return new RealByteString(data.clone());
    }

    /**
     * @param offset    the start offset (inclusive) in the {@code data} byte array.
     * @param byteCount the number of bytes to copy.
     * @return a new byte string containing a copy of {@code byteCount} bytes of {@code data} starting at
     * {@code offset}.
     * @throws IndexOutOfBoundsException if {@code offset} or {@code byteCount} is out of range of
     *                                   {@code data} indices.
     */
    static @NonNull ByteString of(final byte @NonNull [] data, final int offset, final int byteCount) {
        return new RealByteString(data, offset, byteCount);
    }

    /**
     * @param data a byte buffer from which we will copy the remaining bytes.
     * @return a new byte string containing a copy of the remaining bytes of {@code data}.
     */
    static @NonNull ByteString of(final @NonNull ByteBuffer data) {
        final var copy = new byte[data.remaining()];
        data.get(copy);
        return new RealByteString(copy);
    }

    /**
     * Encodes {@code string} using the provided {@code charset} and wraps these bytes into a byte string.
     */
    static @NonNull ByteString encode(final @NonNull String string, final @NonNull Charset charset) {
        if (charset == StandardCharsets.UTF_8) {
            return new RealByteString(string);
        }
        return new RealByteString(string.getBytes(charset));
    }

    /**
     * Reads {@code byteCount} bytes from {@code in} and wraps them into a byte string.
     *
     * @throws JayoEOFException         if {@code in} has fewer than {@code byteCount} bytes to read.
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     */
    static @NonNull ByteString read(final @NonNull InputStream in, final @NonNegative int byteCount) {
        if (byteCount < 0) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }

        try {
            return new RealByteString(in.readNBytes(byteCount));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * Decodes the Base64-encoded bytes from {@code charSequence} and wraps them into a byte string. Returns
     * {@code null} if this is not a valid Base64-encoded sequence of bytes.
     * <p>
     * The symbols for decoding are not required to be padded by the padding character {@code '='}, but it is accepted.
     *
     * @param charSequence the char sequence to decode Base64-encoded bytes from.
     */
    static @Nullable ByteString decodeBase64(final @NonNull CharSequence charSequence) {
        final var decoded = decodeBase64ToArray(charSequence);
        return (decoded != null) ? new RealByteString(decoded) : null;
    }

    /**
     * Decodes the Hex-encoded bytes from {@code charSequence} and wraps them into a byte string.
     *
     * @param charSequence the char sequence to decode Base64-encoded bytes from.
     * @throws IllegalArgumentException if {@code charSequence} is not a valid Hex char sequence.
     */
    static @NonNull ByteString decodeHex(final @NonNull CharSequence charSequence) {
        if (charSequence.length() % 2 != 0) {
            throw new IllegalArgumentException("Unexpected Hex char sequence: " + charSequence);
        }

        final var result = new byte[charSequence.length() / 2];
        for (var i = 0; i < result.length; i++) {
            final var d1 = decodeHexDigit(charSequence.charAt(i * 2)) << 4;
            final var d2 = decodeHexDigit(charSequence.charAt(i * 2 + 1));
            result[i] = (byte) (d1 + d2);
        }
        return new RealByteString(result);
    }

    /**
     * @return either a new String by decoding all the bytes from this byte string using UTF-8, or the cached one
     * available.
     */
    @NonNull
    String decodeToUtf8();

    /**
     * Constructs a new String by decoding all the bytes from this byte string using {@code charset}.
     *
     * @param charset the charset to use for decoding.
     * @return the constructed String.
     */
    @NonNull
    String decodeToString(final @NonNull Charset charset);

    /**
     * Constructs a new String by encoding all the bytes from this byte string using
     * <a href="https://www.ietf.org/rfc/rfc2045.txt">Base64</a>. In violation of the RFC, the returned string does not
     * wrap lines at 76 columns.
     * <p>
     * If the size of this byte string is not an integral multiple of 3, the result is padded with {@code '='} to an
     * integral multiple of 4 symbols.
     */
    @NonNull
    String base64();

    /**
     * Constructs a new String by encoding all the bytes from this byte string using
     * <a href="https://www.ietf.org/rfc/rfc4648.txt">URL-safe Base64</a>.
     * <p>
     * If the size of this byte string is not an integral multiple of 3, the result is padded with {@code '='} to an
     * integral multiple of 4 symbols.
     */
    @NonNull
    String base64Url();

    /**
     * Constructs a new String by encoding all the bytes from this byte string using Hex format.
     */
    @NonNull
    String hex();

    /**
     * @param digest the chosen message digest algorithm to use for hashing.
     * @return the hash of this byte string.
     */
    @NonNull
    ByteString hash(final @NonNull Digest digest);

    /**
     * @param hMac the chosen "Message Authentication Code" (MAC) algorithm to use.
     * @param key  the key to use for this MAC operation.
     * @return the MAC result of this byte string.
     * @throws IllegalArgumentException if the {@code key} is invalid
     */
    @NonNull
    ByteString hmac(final @NonNull Hmac hMac, final @NonNull ByteString key);

    /**
     * @return a byte string equal to this byte string, but with the bytes 'A' through 'Z' replaced with the
     * corresponding byte in 'a' through 'z'. Returns this byte string if it contains no bytes in 'A' through 'Z'.
     */
    @NonNull
    ByteString toAsciiLowercase();

    /**
     * @return a byte string equal to this byte string, but with the bytes 'a' through 'z' replaced with the
     * corresponding byte in 'A' through 'Z'. Returns this byte string if it contains no bytes in 'a' through 'z'.
     */
    @NonNull
    ByteString toAsciiUppercase();

    /**
     * Returns a byte string that is a subsequence of the bytes of this byte string. The substring begins with the byte
     * at {@code startIndex} and extends to the end of this byte string.
     *
     * @param startIndex the start index (inclusive) of a subsequence to copy.
     * @return the specified substring. If {@code startIndex} is 0, this byte string is returned.
     * @throws IndexOutOfBoundsException if {@code startIndex} is out of range of byte string indices.
     */
    @NonNull
    ByteString substring(final @NonNegative int startIndex);

    /**
     * Returns a byte string that is a subsequence of the bytes of this byte string. The substring begins with the byte
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
    @NonNull
    ByteString substring(final @NonNegative int startIndex, final @NonNegative int endIndex);

    /**
     * @param index the index of the byte to return.
     * @return the byte at {@code index}.
     * @throws IndexOutOfBoundsException if {@code index} is out of range of byte string indices.
     */
    byte getByte(final @NonNegative int index);

    /**
     * @return the number of bytes in this ByteString.
     */
    @NonNegative
    int byteSize();

    /**
     * @return {@code true} if this byte string is empty.
     */
    boolean isEmpty();

    /**
     * @return a new byte array containing a copy of all the bytes in this byte string.
     */
    byte @NonNull [] toByteArray();

    /**
     * @return a new byte buffer containing a copy of all the bytes in this byte string.
     */
    @NonNull
    ByteBuffer asByteBuffer();

    /**
     * Writes all the bytes of this byte string to {@code out}.
     */
    void write(final @NonNull OutputStream out);

    /**
     * @return true if the bytes of this byte string in {@code [offset..offset+byteCount)} equal the bytes of
     * {@code other} byte string in {@code [otherOffset..otherOffset+byteCount)}. Returns false if either range is out
     * of bounds.
     */
    boolean rangeEquals(final @NonNegative int offset,
                        final @NonNull ByteString other,
                        final @NonNegative int otherOffset,
                        final @NonNegative int byteCount);

    /**
     * @return true if the bytes of this byte string in {@code [offset..offset+byteCount)} equal the bytes of
     * {@code other} byte array in {@code [otherOffset..otherOffset+byteCount)}. Returns false if either range is out
     * of bounds.
     */
    boolean rangeEquals(final @NonNegative int offset,
                        final byte @NonNull [] other,
                        final @NonNegative int otherOffset,
                        final @NonNegative int byteCount);

    /**
     * Copies the bytes of this byte string in {@code [offset..offset+byteCount)} to {@code target} byte array in
     * {@code [targetOffset..targetOffset+byteCount)}.
     *
     * @throws IndexOutOfBoundsException if either range is out of bounds.
     */
    void copyInto(final @NonNegative int offset,
                  final byte @NonNull [] target,
                  final @NonNegative int targetOffset,
                  final @NonNegative int byteCount);

    /**
     * @param prefix the prefix to check for.
     * @return true if this byte string starts with the {@code prefix}.
     * @implNote Behavior of this method is compatible with {@link String#startsWith(String)}.
     */
    boolean startsWith(final @NonNull ByteString prefix);

    /**
     * @param prefix the prefix to check for.
     * @return true if this byte string starts with the {@code prefix}.
     * @implNote Behavior of this method is compatible with {@link String#startsWith(String)}.
     */
    boolean startsWith(final byte @NonNull [] prefix);

    /**
     * @param suffix the suffix to check for.
     * @return true if this byte string ends with the {@code suffix}.
     * @implNote Behavior of this method is compatible with {@link String#endsWith(String)}.
     */
    boolean endsWith(final @NonNull ByteString suffix);

    /**
     * @param suffix the suffix to check for.
     * @return true if this byte string ends with the {@code suffix}.
     * @implNote Behavior of this method is compatible with {@link String#endsWith(String)}.
     */
    boolean endsWith(final byte @NonNull [] suffix);

    /**
     * @param other the sequence of bytes to find within this byte string.
     * @return the index of {@code other} first occurrence in this byte string, or {@code -1} if it doesn't contain
     * {@code other}.
     * @implNote Behavior of this method is compatible with {@link String#indexOf(String)}.
     */
    int indexOf(final @NonNull ByteString other);

    /**
     * @param other      the sequence of bytes to find within this byte string.
     * @param startIndex the start of the range (inclusive) to find {@code other}.
     * @return the index of {@code other} first occurrence in this byte string at or after {@code startIndex}, or
     * {@code -1} if it doesn't contain {@code other}.
     * @throws IllegalArgumentException if {@code startIndex} is negative.
     * @implNote Behavior of this method is compatible with {@link String#indexOf(String, int)}.
     */
    int indexOf(final @NonNull ByteString other, final @NonNegative int startIndex);

    /**
     * @param other the sequence of bytes to find within this byte string.
     * @return the index of {@code other} first occurrence in this byte string, or {@code -1} if it doesn't contain
     * {@code other}.
     * @implNote Behavior of this method is compatible with {@link String#indexOf(String)}.
     */
    int indexOf(final byte @NonNull [] other);

    /**
     * @param other      the sequence of bytes to find within this byte string.
     * @param startIndex the start of the range (inclusive) to find {@code other}.
     * @return the index of {@code other} first occurrence in this byte string at or after {@code startIndex}, or
     * {@code -1} if it doesn't contain {@code other}.
     * @throws IllegalArgumentException if {@code startIndex} is negative.
     * @implNote Behavior of this method is compatible with {@link String#indexOf(String, int)}.
     */
    int indexOf(final byte @NonNull [] other, final @NonNegative int startIndex);

    /**
     * @param other the sequence of bytes to find within this byte string.
     * @return the index of {@code other} last occurrence in this byte string, or {@code -1} if it doesn't contain
     * {@code other}.
     * @implNote Behavior of this method is compatible with {@link String#lastIndexOf(String)}.
     */
    int lastIndexOf(final @NonNull ByteString other);

    /**
     * @param other      the sequence of bytes to find within this byte string.
     * @param startIndex the start of the range (inclusive) to find {@code other}.
     * @return the index of {@code other} last occurrence in this byte string at or after {@code startIndex}, or
     * {@code -1} if it doesn't contain {@code other}.
     * @throws IllegalArgumentException if {@code startIndex} is negative.
     * @implNote Behavior of this method is compatible with {@link String#lastIndexOf(String, int)}
     */
    int lastIndexOf(final @NonNull ByteString other, final @NonNegative int startIndex);

    /**
     * @param other the sequence of bytes to find within this byte string.
     * @return the index of {@code other} last occurrence in this byte string, or {@code -1} if it doesn't contain
     * {@code other}.
     * @implNote Behavior of this method is compatible with {@link String#lastIndexOf(String)}.
     */
    int lastIndexOf(final byte @NonNull [] other);

    /**
     * @param other      the sequence of bytes to find within this byte string.
     * @param startIndex the start of the range (inclusive) to find {@code other}.
     * @return the index of {@code other} last occurrence in this byte string at or after {@code startIndex}, or
     * {@code -1} if it doesn't contain {@code other}.
     * @throws IllegalArgumentException if {@code startIndex} is negative.
     * @implNote Behavior of this method is compatible with {@link String#lastIndexOf(String, int)}
     */
    int lastIndexOf(final byte @NonNull [] other, final @NonNegative int startIndex);

    /**
     * Returns a string representation of this byte string. A string representation consists of {@code size} and a
     * hexadecimal-encoded string of the bytes wrapped by this byte string.
     * <p>
     * The string representation has the following format {@code ByteString(size=3 hex=ABCDEF)}, for empty strings it's
     * always {@code ByteString(size=0)}.
     * <p>
     * Note that a string representation includes the whole byte string content encoded. Due to limitations exposed for
     * the maximum string length, an attempt to return a string representation of too long byte string may fail.
     */
    @Override
    @NonNull
    String toString();

    private static int decodeHexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        } else if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        } else {
            throw new IllegalArgumentException("Unexpected hex digit: " + c);
        }
    }
}
