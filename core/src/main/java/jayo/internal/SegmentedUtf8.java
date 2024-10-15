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

import jayo.Utf8;
import jayo.JayoCharacterCodingException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.io.Serial;
import java.util.Arrays;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static jayo.internal.RealUtf8.decodeToUtf8Static;
import static jayo.internal.UnsafeUtils.SUPPORT_COMPACT_STRING;
import static jayo.internal.UnsafeUtils.UNSAFE_AVAILABLE;
import static jayo.internal.Utf8Utils.UTF8_REPLACEMENT_CODE_POINT;

public final class SegmentedUtf8 extends SegmentedByteString implements Utf8 {
    SegmentedUtf8(final @NonNull Segment[] segments, final int @NonNull [] directory, final boolean isAscii) {
        super(segments, directory);
        this.isAscii = isAscii;
    }

    @Override
    public @NonNull String decodeToString() {
        return decodeToUtf8Static(this, isAscii, UNSAFE_AVAILABLE && SUPPORT_COMPACT_STRING);
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
            private int byteIndexInSegment = directory[segments.length];
            private int segmentIndex = 0;
            private int nextSegmentOffset = directory[0];

            public boolean hasNext() {
                return byteIndex < byteSize();
            }

            public int nextInt() {
                final var b0 = currentByte();
                try {
                    advance(1);
                    // fast-path for ASCII byte
                    if (isAscii || (b0 & 0x80) == 0) {
                        // 0xxxxxxx : 7 bits (ASCII).
                        return b0 & 0x7f;
                    }

                    int codePoint;
                    int remainingBytes;
                    final int min;
                    if ((b0 & 0xe0) == 0xc0) {
                        // 0x110xxxxx
                        codePoint = b0 & 0x1f;
                        remainingBytes = 1; // 11 bits (5 + 6).
                        min = 0x80;
                    } else if ((b0 & 0xf0) == 0xe0) {
                        // 0x1110xxxx
                        codePoint = b0 & 0x0f;
                        remainingBytes = 2; // 16 bits (4 + 6 + 6).
                        min = 0x800;
                    } else if ((b0 & 0xf8) == 0xf0) {
                        // 0x11110xxx
                        codePoint = b0 & 0x07;
                        remainingBytes = 3; // 21 bits (3 + 6 + 6 + 6).
                        min = 0x10000;
                    } else {
                        // We expected the first byte of a code point but got something else.
                        advance(1);
                        return UTF8_REPLACEMENT_CODE_POINT;
                    }

                    // Read the continuation bytes. If we encounter a non-continuation byte, the sequence consumed thus far
                    // is truncated and is decoded as the replacement character.
                    while (remainingBytes > 0) {
                        final var b = currentByte();
                        if ((b & 0xc0) == 0x80) {
                            // 0x10xxxxxx
                            codePoint = codePoint << 6;
                            codePoint = codePoint | (b & 0x3f);
                            advance(1);
                            remainingBytes--;
                        } else {
                            advance(remainingBytes);
                            return UTF8_REPLACEMENT_CODE_POINT;
                        }
                    }

                    if (codePoint > 0x10ffff // Reject code points larger than the Unicode maximum.
                            || (0xd800 <= codePoint && codePoint <= 0xdfff) // Reject partial surrogates.
                            || codePoint < min) { // Reject overlong code points.
                        return UTF8_REPLACEMENT_CODE_POINT;
                    }

                    return codePoint;
                } catch (JayoCharacterCodingException _unused) {
                    byteIndex = byteSize();
                    return UTF8_REPLACEMENT_CODE_POINT;
                }
            }

            private byte currentByte() {
                return segments[segmentIndex].data[byteIndexInSegment];
            }

            private void advance(final @NonNegative int increment) {
                byteIndex += increment;
                if (byteIndex < nextSegmentOffset) {
                    // we stay in current segment
                    byteIndexInSegment += increment;
                    return;
                }
                if (byteIndex == byteSize()) {
                    return;
                }
                if (byteIndex > byteSize()) {
                    throw new JayoCharacterCodingException("unexpected end of byte string");
                }

                // we must switch to next segment until the expected increment
                while (byteIndex > nextSegmentOffset) {
                    nextSegmentOffset = directory[++segmentIndex];
                }
                final var offset = byteIndex - nextSegmentOffset;
                nextSegmentOffset = directory[++segmentIndex];
                byteIndexInSegment = directory[segments.length + segmentIndex] + offset;
            }

            @Override
            public void forEachRemaining(IntConsumer block) {
                while (byteIndex < byteSize()) {
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
    public Utf8 substring(final @NonNegative int startIndex) {
        return substring(startIndex, byteSize());
    }

    @Override
    public @NonNull Utf8 substring(final @NonNegative int startIndex, final @NonNegative int endIndex) {
        checkSubstringParameters(startIndex, endIndex);
        if (startIndex == 0 && endIndex == byteSize()) {
            return this;
        } else if (startIndex == endIndex) {
            return Utf8.EMPTY;
        }

        final var subLen = endIndex - startIndex;
        final var beginSegment = segment(startIndex); // First segment to include
        final var endSegment = segment(endIndex - 1); // Last segment to include

        final var newSegments = Arrays.copyOfRange(segments, beginSegment, endSegment + 1);
        final var newDirectory = new int[newSegments.length * 2];
        var index = 0;
        for (var s = beginSegment; s <= endSegment; s++) {
            newDirectory[index] = Math.min(directory[s] - startIndex, subLen);
            newDirectory[index++ + newSegments.length] = directory[s + segments.length];
        }

        // Set the new position of the first segment
        final var segmentOffset = (beginSegment == 0) ? 0 : directory[beginSegment - 1];
        newDirectory[newSegments.length] += startIndex - segmentOffset;

        return new SegmentedUtf8(newSegments, newDirectory, isAscii);
    }

    @Override
    public @NonNull Utf8 toAsciiLowercase() {
        return toByteString().toAsciiLowercase();
    }

    @Override
    public @NonNull Utf8 toAsciiUppercase() {
        return toByteString().toAsciiUppercase();
    }

    @Override
    public @NonNull String toString() {
        return decodeToString();
    }

    /**
     * Returns a copy as a non-segmented byte string.
     */
    @Override
    RealUtf8 toByteString() {
        return new RealUtf8(toByteArray(), isAscii);
    }

    private void fullScan() {
        final var byteSize = byteSize();
        var byteIndex = 0;
        var byteIndexInSegment = directory[segments.length];
        var segmentIndex = 0;
        var nextSegmentOffset = directory[0];
        var isAscii = true;
        while (true) {
            if ((segments[segmentIndex].data[byteIndexInSegment] & 0x80) != 0) {
                isAscii = false;
                break;
            }
            byteIndex++;
            if (byteIndex < nextSegmentOffset) {
                // we stay in current segment
                byteIndexInSegment++;
                continue;
            }
            if (byteIndex == byteSize) {
                break;
            }

            nextSegmentOffset = directory[++segmentIndex];
            byteIndexInSegment = directory[segments.length + segmentIndex];
        }

        this.isAscii = isAscii;
        if (isAscii) {
            this.length = byteSize;
            return;
        }

        var length = byteIndex;
        while (true) {
            final var b0 = segments[segmentIndex].data[byteIndexInSegment];
            final int increment;
            if ((b0 & 0x80) == 0) {
                // 0xxxxxxx : 7 bits (ASCII).
                increment = 1;
                length++;
            } else if ((b0 & 0xe0) == 0xc0) {
                // 0x110xxxxx : 11 bits (5 + 6).
                increment = 2;
                length++;
            } else if ((b0 & 0xf0) == 0xe0) {
                // 0x1110xxxx : 16 bits (4 + 6 + 6).
                increment = 3;
                length++;
            } else if ((b0 & 0xf8) == 0xf0) {
                // 0x11110xxx : 21 bits (3 + 6 + 6 + 6).
                increment = 4;
                length += 2;
            } else {
                // We expected the first byte of a code point but got something else.
                throw new JayoCharacterCodingException(
                        "We expected the first byte of a code point but got something else at byte " + (byteIndex));
            }

            byteIndex += increment;
            if (byteIndex < nextSegmentOffset) {
                // we stay in current segment
                byteIndexInSegment += increment;
                continue;
            }

            if (byteIndex == byteSize) {
                break;
            }
            if (byteIndex > byteSize) {
                throw new JayoCharacterCodingException("malformed input: partial character at end");
            }

            // we must switch to next segment until the expected increment
            while (byteIndex > nextSegmentOffset) {
                nextSegmentOffset = directory[++segmentIndex];
            }
            final var offset = byteIndex - nextSegmentOffset;
            nextSegmentOffset = directory[++segmentIndex];
            byteIndexInSegment = directory[segments.length + segmentIndex] + offset;
        }

        this.length = length;
    }

    // region native-jvm-serialization

    @Serial
    private @NonNull Object writeReplace() { // For Java Serialization.
        return toByteString();
    }

    // endregion
}
