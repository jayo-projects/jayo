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

package jayo;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Jayo assumes most applications use UTF-8 exclusively, and offers optimized implementations of common operations on
 * UTF-8 strings.
 *
 * <table border="1">
 * <tr>
 * <th></th>
 * <th>{@link ByteString}</th>
 * <th>{@link Buffer}, {@link Sink}, {@link Source}</th>
 * </tr>
 * <tr>
 * <td>Encode a string</td>
 * <td>{@link ByteString#encodeUtf8(String)}</td>
 * <td>{@link Sink#writeUtf8(CharSequence)}, {@link Sink#writeUtf8(CharSequence, int, int)}</td>
 * </tr>
 * <tr>
 * <td>Encode a code point</td>
 * <td></td>
 * <td>{@link Sink#writeUtf8CodePoint(int)}</td>
 * </tr>
 * <tr>
 * <td>Decode a string</td>
 * <td>{@link ByteString#decodeToUtf8()}</td>
 * <td>{@link Source#readUtf8()}, {@link Source#readUtf8(long)}</td>
 * </tr>
 * <tr>
 * <td>Decode a code point</td>
 * <td></td>
 * <td>{@link Source#readUtf8CodePoint()}</td>
 * </tr>
 * <tr>
 * <td>Decode until the next {@code \r\n} or {@code \n}</td>
 * <td></td>
 * <td>{@link Source#readUtf8LineStrict()}, {@link Source#readUtf8LineStrict(long)}</td>
 * </tr>
 * <tr>
 * <td>Decode until the next {@code \r\n}, {@code \n}, or {@code EOF}</td>
 * <td></td>
 * <td>{@link Source#readUtf8Line()}</td>
 * </tr>
 * <tr>
 * <td>Measure the bytes in a UTF-8 char sequence</td>
 * <td colspan="2">{@link Utf8#size(CharSequence)}</td>
 * </tr>
 * </table>
 */
public final class Utf8 {
    // un-instantiable
    private Utf8() {
    }

    /**
     * @return the number of bytes needed to encode the slice of {@code charSequence} as UTF-8 when using
     * {@link Sink#writeUtf8(CharSequence)}.
     */
    public static long size(final @NonNull CharSequence charSequence) {
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
}
