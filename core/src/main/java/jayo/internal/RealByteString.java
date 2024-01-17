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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import jayo.ByteString;
import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import jayo.exceptions.JayoException;
import jayo.external.NonNegative;

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

import static jayo.internal.Utils.*;

public sealed class RealByteString implements ByteString permits SegmentedByteString {
    @Serial
    private static final long serialVersionUID = 42L;

    final byte @NonNull [] data;
    transient protected int hashCode = 0; // Lazily computed; 0 if unknown.
    @Nullable
    transient private String utf8 = null; // Lazily computed.

    public RealByteString(final byte @NonNull [] data) {
        this.data = Objects.requireNonNull(data);
    }

    public RealByteString(final byte @NonNull [] data,
                          final @NonNegative int offset,
                          final @NonNegative int byteCount) {
        checkOffsetAndCount(Objects.requireNonNull(data).length, offset, byteCount);
        this.data = Arrays.copyOfRange(data, offset, offset + byteCount);
    }

    /**
     * @param string a String that will be encoded in UTF-8
     */
    public RealByteString(final @NonNull String string) {
        this.utf8 = Objects.requireNonNull(string);
        this.data = string.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public final @NonNull String decodeToString() {
        var result = utf8;
        if (result == null) {
            // We don't care if we double-allocate in racy code.
            result = new String(internalArray(), StandardCharsets.UTF_8);
            utf8 = result;
        }
        return result;
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
    public @NonNull ByteString hmac(final @NonNull Hmac mac, final @NonNull ByteString key) {
        Objects.requireNonNull(key);
        final javax.crypto.Mac javaMac;
        try {
            javaMac = javax.crypto.Mac.getInstance(mac.algorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm is not available : " + mac.algorithm(), e);
        }
        if (!(key instanceof RealByteString _key)) {
            throw new IllegalArgumentException("key must be an instance of JayoByteString");
        }
        try {
            javaMac.init(new SecretKeySpec(_key.internalArray(), mac.algorithm()));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("InvalidKeyException was fired with the provided ByteString key", e);
        }

        return new RealByteString(javaMac.doFinal(data));
    }

    @Override
    public @NonNull ByteString toAsciiLowercase() {
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
            return new RealByteString(lowercase);
        }
        return this;
    }

    @Override
    public @NonNull ByteString toAsciiUppercase() {
        // Search for a lowercase character. If we don't find one, return this.
        var i = 0;
        while (i < data.length) {
            var c = data[i];
            if (c < (byte) ((int) 'a') || c > (byte) ((int) 'z')) {
                i++;
                continue;
            }

            // This string needs to be uppercased. Create and return a new byte string.
            final var lowercase = data.clone();
            lowercase[i++] = (byte) (c - ('a' - 'A'));
            while (i < lowercase.length) {
                c = lowercase[i];
                if (c < (byte) ((int) 'a') || c > (byte) ((int) 'z')) {
                    i++;
                    continue;
                }
                lowercase[i] = (byte) (c - ('a' - 'A'));
                i++;
            }
            return new RealByteString(lowercase);
        }
        return this;
    }

    @Override
    public final @NonNull ByteString substring(final @NonNegative int startIndex) {
        return substring(startIndex, getSize());
    }

    @Override
    public @NonNull ByteString substring(final @NonNegative int startIndex, final @NonNegative int endIndex) {
        if (startIndex < 0) {
            throw new IllegalArgumentException("beginIndex < 0: " + startIndex);
        }
        if (endIndex > data.length) {
            throw new IllegalArgumentException("endIndex > length(" + data.length + ")");
        }
        if (endIndex < startIndex) {
            throw new IllegalArgumentException("endIndex < beginIndex");
        }

        if (startIndex == 0 && endIndex == data.length) {
            return this;
        }
        return new RealByteString(Arrays.copyOfRange(data, startIndex, endIndex));
    }

    @Override
    public byte get(final @NonNegative int index) {
        return data[index];
    }

    @Override
    public @NonNegative int getSize() {
        return data.length;
    }

    @Override
    public boolean isEmpty() {
        return getSize() == 0;
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
        return rangeEquals(0, prefix, 0, prefix.getSize());
    }

    @Override
    public final boolean startsWith(final byte @NonNull [] prefix) {
        return rangeEquals(0, prefix, 0, prefix.length);
    }

    @Override
    public final boolean endsWith(final @NonNull ByteString suffix) {
        return rangeEquals(getSize() - suffix.getSize(), suffix, 0, suffix.getSize());
    }

    @Override
    public final boolean endsWith(final byte @NonNull [] suffix) {
        return rangeEquals(getSize() - suffix.length, suffix, 0, suffix.length);
    }

    @Override
    public final int indexOf(final @NonNull ByteString other) {
        return indexOf(other, 0);
    }

    @Override
    public final int indexOf(final @NonNull ByteString other, final @NonNegative int startIndex) {
        if (!(Objects.requireNonNull(other) instanceof RealByteString _other)) {
            throw new IllegalArgumentException("other must be an instance of JayoByteString");
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
        return lastIndexOf(other, getSize());
    }

    @Override
    public final int lastIndexOf(final @NonNull ByteString other, final @NonNegative int startIndex) {
        if (!(Objects.requireNonNull(other) instanceof RealByteString _other)) {
            throw new IllegalArgumentException("other must be an instance of JayoByteString");
        }
        return lastIndexOf(_other.internalArray(), startIndex);
    }

    @Override
    public final int lastIndexOf(final byte @NonNull [] other) {
        return lastIndexOf(other, getSize());
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
        return _other.getSize() == data.length && _other.rangeEquals(0, data, 0, data.length);
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
        final var sizeA = getSize();
        final var sizeB = other.getSize();
        var i = 0;
        final var size = Math.min(sizeA, sizeB);
        while (i < size) {
            final var byteA = get(i) & 0xff;
            final var byteB = other.get(i) & 0xff;
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
        final var size = getSize();
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

    protected byte @NonNull [] internalArray() {
        return data;
    }

    @Serial
    private void readObject(final @NonNull ObjectInputStream in) throws IOException { // For Java Serialization.
        final var dataLength = in.readInt();
        final var byteString = (RealByteString) ByteString.read(in, dataLength);
        final Field field;
        try {
            field = RealByteString.class.getDeclaredField("data");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("JayoByteString should contain a 'data' field", e);
        }
        field.setAccessible(true);
        try {
            field.set(this, byteString.data);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("It should be possible to set JayoByteString's 'data' field", e);
        }
    }

    @Serial
    private void writeObject(final @NonNull ObjectOutputStream out) throws IOException { // For Java Serialization.
        out.writeInt(data.length);
        out.write(data);
    }
}
