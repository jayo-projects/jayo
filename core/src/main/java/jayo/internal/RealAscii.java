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

import jayo.JayoException;
import jayo.bytestring.Ascii;
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
import java.util.*;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static jayo.internal.BaseByteString.*;
import static jayo.internal.Utils.ASCII_REPLACEMENT_CHARACTER;
import static jayo.internal.Utils.ASCII_REPLACEMENT_CODE_POINT;
import static jayo.tools.JayoUtils.checkOffsetAndCount;

public final /*Valhalla 'primitive class' or at least 'value class'*/ class RealAscii implements Ascii {
    @Serial
    private static final long serialVersionUID = 43L;

    final byte @NonNull [] data;

    public RealAscii(final byte @NonNull [] data) {
        assert data != null;
        this.data = data;
    }

    public RealAscii(final byte @NonNull [] data,
                     final int offset,
                     final int byteCount) {
        assert data != null;
        checkOffsetAndCount(data.length, offset, byteCount);
        this.data = Arrays.copyOfRange(data, offset, offset + byteCount);
    }

    public RealAscii(final @NonNull String string) {
        assert string != null;
        data = string.getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public @NonNull String decodeToString() {
        return new String(data, StandardCharsets.US_ASCII);
    }

    @Override
    public @NonNull String decodeToString(final @NonNull Charset charset) {
        Objects.requireNonNull(charset);

        if (!charset.equals(StandardCharsets.US_ASCII)) {
            throw new IllegalArgumentException("Utf8 only supports ASCII, not " + charset);
        }

        return decodeToString();
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
        return hexStatic(data);
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
    public int length() {
        return data.length;
    }

    @Override
    public @NonNull IntStream codePoints() {
        class CodePointIterator implements PrimitiveIterator.OfInt {
            private int byteIndex = 0;

            public boolean hasNext() {
                return byteIndex < data.length;
            }

            public int nextInt() {
                final var b = data[byteIndex++];
                if ((b & 0x80) == 0) {
                    // 0xxxxxxx : 7 bits (ASCII).
                    return b & 0x7f;
                }
                return ASCII_REPLACEMENT_CODE_POINT;
            }

            @Override
            public void forEachRemaining(final @NonNull IntConsumer block) {
                Objects.requireNonNull(block);
                while (byteIndex < data.length) {
                    block.accept(nextInt());
                }
            }
        }

        return StreamSupport.intStream(() ->
                        Spliterators.spliteratorUnknownSize(new CodePointIterator(), Spliterator.ORDERED),
                Spliterator.ORDERED, false);
    }

    @Override
    public @NonNull Ascii toAsciiLowercase() {
        final var lowercase = toAsciiLowercaseBytes(data);
        return (lowercase != null) ? new RealAscii(lowercase) : this;
    }

    @Override
    public @NonNull Ascii toAsciiUppercase() {
        final var uppercase = toAsciiUppercaseBytes(data);
        return (uppercase != null) ? new RealAscii(uppercase) : this;
    }

    @Override
    public @NonNull Ascii substring(final int startIndex) {
        return substring(startIndex, byteSize());
    }

    @Override
    public @NonNull Ascii substring(final int startIndex, final int endIndex) {
        checkSubstringParameters(startIndex, endIndex, byteSize());
        if (startIndex == 0 && endIndex == data.length) {
            return this;
        }
        return new RealAscii(Arrays.copyOfRange(data, startIndex, endIndex));
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
    public char charAt(final int index) {
        final var b = data[index];
        if ((b & 0x80) == 0) {
            // valid ASCII
            return (char) (b & 0x7f);
        }

        return ASCII_REPLACEMENT_CHARACTER;
    }

    @Override
    public boolean isEmpty() {
        return byteSize() == 0;
    }

    @Override
    public @NonNull CharSequence subSequence(final int start, final int end) {
        return substring(start, end);
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
        return rangeEqualsStatic(data, offset, other, otherOffset, byteCount);
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
        return indexOfStatic(data, other, startIndex);
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
        return lastIndexOfStatic(data, other, startIndex);
    }

    @Override
    public boolean equals(final @Nullable Object other) {
        if (other == this) {
            return true;
        }
        return equalsStatic(data, other);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public int compareTo(final @NonNull ByteString other) {
        return compareToStatic(this, other);
    }

    @Override
    public @NonNull String toString() {
        return decodeToString();
    }

    // region native-jvm-serialization

    @Serial
    private void readObject(final @NonNull ObjectInputStream in) throws IOException {
        assert in != null;

        final var dataLength = in.readInt();
        final var bytes = in.readNBytes(dataLength);
        final Field dataField;
        try {
            dataField = RealAscii.class.getDeclaredField("data");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("RealAscii should contain 'data' field", e);
        }
        dataField.setAccessible(true);
        try {
            dataField.set(this, bytes);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("It should be possible to set RealAscii's 'data' field", e);
        }
    }

    @Serial
    private void writeObject(final @NonNull ObjectOutputStream out) throws IOException {
        out.writeInt(data.length);
        out.write(data);
    }

    // endregion
}
