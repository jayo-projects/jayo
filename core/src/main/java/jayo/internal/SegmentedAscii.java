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
import jayo.bytestring.Ascii;
import org.jspecify.annotations.NonNull;

import java.io.Serial;
import java.util.Arrays;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static jayo.internal.Utf8Utils.ASCII_REPLACEMENT_CHARACTER;
import static jayo.internal.Utf8Utils.ASCII_REPLACEMENT_CODE_POINT;

public final class SegmentedAscii extends SegmentedUtf8 implements Ascii {
    SegmentedAscii(final byte @NonNull [] @NonNull [] segments, final int @NonNull [] directory) {
        super(segments, directory, true);
        length = byteSize();
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public char charAt(final int index) {
        final var b = getByte(index);
        if ((b & 0x80) == 0) {
            // valid ASCII
            return (char) (b & 0x7f);
        }

        return ASCII_REPLACEMENT_CHARACTER;
    }

    @Override
    public @NonNull CharSequence subSequence(final int start, final int end) {
        return substring(start, end);
    }

    @NonNull
    public IntStream codePoints() {
        class CodePointIterator implements PrimitiveIterator.OfInt {
            private int byteIndex = 0;
            private int byteIndexInSegment = directory[segments.length];
            private int segmentIndex = 0;
            private int nextSegmentOffset = directory[0];

            public boolean hasNext() {
                return byteIndex < byteSize();
            }

            public int nextInt() {
                final var b = currentByte();
                advance();
                if ((b & 0x80) == 0) {
                    // 0xxxxxxx : 7 bits (ASCII).
                    return b & 0x7f;
                }
                return ASCII_REPLACEMENT_CODE_POINT;
            }

            private byte currentByte() {
                return segments[segmentIndex][byteIndexInSegment];
            }

            private void advance() {
                byteIndex += 1;
                if (byteIndex < nextSegmentOffset) {
                    // we stay in current segment
                    byteIndexInSegment += 1;
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
    public Ascii substring(final int startIndex) {
        return substring(startIndex, byteSize());
    }

    @Override
    public @NonNull Ascii substring(final int startIndex, final int endIndex) {
        checkSubstringParameters(startIndex, endIndex, byteSize());
        if (startIndex == 0 && endIndex == byteSize()) {
            return this;
        } else if (startIndex == endIndex) {
            return Ascii.EMPTY;
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

        return new SegmentedAscii(newSegments, newDirectory);
    }

    @Override
    public @NonNull Ascii toAsciiLowercase() {
        return toByteString().toAsciiLowercase();
    }

    @Override
    public @NonNull Ascii toAsciiUppercase() {
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
    Ascii toByteString() {
        return new RealAscii(toByteArray());
    }

    // region native-jvm-serialization

    @Serial
    private @NonNull Object writeReplace() { // For Java Serialization.
        return toByteString();
    }

    // endregion
}
