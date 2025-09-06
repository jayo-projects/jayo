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
import jayo.JayoException;
import jayo.bytestring.ByteString;
import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serial;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;

import static java.lang.System.Logger.Level.TRACE;
import static jayo.internal.ByteStringUtils.checkSubstringParameters;
import static jayo.internal.Utils.*;
import static jayo.tools.JayoUtils.checkOffsetAndCount;

/**
 * An immutable byte string composed of segments. This class exists to implement efficient snapshots of buffers. It is
 * implemented as an array of segments, plus a directory in two halves that describes how the segments compose this byte
 * string.
 * <p>
 * The directory array contains the cumulative byte count covered by each segment. The element at {@code directory[0]}
 * contains the number of bytes held in {@code segments[0]}; the element at {@code directory[1]} contains the number of
 * bytes held in {@code segments[0] + segments[1]}, and so on. The element at {@code directory[segments.length - 1]}
 * contains the total size of this byte string. Directory is always monotonically increasing.
 * <p>
 * Suppose we have a byte string, {@code [A, B, C, D, E, F, G, H, I, J, K, L, M]} that is stored
 * across three memory segments: {@code [x, x, x, x, A, B, C, D, E, x, x, x]}, {@code [x, F, G]}, and {@code [H, I, J,
 * K, L, M, x, x, x, x, x, x]}. They would be stored in {@code segments} in order. Since the arrays contribute 5, 2, and
 * 6 elements respectively, the {@code directory} contains {@code [5, 7, 13} to hold the cumulative total at each
 * position.
 * <p>
 * This structure is chosen so that the segment holding a particular offset can be found by an efficient binary search.
 */
public final class SegmentedByteString implements ByteString {
    @Serial
    private static final long serialVersionUID = 43L;

    transient final @NonNull Segment @NonNull [] segments;
    private transient final int @NonNull [] directory;

    private transient int hashCode = 0; // Lazily computed; 0 if unknown.
    private transient @Nullable String utf8; // Lazily computed.

    SegmentedByteString(final @NonNull Segment @NonNull [] segments, final int @NonNull [] directory) {
        assert segments != null;
        assert directory != null;

        this.segments = segments;
        this.directory = directory;
        JAYO_CLEANER.register(this, new SegmentsRecycler(segments));
    }

    @Override
    public @NonNull String decodeToString() {
        var utf8String = utf8;
        if (utf8String == null) {
            // We don't care if we double-allocate in racy code.
            utf8String = new String(toByteArray(), StandardCharsets.UTF_8);
            utf8 = utf8String;
        }
        return utf8String;
    }

    @Override
    public @NonNull ByteString hash(final @NonNull Digest digest) {
        Objects.requireNonNull(digest);

        final var messageDigest = messageDigest(digest);
        forEachSegment((s, byteCount) -> messageDigest.update(s.data, s.pos, byteCount));
        return new RealByteString(messageDigest.digest());
    }

    @Override
    public @NonNull ByteString hmac(final @NonNull Hmac hMac, final @NonNull ByteString key) {
        Objects.requireNonNull(hMac);
        Objects.requireNonNull(key);

        final var javaMac = mac(hMac, key);
        forEachSegment((s, byteCount) -> javaMac.update(s.data, s.pos, byteCount));
        return new RealByteString(javaMac.doFinal());
    }

    @Override
    public @NonNull ByteString substring(final int startIndex) {
        return substring(startIndex, byteSize());
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
        final var newDirectory = new int[newSegments.length];
        var index = 0;
        for (var s = beginSegment; s <= endSegment; s++) {
            // replace each segment in the substring with a new shared copy
            newSegments[index] = newSegments[index].sharedCopy();
            newDirectory[index++] = Math.min(directory[s] - startIndex, subLen);
        }

        // Set the new position of the first segment
        final var segmentOffset = (beginSegment == 0) ? 0 : directory[beginSegment - 1];
        newSegments[0].pos += startIndex - segmentOffset;

        return new SegmentedByteString(newSegments, newDirectory);
    }

    @Override
    public byte getByte(final int index) {
        checkOffsetAndCount(directory[segments.length - 1], index, 1);

        final var segmentIndex = segment(index);
        final var segmentOffset = (segmentIndex == 0) ? 0 : directory[segmentIndex - 1];
        final var segment = segments[segmentIndex];
        return segment.data[index - segmentOffset + segment.pos];
    }

    @Override
    public int byteSize() {
        return directory[segments.length - 1];
    }

    @Override
    public boolean isEmpty() {
        return byteSize() == 0;
    }

    @Override
    public byte @NonNull [] toByteArray() {
        final var result = new byte[byteSize()];
        final var resultPos = new Wrapper.Int();
        forEachSegment((s, byteCount) -> {
            System.arraycopy(s.data, s.pos, result, resultPos.value, byteCount);
            resultPos.value += byteCount;
        });
        return result;
    }

    @Override
    public @NonNull ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(toByteArray());
    }

    @Override
    public void write(final @NonNull OutputStream out) {
        Objects.requireNonNull(out);

        forEachSegment((s, byteCount) -> {
            try {
                out.write(s.data, s.pos, byteCount);
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
        });
    }

    /**
     * Writes the contents of this byte string to {@code buffer}.
     */
    void write(final @NonNull RealBuffer buffer,
               final int offset,
               final int byteCount) {
        assert buffer != null;

        forEachSegment(offset, offset + byteCount, (s, _offset, _byteCount) -> {
            // build a new shared segment from the current segment
            s.limit = _offset + _byteCount;
            final var copy = s.sharedCopy();

            if (buffer.head == null) {
                copy.prev = copy;
                copy.next = copy;
                buffer.head = copy;
            } else {
                assert buffer.head.prev != null;
                buffer.head.prev.push(copy);
            }
            return true;
        });
        buffer.byteSize += byteCount;
    }

    @Override
    public boolean rangeEquals(final int offset,
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
    public boolean rangeEquals(final int offset,
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
    public void copyInto(final int offset,
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
    public @NonNull String decodeToString(final @NonNull Charset charset) {
        Objects.requireNonNull(charset);
        return toRealByteString().decodeToString(charset);
    }

    @Override
    public @NonNull String base64() {
        return toRealByteString().base64();
    }

    @Override
    public @NonNull String base64Url() {
        return toRealByteString().base64Url();
    }

    @Override
    public @NonNull String hex() {
        return toRealByteString().hex();
    }

    @Override
    public @NonNull ByteString toAsciiLowercase() {
        return toRealByteString().toAsciiLowercase();
    }

    @Override
    public @NonNull ByteString toAsciiUppercase() {
        return toRealByteString().toAsciiUppercase();
    }

    @Override
    public boolean startsWith(final @NonNull ByteString prefix) {
        return rangeEquals(0, prefix, 0, prefix.byteSize());
    }

    @Override
    public boolean startsWith(final byte @NonNull [] prefix) {
        return rangeEquals(0, prefix, 0, prefix.length);
    }

    @Override
    public boolean endsWith(final @NonNull ByteString suffix) {
        return rangeEquals(byteSize() - suffix.byteSize(), suffix, 0, suffix.byteSize());
    }

    @Override
    public boolean endsWith(final byte @NonNull [] suffix) {
        return rangeEquals(byteSize() - suffix.length, suffix, 0, suffix.length);
    }

    @Override
    public int indexOf(final @NonNull ByteString other) {
        return indexOf(other, 0);
    }

    @Override
    public int indexOf(final @NonNull ByteString other, final int startIndex) {
        return indexOf(Utils.internalArray(other), startIndex);
    }

    @Override
    public int indexOf(final byte @NonNull [] other) {
        return indexOf(other, 0);
    }

    @Override
    public int indexOf(final byte @NonNull [] other, final int startIndex) {
        Objects.requireNonNull(other);
        return toRealByteString().indexOf(other, startIndex);
    }

    @Override
    public int lastIndexOf(final @NonNull ByteString other) {
        return lastIndexOf(other, byteSize());
    }

    @Override
    public int lastIndexOf(final @NonNull ByteString other, final int startIndex) {
        return lastIndexOf(Utils.internalArray(other), startIndex);
    }

    @Override
    public int lastIndexOf(final byte @NonNull [] other) {
        return lastIndexOf(other, byteSize());
    }

    @Override
    public int lastIndexOf(final byte @NonNull [] other, final int startIndex) {
        Objects.requireNonNull(other);
        return toRealByteString().lastIndexOf(other, startIndex);
    }

    /**
     * Returns a copy as a non-segmented byte string.
     */
    private ByteString toRealByteString() {
        return new RealByteString(toByteArray());
    }

    @Override
    public boolean equals(final @Nullable Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof ByteString that)) {
            return false;
        }
        return that.byteSize() == byteSize() && rangeEquals(0, that, 0, byteSize());
    }

    @Override
    public int hashCode() {
        if (hashCode != 0) {
            return hashCode;
        }

        // Equivalent to Arrays.hashCode(toByteArray()).
        final var result = new Wrapper.Int(1);
        forEachSegment((s, byteCount) -> {
            var i = s.pos;
            final var limit = i + byteCount;
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
        return toRealByteString().toString();
    }

    @Override
    public int compareTo(final @NonNull ByteString other) {
        return ByteStringUtils.compareTo(this, other);
    }

    /**
     * Processes the segments between `beginIndex` and `endIndex`, invoking `action` with the ByteArray
     * and range of the valid data.
     */
    private boolean forEachSegment(final int beginIndex,
                                   final int endIndex,
                                   final @NonNull TriPredicate<Segment, Integer, Integer> action) {
        assert action != null;

        var segmentIndex = segment(beginIndex);
        var pos = beginIndex;
        while (pos < endIndex) {
            final var segmentOffset = (segmentIndex == 0) ? 0 : directory[segmentIndex - 1];
            final var segmentSize = directory[segmentIndex] - segmentOffset;
            final var byteCount = Math.min(endIndex, segmentOffset + segmentSize) - pos;
            final var segment = segments[segmentIndex];
            final var offset = segment.pos + (pos - segmentOffset);

            if (!action.test(segment, offset, byteCount)) {
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
    private void forEachSegment(final @NonNull BiConsumer<Segment, Integer> action) {
        assert action != null;

        var segmentIndex = 0;
        var pos = 0;
        while (segmentIndex < segments.length) {
            final var nextSegmentOffset = directory[segmentIndex];
            final var segment = segments[segmentIndex];
            action.accept(segment, nextSegmentOffset - pos);
            pos = nextSegmentOffset;
            segmentIndex++;
        }
    }

    /**
     * Returns the index of the segment that contains the byte at `pos`.
     */
    int segment(final int pos) {
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
    interface TriPredicate<T, U, V> {
        boolean test(T t, U u, V v);
    }

    record SegmentsRecycler(@NonNull Segment @NonNull [] segments) implements Runnable {
        static final System.Logger LOGGER = System.getLogger("jayo.byteString.SegmentsRecycler");

        @Override
        public void run() {
            for (final var segment : segments) {
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, "Recycling Segment#{0} from the cleaned segmented ByteString",
                            segment.hashCode());
                }
                SegmentPool.recycle(segment);
            }
        }
    }

    // region native-jvm-serialization

//    @Serial
//    private void readObject(final @NonNull ObjectInputStream in) throws IOException {
//        final var dataLength = in.readInt();
//        final var bytes = in.readNBytes(dataLength);
//        final var isAscii = in.readBoolean();
//        final var length = in.readInt();
//        final Field dataField;
//        final Field isAsciiField;
//        final Field lengthField;
//        try {
//            dataField = ByteStringUtils.class.getDeclaredField("data");
//            isAsciiField = ByteStringUtils.class.getDeclaredField("isAscii");
//            lengthField = ByteStringUtils.class.getDeclaredField("length");
//        } catch (NoSuchFieldException e) {
//            throw new IllegalStateException("ByteStringUtils should contain 'data', 'isAscii' and 'length' fields", e);
//        }
//        dataField.setAccessible(true);
//        isAsciiField.setAccessible(true);
//        lengthField.setAccessible(true);
//        try {
//            dataField.set(this, bytes);
//            isAsciiField.set(this, isAscii);
//            lengthField.set(this, length);
//        } catch (IllegalAccessException e) {
//            throw new IllegalStateException("It should be possible to set ByteStringUtils's 'data', 'isAscii' and " +
//                    "'length' fields", e);
//        }
//    }

    @Serial
    private @NonNull Object writeReplace() { // For Java Serialization.
        return toRealByteString();
    }

    // endregion

    public static int utf8Length(final @NonNull SegmentedByteString byteString) {
        assert byteString != null;

        final var byteSize = byteString.byteSize();
        final var directory = byteString.directory;
        final var segments = byteString.segments;

        var byteIndex = 0;
        var segmentIndex = 0;
        var nextSegmentOffset = directory[0];
        var byteIndexInSegment = segments[0].pos;
        while (segments[segmentIndex].data[byteIndexInSegment] >= 0) {
            byteIndex++;
            if (byteIndex < nextSegmentOffset) {
                // we stay in the current segment
                byteIndexInSegment++;
                continue;
            }
            if (byteIndex == byteSize) {
                return byteSize;
            }

            nextSegmentOffset = directory[++segmentIndex];
            byteIndexInSegment = segments[segmentIndex].pos;
        }

        var length = byteIndex;
        while (true) {
            final var b0 = segments[segmentIndex].data[byteIndexInSegment];
            final int increment;
            if (b0 >= 0) {
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
                // we stay in the current segment
                byteIndexInSegment += increment;
                continue;
            }

            if (byteIndex == byteSize) {
                return length;
            }
            if (byteIndex > byteSize) {
                throw new JayoCharacterCodingException("malformed input: partial character at end");
            }

            // we must switch to the next segment until the expected increment
            int offset = 0;
            while (byteIndex >= nextSegmentOffset) {
                offset = byteIndex - nextSegmentOffset;
                nextSegmentOffset = directory[++segmentIndex];
            }
            byteIndexInSegment = segments[segmentIndex].pos + offset;
        }
    }
}
