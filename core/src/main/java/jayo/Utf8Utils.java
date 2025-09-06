/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
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

package jayo;

import jayo.bytestring.ByteString;
import jayo.internal.RealByteString;
import jayo.internal.SegmentedByteString;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

import static jayo.internal.RealByteString.utf8Length;
import static jayo.internal.SegmentedByteString.utf8Length;

/**
 * Jayo assumes most applications use UTF-8 exclusively and offers optimized implementations of common operations on
 * UTF-8 strings.
 * <table border="1">
 * <tr>
 * <th></th>
 * <th>{@link ByteString}</th>
 * <th>{@link Buffer}, {@link Writer}, {@link Reader}</th>
 * </tr>
 * <tr>
 * <td>Encode a string</td>
 * <td>{@link ByteString#encode(String)}</td>
 * <td>{@link Writer#write(String)}</td>
 * </tr>
 * <tr>
 * <td>Encode a code point</td>
 * <td></td>
 * <td>{@link Writer#writeUtf8CodePoint(int)}</td>
 * </tr>
 * <tr>
 * <td>Decode a string</td>
 * <td>{@link ByteString#decodeToString()}</td>
 * <td>{@link Reader#readString()}, {@link Reader#readString(long)}</td>
 * </tr>
 * <tr>
 * <td>Decode a code point</td>
 * <td></td>
 * <td>{@link Reader#readUtf8CodePoint()}</td>
 * </tr>
 * <tr>
 * <td>Decode until the next {@code \r\n} or {@code \n}</td>
 * <td></td>
 * <td>{@link Reader#readLineStrict()}, {@link Reader#readLineStrict(long)}</td>
 * </tr>
 * <tr>
 * <td>Decode until the next {@code \r\n}, {@code \n}, or {@code EOF}</td>
 * <td></td>
 * <td>{@link Reader#readLine()}</td>
 * </tr>
 * <tr>
 * <td>Measure the number of bytes required to encode a char sequence using the UTF-8 encoding</td>
 * <td colspan="2">{@link Utf8Utils#utf8ByteSize(CharSequence)}</td>
 * </tr>
 * </table>
 */
public final class Utf8Utils {
    // un-instantiable
    private Utf8Utils() {
    }

    /**
     * @return the number of bytes needed to encode the slice of {@code charSequence} as UTF-8 when using
     * {@link Writer#write(String)}.
     */
    public static long utf8ByteSize(final @NonNull CharSequence charSequence) {
        Objects.requireNonNull(charSequence);
        var result = 0L;
        var i = 0;
        while (i < charSequence.length()) {
            final var c = (int) charSequence.charAt(i);

            if (c < 0x80) {
                // A 7-bit character with 1 byte.
                result++;
                i++;
            } else if (c < 0x800) {
                // An 11-bit character with 2 bytes.
                result += 2;
                i++;
            } else if (c < 0xd800 || c > 0xdfff) {
                // A 16-bit character with 3 bytes.
                result += 3;
                i++;
            } else {
                final var low = (i + 1 < charSequence.length()) ? (int) charSequence.charAt(i + 1) : 0;
                if (c > 0xdbff || low < 0xdc00 || low > 0xdfff) {
                    // A malformed surrogate, which yields '?'.
                    result++;
                    i++;
                } else {
                    // A 21-bit character with 4 bytes.
                    result += 4;
                    i += 2;
                }
            }
        }

        return result;
    }

    /**
     * @return the UTF-8 length of {@code byteString}. The length is equal to the number of
     * <a href="https://www.unicode.org/glossary/#code_point">Unicode code units</a> in the ByteString.
     * <p>
     * Note: This method returns the same result as:
     * <pre>
     * {@code
     * byteString.decodeToString().length();
     * // which is also the same as
     * byteString.decodeToString(StandardCharsets.UTF_8).length();
     * }
     * </pre>
     */
    public static int length(final @NonNull ByteString byteString) {
        Objects.requireNonNull(byteString);

        if (byteString instanceof RealByteString realByteString) {
            return utf8Length(realByteString);
        }

        if (byteString instanceof SegmentedByteString segmentedByteString) {
            return utf8Length(segmentedByteString);
        }

        throw new IllegalArgumentException("byteString must be an instance of RealByteString or SegmentedByteString");
    }
}
