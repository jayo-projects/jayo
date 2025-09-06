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
import org.jspecify.annotations.NonNull;

import java.util.Objects;

final class ByteStringUtils {
    // un-instantiable
    private ByteStringUtils() {
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

    static int compareTo(final @NonNull ByteString first, final @NonNull ByteString other) {
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
}
