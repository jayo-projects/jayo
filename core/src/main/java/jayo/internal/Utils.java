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

import org.jspecify.annotations.NonNull;
import jayo.Buffer;
import jayo.Source;
import java.lang.Thread.Builder;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class Utils {
    // un-instantiable
    private Utils() {
    }

    static final char UTF8_REPLACEMENT_CHARACTER = '\ufffd';
    static final int UTF8_REPLACEMENT_CODE_POINT = UTF8_REPLACEMENT_CHARACTER;

    static final long OVERFLOW_ZONE = Long.MIN_VALUE / 10L;
    static final long OVERFLOW_DIGIT_START = Long.MIN_VALUE % 10L + 1;
    static final byte[] HEX_DIGIT_BYTES = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    static final char[] HEX_DIGIT_CHARS =
            {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    static void checkOffsetAndCount(final long size, final long offset, final long byteCount) {
        if ((offset | byteCount) < 0 || offset > size || size - offset < byteCount) {
            throw new IndexOutOfBoundsException("size=" + size + " offset=" + offset + " byteCount=" + byteCount);
        }
    }

    /**
     * Returns the index of a final value in options that is a prefix of this buffer. Returns -1 if no final value is
     * found. This method does two simultaneous iterations: it iterates the trie, and it iterates this buffer. It
     * returns when it reaches a result in the trie, when it mismatches in the trie, and when the buffer is exhausted.
     */
    static int selectPrefix(final RealBuffer buffer, final RealOptions options) {
        var s = buffer.segmentQueue.head();
        if (s == null) {
            return -1;
        }

        var data = s.data;
        var pos = s.pos;
        var limit = s.limit;

        final var trie = options.trie;
        var triePos = 0;

        var prefixIndex = -1;

        navigateTrie:
        while (true) {
            final var scanOrSelect = trie[triePos++];

            final var possiblePrefixIndex = trie[triePos++];
            if (possiblePrefixIndex != -1) {
                prefixIndex = possiblePrefixIndex;
            }

            final int nextStep;

            if (s == null) {
                break;
            } else if (scanOrSelect < 0) {
                // Scan: take multiple bytes from the buffer and the trie, looking for any mismatch.
                final var scanByteCount = -1 * scanOrSelect;
                final var trieLimit = triePos + scanByteCount;
                while (true) {
                    final var b = data[pos++] & 0xff;
                    if (b != trie[triePos++]) {
                        return prefixIndex; // Fail 'cause we found a mismatch.
                    }
                    final var scanComplete = (triePos == trieLimit);

                    // Advance to the next buffer segment if this one is exhausted.
                    if (pos == limit) {
                        s = s.next;
                        if (s != buffer.segmentQueue) {
                            pos = s.pos;
                            data = s.data;
                            limit = s.limit;
                        } else {
                            if (!scanComplete) {
                                break navigateTrie; // We were exhausted before the scan completed.
                            }
                            s = null; // We were exhausted at the end of the scan.
                        }
                    }

                    if (scanComplete) {
                        nextStep = trie[triePos];
                        break;
                    }
                }
            } else {
                // Select: take one byte from the buffer and find a match in the trie.
                final var b = data[pos++] & 0xff;
                final var selectLimit = triePos + scanOrSelect;
                while (true) {
                    if (triePos == selectLimit) {
                        return prefixIndex; // Fail 'cause we didn't find a match.
                    }

                    if (b == trie[triePos]) {
                        nextStep = trie[triePos + scanOrSelect];
                        break;
                    }

                    triePos++;
                }

                // Advance to the next buffer segment if this one is exhausted.
                if (pos == limit) {
                    s = s.next;
                    if (s != buffer.segmentQueue) {
                        pos = s.pos;
                        data = s.data;
                        limit = s.limit;
                    } else {
                        s = null; // No more segments! The next trie node will be our last.
                    }
                }
            }

            if (nextStep >= 0) {
                return nextStep; // Found a matching option.
            }
            triePos = -nextStep; // Found another node to continue the search.
        }

        return prefixIndex; // Return any matches we encountered while searching for a deeper match.
    }

    static String readUtf8Line(final Buffer buffer, final long newline) {
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

    static RealBuffer getBufferFromSource(final Source source) {
        if (source instanceof RealBuffer _buffer) {
            return _buffer;
        }
        if (source instanceof RealSource _source) {
            return _source.buffer;
        }
        throw new IllegalArgumentException("Source must be an instance of RealBuffer or RealSource");
    }

    static String toHexString(final byte b) {
        final var result = new char[2];
        result[0] = HEX_DIGIT_CHARS[b >> 4 & 0xf];
        result[1] = HEX_DIGIT_CHARS[b & 0xf];
        return String.valueOf(result);
    }

    static String toHexString(final int i) {
        if (i == 0) {
            return "0"; // Required as code below does not handle 0
        }

        final var result = new char[8];
        result[0] = HEX_DIGIT_CHARS[i >> 28 & 0xf];
        result[1] = HEX_DIGIT_CHARS[i >> 24 & 0xf];
        result[2] = HEX_DIGIT_CHARS[i >> 20 & 0xf];
        result[3] = HEX_DIGIT_CHARS[i >> 16 & 0xf];
        result[4] = HEX_DIGIT_CHARS[i >> 12 & 0xf];
        result[5] = HEX_DIGIT_CHARS[i >> 8 & 0xf];
        result[6] = HEX_DIGIT_CHARS[i >> 4 & 0xf];
        result[7] = HEX_DIGIT_CHARS[i & 0xf];

        // Find the first non-zero index
        var index = 0;
        while (index < result.length) {
            if (result[index] != '0') {
                break;
            }
            index++;
        }

        return String.valueOf(result, index, result.length - index);
    }

    static boolean arrayRangeEquals(
            byte @NonNull [] a,
            int aOffset,
            byte @NonNull [] b,
            int bOffset,
            int byteCount
    ) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);
        for (var i = 0; i < byteCount; i++) {
            if (a[i + aOffset] != b[i + bOffset]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mask every bit below the
     * <a href="https://aggregate.org/MAGIC/#Most%20Significant%201%20Bit">most significant bit</a> to a 1
     */
    static int getHexadecimalUnsignedLongWidth(final long _l) {
        var x = _l;
        x = x | (x >>> 1);
        x = x | (x >>> 2);
        x = x | (x >>> 4);
        x = x | (x >>> 8);
        x = x | (x >>> 16);
        x = x | (x >>> 32);

        // Count the number of 1s
        // https://aggregate.org/MAGIC/#Population%20Count%20(Ones%20Count)
        x -= x >>> 1 & 0x5555555555555555L;
        x = (x >>> 2 & 0x3333333333333333L) + (x & 0x3333333333333333L);
        x = (x >>> 4) + x & 0x0f0f0f0f0f0f0f0fL;
        x += x >>> 8;
        x += x >>> 16;
        x = (x & 0x3f) + ((x >>> 32) & 0x3f);

        // Round up to the nearest full byte
        return (int) ((x + 3) / 4);
    }

    static @NonNull Builder threadBuilder(final @NonNull String prefix) {
        Objects.requireNonNull(prefix);
        return Thread.ofVirtual()
                .name(prefix, 0)
                .inheritInheritableThreadLocals(true);
    }
}
