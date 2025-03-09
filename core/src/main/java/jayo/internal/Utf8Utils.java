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
import org.jspecify.annotations.NonNull;

import java.util.Objects;

final class Utf8Utils {
    // un-instantiable
    private Utf8Utils() {
    }

    static final char UTF8_REPLACEMENT_CHARACTER = '\ufffd';
    static final int UTF8_REPLACEMENT_CODE_POINT = UTF8_REPLACEMENT_CHARACTER;
    static final char ASCII_REPLACEMENT_CHARACTER = '?';
    static final int ASCII_REPLACEMENT_CODE_POINT = ASCII_REPLACEMENT_CHARACTER;

    static String readUtf8Line(final @NonNull Buffer buffer, final long newline) {
        Objects.requireNonNull(buffer);
        if (newline > 0L && buffer.getByte(newline - 1) == (byte) ((int) '\r')) {
            // Read everything until '\r\n', then skip the '\r\n'.
            final var result = buffer.readString(newline - 1L);
            buffer.skip(2L);
            return result;
        }

        // Read everything until '\n', then skip the '\n'.
        final var result = buffer.readString(newline);
        buffer.skip(1L);
        return result;
    }
}
