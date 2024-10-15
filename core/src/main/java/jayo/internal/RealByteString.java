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

import jayo.ByteString;
import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import jayo.JayoException;
import jayo.external.NonNegative;
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

import static jayo.external.JayoUtils.checkOffsetAndCount;
import static jayo.internal.Utils.HEX_DIGIT_CHARS;
import static jayo.internal.Utils.arrayRangeEquals;

public sealed class RealByteString implements ByteString permits RealUtf8, SegmentedByteString {
    @Serial
    private static final long serialVersionUID = 42L;

    final byte @NonNull [] data;
    transient int hashCode = 0; // Lazily computed; 0 if unknown.
    transient @Nullable String utf8; // Lazily computed.

    // these 2 fields are only used in UTF-8 byte strings.
    boolean isAscii = false; // Lazily computed, false can mean non-ascii or unknown (=not yet scanned).
    transient @NonNegative int length = -1; // Lazily computed.

    public RealByteString(final byte @NonNull [] data) {
        this.data = Objects.requireNonNull(data);
        utf8 = null;
    }

    public RealByteString(final byte @NonNull [] data,
                          final @NonNegative int offset,
                          final @NonNegative int byteCount) {
        Objects.requireNonNull(data);
        checkOffsetAndCount(data.length, offset, byteCount);
        this.data = Arrays.copyOfRange(data, offset, offset + byteCount);
        utf8 = null;
    }

    /**
     * @param string a String that will be encoded in UTF-8
     */
    public RealByteString(final @NonNull String string) {
        this.utf8 = Objects.requireNonNull(string);
        this.data = string.getBytes(StandardCharsets.UTF_8);
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
        if (charset == StandardCharsets.UTF_8) {
            return decodeToString();
        }
        return new String(data, charset);
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
    public @NonNull String hex() {
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
        final MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(digest.algorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm is not available : " + digest.algorithm(), e);
        }
        return new RealByteString(messageDigest.digest(data));
    }

    @Override
    public @NonNull ByteString hmac(final @NonNull Hmac hMac, final @NonNull ByteString key) {
        Objects.requireNonNull(key);
        final javax.crypto.Mac javaMac;
        try {
            javaMac = javax.crypto.Mac.getInstance(hMac.algorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm is not available : " + hMac.algorithm(), e);
        }
        if (!(key instanceof RealByteString _key)) {
            throw new IllegalArgumentException("key must be an instance of RealByteString");
        }
        try {
            javaMac.init(new SecretKeySpec(_key.internalArray(), hMac.algorithm()));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("InvalidKeyException was fired with the provided ByteString key", e);
        }

        return new RealByteString(javaMac.doFinal(data));
    }

    @Override
    public @NonNull ByteString toAsciiLowercase() {
        final byte[] lowercase = toAsciiLowercaseBytes();
        return (lowercase != null) ? new RealByteString(lowercase) : this;
    }

    final byte @Nullable [] toAsciiLowercaseBytes() {
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
        final byte[] uppercase = toAsciiUppercaseBytes();
        return (uppercase != null) ? new RealByteString(uppercase) : this;
    }

    final byte @Nullable [] toAsciiUppercaseBytes() {
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
    public @NonNull ByteString substring(final @NonNegative int startIndex) {
        return substring(startIndex, byteSize());
    }

    @Override
    public @NonNull ByteString substring(final @NonNegative int startIndex, final @NonNegative int endIndex) {
        checkSubstringParameters(startIndex, endIndex);
        if (startIndex == 0 && endIndex == data.length) {
            return this;
        }
        return new RealByteString(Arrays.copyOfRange(data, startIndex, endIndex));
    }

    final void checkSubstringParameters(final @NonNegative int startIndex, final @NonNegative int endIndex) {
        if (startIndex < 0) {
            throw new IllegalArgumentException("beginIndex < 0: " + startIndex);
        }
        if (endIndex > byteSize()) {
            throw new IllegalArgumentException("endIndex > length(" + byteSize() + ")");
        }
        if (endIndex < startIndex) {
            throw new IllegalArgumentException("endIndex < beginIndex");
        }
    }

    @Override
    public byte getByte(final @NonNegative int index) {
        return data[index];
    }

    @Override
    public @NonNegative int byteSize() {
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
               final @NonNegative int offset,
               final @NonNegative int byteCount) {
        Objects.requireNonNull(buffer).write(data, offset, byteCount);
    }

    @Override
    public boolean rangeEquals(final @NonNegative int offset,
                               final @NonNull ByteString other,
                               final @NonNegative int otherOffset,
                               final @NonNegative int byteCount) {
        return Objects.requireNonNull(other).rangeEquals(otherOffset, this.data, offset, byteCount);
    }

    @Override
    public boolean rangeEquals(final @NonNegative int offset,
                               final byte @NonNull [] other,
                               final @NonNegative int otherOffset,
                               final @NonNegative int byteCount) {
        Objects.requireNonNull(other);
        return (
                offset >= 0 && offset <= data.length - byteCount &&
                        otherOffset >= 0 && otherOffset <= other.length - byteCount &&
                        arrayRangeEquals(data, offset, other, otherOffset, byteCount)
        );
    }

    @Override
    public void copyInto(final @NonNegative int offset,
                         final byte @NonNull [] target,
                         final @NonNegative int targetOffset,
                         final @NonNegative int byteCount) {
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
    public final int indexOf(final @NonNull ByteString other, final @NonNegative int startIndex) {
        if (!(Objects.requireNonNull(other) instanceof RealByteString _other)) {
            throw new IllegalArgumentException("other must be an instance of RealByteString");
        }
        return indexOf(_other.internalArray(), startIndex);
    }

    @Override
    public final int indexOf(final byte @NonNull [] other) {
        return indexOf(other, 0);
    }

    @Override
    public int indexOf(final byte @NonNull [] other, final @NonNegative int startIndex) {
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
    public final int lastIndexOf(final @NonNull ByteString other, final @NonNegative int startIndex) {
        if (!(Objects.requireNonNull(other) instanceof RealByteString _other)) {
            throw new IllegalArgumentException("other must be an instance of RealByteString");
        }
        return lastIndexOf(_other.internalArray(), startIndex);
    }

    @Override
    public final int lastIndexOf(final byte @NonNull [] other) {
        return lastIndexOf(other, byteSize());
    }

    @Override
    public int lastIndexOf(final byte @NonNull [] other, final @NonNegative int startIndex) {
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
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
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
        Objects.requireNonNull(other);
        final var sizeA = byteSize();
        final var sizeB = other.byteSize();
        var i = 0;
        final var size = Math.min(sizeA, sizeB);
        while (i < size) {
            final var byteA = getByte(i) & 0xff;
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

    // this method comes from kotlinx-io
    @Override
    public @NonNull String toString() {
        if (data.length == 0) {
            return "ByteString(size=0)";
        }
        // format: "ByteString(size=XXX hex=YYYY)"
        final var size = data.length;
        final var sizeStr = String.valueOf(size);
        final var len = 22 + sizeStr.length() + size * 2;
        final StringBuilder sb = new StringBuilder(len);
        sb.append("ByteString(size=");
        sb.append(sizeStr);
        sb.append(" hex=");
        for (var i = 0; i < size; i++) {
            final var b = (int) data[i];
            sb.append(HEX_DIGIT_CHARS[b >> 4 & 0xf]);
            sb.append(HEX_DIGIT_CHARS[b & 0xf]);
        }
        sb.append(')');
        return sb.toString();
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
            dataField = RealByteString.class.getDeclaredField("data");
            isAsciiField = RealByteString.class.getDeclaredField("isAscii");
            lengthField = RealByteString.class.getDeclaredField("length");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("RealByteString should contain 'data', 'isAscii' and 'length' fields", e);
        }
        dataField.setAccessible(true);
        isAsciiField.setAccessible(true);
        lengthField.setAccessible(true);
        try {
            dataField.set(this, bytes);
            isAsciiField.set(this, isAscii);
            lengthField.set(this, length);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("It should be possible to set RealUtf8's 'data', 'isAscii' and " +
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
