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

import jayo.JayoCharacterCodingException;
import jayo.JayoException;
import jayo.bytestring.ByteString;
import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import static jayo.internal.ByteStringUtils.checkSubstringParameters;
import static jayo.internal.UnsafeUtils.*;
import static jayo.internal.Utils.*;
import static jayo.tools.JayoUtils.checkOffsetAndCount;

public final /*Valhalla 'primitive class' or at least 'value class'*/ class RealByteString implements ByteString {
    @Serial
    private static final long serialVersionUID = 42L;
    private static final boolean ALLOW_COMPACT_STRING = UNSAFE_AVAILABLE && SUPPORT_COMPACT_STRING;

    final byte @NonNull [] data;

    public RealByteString(final byte @NonNull [] data) {
        assert data != null;
        this.data = data;
    }

    public RealByteString(final byte @NonNull [] data,
                          final int offset,
                          final int byteCount) {
        assert data != null;
        checkOffsetAndCount(data.length, offset, byteCount);
        this.data = Arrays.copyOfRange(data, offset, offset + byteCount);
    }

    @Override
    public @NonNull String decodeToString() {
        return new String(data, StandardCharsets.UTF_8);
    }

    @Override
    public @NonNull String decodeToString(final @NonNull Charset charset) {
        Objects.requireNonNull(charset);

        if (charset.equals(StandardCharsets.ISO_8859_1) && ALLOW_COMPACT_STRING) {
            return noCopyStringFromLatin1Bytes(data);
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
        return new RealByteString(messageDigest(digest).digest(data));
    }

    @Override
    public @NonNull ByteString hmac(final @NonNull Hmac hMac, final @NonNull ByteString key) {
        return new RealByteString(mac(hMac, key).doFinal(data));
    }

    @Override
    public @NonNull ByteString toAsciiLowercase() {
        // Search for an uppercase character. If we don't find one, return this.
        var i = 0;
        byte[] lowercase = null;
        while (i < data.length) {
            var c = data[i];
            if (c < (byte) ((int) 'A') || c > (byte) ((int) 'Z')) {
                i++;
                continue;
            }

            // This string needs to be lowercased. Create and return a new byte string.
            lowercase = data.clone();
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
        }
        return (lowercase != null) ? new RealByteString(lowercase) : this;
    }

    @Override
    public @NonNull ByteString toAsciiUppercase() {
        // Search for a lowercase character. If we don't find one, return this.
        var i = 0;
        byte[] uppercase = null;
        while (i < data.length) {
            var c = data[i];
            if (c < (byte) ((int) 'a') || c > (byte) ((int) 'z')) {
                i++;
                continue;
            }

            // This string needs to be uppercased. Create and return a new byte string.
            uppercase = data.clone();
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
        }
        return (uppercase != null) ? new RealByteString(uppercase) : this;
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

    @Override
    public byte getByte(final int index) {
        return data[index];
    }

    @Override
    public int byteSize() {
        return data.length;
    }

    @Override
    public boolean isEmpty() {
        return byteSize() == 0;
    }

    @Override
    public byte @NonNull [] toByteArray() {
        return data.clone();
    }

    @Override
    public @NonNull ByteBuffer asByteBuffer() {
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
    public boolean startsWith(final @NonNull ByteString prefix) {
        return rangeEquals(0, prefix, 0, prefix.byteSize());
    }

    @Override
    public boolean startsWith(final byte @NonNull [] prefix) {
        return rangeEquals(0, prefix, 0, prefix.length);
    }

    @Override
    public boolean endsWith(final @NonNull ByteString suffix) {
        return rangeEquals(byteSize() - suffix.byteSize(), suffix, 0, suffix.byteSize());
    }

    @Override
    public boolean endsWith(final byte @NonNull [] suffix) {
        return rangeEquals(byteSize() - suffix.length, suffix, 0, suffix.length);
    }

    @Override
    public int indexOf(final @NonNull ByteString other) {
        return indexOf(other, 0);
    }

    @Override
    public int indexOf(final @NonNull ByteString other, final int startIndex) {
        return indexOf(Utils.internalArray(other), startIndex);
    }

    @Override
    public int indexOf(final byte @NonNull [] other) {
        return indexOf(other, 0);
    }

    @Override
    public int indexOf(final byte @NonNull [] other, final int startIndex) {
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
    public int lastIndexOf(final @NonNull ByteString other) {
        return lastIndexOf(other, byteSize());
    }

    @Override
    public int lastIndexOf(final @NonNull ByteString other, final int startIndex) {
        return lastIndexOf(Utils.internalArray(other), startIndex);
    }

    @Override
    public int lastIndexOf(final byte @NonNull [] other) {
        return lastIndexOf(other, byteSize());
    }

    @Override
    public int lastIndexOf(final byte @NonNull [] other, final int startIndex) {
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
        if (!(other instanceof ByteString that)) {
            return false;
        }
        return that.byteSize() == data.length && that.rangeEquals(0, data, 0, data.length);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public int compareTo(final @NonNull ByteString other) {
        return ByteStringUtils.compareTo(this, other);
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

    // region native-jvm-serialization

    @Serial
    private void readObject(final @NonNull ObjectInputStream in) throws IOException {
        final var dataLength = in.readInt();
        final var bytes = in.readNBytes(dataLength);
        final Field dataField;
        try {
            dataField = RealByteString.class.getDeclaredField("data");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("RealByteString should contain 'data' field", e);
        }
        dataField.setAccessible(true);
        try {
            dataField.set(this, bytes);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("It should be possible to set RealByteString's 'data' field", e);
        }
    }

    @Serial
    private void writeObject(final @NonNull ObjectOutputStream out) throws IOException {
        out.writeInt(data.length);
        out.write(data);
    }

    // endregion

    public static int utf8Length(final @NonNull RealByteString byteString) {
        assert byteString != null;

        final var data = byteString.data;
        var byteIndex = 0;

        while (byteIndex < data.length) {
            if (data[byteIndex] < 0) {
                break;
            }
            byteIndex++;
        }

        var length = byteIndex;
        while (byteIndex < data.length) {
            final var b0 = data[byteIndex];
            if (b0 >= 0) {
                // 0xxxxxxx : 7 bits (ASCII).
                byteIndex++;
                length++;
            } else if ((b0 & 0xe0) == 0xc0) {
                // 0x110xxxxx : 11 bits (5 + 6).
                byteIndex += 2;
                length++;
            } else if ((b0 & 0xf0) == 0xe0) {
                // 0x1110xxxx : 16 bits (4 + 6 + 6).
                byteIndex += 3;
                length++;
            } else if ((b0 & 0xf8) == 0xf0) {
                // 0x11110xxx : 21 bits (3 + 6 + 6 + 6).
                byteIndex += 4;
                length += 2;
            } else {
                // We expected the first byte of a code point but got something else.
                throw new JayoCharacterCodingException(
                        "We expected the first byte of a code point but got something else at byte " + (byteIndex));
            }
        }
        if (byteIndex > data.length) {
            throw new JayoCharacterCodingException("malformed input: partial character at end");
        }

        return length;
    }
}
