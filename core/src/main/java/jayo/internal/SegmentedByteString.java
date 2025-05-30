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
import jayo.JayoException;
import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serial;
import java.util.Arrays;
import java.util.Objects;

import static jayo.internal.Utils.arrayRangeEquals;
import static jayo.tools.JayoUtils.checkOffsetAndCount;

/**
 * An immutable byte string composed of segments. This class exists to implement
 * efficient snapshots of buffers. It is implemented as an array of segments, plus a directory in
 * two halves that describes how the segments compose this byte string.
 * <p>
 * The first half of the directory is the cumulative byte count covered by each segment. The
 * element at `directory[0]` contains the number of bytes held in `segments[0]`; the
 * element at `directory[1]` contains the number of bytes held in `segments[0] +
 * segments[1]`, and so on. The element at `directory[segments.length - 1]` contains the total
 * size of this byte string. The first half of the directory is always monotonically increasing.
 * <p>
 * The second half of the directory is the offset in `segments` of the first content byte.
 * Bytes preceding this offset are unused, as are bytes beyond the segment's effective size.
 * <p>
 * Suppose we have a byte string, `[A, B, C, D, E, F, G, H, I, J, K, L, M]` that is stored
 * across three byte arrays: `[x, x, x, x, A, B, C, D, E, x, x, x]`, `[x, F, G]`, and `[H, I, J, K,
 * L, M, x, x, x, x, x, x]`. The three byte arrays would be stored in `segments` in order. Since the
 * arrays contribute 5, 2, and 6 elements respectively, the directory starts with `[5, 7, 13` to
 * hold the cumulative total at each position. Since the offsets into the arrays are 4, 1, and 0
 * respectively, the directory ends with `4, 1, 0]`. Concatenating these two halves, the complete
 * directory is `[5, 7, 13, 4, 1, 0]`.
 * <p>
 * This structure is chosen so that the segment holding a particular offset can be found by
 * binary search. We use one array rather than two for the directory as a micro-optimization.
 */
public sealed class SegmentedByteString extends BaseByteString implements ByteString permits SegmentedUtf8 {
    transient final @NonNull Segment @NonNull [] segments;
    transient final int @NonNull [] directory;

    SegmentedByteString(final @NonNull Segment @NonNull [] segments, final int @NonNull [] directory) {
        super(((RealByteString) EMPTY).data);
        this.segments = Objects.requireNonNull(segments);
        this.directory = Objects.requireNonNull(directory);
    }

    @Override
    public final @NonNull String base64() {
        return toByteString().base64();
    }

    @Override
    public final @NonNull String base64Url() {
        return toByteString().base64Url();
    }

    @Override
    public @NonNull ByteString toAsciiLowercase() {
        return toByteString().toAsciiLowercase();
    }

    @Override
    public @NonNull ByteString toAsciiUppercase() {
        return toByteString().toAsciiUppercase();
    }

    @Override
    public final @NonNull ByteString hash(final @NonNull Digest digest) {
        final var messageDigest = messageDigest(digest);
        forEachSegment((s, offset, byteCount) -> messageDigest.update(s.data, offset, byteCount));
        return new RealByteString(messageDigest.digest());
    }

    @Override
    public final @NonNull ByteString hmac(final @NonNull Hmac hMac, final @NonNull ByteString key) {
        final var javaMac = mac(hMac, key);
        forEachSegment((s, offset, byteCount) -> javaMac.update(s.data, offset, byteCount));
        return new RealByteString(javaMac.doFinal());
    }

    @Override
    public @NonNull ByteString substring(final int startIndex, final int endIndex) {
        checkSubstringParameters(startIndex, endIndex, byteSize());
        if (startIndex == 0 && endIndex == byteSize()) {
            return this;
        } else if (startIndex == endIndex) {
            return ByteString.EMPTY;
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

        return new SegmentedByteString(newSegments, newDirectory);
    }

    @Override
    public final byte getByte(final int index) {
        checkOffsetAndCount(directory[segments.length - 1], index, 1);
        final var segment = segment(index);
        final var segmentOffset = (segment == 0) ? 0 : directory[segment - 1];
        final var segmentPos = directory[segment + segments.length];
        return segments[segment].data[index - segmentOffset + segmentPos];
    }

    @Override
    public final int byteSize() {
        return directory[segments.length - 1];
    }

    @Override
    public final byte @NonNull [] toByteArray() {
        final var result = new byte[byteSize()];
        final var resultPos = new Wrapper.Int();
        forEachSegment((s, offset, byteCount) -> {
            System.arraycopy(s.data, offset, result, resultPos.value, byteCount);
            resultPos.value += byteCount;
        });
        return result;
    }

    @Override
    public final void write(final @NonNull OutputStream out) {
        Objects.requireNonNull(out);
        forEachSegment((s, offset, byteCount) -> {
            try {
                out.write(s.data, offset, byteCount);
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
        });
    }

    @Override
    final void write(final @NonNull RealBuffer buffer,
                     final int offset,
                     final int byteCount) {
        Objects.requireNonNull(buffer);
        forEachSegment(offset, offset + byteCount, (s, _offset, _byteCount) -> {
            s.pos = _offset;
            s.limit = _offset + _byteCount;
            final var copy = s.sharedCopy();
            final var bufferTail = buffer.segmentQueue.nonRemovedTailOrNull();
            buffer.segmentQueue.addWritableTail(bufferTail, copy, true);
            buffer.segmentQueue.incrementSize(_byteCount);
            return true;
        });
    }

    @Override
    public final boolean rangeEquals(final int offset,
                                     final @NonNull ByteString other,
                                     final int otherOffset,
                                     final int byteCount) {
        Objects.requireNonNull(other);
        if (offset < 0 || offset > byteSize() - byteCount) {
            return false;
        }
        // Go segment-by-segment through this, passing arrays to other's rangeEquals().
        final var _otherOffset = new Wrapper.Int(otherOffset);
        return forEachSegment(offset, offset + byteCount, (s, _offset, _byteCount) -> {
            if (!other.rangeEquals(_otherOffset.value, s.data, _offset, _byteCount)) {
                return false;
            }
            _otherOffset.value += _byteCount;
            return true;
        });
    }

    @Override
    public final boolean rangeEquals(final int offset,
                                     final byte @NonNull [] other,
                                     final int otherOffset,
                                     final int byteCount) {
        Objects.requireNonNull(other);
        if (offset < 0 || offset > byteSize() - byteCount ||
                otherOffset < 0 || otherOffset > other.length - byteCount
        ) {
            return false;
        }
        // Go segment-by-segment through this, comparing ranges of arrays.
        final var _otherOffset = new Wrapper.Int(otherOffset);
        return forEachSegment(offset, offset + byteCount, (s, _offset, _byteCount) -> {
            if (!arrayRangeEquals(s.data, _offset, other, _otherOffset.value, _byteCount)) {
                return false;
            }
            _otherOffset.value += _byteCount;
            return true;
        });
    }

    @Override
    public final void copyInto(final int offset,
                               final byte @NonNull [] target,
                               final int targetOffset,
                               final int byteCount) {
        Objects.requireNonNull(target);
        checkOffsetAndCount(byteSize(), offset, byteCount);
        checkOffsetAndCount(target.length, targetOffset, byteCount);
        // Go segment-by-segment through this, copying ranges of arrays.
        var _targetOffset = new Wrapper.Int(targetOffset);
        forEachSegment(offset, offset + byteCount, (s, _offset, _byteCount) -> {
            System.arraycopy(s.data, _offset, target, _targetOffset.value, _byteCount);
            _targetOffset.value += _byteCount;
            return true;
        });
    }

    @Override
    public final int indexOf(final byte @NonNull [] other, final int startIndex) {
        Objects.requireNonNull(other);
        return toByteString().indexOf(other, startIndex);
    }

    @Override
    public final int lastIndexOf(final byte @NonNull [] other, final int startIndex) {
        Objects.requireNonNull(other);
        return toByteString().lastIndexOf(other, startIndex);
    }

    /**
     * Returns a copy as a non-segmented byte string.
     */
    ByteString toByteString() {
        return new RealByteString(toByteArray());
    }

    @Override
    final byte @NonNull [] internalArray() {
        return toByteArray();
    }

    @Override
    public final boolean equals(final @Nullable Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof ByteString _other)) {
            return false;
        }
        return _other.byteSize() == byteSize() && rangeEquals(0, _other, 0, byteSize());
    }

    @Override
    public final int hashCode() {
        if (hashCode != 0) {
            return hashCode;
        }

        // Equivalent to Arrays.hashCode(toByteArray()).
        final var result = new Wrapper.Int(1);
        forEachSegment((s, offset, byteCount) -> {
            var i = offset;
            final var limit = offset + byteCount;
            while (i < limit) {
                result.value = (31 * result.value + s.data[i]);
                i++;
            }
        });
        hashCode = result.value;
        return hashCode;
    }

    @Override
    public @NonNull String toString() {
        return toByteString().toString();
    }

    /**
     * Processes the segments between `beginIndex` and `endIndex`, invoking `action` with the ByteArray
     * and range of the valid data.
     */
    final boolean forEachSegment(final int beginIndex,
                                 final int endIndex,
                                 final @NonNull TriPredicate<Segment, Integer, Integer> action) {
        Objects.requireNonNull(action);
        var segmentIndex = segment(beginIndex);
        var pos = beginIndex;
        while (pos < endIndex) {
            final var segmentOffset = (segmentIndex == 0) ? 0 : directory[segmentIndex - 1];
            final var segmentSize = directory[segmentIndex] - segmentOffset;
            final var segmentPos = directory[segments.length + segmentIndex];

            final var byteCount = Math.min(endIndex, segmentOffset + segmentSize) - pos;
            final var offset = segmentPos + (pos - segmentOffset);
            if (!action.test(segments[segmentIndex], offset, byteCount)) {
                return false;
            }
            pos += byteCount;
            segmentIndex++;
        }
        return true;
    }

    /**
     * Processes all segments, invoking `action` with the ByteArray and range of valid data.
     */
    private void forEachSegment(final @NonNull TriConsumer<Segment, Integer, Integer> action) {
        Objects.requireNonNull(action);
        var segmentIndex = 0;
        var pos = 0;
        while (segmentIndex < segments.length) {
            final var segmentPos = directory[segments.length + segmentIndex];
            final var nextSegmentOffset = directory[segmentIndex];

            action.accept(segments[segmentIndex], segmentPos, nextSegmentOffset - pos);
            pos = nextSegmentOffset;
            segmentIndex++;
        }
    }

    /**
     * Returns the index of the segment that contains the byte at `pos`.
     */
    final int segment(final int pos) {
        // Search for (pos + 1) instead of (pos) because the directory holds sizes, not indexes.
        final var i = binarySearch(pos + 1, segments.length);
        return (i >= 0) ? i : ~i; // If i is negative, bitflip to get the invert position.
    }

    private int binarySearch(final int value, final int endIndex) {
        var left = 0;
        var right = endIndex - 1;

        while (left <= right) {
            final var mid = (left + right) >>> 1; // protect from overflow
            final var midVal = directory[mid];

            if (midVal < value) {
                left = mid + 1;
            } else if (midVal > value) {
                right = mid - 1;
            } else {
                return mid;
            }
        }

        // no exact match, return negative of where it should match
        return -left - 1;
    }

    @FunctionalInterface
    private interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    @FunctionalInterface
    interface TriPredicate<T, U, V> {
        boolean test(T t, U u, V v);
    }

    // region native-jvm-serialization

    @Serial
    private @NonNull Object writeReplace() { // For Java Serialization.
        return toByteString();
    }

    // endregion
}
