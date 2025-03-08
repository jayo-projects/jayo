/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
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

package jayo.internal;

import jayo.bytestring.ByteString;
import jayo.JayoException;
import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

import static jayo.internal.UnsafeUtils.*;
import static jayo.internal.Utils.HEX_DIGIT_CHARS;
import static jayo.internal.Utils.arrayRangeEquals;
import static jayo.tools.JayoUtils.checkOffsetAndCount;

public sealed class BaseByteString implements ByteString permits RealUtf8, SegmentedByteString {
    @Serial
    private static final long serialVersionUID = 43L;
    static final boolean ALLOW_COMPACT_STRING = UNSAFE_AVAILABLE && SUPPORT_COMPACT_STRING;

    final byte @NonNull [] data;
    transient int hashCode = 0; // Lazily computed; 0 if unknown.
    transient @Nullable String utf8; // Lazily computed.

    // these 2 fields are only used in UTF-8 byte strings.
    boolean isAscii = false; // Lazily computed, false can mean non-ascii or unknown (=not yet scanned).
    transient int length = -1; // Lazily computed.

    BaseByteString(final byte @NonNull [] data) {
        this.data = Objects.requireNonNull(data);
        utf8 = null;
    }

    BaseByteString(final byte @NonNull [] data,
                   final int offset,
                   final int byteCount) {
        Objects.requireNonNull(data);
        checkOffsetAndCount(data.length, offset, byteCount);
        this.data = Arrays.copyOfRange(data, offset, offset + byteCount);
        utf8 = null;
    }

    @Override
    public @NonNull String decodeToString() {
        var utf8String = utf8;
        if (utf8String == null) {
            // We don't care if we double-allocate in racy code.
            utf8String = new String(internalArray(), StandardCharsets.UTF_8);
            utf8 = utf8String;
        }
        return utf8String;
    }

    @Override
    public @NonNull String decodeToString(final @NonNull Charset charset) {
        Objects.requireNonNull(charset);

        if (charset.equals(StandardCharsets.ISO_8859_1) && ALLOW_COMPACT_STRING) {
            return noCopyStringFromLatin1Bytes(internalArray());
        }

        return new String(internalArray(), charset);
    }

    @Override
    public @NonNull String base64() {
        return Base64Utils.encode(data);
    }

    @Override
    public @NonNull String base64Url() {
        return Base64Utils.encodeUrl(data);
    }

    @Override
    public final @NonNull String hex() {
        return hexStatic(internalArray());
    }

    static @NonNull String hexStatic(final byte @NonNull [] data) {
        final var result = new char[data.length * 2];
        var c = 0;
        for (final var b : data) {
            result[c++] = HEX_DIGIT_CHARS[b >> 4 & 0xf];
            result[c++] = HEX_DIGIT_CHARS[b & 0xf];
        }
        return new String(result);
    }

    @Override
    public @NonNull ByteString hash(final @NonNull Digest digest) {
        return new RealByteString(messageDigest(digest).digest(data));
    }

    static @NonNull MessageDigest messageDigest(final @NonNull Digest digest) {
        Objects.requireNonNull(digest);

        try {
            return MessageDigest.getInstance(digest.toString());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm is not available : " + digest, e);
        }
    }

    @Override
    public @NonNull ByteString hmac(final @NonNull Hmac hMac, final @NonNull ByteString key) {
        final var javaMac = mac(hMac, key);
        return new RealByteString(javaMac.doFinal(data));
    }

    static javax.crypto.@NonNull Mac mac(final @NonNull Hmac hMac, final ByteString key) {
        Objects.requireNonNull(hMac);
        Objects.requireNonNull(key);

        final javax.crypto.Mac javaMac;
        try {
            javaMac = javax.crypto.Mac.getInstance(hMac.toString());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm is not available : " + hMac, e);
        }
        try {
            javaMac.init(new SecretKeySpec(Utils.internalArray(key), hMac.toString()));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("InvalidKeyException was fired with the provided ByteString key", e);
        }
        return javaMac;
    }

    @Override
    public @NonNull ByteString toAsciiLowercase() {
        final var lowercase = toAsciiLowercaseBytes(data);
        return (lowercase != null) ? new RealByteString(lowercase) : this;
    }

    static byte @Nullable [] toAsciiLowercaseBytes(final byte @NonNull [] data) {
        // Search for an uppercase character. If we don't find one, return this.
        var i = 0;
        while (i < data.length) {
            var c = data[i];
            if (c < (byte) ((int) 'A') || c > (byte) ((int) 'Z')) {
                i++;
                continue;
            }

            // This string needs to be lowercased. Create and return a new byte string.
            final var lowercase = data.clone();
            lowercase[i++] = (byte) (c - ('A' - 'a'));
            while (i < lowercase.length) {
                c = lowercase[i];
                if (c < (byte) ((int) 'A') || c > (byte) ((int) 'Z')) {
                    i++;
                    continue;
                }
                lowercase[i] = (byte) (c - ('A' - 'a'));
                i++;
            }
            return lowercase;
        }
        return null;
    }

    @Override
    public @NonNull ByteString toAsciiUppercase() {
        final var uppercase = toAsciiUppercaseBytes(data);
        return (uppercase != null) ? new RealByteString(uppercase) : this;
    }

    static byte @Nullable [] toAsciiUppercaseBytes(final byte @NonNull [] data) {
        // Search for a lowercase character. If we don't find one, return this.
        var i = 0;
        while (i < data.length) {
            var c = data[i];
            if (c < (byte) ((int) 'a') || c > (byte) ((int) 'z')) {
                i++;
                continue;
            }

            // This string needs to be uppercased. Create and return a new byte string.
            final var uppercase = data.clone();
            uppercase[i++] = (byte) (c - ('a' - 'A'));
            while (i < uppercase.length) {
                c = uppercase[i];
                if (c < (byte) ((int) 'a') || c > (byte) ((int) 'z')) {
                    i++;
                    continue;
                }
                uppercase[i] = (byte) (c - ('a' - 'A'));
                i++;
            }
            return uppercase;
        }
        return null;
    }

    @Override
    public @NonNull ByteString substring(final int startIndex) {
        return substring(startIndex, byteSize());
    }

    @Override
    public @NonNull ByteString substring(final int startIndex, final int endIndex) {
        checkSubstringParameters(startIndex, endIndex, byteSize());
        if (startIndex == 0 && endIndex == data.length) {
            return this;
        }
        return new RealByteString(Arrays.copyOfRange(data, startIndex, endIndex));
    }

    static void checkSubstringParameters(final int startIndex,
                                         final int endIndex,
                                         final long byteSize) {
        if (startIndex < 0) {
            throw new IllegalArgumentException("beginIndex < 0: " + startIndex);
        }
        if (endIndex > byteSize) {
            throw new IllegalArgumentException("endIndex > length(" + byteSize + ")");
        }
        if (endIndex < startIndex) {
            throw new IllegalArgumentException("endIndex < beginIndex");
        }
    }

    @Override
    public byte getByte(final int index) {
        return data[index];
    }

    @Override
    public int byteSize() {
        return data.length;
    }

    @Override
    public final boolean isEmpty() {
        return byteSize() == 0;
    }

    @Override
    public byte @NonNull [] toByteArray() {
        return data.clone();
    }

    @Override
    public final @NonNull ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(toByteArray());
    }

    @Override
    public void write(final @NonNull OutputStream out) {
        try {
            Objects.requireNonNull(out).write(data);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * Writes the contents of this byte string to {@code buffer}.
     */
    void write(final @NonNull RealBuffer buffer,
               final int offset,
               final int byteCount) {
        Objects.requireNonNull(buffer).write(data, offset, byteCount);
    }

    @Override
    public boolean rangeEquals(final int offset,
                               final @NonNull ByteString other,
                               final int otherOffset,
                               final int byteCount) {
        return Objects.requireNonNull(other).rangeEquals(otherOffset, this.data, offset, byteCount);
    }

    @Override
    public boolean rangeEquals(final int offset,
                               final byte @NonNull [] other,
                               final int otherOffset,
                               final int byteCount) {
        return rangeEqualsStatic(data, offset, other, otherOffset, byteCount);
    }

    static boolean rangeEqualsStatic(final byte @NonNull [] data,
                                     final int offset,
                                     final byte @NonNull [] other,
                                     final int otherOffset,
                                     final int byteCount) {
        Objects.requireNonNull(other);
        return (
                offset >= 0 && offset <= data.length - byteCount &&
                        otherOffset >= 0 && otherOffset <= other.length - byteCount &&
                        arrayRangeEquals(data, offset, other, otherOffset, byteCount)
        );
    }

    @Override
    public void copyInto(final int offset,
                         final byte @NonNull [] target,
                         final int targetOffset,
                         final int byteCount) {
        Objects.requireNonNull(target);
        System.arraycopy(data, offset, target, targetOffset, byteCount);
    }

    @Override
    public final boolean startsWith(final @NonNull ByteString prefix) {
        return rangeEquals(0, prefix, 0, prefix.byteSize());
    }

    @Override
    public final boolean startsWith(final byte @NonNull [] prefix) {
        return rangeEquals(0, prefix, 0, prefix.length);
    }

    @Override
    public final boolean endsWith(final @NonNull ByteString suffix) {
        return rangeEquals(byteSize() - suffix.byteSize(), suffix, 0, suffix.byteSize());
    }

    @Override
    public final boolean endsWith(final byte @NonNull [] suffix) {
        return rangeEquals(byteSize() - suffix.length, suffix, 0, suffix.length);
    }

    @Override
    public final int indexOf(final @NonNull ByteString other) {
        return indexOf(other, 0);
    }

    @Override
    public final int indexOf(final @NonNull ByteString other, final int startIndex) {
        return indexOf(Utils.internalArray(other), startIndex);
    }

    @Override
    public final int indexOf(final byte @NonNull [] other) {
        return indexOf(other, 0);
    }

    @Override
    public int indexOf(final byte @NonNull [] other, final int startIndex) {
        return indexOfStatic(data, other, startIndex);
    }

    static int indexOfStatic(final byte @NonNull [] data,
                             final byte @NonNull [] other,
                             final int startIndex) {
        Objects.requireNonNull(other);
        final var limit = data.length - other.length;
        for (var i = Math.max(startIndex, 0); i <= limit; i++) {
            if (arrayRangeEquals(data, i, other, 0, other.length)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public final int lastIndexOf(final @NonNull ByteString other) {
        return lastIndexOf(other, byteSize());
    }

    @Override
    public final int lastIndexOf(final @NonNull ByteString other, final int startIndex) {
        return lastIndexOf(Utils.internalArray(other), startIndex);
    }

    @Override
    public final int lastIndexOf(final byte @NonNull [] other) {
        return lastIndexOf(other, byteSize());
    }

    @Override
    public int lastIndexOf(final byte @NonNull [] other, final int startIndex) {
        return lastIndexOfStatic(data, other, startIndex);
    }

    static int lastIndexOfStatic(final byte @NonNull [] data,
                                 final byte @NonNull [] other,
                                 final int startIndex) {
        Objects.requireNonNull(other);
        final var limit = data.length - other.length;
        for (var i = Math.min(startIndex, limit); i >= 0; i--) {
            if (arrayRangeEquals(data, i, other, 0, other.length)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean equals(final @Nullable Object other) {
        if (other == this) {
            return true;
        }
        return equalsStatic(data, other);
    }

    static boolean equalsStatic(final byte @NonNull [] data, final @Nullable Object other) {
        if (!(other instanceof ByteString _other)) {
            return false;
        }
        return _other.byteSize() == data.length && _other.rangeEquals(0, data, 0, data.length);
    }

    @Override
    public int hashCode() {
        final var result = hashCode;
        if (result != 0) {
            return result;
        }
        hashCode = Arrays.hashCode(data);
        return hashCode;
    }

    @Override
    public final int compareTo(final @NonNull ByteString other) {
        return compareToStatic(this, other);
    }

    static int compareToStatic(final @NonNull ByteString first, final @NonNull ByteString other) {
        assert first != null;
        Objects.requireNonNull(other);

        final var sizeA = first.byteSize();
        final var sizeB = other.byteSize();
        var i = 0;
        final var size = Math.min(sizeA, sizeB);
        while (i < size) {
            final var byteA = first.getByte(i) & 0xff;
            final var byteB = other.getByte(i) & 0xff;
            if (byteA == byteB) {
                i++;
                continue;
            }
            return (byteA < byteB) ? -1 : 1;
        }
        if (sizeA == sizeB) {
            return 0;
        }
        return (sizeA < sizeB) ? -1 : 1;
    }

    @Override
    public @NonNull String toString() {
        throw new UnsupportedOperationException();
    }

    byte @NonNull [] internalArray() {
        return data;
    }

    // region native-jvm-serialization

    @Serial
    private void readObject(final @NonNull ObjectInputStream in) throws IOException {
        final var dataLength = in.readInt();
        final var bytes = in.readNBytes(dataLength);
        final var isAscii = in.readBoolean();
        final var length = in.readInt();
        final Field dataField;
        final Field isAsciiField;
        final Field lengthField;
        try {
            dataField = BaseByteString.class.getDeclaredField("data");
            isAsciiField = BaseByteString.class.getDeclaredField("isAscii");
            lengthField = BaseByteString.class.getDeclaredField("length");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("BaseByteString should contain 'data', 'isAscii' and 'length' fields", e);
        }
        dataField.setAccessible(true);
        isAsciiField.setAccessible(true);
        lengthField.setAccessible(true);
        try {
            dataField.set(this, bytes);
            isAsciiField.set(this, isAscii);
            lengthField.set(this, length);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("It should be possible to set BaseByteString's 'data', 'isAscii' and " +
                    "'length' fields", e);
        }
    }

    @Serial
    private void writeObject(final @NonNull ObjectOutputStream out) throws IOException {
        out.writeInt(data.length);
        out.write(data);
        out.writeBoolean(isAscii);
        out.writeInt(length);
    }

    // endregion
}
