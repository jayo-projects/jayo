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

import jayo.Buffer;
import jayo.exceptions.JayoCharacterCodingException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

import static jayo.internal.UnsafeUtils.noCopyStringBuilderAppendLatin1Bytes;

final class Utf8Utils {
    // un-instantiable
    private Utf8Utils() {
    }

    static final char UTF8_REPLACEMENT_CHARACTER = '\ufffd';
    static final int UTF8_REPLACEMENT_CODE_POINT = UTF8_REPLACEMENT_CHARACTER;

    static String readUtf8Line(final @NonNull Buffer buffer, final long newline) {
        Objects.requireNonNull(buffer);
        if (newline > 0L && buffer.get(newline - 1) == (byte) ((int) '\r')) {
            // Read everything until '\r\n', then skip the '\r\n'.
            final var result = buffer.readUtf8(newline - 1L);
            buffer.skip(2L);
            return result;
        }

        // Read everything until '\n', then skip the '\n'.
        final var result = buffer.readUtf8(newline);
        buffer.skip(1L);
        return result;
    }

    static @NonNegative int parseUtf8(final byte @NonNull [] data,
                                      final char @NonNull [] chars,
                                      final @NonNegative int firstNonAscii) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(chars);
        final var endIndex = data.length;
        var charIndex = 0;

        // fill prefix of ASCII bytes to characters
        for (var i = 0; i < firstNonAscii; i++) {
            final var c = (int) data[i] & 0xff;
            chars[charIndex++] = (char) c;
        }
        var i = firstNonAscii;
        while (i < endIndex) {
            var b = data[i];
            if ((b & 0x80) == 0) {
                // 0xxxxxxx : 7 bits (ASCII).
                var c = (int) b & 0xff;
                chars[charIndex++] = (char) c;
                i++;

                // Fast-path contiguous runs of ASCII characters.
                while (i < endIndex) {
                    b = data[i];
                    if ((b & 0x80) != 0) {
                        break;
                    }
                    c = (int) b & 0xff;
                    chars[charIndex++] = (char) c;
                    i++;
                }

                continue;
            }

            final long codePointAndIndex = readNonAsciiCodePoint(data, b, i, endIndex);
            final var codePoint = (int) (codePointAndIndex >> 32);
            i = (int) codePointAndIndex;

            try {
                charIndex += Character.toChars(codePoint, chars, charIndex);
            } catch (IllegalArgumentException _unused) {
                // Reject code points larger than the Unicode maximum or partial surrogates.
                throw new JayoCharacterCodingException("malformed input around byte " + (i - 1));
            }
        }
        return charIndex;
    }

    static @NonNull StringBuilder buildUtf8StringBuilder(final byte @NonNull [] data, final @NonNegative int firstNonAscii) {
        Objects.requireNonNull(data);
        final var endIndex = data.length;
        final var sb = new StringBuilder(endIndex);
        // fill prefix of ASCII bytes to characters
        noCopyStringBuilderAppendLatin1Bytes(sb, data, 0, firstNonAscii);
        var i = firstNonAscii;
        while (i < endIndex) {
            var b = data[i];
            if ((b & 0x80) == 0) {
                // 0xxxxxxx : 7 bits (ASCII).
                final var asciiStartIndex = i++;
                // Fast-path contiguous runs of ASCII characters.
                while (i < endIndex) {
                    b = data[i];
                    if ((b & 0x80) != 0) {
                        break;
                    }
                    i++;
                }
                noCopyStringBuilderAppendLatin1Bytes(sb, data, asciiStartIndex, i - asciiStartIndex);

                continue;
            }
            final long codePointAndIndex = readNonAsciiCodePoint(data, b, i, endIndex);
            final var codePoint = (int) (codePointAndIndex >> 32);
            i = (int) codePointAndIndex;

            try {
                sb.appendCodePoint(codePoint);
            } catch (IllegalArgumentException _unused) {
                // Reject code points larger than the Unicode maximum or partial surrogates.
                throw new JayoCharacterCodingException("malformed input around byte " + (i - 1));
            }
        }
        return sb;
    }

    private static long readNonAsciiCodePoint(byte[] data,
                                             final byte b0,
                                             final @NonNegative int byteIndex,
                                             final @NonNegative int endIndex) {
        int codePoint;
        final int continuationBytesLimit;
        final int min;
        if ((b0 & 0xe0) == 0xc0) {
            // 0x110xxxxx
            continuationBytesLimit = byteIndex + 2; // 11 bits (5 + 6).
            if (continuationBytesLimit > endIndex) {
                throw new JayoCharacterCodingException("malformed input: partial character at end");
            }
            codePoint = b0 & 0x1f;
            min = 0x80;
        } else if ((b0 & 0xf0) == 0xe0) {
            // 0x1110xxxx
            continuationBytesLimit = byteIndex + 3; // 16 bits (4 + 6 + 6).
            if (continuationBytesLimit > endIndex) {
                throw new JayoCharacterCodingException("malformed input: partial character at end");
            }
            codePoint = b0 & 0x0f;
            min = 0x800;
        } else if ((b0 & 0xf8) == 0xf0) {
            // 0x11110xxx
            continuationBytesLimit = byteIndex + 4; // 21 bits (3 + 6 + 6 + 6).
            if (continuationBytesLimit > endIndex) {
                throw new JayoCharacterCodingException("malformed input: partial character at end");
            }
            codePoint = b0 & 0x07;
            min = 0x10000;
        } else {
            // We expected the first byte of a code point but got something else.
            throw new JayoCharacterCodingException(
                    "We expected the first byte of a code point but got something else at byte " + (byteIndex));
        }

        var i = byteIndex + 1;
        // Read the continuation byte(s).
        while (i < continuationBytesLimit) {
            final var b = data[i++];
            if ((b & 0xc0) == 0x80) {
                // 0x10xxxxxx
                codePoint = codePoint << 6;
                codePoint = codePoint | (b & 0x3f);
            } else {
                throw new JayoCharacterCodingException("malformed input around byte " + (i - 1));
            }
        }

        if (codePoint < min) { // Reject overlong code points.
            throw new JayoCharacterCodingException("malformed input around byte " + (i - 1));
        }

        return (((long)codePoint) << 32) | (i & 0xffffffffL);
    }
}
