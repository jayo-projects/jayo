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

import jayo.*;
import jayo.bytestring.Ascii;
import jayo.bytestring.ByteString;
import jayo.bytestring.Utf8;
import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import jayo.tools.BasicLock;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

import static java.lang.System.Logger.Level.TRACE;
import static jayo.internal.Segment.TRANSFERRING;
import static jayo.internal.Segment.WRITING;
import static jayo.internal.UnsafeUtils.*;
import static jayo.internal.Utf8Utils.UTF8_REPLACEMENT_CODE_POINT;
import static jayo.internal.Utils.*;
import static jayo.tools.JayoUtils.checkOffsetAndCount;


public final class RealBuffer implements Buffer {
    private static final System.Logger LOGGER = System.getLogger("jayo.Buffer");

    final @NonNull SegmentQueue segmentQueue;

    public RealBuffer() {
        this(new SegmentQueue(BasicLock.create()));
    }

    RealBuffer(final @NonNull SegmentQueue segmentQueue) {
        this.segmentQueue = Objects.requireNonNull(segmentQueue);
    }

    @Override
    public long bytesAvailable() {
        return segmentQueue.size();
    }

    @Override
    public @NonNull Buffer emitCompleteSegments() {
        return this; // Nowhere to emit to!
    }

    @Override
    public @NonNull Buffer emit() {
        return this; // Nowhere to emit to!
    }

    @Override
    public @NonNull Reader peek() {
        return new RealReader(new PeekRawReader(this));
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull OutputStream out) {
        return copyTo(out, 0L);
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull OutputStream out, final long offset) {
        return copyTo(out, offset, bytesAvailable() - offset);
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull OutputStream out,
                                  final long offset,
                                  final long byteCount) {
        Objects.requireNonNull(out);
        checkOffsetAndCount(bytesAvailable(), offset, byteCount);
        if (byteCount == 0L) {
            return this;
        }

        var _offset = offset;
        var _byteCount = byteCount;

        // Skip segments that we aren't copying from.
        var segment = segmentQueue.headRef.value();
        assert segment != null;
        var segmentSize = segment.limit - segment.pos;
        while (_offset >= segmentSize) {
            _offset -= segmentSize;
            segment = segment.next;
            assert segment != null;
            segmentSize = segment.limit - segment.pos;
        }

        // Copy from one segment at a time.
        while (_byteCount > 0L) {
            assert segment != null;
            final var pos = (int) (segment.pos + _offset);
            final var toCopy = (int) Math.min(segment.limit - pos, _byteCount);
            try {
                out.write(segment.data, pos, toCopy);
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
            _byteCount -= toCopy;
            _offset = 0L;
            segment = segment.next;
        }
        return this;
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull Buffer out) {
        return copyTo(out, 0L);
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull Buffer out, final long offset) {
        return copyTo(out, offset, bytesAvailable() - offset);
    }

    @Override
    public @NonNull Buffer copyTo(final @NonNull Buffer out,
                                  final long offset,
                                  final long byteCount) {
        Objects.requireNonNull(out);
        checkOffsetAndCount(bytesAvailable(), offset, byteCount);
        if (!(out instanceof RealBuffer _out)) {
            throw new IllegalArgumentException("out must be an instance of RealBuffer");
        }
        if (byteCount == 0L) {
            return this;
        }

        var _offset = offset;
        var _byteCount = byteCount;

        // Skip segment nodes that we aren't copying from.
        var segment = segmentQueue.headRef.value();
        assert segment != null;
        var segmentSize = segment.limit - segment.pos;
        while (_offset >= segmentSize) {
            _offset -= segmentSize;
            segment = segment.next;
            assert segment != null;
            segmentSize = segment.limit - segment.pos;
        }

        // Copy from one segment at a time.
        while (_byteCount > 0L) {
            assert segment != null;
            final var segmentCopy = segment.sharedCopy();
            final var pos = (int) (segmentCopy.pos + _offset);
            segmentCopy.pos = pos;
            final var limit = Math.min(pos + (int) _byteCount, segmentCopy.limit);
            segmentCopy.limit = limit;
            final var written = limit - pos;
            final var outTail = _out.segmentQueue.nonRemovedTailOrNull();
            _out.segmentQueue.addWritableTail(outTail, segmentCopy, true);
            _out.segmentQueue.incrementSize(written);
            _byteCount -= written;
            _offset = 0L;
            segment = segment.next;
        }
        return this;
    }

    @Override
    public @NonNull Buffer readTo(final @NonNull OutputStream out) {
        return readTo(out, segmentQueue.size());
    }

    @Override
    public @NonNull Buffer readTo(final @NonNull OutputStream out, final long byteCount) {
        Objects.requireNonNull(out);
        checkOffsetAndCount(segmentQueue.size(), 0L, byteCount);
        if (byteCount == 0L) {
            return this;
        }

        var remaining = byteCount;
        var head = segmentQueue.head();
        assert head != null;
        var headLimit = head.limit;
        while (remaining > 0L) {
            if (head.pos == headLimit) {
                head = segmentQueue.removeHead(head, true);
                assert head != null;
                headLimit = head.limit;
            }

            final var toCopy = (int) Math.min(remaining, headLimit - head.pos);
            try {
                out.write(head.data, head.pos, toCopy);
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
            head.pos += toCopy;
            segmentQueue.decrementSize(toCopy);
            remaining -= toCopy;
        }
        if (head.pos == head.limit) {
            segmentQueue.removeHead(head, false);
        }

        return this;
    }

    @Override
    public @NonNull Buffer transferFrom(final @NonNull InputStream input) {
        write(input, Long.MAX_VALUE, true);
        return this;
    }

    @Override
    public @NonNull Buffer write(final @NonNull InputStream input, final long byteCount) {
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        write(input, byteCount, false);
        return this;
    }

    private void write(final @NonNull InputStream in,
                       final long byteCount,
                       final boolean forever) {
        Objects.requireNonNull(in);
        final var _byteCount = new Wrapper.Long(byteCount);

        while (_byteCount.value > 0L || forever) {
            final var success = segmentQueue.withWritableTail(1, tail -> {
                final var tailLimit = tail.limit;
                final var toRead = (int) Math.min(_byteCount.value, Segment.SIZE - tailLimit);
                final int read;
                try {
                    read = in.read(tail.data, tailLimit, toRead);
                } catch (IOException e) {
                    throw JayoException.buildJayoException(e);
                }
                if (read == -1) {
                    return false;
                }
                _byteCount.value -= read;
                tail.limit = tailLimit + read;
                return true;
            });
            if (!success) {
                if (forever) {
                    return;
                }
                throw new JayoEOFException();
            }
        }
    }

    @Override
    public long completeSegmentByteCount() {
        // Omit the tail if it's still writable.
        final var tail = segmentQueue.nonRemovedTailOrNull();
        if (tail == null) {
            return 0L;
        }
        try {
            var result = segmentQueue.size();
            final var tailLimit = tail.limit;
            if (tail.owner && tailLimit < Segment.SIZE) {
                result -= (tailLimit - tail.pos);
            }
            return result;
        } finally {
            tail.finishWrite();
        }
    }

    @Override
    public byte getByte(final long pos) {
        checkOffsetAndCount(segmentQueue.size(), pos, 1L);
        return seek(pos, (segment, offset) -> segment.data[(int) (segment.pos + pos - offset)]);
    }

    @Override
    public boolean exhausted() {
        return segmentQueue.size() == 0L;
    }


    @Override
    public boolean request(final long byteCount) {
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0L: " + byteCount);
        }
        return segmentQueue.size() >= byteCount;
    }

    @Override
    public void require(final long byteCount) {
        if (!request(byteCount)) {
            throw new JayoEOFException();
        }
    }

    @Override
    public byte readByte() {
        if (segmentQueue.size() == 0L) {
            throw new JayoEOFException();
        }

        final var head = segmentQueue.head();
        assert head != null;
        return readByte(head);
    }

    private byte readByte(final @NonNull Segment head) {
        final var b = head.data[head.pos++];
        segmentQueue.decrementSize(1L);

        if (head.pos == head.limit) {
            segmentQueue.removeHead(head, false);
        }
        return b;
    }

    @Override
    public short readShort() {
        if (segmentQueue.size() < 2L) {
            throw new JayoEOFException();
        }

        var head = segmentQueue.head();
        assert head != null;
        final var currentLimit = head.limit;
        // If the short is split across multiple segments, delegate to readByte().
        if (currentLimit - head.pos < 2) {
            return (short) (((readByte(head) & 0xff) << 8) | (readByte() & 0xff));
        }

        final var s = (short) (((head.data[head.pos++] & 0xff) << 8) | (head.data[head.pos++] & 0xff));
        segmentQueue.decrementSize(2L);

        if (head.pos == currentLimit) {
            segmentQueue.removeHead(head, false);
        }
        return s;
    }

    @Override
    public int readInt() {
        if (segmentQueue.size() < 4L) {
            throw new JayoEOFException();
        }

        final var head = segmentQueue.head();
        assert head != null;
        return readInt(head);
    }

    private int readInt(final @NonNull Segment head) {
        final var currentLimit = head.limit;
        // If the int is split across multiple segments, delegate to readByte().
        if (currentLimit - head.pos < 4L) {
            return (((readByte(head) & 0xff) << 24)
                    | ((readByte() & 0xff) << 16)
                    | ((readByte() & 0xff) << 8)
                    | (readByte() & 0xff));
        }

        final var i = (((head.data[head.pos++] & 0xff) << 24)
                | ((head.data[head.pos++] & 0xff) << 16)
                | ((head.data[head.pos++] & 0xff) << 8)
                | (head.data[head.pos++] & 0xff));
        segmentQueue.decrementSize(4L);

        if (head.pos == currentLimit) {
            segmentQueue.removeHead(head, false);
        }
        return i;
    }

    @Override
    public long readLong() {
        if (segmentQueue.size() < 8L) {
            throw new JayoEOFException();
        }

        final var head = segmentQueue.head();
        assert head != null;
        final var currentLimit = head.limit;
        // If the long is split across multiple segments, delegate to readInt().
        if (currentLimit - head.pos < 8L) {
            return (((readInt(head) & 0xffffffffL) << 32) | (readInt() & 0xffffffffL));
        }

        final var l = (((head.data[head.pos++] & 0xffL) << 56)
                | ((head.data[head.pos++] & 0xffL) << 48)
                | ((head.data[head.pos++] & 0xffL) << 40)
                | ((head.data[head.pos++] & 0xffL) << 32)
                | ((head.data[head.pos++] & 0xffL) << 24)
                | ((head.data[head.pos++] & 0xffL) << 16)
                | ((head.data[head.pos++] & 0xffL) << 8)
                | (head.data[head.pos++] & 0xffL));
        segmentQueue.decrementSize(8L);

        if (head.pos == currentLimit) {
            segmentQueue.removeHead(head, false);
        }
        return l;
    }

    @Override
    public long readDecimalLong() {
        final var currentSize = segmentQueue.size();
        if (currentSize == 0L) {
            throw new JayoEOFException();
        }

        // This value is always built negatively in order to accommodate Long.MIN_VALUE.
        var value = 0L;
        var seen = 0;
        var negative = false;
        var done = false;

        var overflowDigit = OVERFLOW_DIGIT_START;
        while (!done) {
            if (seen >= currentSize && segmentQueue.expectSize(20L) == 0L) {
                break;
            }
            final var head = segmentQueue.head();
            assert head != null;
            final var data = head.data;
            var pos = head.pos;
            final var currentLimit = head.limit;

            while (pos < currentLimit) {
                final var b = data[pos];
                if (b >= (byte) ((int) '0') && b <= (byte) ((int) '9')) {
                    final var digit = (byte) ((int) '0') - b;

                    // Detect when the digit would cause an overflow.
                    if (value < OVERFLOW_ZONE || value == OVERFLOW_ZONE
                            && digit < overflowDigit) {
                        try (final var buffer = new RealBuffer()) {
                            buffer.writeDecimalLong(value).writeByte(b);
                            if (!negative) {
                                buffer.readByte(); // Skip negative sign.
                            }
                            throw new NumberFormatException("Number too large: " + buffer.readString());
                        }
                    }
                    value *= 10L;
                    value += digit;
                } else if (b == (byte) ((int) '-') && seen == 0) {
                    negative = true;
                    overflowDigit -= 1;
                } else {
                    // Set a flag to stop iteration. We still need to run through segment updating below.
                    done = true;
                    break;
                }
                pos++;
                seen++;
            }

            final var read = pos - head.pos;
            head.pos = pos;
            segmentQueue.decrementSize(read);

            if (pos == currentLimit) {
                segmentQueue.removeHead(head, false);
            }
        }

        final var minimumSeen = (negative) ? 2 : 1;
        if (seen < minimumSeen) {
            if (segmentQueue.size() == 0L) {
                throw new JayoEOFException();
            }
            final var expected = (negative) ? "Expected a digit" : "Expected a digit or '-'";
            throw new NumberFormatException(expected + " but was 0x" + toHexString(getByte(0)));
        }

        return (negative) ? value : -value;
    }

    @Override
    public long readHexadecimalUnsignedLong() {
        final var currentSize = segmentQueue.size();
        if (currentSize == 0L) {
            throw new JayoEOFException();
        }

        // This value is always built negatively in order to accommodate Long.MIN_VALUE.
        var value = 0L;
        var seen = 0;
        var done = false;

        while (!done) {
            if (seen >= currentSize && segmentQueue.expectSize(17L) == 0L) {
                break;
            }
            final var head = segmentQueue.head();
            assert head != null;
            final var data = head.data;
            var pos = head.pos;
            final var currentLimit = head.limit;

            while (pos < currentLimit) {
                final int digit;

                final var b = data[pos];
                if (b >= (byte) ((int) '0') && b <= (byte) ((int) '9')) {
                    digit = b - (byte) ((int) '0');
                } else if (b >= (byte) ((int) 'a') && b <= (byte) ((int) 'f')) {
                    digit = b - (byte) ((int) 'a') + 10;
                } else if (b >= (byte) ((int) 'A') && b <= (byte) ((int) 'F')) {
                    digit = b - (byte) ((int) 'A') + 10; // We never write uppercase, but we support reading it.
                } else {
                    if (seen == 0) {
                        throw new NumberFormatException(
                                "Expected leading [0-9a-fA-F] character but was 0x" + toHexString(b));
                    }
                    // Set a flag to stop iteration. We still need to run through segment updating below.
                    done = true;
                    break;
                }

                // Detect when the shift will overflow.
                if ((value & -0x1000000000000000L) != 0L) {
                    try (final var buffer = new RealBuffer()) {
                        buffer.writeHexadecimalUnsignedLong(value).writeByte(b);
                        throw new NumberFormatException("Number too large: " + buffer.readString());
                    }
                }

                value = value << 4;
                value = value | (long) digit;
                pos++;
                seen++;
            }

            final var read = pos - head.pos;
            head.pos = pos;
            segmentQueue.decrementSize(read);

            if (pos == currentLimit) {
                segmentQueue.removeHead(head, false);
            }
        }

        return value;
    }

    @Override
    public @NonNull ByteString readByteString() {
        return readByteString(segmentQueue.size());
    }

    // Threshold determined empirically via ReadByteStringBenchmark
    /**
     * Create SegmentedByteString when size is greater than this many bytes.
     */
    private static final int SEGMENTING_THRESHOLD = 4096;

    @Override
    public @NonNull ByteString readByteString(final long byteCount) {
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        if (segmentQueue.size() < byteCount) {
            throw new JayoEOFException();
        }
        if (byteCount == 0L) {
            return ByteString.EMPTY;
        }

        if (byteCount >= SEGMENTING_THRESHOLD) {
            final var byteStringBuilder = prepareByteString(byteCount);
            final var segments = byteStringBuilder.segments.toArray(new Segment[0]);
            final var size = byteStringBuilder.offsets.size();
            final var directory = new int[size * 2];
            for (var i = 0; i < size; i++) {
                directory[i] = byteStringBuilder.offsets.get(i);
                directory[i + size] = byteStringBuilder.positions.get(i);
            }
            return new SegmentedByteString(segments, directory);
        }

        return new RealByteString(readByteArray(byteCount));
    }

    private @NonNull ByteStringBuilder prepareByteString(final long byteCount) {
        final var byteStringBuilder = new ByteStringBuilder();
        var head = segmentQueue.head();
        var offset = 0;
        var finished = false;
        while (!finished) {
            assert head != null;
            final var copy = head.sharedCopy();
            byteStringBuilder.segments.add(copy);
            final var copyPos = copy.pos;
            final var segmentSize = copy.limit - copyPos;
            offset += segmentSize;
            finished = offset >= byteCount;
            // Despite sharing more bytes, only report having up to byteCount.
            byteStringBuilder.offsets.add((int) Math.min(offset, byteCount));
            byteStringBuilder.positions.add(copyPos);
            if (offset <= byteCount) {
                final var oldHead = head;
                var mustRecycle = false;
                segmentQueue.lock.lock();
                try {
                    if (finished) {
                        if (head.tryRemove()) {
                            segmentQueue.decrementSize(segmentSize);
                            segmentQueue.removeHeadMaybeSplit(head, false);
                            mustRecycle = true;
                        } else {
                            // if a write is ongoing, we do not remove the segment, in this case we increment its pos
                            head.pos += segmentSize;
                            segmentQueue.decrementSize(segmentSize);
                        }
                    } else {
                        if (!head.tryRemove()) {
                            throw new IllegalStateException("Non tail segment must be removable");
                        }
                        segmentQueue.decrementSize(segmentSize);
                        head = segmentQueue.removeHeadMaybeSplit(head, false);
                        mustRecycle = true;
                    }
                } finally {
                    segmentQueue.lock.unlock();
                }
                if (mustRecycle) {
                    SegmentPool.recycle(oldHead);
                }
            } else {
                final var toRead = (int) (segmentSize - (offset - byteCount));
                head.pos += toRead;
                segmentQueue.decrementSize(toRead);
            }
        }
        return byteStringBuilder;
    }

    private static class ByteStringBuilder {
        private final List<Segment> segments = new ArrayList<>();
        private final List<Integer> offsets = new ArrayList<>();
        private final List<Integer> positions = new ArrayList<>();
    }

    @Override
    public @NonNull Utf8 readUtf8() {
        return readUtf8(segmentQueue.size());
    }

    public @NonNull Utf8 readUtf8(final long byteCount) {
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        if (segmentQueue.size() < byteCount) {
            throw new JayoEOFException();
        }

        return readUtf8(byteCount, false);
    }

    @NonNull
    Utf8 readUtf8(final long byteCount, final boolean forceSegmented) {
        if (byteCount == 0L) {
            return Utf8.EMPTY;
        }

        if (forceSegmented || byteCount >= SEGMENTING_THRESHOLD) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "Buffer(SegmentQueue#{0}) : Start readUtf8({1}), will return a " +
                                "segmented Utf8{2}",
                        segmentQueue.hashCode(), byteCount, System.lineSeparator());
            }
            final var byteStringBuilder = prepareByteString(byteCount);
            final var segments = byteStringBuilder.segments.toArray(new Segment[0]);
            final var size = byteStringBuilder.offsets.size();
            final var directory = new int[size * 2];
            for (var i = 0; i < size; i++) {
                directory[i] = byteStringBuilder.offsets.get(i);
                directory[i + size] = byteStringBuilder.positions.get(i);
            }
            return new SegmentedUtf8(segments, directory, false);
        }

        return new RealUtf8(readByteArray(byteCount), false);
    }

    @Override
    public @NonNull Ascii readAscii() {
        return readAscii(segmentQueue.size());
    }

    public @NonNull Ascii readAscii(final long byteCount) {
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        if (segmentQueue.size() < byteCount) {
            throw new JayoEOFException();
        }

        return readAscii(byteCount, false);
    }

    @NonNull
    Ascii readAscii(final long byteCount, final boolean forceSegmented) {
        if (byteCount == 0L) {
            return Ascii.EMPTY;
        }

        if (forceSegmented || byteCount >= SEGMENTING_THRESHOLD) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "Buffer(SegmentQueue#{0}) : Start readUtf8({1}), will return a " +
                                "segmented Utf8{2}",
                        segmentQueue.hashCode(), byteCount, System.lineSeparator());
            }
            final var byteStringBuilder = prepareByteString(byteCount);
            final var segments = byteStringBuilder.segments.toArray(new Segment[0]);
            final var size = byteStringBuilder.offsets.size();
            final var directory = new int[size * 2];
            for (var i = 0; i < size; i++) {
                directory[i] = byteStringBuilder.offsets.get(i);
                directory[i + size] = byteStringBuilder.positions.get(i);
            }
            return new SegmentedAscii(segments, directory);
        }

        return new RealAscii(readByteArray(byteCount));
    }

    @Override
    public int select(final @NonNull Options options) {
        if (!(Objects.requireNonNull(options) instanceof RealOptions _options)) {
            throw new IllegalArgumentException("options must be an instance of JayoOptions");
        }
        final var index = selectPrefix(this, _options);
        if (index == -1) {
            return -1;
        }

        // If the prefix match actually matched a full byte string, consume it and return it.
        final var selectedSize = _options.byteStrings[index].byteSize();
        skip(selectedSize);
        return index;
    }

    @Override
    public void readTo(final @NonNull RawWriter writer, final long byteCount) {
        Objects.requireNonNull(writer);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0L: " + byteCount);
        }
        final var currentSize = segmentQueue.size();
        if (currentSize < byteCount) {
            writer.write(this, currentSize); // Exhaust ourselves.
            throw new JayoEOFException(
                    "Buffer exhausted before writing " + byteCount + " bytes. Only " + currentSize
                            + " bytes were written.");
        }
        writer.write(this, byteCount);
    }

    @Override
    public long transferTo(final @NonNull RawWriter writer) {
        Objects.requireNonNull(writer);
        final var byteCount = segmentQueue.size();
        if (byteCount > 0L) {
            writer.write(this, byteCount);
        }
        return byteCount;
    }

    @Override
    public @NonNull String readString() {
        return readString(segmentQueue.size(), StandardCharsets.UTF_8);
    }

    @Override
    public @NonNull String readString(final long byteCount) {
        return readString(byteCount, StandardCharsets.UTF_8);
    }

    @Override
    public @NonNull String readString(final @NonNull Charset charset) {
        return readString(segmentQueue.size(), charset);
    }

    @Override
    public @NonNull String readString(final long byteCount, final @NonNull Charset charset) {
        Objects.requireNonNull(charset);
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        if (segmentQueue.size() < byteCount) {
            throw new JayoEOFException();
        }
        if (byteCount == 0L) {
            return "";
        }

        final var head = segmentQueue.head();
        assert head != null;
        final var currentLimit = head.limit;
        if (head.pos + byteCount > currentLimit) {
            // If the string spans multiple segments, delegate to readByteArray().
            return new String(readByteArray(head, (int) byteCount), charset);
        }

        // else all bytes of this future String are in head Segment itself
        final var result = new String(head.data, head.pos, (int) byteCount, charset);
        head.pos += (int) byteCount;
        segmentQueue.decrementSize(byteCount);

        if (head.pos == currentLimit) {
            segmentQueue.removeHead(head, false);
        }

        return result;
    }

    @Override
    public @Nullable String readLine() {
        final var newline = indexOf((byte) ((int) '\n'));

        if (newline != -1L) {
            return Utf8Utils.readUtf8Line(this, newline);
        }
        if (segmentQueue.size() != 0L) {
            return readString(segmentQueue.size());
        }

        return null;
    }

    @Override
    public @NonNull String readLineStrict() {
        return readLineStrict(Long.MAX_VALUE);
    }

    @Override
    public @NonNull String readLineStrict(final long limit) {
        if (limit < 0L) {
            throw new IllegalArgumentException("limit < 0: " + limit);
        }
        final var scanLength = (limit == Long.MAX_VALUE) ? Long.MAX_VALUE : limit + 1L;
        final var newline = indexOf((byte) ((int) '\n'), 0L, scanLength);
        if (newline != -1L) {
            return Utf8Utils.readUtf8Line(this, newline);
        }
        if (scanLength < segmentQueue.size() &&
                getByte(scanLength - 1) == (byte) ((int) '\r') &&
                getByte(scanLength) == (byte) ((int) '\n')) {
            // The line was 'limit' UTF-8 bytes followed by \r\n.
            return Utf8Utils.readUtf8Line(this, scanLength);
        }
        final var data = new RealBuffer();
        copyTo(data, 0, Math.min(32, segmentQueue.size()));
        throw new JayoEOFException(
                "\\n not found: limit=" + Math.min(segmentQueue.size(), limit) + " content="
                        + data.readByteString().hex() + "'…'");
    }

    @Override
    public int readUtf8CodePoint() {
        if (segmentQueue.size() == 0L) {
            throw new JayoEOFException();
        }

        final var b0 = getByte(0);
        int codePoint;
        final int byteCount;
        final int min;

        if ((b0 & 0x80) == 0) {
            // 0xxxxxxx.
            codePoint = b0 & 0x7f;
            byteCount = 1; // 7 bits (ASCII).
            min = 0x0;
        } else if ((b0 & 0xe0) == 0xc0) {
            // 0x110xxxxx
            codePoint = b0 & 0x1f;
            byteCount = 2; // 11 bits (5 + 6).
            min = 0x80;
        } else if ((b0 & 0xf0) == 0xe0) {
            // 0x1110xxxx
            codePoint = b0 & 0x0f;
            byteCount = 3; // 16 bits (4 + 6 + 6).
            min = 0x800;
        } else if ((b0 & 0xf8) == 0xf0) {
            // 0x11110xxx
            codePoint = b0 & 0x07;
            byteCount = 4; // 21 bits (3 + 6 + 6 + 6).
            min = 0x10000;
        } else {
            // We expected the first byte of a code point but got something else.
            skip(1);
            return UTF8_REPLACEMENT_CODE_POINT;
        }

        if (segmentQueue.size() < byteCount) {
            throw new JayoEOFException(
                    "size < " + byteCount + ": " + segmentQueue.size() + " (to read code point prefixed 0x"
                            + toHexString(b0) + ")");
        }

        // Read the continuation bytes. If we encounter a non-continuation byte, the sequence consumed thus far is
        // truncated and is decoded as the replacement character. That non-continuation byte is left in the stream for
        // processing by the next call to readUtf8CodePoint().
        for (var i = 1; i < byteCount; i++) {
            final var b = getByte(i);
            if ((b & 0xc0) == 0x80) {
                // 0x10xxxxxx
                codePoint = codePoint << 6;
                codePoint = codePoint | (b & 0x3f);
            } else {
                skip(i);
                return UTF8_REPLACEMENT_CODE_POINT;
            }
        }

        skip(byteCount);

        if (codePoint > 0x10ffff // Reject code points larger than the Unicode maximum.
                || (0xd800 <= codePoint && codePoint <= 0xdfff) // Reject partial surrogates.
                || codePoint < min) { // Reject overlong code points.
            return UTF8_REPLACEMENT_CODE_POINT;
        }
        return codePoint;
    }

    @Override
    public byte @NonNull [] readByteArray() {
        return readByteArray(segmentQueue.size());
    }

    @Override
    public byte @NonNull [] readByteArray(final long byteCount) {
        if (byteCount < 0 || byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byteCount: " + byteCount);
        }
        if (segmentQueue.size() < byteCount) {
            throw new JayoEOFException();
        }
        if (byteCount == 0L) {
            return new byte[0];
        }

        final var head = segmentQueue.head();
        assert head != null;
        return readByteArray(head, (int) byteCount);
    }

    private byte @NonNull [] readByteArray(final @NonNull Segment head, final int byteCount) {
        final var result = new byte[byteCount];
        readTo(head, result, 0, byteCount);
        return result;
    }

    @Override
    public void readTo(final byte @NonNull [] writer) {
        readTo(writer, 0, writer.length);
    }

    @Override
    public void readTo(final byte @NonNull [] writer,
                       final int offset,
                       final int byteCount) {
        Objects.requireNonNull(writer);
        checkOffsetAndCount(writer.length, offset, byteCount);

        final var head = segmentQueue.head();
        assert head != null;
        readTo(head, writer, offset, byteCount);
    }

    private void readTo(final @NonNull Segment head,
                        final byte @NonNull [] writer,
                        final int offset,
                        final int byteCount) {
        var _head = head;
        var _offset = offset;
        final var toWrite = (int) Math.min(byteCount, segmentQueue.size());
        var remaining = toWrite;
        var finished = false;
        while (!finished) {
            assert _head != null;
            final var currentLimit = _head.limit;
            final var toCopy = Math.min(remaining, currentLimit - _head.pos);
            remaining -= toCopy;
            finished = remaining == 0;
            _head = readAtMostTo(_head, writer, _offset, toCopy, currentLimit);
            _offset += toCopy;
        }

        if (toWrite < byteCount) {
            throw new JayoEOFException("could not write all the requested bytes to byte array, written " +
                    toWrite + "/" + byteCount);
        }
    }

    @Override
    public int readAtMostTo(final byte @NonNull [] writer) {
        return readAtMostTo(writer, 0, writer.length);
    }

    @Override
    public int readAtMostTo(final byte @NonNull [] writer,
                            final int offset,
                            final int byteCount) {
        Objects.requireNonNull(writer);
        checkOffsetAndCount(writer.length, offset, byteCount);

        if (segmentQueue.size() == 0L) {
            return -1;
        }

        final var head = segmentQueue.head();
        assert head != null;
        final var currentLimit = head.limit;
        final var toCopy = Math.min(byteCount, currentLimit - head.pos);
        readAtMostTo(head, writer, offset, toCopy, currentLimit);
        return toCopy;
    }

    private @Nullable Segment readAtMostTo(final @NonNull Segment head,
                                           final byte @NonNull [] writer,
                                           final int offset,
                                           final int byteCount,
                                           final int currentLimit) {
        final var toCopy = Math.min(byteCount, currentLimit - head.pos);
        System.arraycopy(head.data, head.pos, writer, offset, toCopy);
        head.pos += toCopy;
        segmentQueue.decrementSize(toCopy);

        if (head.pos == currentLimit) {
            return segmentQueue.removeHead(head, false);
        }

        return null;
    }

    @Override
    public int readAtMostTo(final @NonNull ByteBuffer writer) {
        Objects.requireNonNull(writer);

        if (segmentQueue.size() == 0L) {
            return -1;
        }

        final var head = segmentQueue.head();
        assert head != null;
        final var currentLimit = head.limit;
        final var toCopy = Math.min(writer.remaining(), currentLimit - head.pos);
        writer.put(head.data, head.pos, toCopy);
        head.pos += toCopy;
        segmentQueue.decrementSize(toCopy);

        if (head.pos == currentLimit) {
            segmentQueue.removeHead(head, false);
        }

        return toCopy;
    }

    @Override
    public void clear() {
        final var size = segmentQueue.expectSize(Long.MAX_VALUE);
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, """
                            Buffer(SegmentQueue#{0}) clear(): Start clearing all {1} bytes from this
                            {2}{3}""",
                    segmentQueue.hashCode(), size, segmentQueue, System.lineSeparator());
        }
        skipInternal(size);
    }

    @Override
    public void skip(final long byteCount) {
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0L: " + byteCount);
        }
        if (byteCount == 0L) {
            return;
        }
        final var toSkip = Math.min(byteCount, segmentQueue.expectSize(byteCount));
        skipInternal(toSkip);
        if (toSkip < byteCount) {
            throw new JayoEOFException("could not skip " + byteCount + " bytes, skipped: " + toSkip);
        }
    }

    void skipInternal(final long byteCount) {
        if (byteCount == 0L) {
            return;
        }
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, """
                            Buffer(SegmentQueue#{0}) : Start skipping {1} bytes from this
                            {2}{3}""",
                    segmentQueue.hashCode(), byteCount, segmentQueue, System.lineSeparator());
        }
        var remaining = byteCount;
        var head = segmentQueue.head();
        assert head != null;
        var headLimit = head.limit;
        while (remaining > 0L) {
            if (head.pos == headLimit) {
                head = segmentQueue.removeHead(head, true);
                assert head != null;
                headLimit = head.limit;
            }

            var toSkipInSegment = (int) Math.min(remaining, headLimit - head.pos);
            head.pos += toSkipInSegment;
            segmentQueue.decrementSize(toSkipInSegment);
            remaining -= toSkipInSegment;
        }
        if (head.pos == head.limit) {
            segmentQueue.removeHead(head, false);
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, """
                            Buffer(SegmentQueue#{0}) : Finished skipping {1} bytes from this
                            {2}{3}""",
                    segmentQueue.hashCode(), byteCount, segmentQueue, System.lineSeparator());
        }
    }

    @Override
    public @NonNull Buffer write(final @NonNull ByteString byteString) {
        return write(byteString, 0, byteString.byteSize());
    }

    @Override
    public @NonNull Buffer write(final @NonNull ByteString byteString,
                                 final int offset,
                                 final int byteCount) {
        Objects.requireNonNull(byteString);
        if (byteString instanceof RealByteString _byteString) {
            _byteString.write(this, offset, byteCount);
        } else if (byteString instanceof RealAscii _byteString) {
            _byteString.write(this, offset, byteCount);
        } else if (byteString instanceof BaseByteString _byteString) {
            _byteString.write(this, offset, byteCount);
        } else {
            throw new IllegalArgumentException("byteString must be an instance of RealByteString or BaseByteString");
        }

        return this;
    }

    @Override
    public @NonNull Buffer write(final @NonNull String string) {
        return write(string, 0, string.length());
    }

    @Override
    public @NonNull Buffer write(final @NonNull String string,
                                 final int startIndex,
                                 final int endIndex) {
        Objects.requireNonNull(string);
        if (endIndex < startIndex) {
            throw new IllegalArgumentException("endIndex < beginIndex: " + endIndex + " < " + startIndex);
        }
        if (startIndex < 0) {
            throw new IndexOutOfBoundsException("beginIndex < 0: " + startIndex);
        }
        if (endIndex > string.length()) {
            throw new IndexOutOfBoundsException("endIndex > string.length: " + endIndex + " > " + string.length());
        }

        if (startIndex == endIndex) {
            return this;
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE,
                    "Buffer(SegmentQueue#{0}) : Start writing {1} chars from CharSequence encoded in UTF-8{2}",
                    segmentQueue.hashCode(), endIndex - startIndex, System.lineSeparator());
        }

        // fast-path for ISO_8859_1 encoding
        if (UNSAFE_AVAILABLE && isLatin1(string)) {
            final var stringBytes = getBytes(string);
            writeLatin1ToUtf8(stringBytes, startIndex, endIndex);
        } else {
            writeUtf16ToUtf8(string, startIndex, endIndex);
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, """
                            Buffer(SegmentQueue#{0}) : Finished writing {1} chars from CharSequence encoded in UTF-8 to this segment queue
                            {2}{3}""",
                    segmentQueue.hashCode(), endIndex - startIndex, segmentQueue, System.lineSeparator());
        }
        return this;
    }

    /**
     * Transcode a ISO_8859_1-encoded byte array to UTF-8 bytes.
     */
    private void writeLatin1ToUtf8(final byte @NonNull [] stringBytes,
                                   final int startIndex,
                                   final int endIndex) {
        final var i = new Wrapper.Int(startIndex);
        while (i.value < endIndex) {
            // We require at least 2 writable bytes in the tail to write one ISO_8859_1 byte of this byte array : the
            // max byte size of a ISO_8859_1 byte is 2 !
            segmentQueue.withWritableTail(2, tail -> {
                var limit = tail.limit;
                while (i.value < endIndex && (Segment.SIZE - limit) > 1) {
                    final var data = tail.data;
                    final int c = stringBytes[i.value] & 0xff;
                    if (c < 0x80) {
                        final var segmentOffset = limit - i.value;
                        final var runLimit = Math.min(endIndex, Segment.SIZE - segmentOffset);

                        // Emit a 7-bit character with 1 byte.
                        data[segmentOffset + i.value++] = (byte) c; // 0xxxxxxx

                        // Fast-path contiguous runs of ASCII characters. This is ugly, but yields a ~4x performance
                        // improvement over independent calls to writeByte().
                        while (i.value < runLimit) {
                            final int c1 = stringBytes[i.value] & 0xff;
                            if (c1 >= 0x80) {
                                break;
                            }
                            data[segmentOffset + i.value++] = (byte) c1; // 0xxxxxxx
                        }

                        limit = i.value + segmentOffset; // Equivalent to i - (previous i).
                    } else {
                        // Emit a 11-bit character with 2 bytes.
                        data[limit++] = (byte) ((c >> 6) | 0xc0); // 110xxxxx
                        data[limit++] = (byte) ((c & 0x3f) | 0x80); // 10xxxxxx
                        i.value++;
                    }
                }
                tail.limit = limit;
                return null;
            });
        }
    }

    /**
     * Transcode a UTF-16 Java String to UTF-8 bytes.
     */
    private void writeUtf16ToUtf8(final @NonNull CharSequence charSequence,
                                  final int startIndex,
                                  final int endIndex) {
        final var i = new Wrapper.Int(startIndex);
        while (i.value < endIndex) {
            // We require at least 4 writable bytes in the tail to write one code point of this char-sequence : the max
            // byte size of a char code point is 4 !
            segmentQueue.withWritableTail(4, tail -> {
                var limit = tail.limit;
                while (i.value < endIndex && (Segment.SIZE - limit) > 3) {
                    final var data = tail.data;
                    final int c = charSequence.charAt(i.value);
                    if (c < 0x80) {
                        final var segmentOffset = limit - i.value;
                        final var runLimit = Math.min(endIndex, Segment.SIZE - segmentOffset);

                        // Emit a 7-bit character with 1 byte.
                        data[segmentOffset + i.value++] = (byte) c; // 0xxxxxxx

                        // Fast-path contiguous runs of ASCII characters. This is ugly, but yields a ~4x performance
                        // improvement over independent calls to writeByte().
                        while (i.value < runLimit) {
                            final var c1 = charSequence.charAt(i.value);
                            if (c1 >= 0x80) {
                                break;
                            }
                            data[segmentOffset + i.value++] = (byte) c1; // 0xxxxxxx
                        }

                        limit = i.value + segmentOffset; // Equivalent to i - (previous i).
                    } else if (c < 0x800) {
                        // Emit a 11-bit character with 2 bytes.
                        data[limit++] = (byte) (c >> 6 | 0xc0); // 110xxxxx
                        data[limit++] = (byte) (c & 0x3f | 0x80); // 10xxxxxx
                        i.value++;
                    } else if ((c < 0xd800) || (c > 0xdfff)) {
                        // Emit a 16-bit character with 3 bytes.
                        data[limit++] = (byte) (c >> 12 | 0xe0); // 1110xxxx
                        data[limit++] = (byte) (c >> 6 & 0x3f | 0x80); // 10xxxxxx
                        data[limit++] = (byte) (c & 0x3f | 0x80); // 10xxxxxx
                        i.value++;
                    } else {
                        // c is a surrogate. Mac successor is a low surrogate. If not, the UTF-16 is invalid, in which
                        // case we emit a replacement character.
                        final int low = (i.value + 1 < endIndex) ? charSequence.charAt(i.value + 1) : 0;
                        if (c > 0xdbff || low < 0xdc00 || low > 0xdfff) {
                            data[limit++] = (byte) ((int) '?');
                            i.value++;
                        } else {
                            // UTF-16 high surrogate: 110110xxxxxxxxxx (10 bits)
                            // UTF-16 low surrogate:  110111yyyyyyyyyy (10 bits)
                            // Unicode code point:    00010000000000000000 + xxxxxxxxxxyyyyyyyyyy (21 bits)
                            final var codePoint = 0x010000 + ((c & 0x03ff) << 10 | (low & 0x03ff));

                            // Emit a 21-bit character with 4 bytes.
                            data[limit++] = (byte) (codePoint >> 18 | 0xf0); // 11110xxx
                            data[limit++] = (byte) (codePoint >> 12 & 0x3f | 0x80); // 10xxxxxx
                            data[limit++] = (byte) (codePoint >> 6 & 0x3f | 0x80); // 10xxyyyy
                            data[limit++] = (byte) (codePoint & 0x3f | 0x80); // 10yyyyyy
                            i.value += 2;
                        }
                    }
                }
                tail.limit = limit;
                return null;
            });
        }
    }

    @Override
    public @NonNull Buffer write(final @NonNull String string, final @NonNull Charset charset) {
        return this.write(string, 0, string.length(), charset);
    }

    @Override
    public @NonNull Buffer write(final @NonNull String string,
                                 final int startIndex,
                                 final int endIndex,
                                 final @NonNull Charset charset) {
        Objects.requireNonNull(string);
        if (startIndex < 0) {
            throw new IllegalArgumentException("beginIndex < 0: " + startIndex);
        }
        if (endIndex < startIndex) {
            throw new IllegalArgumentException("endIndex < beginIndex: " + endIndex + " < " + startIndex);
        }
        if (endIndex > string.length()) {
            throw new IllegalArgumentException("endIndex > string.length: " + endIndex + " > " + string.length());
        }
        // fast-path#1 for UTF-8 encoding
        if (charset.equals(StandardCharsets.UTF_8)) {
            return write(string, startIndex, endIndex);
        }

        // fast-path#2 for ISO_8859_1 encoding
        if (charset.equals(StandardCharsets.ISO_8859_1) && UNSAFE_AVAILABLE && isLatin1(string)) {
            final var stringBytes = getBytes(string);
            return write(stringBytes, startIndex, endIndex - startIndex);
        }

        final var substring = string.substring(startIndex, endIndex);
        final var data = substring.getBytes(charset);
        return write(data, 0, data.length);
    }

    @Override
    public @NonNull Buffer writeUtf8CodePoint(final int codePoint) {
        if (codePoint < 0x80) {
            // Emit a 7-bit code point with 1 byte.
            writeByte((byte) codePoint);
        } else if (codePoint < 0x800) {
            // Emit a 11-bit code point with 2 bytes.
            segmentQueue.withWritableTail(2, s -> {
                final var data = s.data;
                var limit = s.limit;
                data[limit++] = (byte) (codePoint >> 6 | 0xc0); // 110xxxxx
                data[limit++] = (byte) (codePoint & 0x3f | 0x80); // 10xxxxxx
                s.limit = limit;
                return null;
            });
        } else if (codePoint >= 0xd800 && codePoint <= 0xdfff) {
            // Emit a replacement character for a partial surrogate.
            writeByte((byte) ((int) '?'));
        } else if (codePoint < 0x10000) {
            // Emit a 16-bit code point with 3 bytes.
            segmentQueue.withWritableTail(3, s -> {
                final var data = s.data;
                var limit = s.limit;
                data[limit++] = (byte) (codePoint >> 12 | 0xe0); // 1110xxxx
                data[limit++] = (byte) (codePoint >> 6 & 0x3f | 0x80); // 10xxxxxx
                data[limit++] = (byte) (codePoint & 0x3f | 0x80); // 10xxxxxx
                s.limit = limit;
                return null;
            });
        } else if (codePoint <= 0x10ffff) {
            // Emit a 21-bit code point with 4 bytes.
            segmentQueue.withWritableTail(4, s -> {
                final var data = s.data;
                var limit = s.limit;
                data[limit++] = (byte) (codePoint >> 18 | 0xf0); // 11110xxx
                data[limit++] = (byte) (codePoint >> 12 & 0x3f | 0x80); // 10xxxxxx
                data[limit++] = (byte) (codePoint >> 6 & 0x3f | 0x80); // 10xxyyyy
                data[limit++] = (byte) (codePoint & 0x3f | 0x80); // 10yyyyyy
                s.limit = limit;
                return null;
            });
        } else {
            throw new IllegalArgumentException("Unexpected code point: 0x" + toHexString(codePoint));
        }

        return this;
    }

    @Override
    public @NonNull Buffer write(final byte @NonNull [] source) {
        return write(source, 0, source.length);
    }

    @Override
    public @NonNull Buffer write(final byte @NonNull [] source,
                                 final int offset,
                                 final int byteCount) {
        checkOffsetAndCount(Objects.requireNonNull(source).length, offset, byteCount);

        final var limit = offset + byteCount;
        final var _offset = new Wrapper.Int(offset);
        while (_offset.value < limit) {
            segmentQueue.withWritableTail(1, s -> {
                final var toCopy = Math.min(limit - _offset.value, Segment.SIZE - s.limit);
                System.arraycopy(source, _offset.value, s.data, s.limit, toCopy);
                _offset.value += toCopy;
                s.limit += toCopy;
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE,
                            "Buffer(SegmentQueue#{0}) : wrote {1} bytes in tail Segment#{2}{3}",
                            segmentQueue.hashCode(), toCopy, s.hashCode(), System.lineSeparator());
                }
                return null;
            });
        }
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE,
                    "Buffer(SegmentQueue#{0}) : wrote {1} bytes, queue = {2}{3}",
                    segmentQueue.hashCode(), byteCount, segmentQueue, System.lineSeparator());
        }

        return this;
    }

    @Override
    public int transferFrom(final @NonNull ByteBuffer reader) {
        final var byteCount = Objects.requireNonNull(reader).remaining();
        final var remaining = new Wrapper.Int(byteCount);
        while (remaining.value > 0) {
            segmentQueue.withWritableTail(1, s -> {
                final var toCopy = Math.min(remaining.value, Segment.SIZE - s.limit);
                reader.get(s.data, s.limit, toCopy);
                remaining.value -= toCopy;
                s.limit += toCopy;
                return null;
            });
        }

        return byteCount;
    }

    @Override
    public long transferFrom(final @NonNull RawReader reader) {
        Objects.requireNonNull(reader);
        var totalBytesRead = 0L;
        while (true) {
            final var readCount = reader.readAtMostTo(this, Segment.SIZE);
            if (readCount == -1L) {
                break;
            }
            totalBytesRead += readCount;
        }
        return totalBytesRead;
    }

    @Override
    public @NonNull Buffer write(final @NonNull RawReader reader, final long byteCount) {
        Objects.requireNonNull(reader);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        var _byteCount = byteCount;
        while (_byteCount > 0L) {
            final var read = reader.readAtMostTo(this, _byteCount);
            if (read == -1L) {
                throw new JayoEOFException();
            }
            _byteCount -= read;
        }
        return this;
    }

    @Override
    public @NonNull Buffer writeByte(final byte b) {
        segmentQueue.withWritableTail(1, s -> {
            s.data[s.limit] = b;
            s.limit += 1;
            return null;
        });
        return this;
    }

    @Override
    public @NonNull Buffer writeShort(final short s) {
        segmentQueue.withWritableTail(2, seg -> {
            var limit = seg.limit;
            seg.data[limit++] = (byte) (s >>> 8 & 0xff);
            seg.data[limit] = (byte) (s & 0xff);
            seg.limit += 2;
            return null;
        });
        return this;
    }

    @Override
    public @NonNull Buffer writeInt(final int i) {
        segmentQueue.withWritableTail(4, s -> {
            var limit = s.limit;
            s.data[limit++] = (byte) (i >>> 24 & 0xff);
            s.data[limit++] = (byte) (i >>> 16 & 0xff);
            s.data[limit++] = (byte) (i >>> 8 & 0xff);
            s.data[limit] = (byte) (i & 0xff);
            s.limit += 4;
            return null;
        });
        return this;
    }

    @Override
    public @NonNull Buffer writeLong(final long l) {
        segmentQueue.withWritableTail(8, s -> {
            var limit = s.limit;
            s.data[limit++] = (byte) (l >>> 56 & 0xffL);
            s.data[limit++] = (byte) (l >>> 48 & 0xffL);
            s.data[limit++] = (byte) (l >>> 40 & 0xffL);
            s.data[limit++] = (byte) (l >>> 32 & 0xffL);
            s.data[limit++] = (byte) (l >>> 24 & 0xffL);
            s.data[limit++] = (byte) (l >>> 16 & 0xffL);
            s.data[limit++] = (byte) (l >>> 8 & 0xffL);
            s.data[limit] = (byte) (l & 0xffL);
            s.limit += 8;
            return null;
        });
        return this;
    }

    @Override
    public @NonNull Buffer writeDecimalLong(final long l) {
        if (l == 0L) {
            // Both a shortcut and required since the following code can't handle zero.
            return writeByte((byte) ((int) '0'));
        }

        var _l = l;
        var negative = false;
        if (_l < 0L) {
            _l = -_l;
            if (_l < 0L) { // Only true for Long.MIN_VALUE.
                return write("-9223372036854775808");
            }
            negative = true;
        }

        // Binary search for character width which favors matching lower numbers.
        int width;
        if (_l < 100000000L) {
            if (_l < 10000L) {
                if (_l < 100L) {
                    if (_l < 10L) {
                        width = 1;
                    } else {
                        width = 2;
                    }
                } else if (_l < 1000L) {
                    width = 3;
                } else {
                    width = 4;
                }
            } else if (_l < 1000000L) {
                if (_l < 100000L) {
                    width = 5;
                } else {
                    width = 6;
                }
            } else if (_l < 10000000L) {
                width = 7;
            } else {
                width = 8;
            }
        } else if (_l < 1000000000000L) {
            if (_l < 10000000000L) {
                if (_l < 1000000000L) {
                    width = 9;
                } else {
                    width = 10;
                }
            } else if (_l < 100000000000L) {
                width = 11;
            } else {
                width = 12;
            }
        } else if (_l < 1000000000000000L) {
            if (_l < 10000000000000L) {
                width = 13;
            } else if (_l < 100000000000000L) {
                width = 14;
            } else {
                width = 15;
            }
        } else if (_l < 100000000000000000L) {
            if (_l < 10000000000000000L) {
                width = 16;
            } else {
                width = 17;
            }
        } else if (_l < 1000000000000000000L) {
            width = 18;
        } else {
            width = 19;
        }

        if (negative) {
            ++width;
        }

        final var _width = width;
        final var _negative = negative;
        final var __l = new Wrapper.Long(_l);
        segmentQueue.withWritableTail(_width, s -> {
            final var data = s.data;
            var pos = s.limit + _width; // We write backwards from right to left.
            while (__l.value != 0L) {
                final var digit = (int) (__l.value % 10);
                data[--pos] = HEX_DIGIT_BYTES[digit];
                __l.value /= 10;
            }
            if (_negative) {
                data[--pos] = (byte) ((int) '-');
            }
            s.limit += _width;
            return null;
        });
        return this;
    }

    @Override
    public @NonNull Buffer writeHexadecimalUnsignedLong(final long l) {
        if (l == 0L) {
            // Both a shortcut and required since the following code can't handle zero.
            return writeByte((byte) ((int) '0'));
        }

        // Mask every bit below the most significant bit to a 1
        // https://aggregate.org/MAGIC/#Most%20Significant%201%20Bit
        final var width = getHexadecimalUnsignedLongWidth(l);

        final var _l = new Wrapper.Long(l);
        segmentQueue.withWritableTail(width, s -> {
            final var data = s.data;
            var pos = s.limit + width - 1; // We write backwards from right to left.
            while (pos >= s.limit) {
                data[pos--] = HEX_DIGIT_BYTES[(int) (_l.value & 0xF)];
                _l.value = _l.value >>> 4;
            }
            s.limit += width;
            return null;
        });
        return this;
    }

    @Override
    public void write(final @NonNull Buffer reader, final long byteCount) {
        // Move bytes from the head of the reader buffer to the tail of this buffer in the most possible effective way !
        // This method is the most crucial part of the Jayo concept based on Buffer = a queue of segments.
        //
        // We must do it while balancing two conflicting goals: don't waste CPU and don't waste memory.
        //
        //
        // Don't waste CPU (i.e. don't copy data around).
        //
        // Copying large amounts of data is expensive. Instead, we prefer to reassign entire segments from one buffer to
        // the other.
        //
        //
        // Don't waste memory.
        //
        // As an invariant, adjacent pairs of segments in a buffer should be at least 50% full, except for the head
        // segment and the tail segment.
        //
        // The head segment cannot maintain the invariant because the application is consuming bytes from this segment,
        // decreasing its level.
        //
        // The tail segment cannot maintain the invariant because the application is producing bytes, which may require
        // new nearly-empty tail segments to be appended.
        //
        //
        // Moving segments between buffers
        //
        // When writing one buffer to another, we prefer to reassign entire segments over copying bytes into their most
        // compact form. Suppose we have a buffer with these segment levels [91%, 61%]. If we append a buffer with a
        // single [72%] segment, that yields [91%, 61%, 72%]. No bytes are copied.
        //
        // Or suppose we have a buffer with these segment levels: [100%, 2%], and we want to append it to a buffer with
        // these segment levels [99%, 3%]. This operation will yield the following segments: [100%, 2%, 99%, 3%]. That
        // is, we do not spend time copying bytes around to achieve more efficient memory use like [100%, 100%, 4%].
        //
        // When combining buffers, we will compact adjacent buffers when their combined level doesn't exceed 100%. For
        // example, when we start with [100%, 40%] and append [30%, 80%], the result is [100%, 70%, 80%].
        //
        //
        // Splitting segments
        //
        // Occasionally we write only part of a reader buffer to a writer buffer. For example, given a writer [51%, 91%], we
        // may want to write the first 30% of a reader [92%, 82%] to it. To simplify, we first transform the reader to
        // an equivalent buffer [30%, 62%, 82%] and then move the head segment, yielding writer [51%, 91%, 30%] and reader
        // [62%, 82%].

        if (Objects.requireNonNull(reader) == this) {
            throw new IllegalArgumentException("reader == this, cannot write in itself");
        }
        checkOffsetAndCount(reader.bytesAvailable(), 0, byteCount);
        if (byteCount == 0L) {
            return;
        }
        if (!(reader instanceof RealBuffer _reader)) {
            throw new IllegalArgumentException("reader must be an instance of RealBuffer");
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, """
                            Buffer(SegmentQueue#{0}) : Start writing {1} bytes from reader segment queue
                            {2}
                            into this segment queue
                            {3}{4}""",
                    segmentQueue.hashCode(), byteCount, _reader.segmentQueue, segmentQueue, System.lineSeparator());
        }

        final var deferredReaderHeadRef = new SegmentRef.Deferred(_reader.segmentQueue.lock);
        Segment nextReaderHead = null;
        segmentQueue.lock.lock();
        final var headRef = (SegmentRef) SegmentQueue.HEAD_REF.getAndSetRelease(_reader.segmentQueue, deferredReaderHeadRef);
        try {
            var tail = segmentQueue.nonRemovedTailOrNull();
            try {
                var readerHead = headRef.value();
                var remaining = byteCount;
                while (remaining > 0) {
                    if (nextReaderHead != null) {
                        readerHead = nextReaderHead;
                    }
                    assert readerHead != null;

                    // Is a prefix of the reader's head segment all that we need to move?
                    var currentLimit = readerHead.limit;
                    var bytesInReader = currentLimit - readerHead.pos;
                    final boolean split;
                    if (remaining < bytesInReader) {
                        if (tryTransferBytes(tail, readerHead, _reader, remaining)) {
                            nextReaderHead = readerHead;
                            return;
                        }
                        split = true;
                    } else {
                        split = readerHead.startTransfer(); // if reader head is currently being written, we must split
                    }

                    if (split) {
                        // We're going to need another segment. Split the reader's head segment in two, then we will move
                        // the first of those two to this buffer.
                        nextReaderHead = readerHead;

                        bytesInReader = (int) Math.min(bytesInReader, remaining);
                        readerHead = readerHead.splitHead(bytesInReader);
                        if (LOGGER.isLoggable(TRACE)) {
                            LOGGER.log(TRACE,
                                    "reader SegmentQueue#{0} : splitHead. prefix Segment#{1}, suffix Segment#{2}{3}",
                                    _reader.segmentQueue.hashCode(), readerHead.hashCode(), nextReaderHead.hashCode(),
                                    System.lineSeparator());
                        }
                        currentLimit = readerHead.limit;
                    }

                    assert readerHead.status == TRANSFERRING;

                    // Remove the reader's head segment and append it to our tail.
                    final var movedByteCount = currentLimit - readerHead.pos;

                    _reader.segmentQueue.decrementSize(movedByteCount);
                    nextReaderHead = _reader.segmentQueue.removeHeadMaybeSplit(readerHead, true);

                    if (LOGGER.isLoggable(TRACE)) {
                        LOGGER.log(TRACE,
                                "Buffer(SegmentQueue#{0}) : decrement {1} bytes of reader SegmentQueue#{2}{3}",
                                segmentQueue.hashCode(), movedByteCount, _reader.segmentQueue.hashCode(),
                                System.lineSeparator());
                    }

                    final var newTail = newTailIfNeeded(tail, readerHead);
                    // newTail != null is true if we will transfer readerHead to our buffer
                    if (newTail != null) {
                        segmentQueue.addWritableTail(tail, newTail, false);

                        // transfer is finished
                        assert newTail.status == TRANSFERRING;
                        newTail.status = WRITING;

                        if (LOGGER.isLoggable(TRACE)) {
                            LOGGER.log(TRACE,
                                    "Buffer(SegmentQueue#{0}) : transferred Segment#{1} of {2} bytes from reader SegmentQueue#{3}{4}",
                                    segmentQueue.hashCode(), newTail.hashCode(), movedByteCount, _reader.segmentQueue.hashCode(), System.lineSeparator());
                        }

                        segmentQueue.incrementSize(movedByteCount);
                        if (tail != null) {
                            tail.finishWrite();
                        }
                        tail = newTail;
                    } else {
                        segmentQueue.incrementSize(movedByteCount);
                        readerHead = null;
                    }

                    if (LOGGER.isLoggable(TRACE)) {
                        LOGGER.log(TRACE,
                                "Buffer(SegmentQueue#{0}) : incremented {1} bytes of this segment queue{2}{3}",
                                segmentQueue.hashCode(), movedByteCount, segmentQueue, System.lineSeparator());
                    }

                    remaining -= movedByteCount;
                }
            } finally {
                if (tail != null) {
                    tail.finishWrite();
                }
            }

            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, """
                                Buffer(SegmentQueue#{0}) : Finished writing {1} bytes from reader segment queue
                                {2}
                                into this segment queue
                                {3}{4}""",
                        segmentQueue.hashCode(), byteCount, _reader.segmentQueue, segmentQueue, System.lineSeparator());
            }
        } finally {
            deferredReaderHeadRef.segment(nextReaderHead);
            segmentQueue.lock.unlock();
        }
    }

    private boolean tryTransferBytes(final @Nullable Segment tail,
                                     final @NonNull Segment readerHead,
                                     final @NonNull RealBuffer reader,
                                     final long byteCount) {
        if (tail != null && tail.owner &&
                byteCount + tail.limit - ((tail.isShared()) ? 0 : tail.pos) <= Segment.SIZE) {
            // Our existing segments are sufficient. Transfer bytes from reader's head to our tail.
            readerHead.writeTo(tail, (int) byteCount);
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "Buffer(SegmentQueue#{0}) : transferred {1} bytes from reader " +
                                "Segment#{2} to target Segment#{3}{4}",
                        segmentQueue.hashCode(), byteCount, readerHead.hashCode(), tail.hashCode(),
                        System.lineSeparator());
            }
            reader.segmentQueue.decrementSize(byteCount);
            segmentQueue.incrementSize(byteCount);
            readerHead.finishTransfer();
            return true;
        }
        return false;
    }

    /**
     * Call this when the tail and its predecessor may both be less than half full. In this case, we will copy data so
     * that a segment can be recycled.
     */
    private @Nullable Segment newTailIfNeeded(final @Nullable Segment currentTail,
                                              final @NonNull Segment newTail) {
        Objects.requireNonNull(newTail);
        if (currentTail == null || !currentTail.owner) {
            // Cannot compact: current tail is null or isn't writable.
            return newTail;
        }
        final var byteCount = newTail.limit - newTail.pos;
        final var availableByteCount = Segment.SIZE - currentTail.limit
                + ((currentTail.isShared()) ? 0 : currentTail.pos);
        if (byteCount > availableByteCount) {
            // Cannot compact: not enough writable space in current tail.
            return newTail;
        }

        newTail.writeTo(currentTail, byteCount);
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, """
                            Buffer(SegmentQueue#{0}) : tail and its predecessor were both less than half full,
                            transferred {1} bytes from reader segment
                            {2}
                            to target segment
                            {3}{4}""",
                    segmentQueue.hashCode(), byteCount, newTail, currentTail, System.lineSeparator());
        }
        SegmentPool.recycle(newTail);
        return null;
    }

    @Override
    public long readAtMostTo(final @NonNull Buffer writer, final long byteCount) {
        Objects.requireNonNull(writer);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        final var size = segmentQueue.size();
        if (size == 0L) {
            return -1L;
        }
        final var _byteCount = Math.min(byteCount, size);
        writer.write(this, _byteCount);
        return _byteCount;
    }

    @Override
    public long indexOf(final byte b) {
        return indexOf(b, 0L, Long.MAX_VALUE);
    }

    @Override
    public long indexOf(final byte b, final long startIndex) {
        return indexOf(b, startIndex, Long.MAX_VALUE);
    }

    @Override
    public long indexOf(final byte b, final long startIndex, final long endIndex) {
        if (startIndex < 0 || startIndex > endIndex) {
            throw new IllegalArgumentException("size=" + segmentQueue.size() + " startIndex=" + startIndex
                    + " endIndex=" + endIndex);
        }

        final long _endIndex = Math.min(endIndex, segmentQueue.size());
        if (startIndex >= _endIndex) {
            return -1L;
        }

        return seek(startIndex, (s, o) -> {
            if (s == null) {
                return -1L;
            }
            var segment = s;
            var offset = o;
            var _startIndex = startIndex;

            // Scan through the segments, searching for b.
            while (offset < _endIndex) {
                assert segment != null;
                final var data = segment.data;
                final var currentPos = segment.pos;
                final var currentLimit = segment.limit;
                final var limit = (int) Math.min(currentLimit, currentPos + _endIndex - offset);
                var pos = (int) (currentPos + _startIndex - offset);
                while (pos < limit) {
                    if (data[pos] == b) {
                        return pos - currentPos + offset;
                    }
                    pos++;
                }

                // Not in this segment. Try the next one.
                offset += (currentLimit - currentPos);
                _startIndex = offset;
                final var next = segment.next;
                if (next == null) {
                    break;
                }
                segment = next;
            }

            return -1L;
        });
    }

    @Override
    public long indexOf(final @NonNull ByteString byteString) {
        return indexOf(byteString, 0L);
    }

    @Override
    public long indexOf(final @NonNull ByteString byteString, final long startIndex) {
        if (Objects.requireNonNull(byteString).isEmpty()) {
            return 0L;
        }
        if (startIndex < 0L) {
            throw new IllegalArgumentException("startIndex < 0: " + startIndex);
        }

        return seek(startIndex, (s, o) -> {
            if (s == null) {
                return -1L;
            }
            var segment = s;
            var offset = o;
            var _startIndex = startIndex;

            // Scan through the segments, searching for the lead byte. Each time that is found, delegate to
            // rangeEquals() to check for a complete match.
            final var targetByteArray = Utils.internalArray(byteString);
            final var b0 = targetByteArray[0];
            final var bytesSize = byteString.byteSize();
            final var resultLimit = segmentQueue.size() - bytesSize + 1L;
            while (offset < resultLimit) {
                assert segment != null;
                final var data = segment.data;
                final var currentPos = segment.pos;
                final var currentLimit = segment.limit;
                final var segmentLimit = (int) Math.min(currentLimit, currentPos + resultLimit - offset);
                for (var pos = (int) (currentPos + _startIndex - offset); pos < segmentLimit; pos++) {
                    if (data[pos] == b0
                            && rangeEquals(segment, pos + 1, targetByteArray, bytesSize)) {
                        return pos - currentPos + offset;
                    }
                }

                // Not in this segment. Try the next one.
                offset += (currentLimit - currentPos);
                _startIndex = offset;
                segment = segment.next;
            }

            return -1L;
        });
    }

    @Override
    public long indexOfElement(final @NonNull ByteString targetBytes) {
        return indexOfElement(targetBytes, 0L);
    }

    @Override
    public long indexOfElement(final @NonNull ByteString targetBytes, final long startIndex) {
        if (startIndex < 0L) {
            throw new IllegalArgumentException("startIndex < 0: " + startIndex);
        }

        return seek(startIndex, (s, o) -> {
            if (s == null) {
                return -1L;
            }
            var segment = s;
            var offset = o;
            var _startIndex = startIndex;

            // Special case searching for one of two bytes. This is a common case for tools like Moshi,
            // which search for pairs of chars like `\r` and `\n` or {@code `"` and `\`. The impact of this
            // optimization is a ~5x speedup for this case without a substantial cost to other cases.
            if (targetBytes.byteSize() == 2) {
                // Scan through the segments, searching for either of the two bytes.
                final var b0 = targetBytes.getByte(0);
                final var b1 = targetBytes.getByte(1);
                while (offset < segmentQueue.size()) {
                    assert segment != null;
                    final var data = segment.data;
                    final var currentPos = segment.pos;
                    var pos = (int) (currentPos + _startIndex - offset);
                    final var currentLimit = segment.limit;
                    while (pos < currentLimit) {
                        final var b = (int) data[pos];
                        if (b == (int) b0 || b == (int) b1) {
                            return pos - currentPos + offset;
                        }
                        pos++;
                    }

                    // Not in this segment. Try the next one.
                    offset += (currentLimit - currentPos);
                    _startIndex = offset;
                    segment = segment.next;
                }
            } else {
                // Scan through the segments, searching for a byte that's also in the array.
                final var targetByteArray = Utils.internalArray(targetBytes);
                while (offset < segmentQueue.size()) {
                    assert segment != null;
                    final var data = segment.data;
                    final var currentPos = segment.pos;
                    var pos = (int) (currentPos + _startIndex - offset);
                    final var currentLimit = segment.limit;
                    while (pos < currentLimit) {
                        final var b = (int) data[pos];
                        for (final var t : targetByteArray) {
                            if (b == (int) t) {
                                return pos - currentPos + offset;
                            }
                        }
                        pos++;
                    }

                    // Not in this segment. Try the next one.
                    offset += (currentLimit - currentPos);
                    _startIndex = offset;
                    segment = segment.next;
                }
            }

            return -1L;
        });
    }

    @Override
    public boolean rangeEquals(final long offset, final @NonNull ByteString byteString) {
        return rangeEquals(offset, byteString, 0, byteString.byteSize());
    }

    @Override
    public boolean rangeEquals(final long offset,
                               final @NonNull ByteString byteString,
                               final int bytesOffset,
                               final int byteCount) {
        Objects.requireNonNull(byteString);
        if (offset < 0L ||
                bytesOffset < 0 ||
                byteCount < 0 ||
                segmentQueue.size() - offset < byteCount ||
                byteString.byteSize() - bytesOffset < byteCount
        ) {
            return false;
        }
        for (var i = 0; i < byteCount; i++) {
            if (getByte(offset + i) != byteString.getByte(bytesOffset + i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        segmentQueue.close();
    }

    @Override
    public @NonNull ByteString hash(final @NonNull Digest digest) {
        final MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(digest.toString());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm is not available : " + digest, e);
        }
        segmentQueue.forEach(segment -> {
            final var currentPos = segment.pos;
            messageDigest.update(segment.data, currentPos, segment.limit - currentPos);
        });
        return new RealByteString(messageDigest.digest());
    }

    @Override
    public @NonNull ByteString hmac(final @NonNull Hmac hMac, final @NonNull ByteString key) {
        Objects.requireNonNull(key);
        final javax.crypto.Mac javaMac;
        try {
            javaMac = javax.crypto.Mac.getInstance(hMac.toString());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm is not available : " + hMac, e);
        }
        try {
            javaMac.init(new SecretKeySpec(Utils.internalArray(key), hMac.toString()));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("InvalidKeyException was fired with the provided ByteString key", e);
        }
        segmentQueue.forEach(segment -> {
            final var currentPos = segment.pos;
            javaMac.update(segment.data, currentPos, segment.limit - currentPos);
        });
        return new RealByteString(javaMac.doFinal());
    }

    @Override
    public @NonNull String toString() {
        final var currentSize = segmentQueue.size();
        if (currentSize == 0L) {
            return "Buffer(size=0)";
        }

        final var maxPrintableBytes = 64;
        final var len = (int) Math.min(maxPrintableBytes, currentSize);

        final var builder = new StringBuilder(len * 2 + ((currentSize > maxPrintableBytes) ? 1 : 0));

        var segment = segmentQueue.head();
        assert segment != null;
        var written = 0;
        var pos = segment.pos;
        while (written < len) {
            if (pos == segment.limit) {
                segment = segment.next;
                assert segment != null;
                pos = segment.pos;
            }

            final var b = (int) segment.data[pos++];
            written++;

            builder.append(HEX_DIGIT_CHARS[(b >> 4) & 0xf])
                    .append(HEX_DIGIT_CHARS[b & 0xf]);
        }

        if (currentSize > maxPrintableBytes) {
            builder.append('…');
        }

        return "Buffer(size=" + currentSize + " hex=" + builder + ")";
    }

    @Override
    public @NonNull Buffer copy() {
        final var out = new RealBuffer();
        if (segmentQueue.size() == 0L) {
            return out;
        }
        segmentQueue.forEach(segment -> {
            final var segmentCopy = segment.sharedCopy();

            final var outTail = out.segmentQueue.nonRemovedTailOrNull();
            out.segmentQueue.addWritableTail(outTail, segmentCopy, true);
            out.segmentQueue.incrementSize(segmentCopy.limit - segmentCopy.pos);
        });
        return out;
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public @NonNull Buffer clone() {
        return copy();
    }

    @Override
    public @NonNull ByteString snapshot() {
        final var size = segmentQueue.size();
        if (size > Integer.MAX_VALUE) {
            throw new IllegalStateException("size > Integer.MAX_VALUE: " + segmentQueue.size());
        }
        return snapshot((int) size);
    }

    @Override
    public @NonNull ByteString snapshot(final int byteCount) {
        if (byteCount == 0) {
            return ByteString.EMPTY;
        }
        // Walk through the buffer to count how many segments we'll need.
        final var segmentCount = checkAndCountSegments(byteCount);

        // Walk through the buffer again to assign segments and build the directory.
        final var segments = new Segment[segmentCount];
        final var directory = new int[segmentCount * 2];
        fillSegmentsAndDirectory(segments, directory, byteCount);

        return new SegmentedByteString(segments, directory);
    }

    private int checkAndCountSegments(final int byteCount) {
        checkOffsetAndCount(segmentQueue.size(), 0L, byteCount);

        var offset = 0;
        var segmentCount = 0;
        var segment = segmentQueue.head();
        while (offset < byteCount) {
            assert segment != null;
            final var currentPos = segment.pos;
            final var currentLimit = segment.limit;
            if (currentLimit == currentPos) {
                // Empty segment. This should not happen!
                throw new AssertionError("segment.limit == segment.pos");
            }
            offset += currentLimit - currentPos;
            segmentCount++;
            segment = segment.next;
        }
        return segmentCount;
    }

    private void fillSegmentsAndDirectory(final Segment @NonNull [] segments,
                                          final int @NonNull [] directory,
                                          final int byteCount) {
        var offset = 0;
        var segmentCount = 0;
        var segment = segmentQueue.head();
        while (offset < byteCount) {
            assert segment != null;
            final var copy = segment.sharedCopy();
            segments[segmentCount] = copy;
            final var copyPos = copy.pos;
            offset += copy.limit - copyPos;
            // Despite sharing more bytes, only report having up to byteCount.
            directory[segmentCount] = Math.min(offset, byteCount);
            directory[segmentCount + segments.length] = copyPos;
            segmentCount++;
            segment = segment.next;
        }
    }

    @Override
    public @NonNull UnsafeCursor readUnsafe() {
        return readUnsafe(new RealUnsafeCursor());
    }

    @Override
    public @NonNull UnsafeCursor readUnsafe(final @NonNull UnsafeCursor unsafeCursor) {
        if (Objects.requireNonNull(unsafeCursor).buffer != null) {
            throw new IllegalStateException("this cursor is already attached to a buffer");
        }

        unsafeCursor.buffer = this;
        unsafeCursor.readWrite = false;
        return unsafeCursor;
    }

    @Override
    public @NonNull UnsafeCursor readAndWriteUnsafe() {
        return readAndWriteUnsafe(new RealUnsafeCursor());
    }

    @Override
    public @NonNull UnsafeCursor readAndWriteUnsafe(final @NonNull UnsafeCursor unsafeCursor) {
        if (Objects.requireNonNull(unsafeCursor).buffer != null) {
            throw new IllegalStateException("this cursor is already attached to a buffer");
        }

        unsafeCursor.buffer = this;
        unsafeCursor.readWrite = true;
        return unsafeCursor;
    }

    /**
     * Invoke `lambda` with the segment and offset at `startIndex`. Searches from the front or the back
     * depending on what's closer to `startIndex`.
     */
    private <T> T seek(final long startIndex, BiFunction<Segment, Long, T> lambda) {
        var segment = segmentQueue.head();
        if (segment == null) {
            return lambda.apply(null, -1L);
        }

        // no more doubly-linked segment queue
//        if (segmentQueue.isDoublyLinked() && size - startIndex < startIndex) {
//            // We're scanning in the back half of this buffer. Find the segment starting at the back.
//            offset = size;
//            node = segmentQueue.tail;
//            while (true) {
//                assert node != null;
//                offset -= (segment.limit - segment.pos);
//                if (offset <= startIndex || node.prev() == null) {
//                    break;
//                }
//                node = node.prev();
//            }
//        } else {
        // We're scanning in the front half of this buffer. Find the segment starting at the front.
        var offset = 0L;
        while (true) {
            assert segment != null;
            final var nextOffset = offset + (segment.limit - segment.pos);
            final var nextSegment = segment.next;
            if (nextOffset > startIndex || nextSegment == null) {
                break;
            }
            segment = nextSegment;
            offset = nextOffset;
        }
        //}
        return lambda.apply(segment, offset);
    }

    /**
     * Returns true if the range within this buffer starting at {@code segmentPos} in {@code segment} is equal to
     * {@code bytes[1..bytesLimit)}.
     */
    private static boolean rangeEquals(
            final @NonNull Segment segment,
            final int segmentPos,
            final byte[] bytes,
            final int bytesLimit
    ) {
        var _segment = segment;
        var data = _segment.data;
        var _segmentPos = segmentPos;
        var segmentLimit = _segment.limit;

        var i = 1;
        while (i < bytesLimit) {
            if (_segmentPos == segmentLimit) {
                _segment = _segment.next;
                assert _segment != null;
                data = _segment.data;
                _segmentPos = _segment.pos;
                segmentLimit = _segment.limit;
            }

            if (data[_segmentPos] != bytes[i]) {
                return false;
            }

            _segmentPos++;
            i++;
        }

        return true;
    }

    @Override
    public @NonNull OutputStream asOutputStream() {
        return new OutputStream() {
            @Override
            public void write(final int b) {
                writeByte((byte) b);
            }

            @Override
            public void write(final byte @NonNull [] data, final int offset, final int byteCount) {
                RealBuffer.this.write(data, offset, byteCount);
            }

            @Override
            public String toString() {
                return RealBuffer.this + ".asOutputStream()";
            }
        };
    }

    @Override
    public @NonNull InputStream asInputStream() {
        return new InputStream() {
            @Override
            public int read() {
                if (segmentQueue.size() > 0L) {
                    return readByte() & 0xff;
                }
                return -1;
            }

            @Override
            public int read(final byte @NonNull [] writer, final int offset, final int byteCount) {
                return readAtMostTo(writer, offset, byteCount);
            }

            @Override
            public byte @NonNull [] readAllBytes() {
                return readByteArray();
            }

            @Override
            public byte @NonNull [] readNBytes(final int len) {
                return readByteArray(len);
            }

            @Override
            public long skip(final long byteCount) {
                if (byteCount < 0L) {
                    return 0L;
                }
                final var toSkip = Math.min(byteCount, segmentQueue.expectSize(byteCount));
                skipInternal(toSkip);
                return toSkip;
            }

            @Override
            public int available() {
                return (int) Math.min(segmentQueue.size(), Integer.MAX_VALUE);
            }

            @Override
            public String toString() {
                return RealBuffer.this + ".asInputStream()";
            }
        };
    }

    @Override
    public @NonNull ReadableByteChannel asReadableByteChannel() {
        return asByteChannel();
    }

    @Override
    public @NonNull WritableByteChannel asWritableByteChannel() {
        return asByteChannel();
    }

    @Override
    public @NonNull ByteChannel asByteChannel() {
        return new ByteChannel() {
            @Override
            public int read(final @NonNull ByteBuffer writer) {
                return RealBuffer.this.readAtMostTo(writer);
            }

            @Override
            public int write(final @NonNull ByteBuffer reader) {
                return RealBuffer.this.transferFrom(reader);
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public void close() {
            }

            @Override
            public String toString() {
                return RealBuffer.this + ".asByteChannel()";
            }
        };
    }

    public static final class RealUnsafeCursor extends UnsafeCursor {
        private Segment segment = null;

        @Override
        public int next() {
            checkHasBuffer();
            assert buffer != null;
            if (offset == buffer.bytesAvailable()) {
                throw new IllegalStateException("no more bytes");
            }
            return (offset == -1L) ? seek(0L) : seek(offset + (limit - pos));
        }

        @Override
        public int seek(final long offset) {
            checkHasBuffer();
            if (!(buffer instanceof RealBuffer _buffer)) {
                throw new IllegalStateException("buffer must be an instance of RealBuffer");
            }
            final var size = _buffer.segmentQueue.size();
            if (offset < -1L || offset > size) {
                throw new ArrayIndexOutOfBoundsException("offset=" + offset + " > size=" + size);
            }

            if (offset == -1L || offset == size) {
                this.segment = null;
                this.offset = offset;
                this.data = null;
                this.pos = -1;
                this.limit = -1;
                return -1;
            }

            // Navigate to the segment that contains `offset`. Start from our current segment if possible.
            var nextOffset = 0L;
            final var currentHead = _buffer.segmentQueue.head();
            assert currentHead != null;
            Segment next = currentHead;
            if (this.segment != null) {
                final var segmentOffset = this.offset - (this.pos - this.segment.pos);
                if (segmentOffset <= offset) {
                    // Set the cursor segment to be the 'beginning'
                    nextOffset = segmentOffset;
                    next = this.segment;
                }
            }

            // no more doubly-linked segment queue
//            if (_buffer.segmentQueue.isDoublyLinked() && max - offset <= offset - min) {
//                // Start at the 'end' and search backwards
//                next = tailNode;
//                nextOffset = max;
//                if (isTail) {
//                    nextOffset -= (next.segment().limit - next.segment().pos);
//                }
//                while (nextOffset > offset) {
//                    next = next.prev();
//                    assert next != null;
//                    nextOffset -= (next.segment().limit - next.segment().pos);
//                }
//            } else {
            // Start at the 'beginning' and search forwards
            Segment previous = null;
            var nextSize = next.limit - next.pos;
            while (offset >= nextOffset + nextSize) {
                nextOffset += nextSize;
                previous = next;
                next = next.next;
                assert next != null;
                nextSize = next.limit - next.pos;
            }
            //}

            // If we're going to write and our segment is shared, swap it for a read-write one.
            if (readWrite && next.isShared()) {
                final var unsharedNext = next.unsharedCopy();
                final var nextNext = next.next;
                if (nextNext != null) {
                    assert unsharedNext.next == null;
                    Segment.NEXT.setRelease(unsharedNext, nextNext);
                } else {
                    assert _buffer.segmentQueue.tail == next;
                    _buffer.segmentQueue.tail = unsharedNext;
                }

                if (previous != null) {
                    assert previous.next == next;
                    Segment.NEXT.setRelease(previous, unsharedNext);
                } else {
                    assert _buffer.segmentQueue.headRef.value() == next;
                    SegmentQueue.HEAD_REF.setRelease(_buffer.segmentQueue, new SegmentRef.Immediate(unsharedNext));
                }
            }

            // Update this cursor to the requested offset within the found segment.
            this.segment = next;
            this.offset = offset;
            this.data = next.data;
            this.pos = next.pos + (int) (offset - nextOffset);
            this.limit = next.limit;
            return limit - pos;
        }

        @Override
        public long resizeBuffer(final long newSize) {
            checkHasBuffer();
            if (!readWrite) {
                throw new IllegalStateException("resizeBuffer() is only permitted for read/write buffers");
            }
            if (!(buffer instanceof RealBuffer _buffer)) {
                throw new IllegalStateException("buffer must be an instance of RealBuffer");
            }

            final var oldSize = _buffer.segmentQueue.size();
            if (newSize <= oldSize) {
                if (newSize < 0L) {
                    throw new IllegalArgumentException("newSize < 0: " + newSize);
                }
                // Shrink the buffer by either shrinking segments or removing them.
                var s = _buffer.segmentQueue.head();
                var remaining = newSize;
                var removeAll = false;
                while (s != null) {
                    final var segmentSize = s.limit - s.pos;
                    if (segmentSize < remaining) {
                        remaining -= segmentSize;
                        s = s.next;
                    } else if (!removeAll) {
                        _buffer.segmentQueue.tail = s;
                        s.limit = s.pos + (int) remaining;
                        removeAll = true;
                        s = s.next;
                    } else {
                        final var next = s.next;
                        SegmentPool.recycle(s);
                        s = next;
                    }
                }
                // Seek to the end.
                this.segment = null;
                this.offset = newSize;
                this.data = null;
                this.pos = -1;
                this.limit = -1;
                _buffer.segmentQueue.decrementSize(oldSize - newSize);
            } else {
                // Enlarge the buffer by either enlarging segments or adding them.
                var needsToSeek = true;
                final var bytesToAdd = new Wrapper.Long(newSize - oldSize);
                final var segmentBytesToAdd = new Wrapper.Int();
                while (bytesToAdd.value > 0L) {
                    _buffer.segmentQueue.withWritableTail(1, tail -> {
                        segmentBytesToAdd.value = (int) Math.min(bytesToAdd.value, Segment.SIZE - tail.limit);
                        tail.limit = tail.limit + segmentBytesToAdd.value;
                        bytesToAdd.value -= segmentBytesToAdd.value;
                        return null;
                    });

                    // If this is the first segment we're adding, seek to it.
                    if (needsToSeek) {
                        final var tail = _buffer.segmentQueue.tail;
                        assert tail != null;
                        this.segment = tail;
                        this.offset = oldSize;
                        this.data = tail.data;
                        this.pos = tail.limit - segmentBytesToAdd.value;
                        this.limit = tail.limit;
                        needsToSeek = false;
                    }
                }
            }

            return oldSize;
        }

        @Override
        public long expandBuffer(final int minByteCount) {
            if (minByteCount <= 0L) {
                throw new IllegalArgumentException("minByteCount <= 0: " + minByteCount);
            }
            if (minByteCount > Segment.SIZE) {
                throw new IllegalArgumentException("minByteCount > Segment.SIZE: " + minByteCount);
            }
            checkHasBuffer();
            if (!readWrite) {
                throw new IllegalStateException("expandBuffer() is only permitted for read/write buffers");
            }
            if (!(buffer instanceof RealBuffer _buffer)) {
                throw new IllegalStateException("buffer must be an instance of RealBuffer");
            }

            final var result = new Wrapper.Int();
            final var oldSize = _buffer.segmentQueue.size();
            _buffer.segmentQueue.withWritableTail(minByteCount, tail -> {
                result.value = Segment.SIZE - tail.limit;
                tail.limit = Segment.SIZE;
                return null;
            });

            // Seek to the old size.
            final var tailNode = _buffer.segmentQueue.tail;
            assert tailNode != null;
            this.segment = tailNode;
            this.offset = oldSize;
            this.data = tailNode.data;
            this.pos = Segment.SIZE - result.value;
            this.limit = Segment.SIZE;

            return result.value;
        }

        @Override
        public void close() {
            checkHasBuffer();

            buffer = null;
            segment = null;
            offset = -1L;
            data = null;
            pos = -1;
            limit = -1;
        }

        private void checkHasBuffer() {
            if (buffer == null) {
                throw new IllegalStateException("not attached to a buffer");
            }
        }
    }
}
