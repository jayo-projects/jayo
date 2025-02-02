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
import jayo.Utf8;
import org.jspecify.annotations.NonNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static jayo.internal.UnsafeUtils.noCopyStringFromLatin1Bytes;
import static jayo.internal.Utf8Utils.UTF8_REPLACEMENT_CODE_POINT;

public final class RealUtf8 extends BaseByteString implements Utf8 {
    public RealUtf8(final byte @NonNull [] data, final boolean isAscii) {
        super(data);
        this.isAscii = isAscii;
        if (isAscii) {
            length = data.length;
        }
    }

    public RealUtf8(final byte @NonNull [] data,
                    final int offset,
                    final int byteCount,
                    final boolean isAscii) {
        super(data, offset, byteCount);
        this.isAscii = isAscii;
        if (isAscii) {
            length = byteCount;
        }
    }

    /**
     * @param string a String that will be encoded in UTF-8
     */
    public RealUtf8(final @NonNull String string) {
        super(Objects.requireNonNull(string).getBytes(StandardCharsets.UTF_8));
        utf8 = string;
        length = string.length();
    }

    @Override
    public @NonNull String decodeToString() {
        return decodeToUtf8(this, isAscii);
    }

    static @NonNull String decodeToUtf8(final @NonNull BaseByteString utf8, final boolean isAscii) {
        assert utf8 != null;

        var result = utf8.utf8;
        if (result != null) {
            return result;
        }
        // We don't care if we double-allocate in racy code.
        if (isAscii) {
            if (ALLOW_COMPACT_STRING) {
                result = noCopyStringFromLatin1Bytes(utf8.internalArray());
            } else {
                result = new String(utf8.internalArray(), StandardCharsets.US_ASCII);
            }
        } else {
            result = new String(utf8.internalArray(), StandardCharsets.UTF_8);
        }
        utf8.length = result.length();
        utf8.utf8 = result;
        return result;
    }

    @Override
    public @NonNull String decodeToString(final @NonNull Charset charset) {
        return decodeToCharset(this, isAscii, charset);
    }

    static @NonNull String decodeToCharset(final @NonNull BaseByteString utf8,
                                           final boolean isAscii,
                                           final @NonNull Charset charset) {
        Objects.requireNonNull(charset);
        assert utf8 != null;

        if (charset.equals(StandardCharsets.UTF_8)) {
            return utf8.decodeToString();
        }

        if (charset.equals(StandardCharsets.US_ASCII)) {
            if (isAscii) {
                var result = utf8.utf8;
                if (result != null) {
                    return result;
                }
                if (ALLOW_COMPACT_STRING) {
                    result = noCopyStringFromLatin1Bytes(utf8.internalArray());
                } else {
                    result = new String(utf8.internalArray(), charset);
                }
                utf8.length = result.length();
                utf8.utf8 = result;
                return result;
            }

            return new String(utf8.internalArray(), charset);
        }

        throw new IllegalArgumentException("Utf8 only supports UTF-8 and ASCII, not " + charset);
    }

    @Override
    public int length() {
        if (length == -1) {
            fullScan();
        }
        return length;
    }

    @NonNull
    public IntStream codePoints() {
        // fast-path if we already have the UTF-8 String
        if (utf8 != null) {
            return utf8.codePoints();
        }

        class CodePointIterator implements PrimitiveIterator.OfInt {
            private int byteIndex = 0;

            public boolean hasNext() {
                return byteIndex < data.length;
            }

            public int nextInt() {
                final var b0 = data[byteIndex];
                // fast-path for ASCII byte
                if (isAscii || (b0 & 0x80) == 0) {
                    // 0xxxxxxx : 7 bits (ASCII).
                    byteIndex++;
                    return b0 & 0x7f;
                }

                int codePoint;
                final int byteCount;
                final int min;
                if ((b0 & 0xe0) == 0xc0) {
                    // 0x110xxxxx
                    codePoint = b0 & 0x1f;
                    byteCount = 2; // 11 bits (5 + 6).
                    min = 0x80;
                } else if ((b0 & 0xf0) == 0xe0) {
                    // 0x1110xxxx
                    codePoint = b0 & 0x0f;
                    byteCount = 3; // 16 bits (4 + 6 + 6).
                    min = 0x800;
                } else if ((b0 & 0xf8) == 0xf0) {
                    // 0x11110xxx
                    codePoint = b0 & 0x07;
                    byteCount = 4; // 21 bits (3 + 6 + 6 + 6).
                    min = 0x10000;
                } else {
                    // We expected the first byte of a code point but got something else.
                    byteIndex++;
                    return UTF8_REPLACEMENT_CODE_POINT;
                }

                if (byteIndex + byteCount > data.length) {
                    byteIndex = data.length;
                    return UTF8_REPLACEMENT_CODE_POINT;
                }

                // Read the continuation bytes. If we encounter a non-continuation byte, the sequence consumed thus far
                // is truncated and is decoded as the replacement character.
                for (var i = 1; i < byteCount; i++) {
                    final var b = data[byteIndex + i];
                    if ((b & 0xc0) == 0x80) {
                        // 0x10xxxxxx
                        codePoint = codePoint << 6;
                        codePoint = codePoint | (b & 0x3f);
                    } else {
                        byteIndex += byteCount;
                        return UTF8_REPLACEMENT_CODE_POINT;
                    }
                }

                byteIndex += byteCount;

                if (codePoint > 0x10ffff // Reject code points larger than the Unicode maximum.
                        || (0xd800 <= codePoint && codePoint <= 0xdfff) // Reject partial surrogates.
                        || codePoint < min) { // Reject overlong code points.
                    return UTF8_REPLACEMENT_CODE_POINT;
                }

                return codePoint;
            }

            @Override
            public void forEachRemaining(IntConsumer block) {
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
    @NonNull
    public Utf8 substring(final int startIndex) {
        return substring(startIndex, byteSize());
    }

    @Override
    public @NonNull Utf8 substring(int startIndex, int endIndex) {
        checkSubstringParameters(startIndex, endIndex, byteSize());
        if (startIndex == 0 && endIndex == data.length) {
            return this;
        }
        return new RealUtf8(Arrays.copyOfRange(data, startIndex, endIndex), isAscii);
    }

    @Override
    public @NonNull Utf8 toAsciiLowercase() {
        final var lowercase = toAsciiLowercaseBytes(data);
        return (lowercase != null) ? new RealUtf8(lowercase, isAscii) : this;
    }

    @Override
    public @NonNull Utf8 toAsciiUppercase() {
        final var uppercase = toAsciiUppercaseBytes(data);
        return (uppercase != null) ? new RealUtf8(uppercase, isAscii) : this;
    }

    @Override
    public @NonNull String toString() {
        return decodeToString();
    }

    private void fullScan() {
        var byteIndex = 0;

        var isAscii = true;
        while (byteIndex < data.length) {
            if ((data[byteIndex] & 0x80) != 0) {
                isAscii = false;
                break;
            }
            byteIndex++;
        }

        this.isAscii = isAscii;
        if (isAscii) {
            length = data.length;
            return;
        }

        var length = byteIndex;
        while (byteIndex < data.length) {
            final var b0 = data[byteIndex];
            if ((b0 & 0x80) == 0) {
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

        this.length = length;
    }
}
