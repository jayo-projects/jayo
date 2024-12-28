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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import jayo.Buffer;
import jayo.ByteString;
import jayo.JayoEOFException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;
import static kotlin.text.Charsets.UTF_8;
import static kotlin.text.StringsKt.repeat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests solely for the behavior of Buffer's implementation. For generic Writer or Reader behavior use WriterTest or
 * ReaderTest, respectively.
 */
public class BufferJavaTest {
    @Test
    void readAndWriteUtf8() {
        Buffer buffer = new RealBuffer();
        buffer.write("ab");
        assertEquals(2, buffer.bytesAvailable());
        buffer.write("cdëf");
        assertEquals(7, buffer.bytesAvailable());
        assertEquals("abcd", buffer.readString(4));
        assertEquals(3, buffer.bytesAvailable());
        assertEquals("ëf", buffer.readString(3));
        assertEquals(0, buffer.bytesAvailable());
        assertThrows(JayoEOFException.class, () -> buffer.readString(1));
    }

    /**
     * Buffer's toString is the same as ByteString's.
     */
    @Test
    void bufferToString() {
        assertEquals("Buffer(size=0)", new RealBuffer().toString());
        assertEquals("Buffer(size=10 hex=610d0a620a630d645c65)",
                new RealBuffer().write("a\r\nb\nc\rd\\e").toString());
        assertEquals("Buffer(size=11 hex=547972616e6e6f73617572)",
                new RealBuffer().write("Tyrannosaur").toString());
        assertEquals("Buffer(size=16 hex=74c999cb8872616ec999cb8c73c3b472)", new RealBuffer()
                .write(ByteString.decodeHex("74c999cb8872616ec999cb8c73c3b472"))
                .toString());
        assertEquals("Buffer(size=64 hex=0000000000000000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000)",
                new RealBuffer().write(new byte[64]).toString());
        assertEquals("Buffer(size=66 hex=0000000000000000000000000000000000000000000000000000000000000000000000000000"
                        + "0000000000000000000000000000000000000000000000000000…)",
                new RealBuffer().write(new byte[66]).toString());
    }

    @Test
    void multipleSegmentBuffers() {
        Buffer buffer = new RealBuffer();
        buffer.write(repeat("a", 1000));
        buffer.write(repeat("b", 2500));
        buffer.write(repeat("c", 5000));
        buffer.write(repeat("d", 10000));
        buffer.write(repeat("e", 25000));
        buffer.write(repeat("f", 50000));

        assertEquals(repeat("a", 999), buffer.readString(999)); // a...a
        assertEquals("a" + repeat("b", 2500) + "c", buffer.readString(2502)); // ab...bc
        assertEquals(repeat("c", 4998), buffer.readString(4998)); // c...c
        assertEquals("c" + repeat("d", 10000) + "e", buffer.readString(10002)); // cd...de
        assertEquals(repeat("e", 24998), buffer.readString(24998)); // e...e
        assertEquals("e" + repeat("f", 50000), buffer.readString(50001)); // ef...f
        assertEquals(0, buffer.bytesAvailable());
    }

    @Test
    void moveBytesBetweenBuffersShareSegment() {
        int size = (Segment.SIZE / 2) - 1;
        final var segmentSizes = moveBytesBetweenBuffers(repeat("a", size), repeat("b", size));
        assertEquals(List.of(size * 2), segmentSizes);
    }

    @Test
    void moveBytesBetweenBuffersReassignSegment() {
        int size = (Segment.SIZE / 2) + 1;
        final var segmentSizes = moveBytesBetweenBuffers(repeat("a", size), repeat("b", size));
        assertEquals(asList(size, size), segmentSizes);
    }

    @Test
    void moveBytesBetweenBuffersMultipleSegments() {
        int size = 3 * Segment.SIZE + 1;
        final var segmentSizes = moveBytesBetweenBuffers(repeat("a", size), repeat("b", size));
        assertEquals(asList(Segment.SIZE, Segment.SIZE, Segment.SIZE, 1, Segment.SIZE, Segment.SIZE, Segment.SIZE, 1)
                , segmentSizes);
    }

    private List<Integer> moveBytesBetweenBuffers(String... contents) {
        StringBuilder expected = new StringBuilder();
        Buffer buffer = new RealBuffer();
        for (String s : contents) {
            Buffer reader = new RealBuffer();
            reader.write(s);
            buffer.transferFrom(reader);
            expected.append(s);
        }
        final var segmentSizes = JayoTestingKt.segmentSizes(buffer);
        assertEquals(expected.toString(), buffer.readString(expected.length()));
        return segmentSizes;
    }

    /**
     * The big part of reader's first segment is being moved.
     */
    @Test
    void writeSplitReaderBufferLeft() {
        long writeSize = Segment.SIZE / 2 + 1;

        Buffer writer = new RealBuffer();
        writer.write(repeat("b", Segment.SIZE - 10));

        Buffer reader = new RealBuffer();
        reader.write(repeat("a", Segment.SIZE * 2));
        writer.write(reader, writeSize);

        assertEquals(asList(Segment.SIZE - 10, (int) writeSize), JayoTestingKt.segmentSizes(writer));
        assertEquals(asList(Segment.SIZE - (int) writeSize, Segment.SIZE), JayoTestingKt.segmentSizes(reader));
    }

    /**
     * The big part of reader's first segment is staying put.
     */
    @Test
    void writeSplitReaderBufferRight() {
        int writeSize = Segment.SIZE / 2 - 1;

        Buffer writer = new RealBuffer();
        writer.write(repeat("b", Segment.SIZE - 10));

        Buffer reader = new RealBuffer();
        reader.write(repeat("a", Segment.SIZE * 2));
        writer.write(reader, writeSize);

        assertEquals(asList(Segment.SIZE - 10, writeSize), JayoTestingKt.segmentSizes(writer));
        assertEquals(asList(Segment.SIZE - writeSize, Segment.SIZE), JayoTestingKt.segmentSizes(reader));
    }

    @Test
    void writePrefixDoesntSplit() {
        Buffer writer = new RealBuffer();
        writer.write(repeat("b", 10));

        Buffer reader = new RealBuffer();
        reader.write(repeat("a", Segment.SIZE * 2));
        writer.write(reader, 20);

        assertEquals(List.of(30), JayoTestingKt.segmentSizes(writer));
        assertEquals(asList(Segment.SIZE - 20, Segment.SIZE), JayoTestingKt.segmentSizes(reader));
        assertEquals(30, writer.bytesAvailable());
        assertEquals(Segment.SIZE * 2L - 20, reader.bytesAvailable());
    }

    @Test
    void writePrefixDoesntSplitButRequiresCompact() {
        Buffer writer = new RealBuffer();
        writer.write(repeat("b", Segment.SIZE - 10)); // limit = size - 10
        writer.readString(Segment.SIZE - 20); // pos = size = 20

        Buffer reader = new RealBuffer();
        reader.write(repeat("a", Segment.SIZE * 2));
        writer.write(reader, 20);

        assertEquals(List.of(30), JayoTestingKt.segmentSizes(writer));
        assertEquals(asList(Segment.SIZE - 20, Segment.SIZE), JayoTestingKt.segmentSizes(reader));
        assertEquals(30L, writer.bytesAvailable());
        assertEquals(Segment.SIZE * 2L - 20, reader.bytesAvailable());
    }

    @Test
    void copyToSpanningSegments() {
        Buffer reader = new RealBuffer();
        reader.write(repeat("a", Segment.SIZE * 2));
        reader.write(repeat("b", Segment.SIZE * 2));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        reader.copyTo(out, 10, Segment.SIZE * 3L);
        String inputContent = out.toString();
        String expected = repeat("a", Segment.SIZE * 2 - 10) + repeat("b", Segment.SIZE + 10);

        assertEquals(expected, inputContent);
        assertEquals(repeat("a", Segment.SIZE * 2) + repeat("b", Segment.SIZE * 2),
                reader.readString(Segment.SIZE * 4L));
    }

    @Test
    void copyToStream() {
        Buffer buffer = new RealBuffer().write("hello, world!");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        buffer.copyTo(out);
        String outString = out.toString(UTF_8);
        assertEquals("hello, world!", outString);
        assertEquals("hello, world!", buffer.readString());
    }

    @Test
    void writeToSpanningSegments() {
        Buffer buffer = new RealBuffer();
        buffer.write(repeat("a", Segment.SIZE * 2));
        buffer.write(repeat("b", Segment.SIZE * 2));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        buffer.skip(10);
        buffer.readTo(out, Segment.SIZE * 3L);

        assertEquals(repeat("a", Segment.SIZE * 2 - 10) + repeat("b", Segment.SIZE + 10),
                out.toString());
        assertEquals(repeat("b", Segment.SIZE - 10), buffer.readString(buffer.bytesAvailable()));
    }

    @Test
    void writeToStream() {
        Buffer buffer = new RealBuffer().write("hello, world!");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        buffer.readTo(out);
        String outString = out.toString(UTF_8);
        assertEquals("hello, world!", outString);
        assertEquals(0L, buffer.bytesAvailable());
    }

    @Test
    void readFromStream() {
        InputStream in = new ByteArrayInputStream("hello, world!".getBytes(UTF_8));
        Buffer buffer = new RealBuffer();
        buffer.transferFrom(in);
        String out = buffer.readString();
        assertEquals("hello, world!", out);
    }

    @Test
    void readFromSpanningSegments() {
        InputStream in = new ByteArrayInputStream("hello, world!".getBytes(UTF_8));
        Buffer buffer = new RealBuffer().write(repeat("a", Segment.SIZE - 10));
        buffer.transferFrom(in);
        String out = buffer.readString();
        assertEquals(repeat("a", Segment.SIZE - 10) + "hello, world!", out);
    }

    @Test
    void readFromStreamWithCount() {
        InputStream in = new ByteArrayInputStream("hello, world!".getBytes(UTF_8));
        Buffer buffer = new RealBuffer();
        buffer.write(in, 10);
        String out = buffer.readString();
        assertEquals("hello, wor", out);
    }

    @Test
    void readFromDoesNotLeaveEmptyTailSegment() {
        Buffer buffer = new RealBuffer();
        buffer.transferFrom(new ByteArrayInputStream(new byte[(int) Segment.SIZE]));
        TestUtil.assertNoEmptySegments(buffer);
    }

    @Test
    void moveAllRequestedBytesWithRead() {
        Buffer writer = new RealBuffer();
        writer.write(repeat("a", 10));

        Buffer reader = new RealBuffer();
        reader.write(repeat("b", 15));

        assertEquals(10, reader.readAtMostTo(writer, 10));
        assertEquals(20, writer.bytesAvailable());
        assertEquals(5, reader.bytesAvailable());
        assertEquals(repeat("a", 10) + repeat("b", 10), writer.readString(20));
    }

    @Test
    void moveFewerThanRequestedBytesWithRead() {
        Buffer writer = new RealBuffer();
        writer.write(repeat("a", 10));

        Buffer reader = new RealBuffer();
        reader.write(repeat("b", 20));

        assertEquals(20, reader.readAtMostTo(writer, 25));
        assertEquals(30, writer.bytesAvailable());
        assertEquals(0, reader.bytesAvailable());
        assertEquals(repeat("a", 10) + repeat("b", 20), writer.readString(30));
    }

    @Test
    void indexOfWithOffset() {
        Buffer buffer = new RealBuffer();
        int halfSegment = Segment.SIZE / 2;
        buffer.write(repeat("a", halfSegment));
        buffer.write(repeat("b", halfSegment));
        buffer.write(repeat("c", halfSegment));
        buffer.write(repeat("d", halfSegment));
        assertEquals(0L, buffer.indexOf((byte) 'a', 0));
        assertEquals(halfSegment - 1, buffer.indexOf((byte) 'a', halfSegment - 1));
        assertEquals(halfSegment, buffer.indexOf((byte) 'b', halfSegment - 1));
        assertEquals(halfSegment * 2, buffer.indexOf((byte) 'c', halfSegment - 1));
        assertEquals(halfSegment * 3L, buffer.indexOf((byte) 'd', halfSegment - 1));
        assertEquals(halfSegment * 3L, buffer.indexOf((byte) 'd', halfSegment * 2));
        assertEquals(halfSegment * 3L, buffer.indexOf((byte) 'd', halfSegment * 3L));
        assertEquals(halfSegment * 4L - 1, buffer.indexOf((byte) 'd', halfSegment * 4L - 1));
    }

    @Test
    void byteAt() {
        Buffer buffer = new RealBuffer();
        buffer.write("a");
        buffer.write(repeat("b", Segment.SIZE));
        buffer.write("c");
        assertEquals('a', buffer.getByte(0));
        assertEquals('a', buffer.getByte(0)); // getByte doesn't mutate!
        assertEquals('c', buffer.getByte(buffer.bytesAvailable() - 1));
        assertEquals('b', buffer.getByte(buffer.bytesAvailable() - 2));
        assertEquals('b', buffer.getByte(buffer.bytesAvailable() - 3));
    }

    @Test
    void getByteOfEmptyBuffer() {
        Buffer buffer = new RealBuffer();
        assertThatThrownBy(() -> {
            buffer.getByte(0);
            Assertions.fail();
        }).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void writePrefixToEmptyBuffer() {
        Buffer writer = new RealBuffer();
        Buffer reader = new RealBuffer();
        reader.write("abcd");
        writer.write(reader, 2);
        assertEquals("ab", writer.readString(2));
    }

    @Test
    void cloneDoesNotObserveWritesToOriginal() {
        Buffer original = new RealBuffer();
        Buffer clone = original.clone();
        original.write("abc");
        assertEquals(0, clone.bytesAvailable());
    }

    @Test
    void cloneDoesNotObserveReadsFromOriginal() {
        Buffer original = new RealBuffer();
        original.write("abc");
        Buffer clone = original.clone();
        assertEquals("abc", original.readString(3));
        assertEquals(3, clone.bytesAvailable());
        assertEquals("ab", clone.readString(2));
    }

    @Test
    void originalDoesNotObserveWritesToClone() {
        Buffer original = new RealBuffer();
        Buffer clone = original.clone();
        clone.write("abc");
        assertEquals(0, original.bytesAvailable());
    }

    @Test
    void originalDoesNotObserveReadsFromClone() {
        Buffer original = new RealBuffer();
        original.write("abc");
        Buffer clone = original.clone();
        assertEquals("abc", clone.readString(3));
        assertEquals(3, original.bytesAvailable());
        assertEquals("ab", original.readString(2));
    }

    @Test
    void cloneMultipleSegments() {
        Buffer original = new RealBuffer();
        original.write(repeat("a", Segment.SIZE * 3));
        Buffer clone = original.clone();
        original.write(repeat("b", Segment.SIZE * 3));
        clone.write(repeat("c", Segment.SIZE * 3));

        assertEquals(repeat("a", Segment.SIZE * 3) + repeat("b", Segment.SIZE * 3),
                original.readString(Segment.SIZE * 6L));
        assertEquals(repeat("a", Segment.SIZE * 3) + repeat("c", Segment.SIZE * 3),
                clone.readString(Segment.SIZE * 6L));
    }

    @Test
    void bufferInputStreamByteByByte() throws IOException {
        Buffer reader = new RealBuffer();
        reader.write("abc");

        InputStream in = reader.asInputStream();
        assertEquals(3, in.available());
        assertEquals('a', in.read());
        assertEquals('b', in.read());
        assertEquals('c', in.read());
        assertEquals(-1, in.read());
        assertEquals(0, in.available());
    }

    @Test
    void bufferInputStreamBulkReads() throws IOException {
        Buffer reader = new RealBuffer();
        reader.write("abc");

        byte[] byteArray = new byte[4];

        Arrays.fill(byteArray, (byte) -5);
        InputStream in = reader.asInputStream();
        assertEquals(3, in.read(byteArray));
        assertEquals("[97, 98, 99, -5]", Arrays.toString(byteArray));

        Arrays.fill(byteArray, (byte) -7);
        assertEquals(-1, in.read(byteArray));
        assertEquals("[-7, -7, -7, -7]", Arrays.toString(byteArray));
    }

    /**
     * When writing data that's already buffered, there's no reason to page the
     * data by segment.
     */
    @Test
    void readAllWritesAllSegmentsAtOnce() {
        Buffer write1 = new RealBuffer().write(
                repeat("a", Segment.SIZE)
                        + repeat("b", Segment.SIZE)
                        + repeat("c", Segment.SIZE));

        Buffer reader = new RealBuffer().write(
                repeat("a", Segment.SIZE)
                        + repeat("b", Segment.SIZE)
                        + repeat("c", Segment.SIZE));

        MockWriter mockWriter = new MockWriter();

        assertEquals(Segment.SIZE * 3L, reader.transferTo(mockWriter));
        assertEquals(0, reader.bytesAvailable());
        mockWriter.assertLog("write(" + write1 + ", " + write1.bytesAvailable() + ")");
    }

    @Test
    void writeAllMultipleSegments() {
        Buffer reader = new RealBuffer().write(repeat("a", Segment.SIZE * 3));
        Buffer writer = new RealBuffer();

        assertEquals(Segment.SIZE * 3L, writer.transferFrom(reader));
        assertEquals(0, reader.bytesAvailable());
        assertEquals(repeat("a", Segment.SIZE * 3), writer.readString());
    }

    @Test
    void copyTo() {
        Buffer reader = new RealBuffer();
        reader.write("party");

        Buffer target = new RealBuffer();
        reader.copyTo(target, 1, 3);

        assertEquals("art", target.readString());
        assertEquals("party", reader.readString());
    }

    @Test
    void copyToOnSegmentBoundary() {
        String as = repeat("a", Segment.SIZE);
        String bs = repeat("b", Segment.SIZE);
        String cs = repeat("c", Segment.SIZE);
        String ds = repeat("d", Segment.SIZE);

        Buffer reader = new RealBuffer();
        reader.write(as);
        reader.write(bs);
        reader.write(cs);

        Buffer target = new RealBuffer();
        target.write(ds);

        reader.copyTo(target, as.length(), bs.length() + cs.length());
        assertEquals(ds + bs + cs, target.readString());
    }

    @Test
    void copyToOffSegmentBoundary() {
        String as = repeat("a", Segment.SIZE - 1);
        String bs = repeat("b", Segment.SIZE + 2);
        String cs = repeat("c", Segment.SIZE - 4);
        String ds = repeat("d", Segment.SIZE + 8);

        Buffer reader = new RealBuffer();
        reader.write(as);
        reader.write(bs);
        reader.write(cs);

        Buffer target = new RealBuffer();
        target.write(ds);

        reader.copyTo(target, as.length(), bs.length() + cs.length());
        assertEquals(ds + bs + cs, target.readString());
    }

    @Test
    void copyToReaderAndTargetCanBeTheSame() {
        String as = repeat("a", Segment.SIZE);
        String bs = repeat("b", Segment.SIZE);

        Buffer reader = new RealBuffer();
        reader.write(as);
        reader.write(bs);

        reader.copyTo(reader, 0, reader.bytesAvailable());
        assertEquals(as + bs + as + bs, reader.readString());
    }

    @Test
    void copyToEmptyReader() {
        Buffer reader = new RealBuffer();
        Buffer target = new RealBuffer().write("aaa");
        reader.copyTo(target, 0L, 0L);
        assertEquals("", reader.readString());
        assertEquals("aaa", target.readString());
    }

    @Test
    void copyToEmptyTarget() {
        Buffer reader = new RealBuffer().write("aaa");
        Buffer target = new RealBuffer();
        reader.copyTo(target, 0L, 3L);
        assertEquals("aaa", reader.readString());
        assertEquals("aaa", target.readString());
    }

    @Test
    void snapshotReportsAccurateSize() {
        Buffer buf = new RealBuffer().write(new byte[]{0, 1, 2, 3});
        assertEquals(1, buf.snapshot(1).byteSize());
    }

    @Test
    void fillAndDrainPool() {
        Buffer buffer = new RealBuffer();

        // Take 2 * MAX_SIZE segments. This will drain the pool, even if other tests filled it.
        buffer.write(new byte[(int) TestUtil.SEGMENT_POOL_MAX_SIZE]);
        buffer.write(new byte[(int) TestUtil.SEGMENT_POOL_MAX_SIZE]);
        assertEquals(0L, TestUtil.segmentPoolByteCount());

        // Recycle MAX_SIZE segments. They're all in the pool.
        buffer.skip(TestUtil.SEGMENT_POOL_MAX_SIZE);
        assertEquals(TestUtil.SEGMENT_POOL_MAX_SIZE, TestUtil.segmentPoolByteCount());

        // Recycle MAX_SIZE more segments. The pool is full so they get garbage collected.
        buffer.skip(TestUtil.SEGMENT_POOL_MAX_SIZE);
        assertEquals(TestUtil.SEGMENT_POOL_MAX_SIZE, TestUtil.segmentPoolByteCount());

        // Take MAX_SIZE segments to drain the pool.
        buffer.write(new byte[(int) TestUtil.SEGMENT_POOL_MAX_SIZE]);
        assertEquals(0, TestUtil.segmentPoolByteCount());

        // Take MAX_SIZE more segments. The pool is drained so these will need to be allocated.
        buffer.write(new byte[(int) TestUtil.SEGMENT_POOL_MAX_SIZE]);
        assertEquals(0, TestUtil.segmentPoolByteCount());
    }
}
