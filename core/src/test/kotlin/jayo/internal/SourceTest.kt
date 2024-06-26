/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio) and kotlinx-io (https://github.com/Kotlin/kotlinx-io), original
 * copyrights are below
 *
 * Copyright 2017-2023 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENCE file.
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

package jayo.internal

import jayo.*
import jayo.exceptions.JayoEOFException
import jayo.internal.TestUtil.assertByteArrayEquals
import jayo.internal.Utils.getBufferFromSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.stream.Stream
import kotlin.test.*

class BufferSourceTest : AbstractSourceTest(SourceFactory.BUFFER)

class RealSourceTest : AbstractSourceTest(SourceFactory.REAL_SOURCE)

class PeekBufferTest : AbstractSourceTest(SourceFactory.PEEK_BUFFER)

class PeekSourceTest : AbstractSourceTest(SourceFactory.PEEK_SOURCE)

class BufferedSourceTest : AbstractSourceTest(SourceFactory.BUFFERED_SOURCE)

abstract class AbstractSourceTest internal constructor(private val factory: SourceFactory) {
    companion object {
        @JvmStatic
        val size = Segment.SIZE * 5

        // These are tricky places where the buffer starts, ends, or segments come together.
        @JvmStatic
        fun parameters(): Stream<Arguments>? {
            return Stream.of(
                Arguments.of(0),
                Arguments.of(1),
                Arguments.of(2),
                Arguments.of(Segment.SIZE - 1),
                Arguments.of(Segment.SIZE),
                Arguments.of(Segment.SIZE + 1),
                Arguments.of(size / 2 - 1),
                Arguments.of(size / 2),
                Arguments.of(size / 2 + 1),
                Arguments.of(size - Segment.SIZE - 1),
                Arguments.of(size - Segment.SIZE),
                Arguments.of(size - Segment.SIZE + 1),
                Arguments.of(size - 3),
                Arguments.of(size - 2),
                Arguments.of(size - 1)
            )
        }
    }

    private lateinit var sink: Sink
    private lateinit var source: Source
    private var originSource: RawSource? = null

    @BeforeEach
    fun before() {
        val pipe = factory.pipe()
        sink = pipe.sink
        source = pipe.source
        originSource = pipe.originSource
    }

    @AfterEach
    fun after() {
        try {
            originSource?.close()
            source.close()
            sink.close()
        } catch (_: Exception) { /*ignored*/
        }
    }

    @Test
    fun exhausted() {
        assertTrue(source.exhausted())
    }

    @Test
    fun readBytes() {
        sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte()))
        sink.emit()
        assertEquals(0xab, (source.readByte() and 0xff).toLong())
        assertEquals(0xcd, (source.readByte() and 0xff).toLong())
        assertTrue(source.exhausted())
    }

    @Test
    fun readByteTooShortThrows() {
        assertFailsWith<JayoEOFException> {
            source.readByte()
        }
    }

    @Test
    fun readShort() {
        sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x01.toByte()))
        sink.emit()
        assertEquals(0xabcd.toShort().toLong(), source.readShort().toLong())
        assertEquals(0xef01.toShort().toLong(), source.readShort().toLong())
        assertTrue(source.exhausted())
    }

    @Test
    fun readShortLe() {
        sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x10.toByte()))
        sink.emit()
        assertEquals(0xcdab.toShort().toLong(), source.readShortLe().toLong())
        assertEquals(0x10ef.toShort().toLong(), source.readShortLe().toLong())
        assertTrue(source.exhausted())
    }

    @Test
    fun readShortSplitAcrossMultipleSegments() {
        sink.writeUtf8("a".repeat(Segment.SIZE - 1))
        sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte()))
        sink.emit()
        source.skip((Segment.SIZE - 1).toLong())
        assertEquals(0xabcd.toShort().toLong(), source.readShort().toLong())
        assertTrue(source.exhausted())
    }

    @Test
    fun readShortTooShortThrows() {
        sink.writeShort(Short.MAX_VALUE)
        sink.emit()
        source.readByte()
        assertFailsWith<JayoEOFException> {
            source.readShort()
        }
        assertEquals(1, source.readByteArray().size)
    }

    @Test
    fun readShortLeTooShortThrows() {
        sink.writeShortLe(Short.MAX_VALUE)
        sink.emit()
        source.readByte()
        assertFailsWith<JayoEOFException> {
            source.readShortLe()
        }
        assertEquals(1, source.readByteArray().size)
    }

    @Test
    fun readInt() {
        sink.write(
            byteArrayOf(
                0xab.toByte(),
                0xcd.toByte(),
                0xef.toByte(),
                0x01.toByte(),
                0x87.toByte(),
                0x65.toByte(),
                0x43.toByte(),
                0x21.toByte()
            )
        )
        sink.emit()
        assertEquals(-0x543210ff, source.readInt().toLong())
        assertEquals(-0x789abcdf, source.readInt().toLong())
        assertTrue(source.exhausted())
    }

    @Test
    fun readIntLe() {
        sink.write(
            byteArrayOf(
                0xab.toByte(),
                0xcd.toByte(),
                0xef.toByte(),
                0x10.toByte(),
                0x87.toByte(),
                0x65.toByte(),
                0x43.toByte(),
                0x21.toByte()
            )
        )
        sink.emit()
        assertEquals(0x10efcdab, source.readIntLe().toLong())
        assertEquals(0x21436587, source.readIntLe().toLong())
        assertTrue(source.exhausted())
    }

    @Test
    fun readIntSplitAcrossMultipleSegments() {
        sink.writeUtf8("a".repeat(Segment.SIZE - 3))
        sink.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x01.toByte()))
        sink.emit()
        source.skip((Segment.SIZE - 3).toLong())
        assertEquals(-0x543210ff, source.readInt().toLong())
        assertTrue(source.exhausted())
    }

    @Test
    fun readIntTooShortThrows() {
        sink.writeInt(Int.MAX_VALUE)
        sink.emit()
        source.readByte()
        assertFailsWith<JayoEOFException> {
            source.readInt()
        }
        assertEquals(3, source.readByteArray().size)
    }

    @Test
    fun readIntLeTooShortThrows() {
        sink.writeIntLe(Int.MAX_VALUE)
        sink.emit()
        source.readByte()
        assertFailsWith<JayoEOFException> {
            source.readIntLe()
        }
        assertEquals(3, source.readByteArray().size)
    }

    @Test
    fun readLong() {
        sink.write(
            byteArrayOf(
                0xab.toByte(),
                0xcd.toByte(),
                0xef.toByte(),
                0x10.toByte(),
                0x87.toByte(),
                0x65.toByte(),
                0x43.toByte(),
                0x21.toByte(),
                0x36.toByte(),
                0x47.toByte(),
                0x58.toByte(),
                0x69.toByte(),
                0x12.toByte(),
                0x23.toByte(),
                0x34.toByte(),
                0x45.toByte()
            )
        )
        sink.emit()
        assertEquals(-0x543210ef789abcdfL, source.readLong())
        assertEquals(0x3647586912233445L, source.readLong())
        assertTrue(source.exhausted())
    }

    @Test
    fun readLongLe() {
        sink.write(
            byteArrayOf(
                0xab.toByte(),
                0xcd.toByte(),
                0xef.toByte(),
                0x10.toByte(),
                0x87.toByte(),
                0x65.toByte(),
                0x43.toByte(),
                0x21.toByte(),
                0x36.toByte(),
                0x47.toByte(),
                0x58.toByte(),
                0x69.toByte(),
                0x12.toByte(),
                0x23.toByte(),
                0x34.toByte(),
                0x45.toByte()
            )
        )
        sink.emit()
        assertEquals(0x2143658710efcdabL, source.readLongLe())
        assertEquals(0x4534231269584736L, source.readLongLe())
        assertTrue(source.exhausted())
    }

    @Test
    fun readLongSplitAcrossMultipleSegments() {
        sink.writeUtf8("a".repeat(Segment.SIZE - 7))
        sink.write(
            byteArrayOf(
                0xab.toByte(),
                0xcd.toByte(),
                0xef.toByte(),
                0x01.toByte(),
                0x87.toByte(),
                0x65.toByte(),
                0x43.toByte(),
                0x21.toByte()
            )
        )
        sink.emit()
        source.skip((Segment.SIZE - 7).toLong())
        assertEquals(-0x543210fe789abcdfL, source.readLong())
        assertTrue(source.exhausted())
    }

    @Test
    fun readLongTooShortThrows() {
        sink.writeLong(Long.MAX_VALUE)
        sink.emit()
        source.readByte()
        assertFailsWith<JayoEOFException> {
            source.readLong()
        }
        assertEquals(7, source.readByteArray().size)
    }

    @Test
    fun readLongLeTooShortThrows() {
        sink.writeLongLe(Long.MAX_VALUE)
        sink.emit()
        source.readByte()
        assertFailsWith<JayoEOFException> {
            source.readLongLe()
        }
        assertEquals(7, source.readByteArray().size)
    }

    @Test
    fun transferTo() {
        getBufferFromSource(source).writeUtf8("abc")
        sink.writeUtf8("def")
        sink.emit()

        val sink = RealBuffer()
        assertEquals(6, source.transferTo(sink))
        assertEquals("abcdef", sink.readUtf8())
        assertTrue(source.exhausted())
    }

    @Test
    fun transferToExhausted() {
        val mockSink = MockSink()
        assertEquals(0, source.transferTo(mockSink))
        assertTrue(source.exhausted())
        mockSink.assertLog()
    }

    @Test
    fun readExhaustedSource() {
        val sink = RealBuffer()
        sink.writeUtf8("a".repeat(10))
        assertEquals(-1, source.readAtMostTo(sink, 10))
        assertEquals(10, sink.byteSize())
        assertTrue(source.exhausted())
    }

    @Test
    fun readZeroBytesFromSource() {
        val sink = RealBuffer()
        sink.writeUtf8("a".repeat(10))

        // Either 0 or -1 is reasonable here. For consistency with Android's
        // ByteArrayInputStream we return 0.
        assertEquals(-1, source.readAtMostTo(sink, 0))
        assertEquals(10, sink.byteSize())
        assertTrue(source.exhausted())
    }

    @Test
    fun readNegativeBytesFromSource() {
        assertFailsWith<IllegalArgumentException> {
            source.readAtMostTo(RealBuffer(), -1L)
        }
    }

    @Test
    fun readFromClosedSource() {
        if (source is Buffer) {
            return
        }

        source.close()
        assertFailsWith<IllegalStateException> {
            source.readAtMostTo(RealBuffer(), 1L)
        }
    }

    @Test
    fun readAtMostToBufferFromSourceWithFilledBuffer() {
        sink.writeByte(42)
        sink.flush()

        source.request(1)
        assertEquals(1, source.readAtMostTo(RealBuffer(), 128))
    }

    @Test
    fun readAtMostToNonEmptyBufferFromSourceWithFilledBuffer() {
        val expectedReadSize = 123

        sink.write(ByteArray(expectedReadSize))
        sink.flush()

        source.request(1)
        val buffer = RealBuffer().also { it.write(ByteArray(SEGMENT_SIZE - expectedReadSize)) }
        assertEquals(expectedReadSize.toLong(), source.readAtMostTo(buffer, SEGMENT_SIZE.toLong()))

        assertTrue(source.exhausted())
        sink.write(ByteArray(expectedReadSize))
        sink.flush()

        source.request(1)
        buffer.clear()
        assertEquals(42L, source.readAtMostTo(buffer, 42L))
    }

    @Test
    fun readAtMostToByteArrayFromSourceWithFilledBuffer() {
        sink.writeByte(42)
        sink.flush()

        source.request(1)
        assertEquals(1, source.readAtMostTo(ByteArray(128)))
    }

    @Test
    fun readToSink() {
        sink.writeUtf8("a".repeat(10000))
        sink.emit()
        val sink = RealBuffer()
        source.readTo(sink, 9999)
        assertEquals("a".repeat(9999), sink.readUtf8())
        assertEquals("a", source.readUtf8())
    }

    @Test
    fun readToSinkTooShortThrows() {
        sink.writeUtf8("Hi")
        sink.emit()
        val sink = RealBuffer()
        assertFailsWith<JayoEOFException> {
            source.readTo(sink, 5)
        }

        // Verify we read all that we could from the source.
        assertEquals("Hi", sink.readUtf8())
        assertTrue(source.exhausted())
    }

    @Test
    fun readToSinkWithNegativeByteCount() {
        val sink = RealBuffer()
        assertFailsWith<IllegalArgumentException> {
            source.readTo(sink, -1)
        }
    }

    @Test
    fun readToSinkZeroBytes() {
        sink.writeUtf8("test")
        sink.flush()
        val sink = RealBuffer()
        source.readTo(sink, 0)
        assertEquals(0, sink.byteSize())
        assertEquals("test", source.readUtf8())
    }

    @Test
    fun readToByteArray() {
        val data = RealBuffer()
        data.writeUtf8("Hello")
        data.writeUtf8("e".repeat(Segment.SIZE))

        val expected = data.copy().readByteArray()
        sink.write(data, data.byteSize())
        sink.emit()

        val sink = ByteArray(Segment.SIZE + 5)
        source.readTo(sink)
        assertArrayEquals(expected, sink)
    }

    @Test
    fun readToByteArraySubrange() {
        val buffer = RealBuffer()
        val source: Source = buffer

        val sink = ByteArray(8)

        buffer.writeUtf8("hello")
        source.readTo(sink, 0, 3)
        assertContentEquals(byteArrayOf('h'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 0, 0, 0, 0, 0), sink)
        assertEquals("lo", source.readUtf8())

        sink.fill(0)
        buffer.writeUtf8("hello")
        source.readTo(sink, 3, 5)
        assertContentEquals(
            byteArrayOf(
                0, 0, 0, 'h'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte(),
                'o'.code.toByte()
            ), sink
        )
        assertTrue(source.exhausted())

        sink.fill(0)
        buffer.writeUtf8("hello")
        source.readTo(sink, 3, 1)
        assertContentEquals(byteArrayOf(0, 0, 0, 'h'.code.toByte(), 0, 0, 0, 0), sink)
        assertEquals("ello", source.readUtf8())
    }

    @Test
    fun readToByteArrayInvalidArguments() {
        val source: Source = RealBuffer()
        val sink = ByteArray(32)

        assertFailsWith<IndexOutOfBoundsException> { source.readTo(sink, 2, -1) }
        assertFailsWith<IndexOutOfBoundsException> { source.readTo(sink, -1, 2) }
        assertFailsWith<IndexOutOfBoundsException> { source.readTo(sink, 33, 34) }
        assertFailsWith<IndexOutOfBoundsException> { source.readTo(sink, 0, 33) }
    }

    @Test
    fun readToByteArrayTooShortThrows() {
        sink.writeUtf8("Hello")
        sink.emit()

        val array = ByteArray(6)
        assertFailsWith<JayoEOFException> {
            source.readTo(array)
        }

        // Verify we read all that we could from the source.
        assertArrayEquals(
            byteArrayOf(
                'H'.code.toByte(),
                'e'.code.toByte(),
                'l'.code.toByte(),
                'l'.code.toByte(),
                'o'.code.toByte(),
                0
            ),
            array
        )
    }

    @Test
    fun readAtMostToByteArray() {
        sink.writeUtf8("abcd")
        sink.emit()

        val sink = ByteArray(3)
        val read = source.readAtMostTo(sink)
        assertEquals(3, read.toLong())
        val expected = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
        assertArrayEquals(expected, sink)
    }

    @Test
    fun readAtMostToByteArrayNotEnough() {
        sink.writeUtf8("abcd")
        sink.emit()

        val sink = ByteArray(5)
        val read = source.readAtMostTo(sink)
        assertEquals(4, read.toLong())
        val expected =
            byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 'd'.code.toByte(), 0)
        assertArrayEquals(expected, sink)
    }

    @Test
    fun readAtMostToByteArrayOffsetAndCount() {
        sink.writeUtf8("abcd")
        sink.emit()

        val sink = ByteArray(7)
        val bytesToRead = 3
        val read = source.readAtMostTo(sink, 2, bytesToRead)
        assertEquals(3, read.toLong())
        val expected =
            byteArrayOf(0, 0, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0, 0)
        assertArrayEquals(expected, sink)
    }

    @Test
    fun readAtMostToByteArrayFromOffset() {
        sink.writeUtf8("abcd")
        sink.emit()

        val sink = ByteArray(7)
        val read = source.readAtMostTo(sink, 4, 3)
        assertEquals(3, read.toLong())
        val expected =
            byteArrayOf(0, 0, 0, 0, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
        assertArrayEquals(expected, sink)
    }

    @Test
    fun readAtMostToByteArrayWithInvalidArguments() {
        sink.write(ByteArray(10))
        sink.emit()

        val sink = ByteArray(4)

        assertFailsWith<IndexOutOfBoundsException> {
            source.readAtMostTo(sink, 4, 1)
        }

        assertFailsWith<IndexOutOfBoundsException> {
            source.readAtMostTo(sink, 1, 5)
        }

        assertFailsWith<IndexOutOfBoundsException> {
            source.readAtMostTo(sink, -1, 2)
        }
    }

    @Test
    fun readByteArray() {
        val string = "abcd" + "e".repeat(Segment.SIZE)
        sink.writeUtf8(string)
        sink.emit()
        assertArrayEquals(string.toByteArray(), source.readByteArray())
    }

    @Test
    fun readByteArrayPartial() {
        sink.writeUtf8("abcd")
        sink.emit()
        assertEquals("[97, 98, 99]", source.readByteArray(3).contentToString())
        assertEquals("d", source.readUtf8(1))
    }

    @Test
    fun readByteArrayTooShortThrows() {
        sink.writeUtf8("abc")
        sink.emit()
        assertFailsWith<JayoEOFException> {
            source.readByteArray(4)
        }

        assertEquals("abc", source.readUtf8()) // The read shouldn't consume any data.
    }

    @Test
    fun readByteArrayWithNegativeSizeThrows() {
        assertFailsWith<IllegalArgumentException> { source.readByteArray(-20) }
    }

    @Test
    open fun readUtf8SpansSegments() {
        sink.writeUtf8("a".repeat(Segment.SIZE * 2))
        sink.emit()
        source.skip((Segment.SIZE - 1).toLong())
        assertEquals("aa", source.readUtf8(2))
    }

    @Test
    fun readUtf8Segment() {
        sink.writeUtf8("a".repeat(Segment.SIZE))
        sink.emit()
        assertEquals("a".repeat(Segment.SIZE), source.readUtf8(Segment.SIZE.toLong()))
    }

    @Test
    fun readUtf8PartialBuffer() {
        sink.writeUtf8("a".repeat(Segment.SIZE + 20))
        sink.emit()
        assertEquals("a".repeat(Segment.SIZE + 10), source.readUtf8((Segment.SIZE + 10).toLong()))
    }

    @Test
    open fun readUtf8EntireBuffer() {
        sink.writeUtf8("a".repeat(Segment.SIZE * 2))
        sink.emit()
        assertEquals("a".repeat(Segment.SIZE * 2), source.readUtf8())
    }

    @Test
    fun readUtf8TooShortThrows() {
        sink.writeUtf8("abc")
        sink.emit()
        assertFailsWith<JayoEOFException> {
            source.readUtf8(4L)
        }

        assertEquals("abc", source.readUtf8()) // The read shouldn't consume any data.
    }

    @Test
    fun skip() {
        sink.writeUtf8("a")
        sink.writeUtf8("b".repeat(Segment.SIZE))
        sink.writeUtf8("c")
        sink.emit()
        source.skip(1)
        assertEquals('b'.code.toLong(), (source.readByte() and 0xff).toLong())
        source.skip((Segment.SIZE - 2).toLong())
        assertEquals('b'.code.toLong(), (source.readByte() and 0xff).toLong())
        source.skip(1)
        assertTrue(source.exhausted())
    }

    @Test
    fun skipInsufficientData() {
        sink.writeUtf8("a")
        sink.emit()
        assertFailsWith<JayoEOFException> {
            source.skip(2)
        }
    }

    @Test
    fun skipNegativeNumberOfBytes() {
        assertFailsWith<IllegalArgumentException> { source.skip(-1) }
    }

    @Test
    fun indexOf() {
        // The segment is empty.
        assertEquals(-1, source.indexOf('a'.code.toByte()))

        // The segment has one value.
        sink.writeUtf8("a") // a
        sink.emit()
        assertEquals(0, source.indexOf('a'.code.toByte()))
        assertEquals(-1, source.indexOf('b'.code.toByte()))

        // The segment has lots of data.
        sink.writeUtf8("b".repeat(Segment.SIZE - 2)) // ab...b
        sink.emit()
        assertEquals(0, source.indexOf('a'.code.toByte()))
        assertEquals(1, source.indexOf('b'.code.toByte()))
        assertEquals(-1, source.indexOf('c'.code.toByte()))

        // The segment doesn't start at 0, it starts at 2.
        source.skip(2) // b...b
        assertEquals(-1, source.indexOf('a'.code.toByte()))
        assertEquals(0, source.indexOf('b'.code.toByte()))
        assertEquals(-1, source.indexOf('c'.code.toByte()))

        // The segment is full.
        sink.writeUtf8("c") // b...bc
        sink.emit()
        assertEquals(-1, source.indexOf('a'.code.toByte()))
        assertEquals(0, source.indexOf('b'.code.toByte()))
        assertEquals((Segment.SIZE - 3).toLong(), source.indexOf('c'.code.toByte()))

        // The segment doesn't start at 2, it starts at 4.
        source.skip(2) // b...bc
        assertEquals(-1, source.indexOf('a'.code.toByte()))
        assertEquals(0, source.indexOf('b'.code.toByte()))
        assertEquals((Segment.SIZE - 5).toLong(), source.indexOf('c'.code.toByte()))

        // Two segments.
        sink.writeUtf8("d") // b...bcd, d is in the 2nd segment.
        sink.emit()
        assertEquals((Segment.SIZE - 4).toLong(), source.indexOf('d'.code.toByte()))
        assertEquals(-1, source.indexOf('e'.code.toByte()))
    }

    @Test
    fun indexOfByteWithStartOffset() {
        with(sink) {
            writeUtf8("a")
            writeUtf8("b".repeat(Segment.SIZE))
            writeUtf8("c")
            emit()
        }
        assertEquals(-1, source.indexOf('a'.code.toByte(), 1))
        assertEquals(15, source.indexOf('b'.code.toByte(), 15))
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun indexOfByteWithIndices(p: Int) {
        val a = 'a'.code.toByte()
        val c = 'c'.code.toByte()

        val bytes = ByteArray(size) { a }


        // We write c to the known point and then search for it using different windows. Some of the windows don't
        // overlap with c's position, and therefore a match shouldn't be found.
        bytes[p] = c
        sink.write(bytes)
        sink.emit()

        assertEquals(p.toLong(), source.indexOf(c, 0, size.toLong()))
        assertEquals(p.toLong(), source.indexOf(c, 0, (p + 1).toLong()))
        assertEquals(p.toLong(), source.indexOf(c, p.toLong(), size.toLong()))
        assertEquals(p.toLong(), source.indexOf(c, p.toLong(), (p + 1).toLong()))
        assertEquals(p.toLong(), source.indexOf(c, (p / 2).toLong(), (p * 2 + 1).toLong()))
        assertEquals(-1, source.indexOf(c, 0, (p / 2).toLong()))
        assertEquals(-1, source.indexOf(c, 0, p.toLong()))
        assertEquals(-1, source.indexOf(c, 0, 0))
        assertEquals(-1, source.indexOf(c, p.toLong(), p.toLong()))
    }

    @Test
    fun indexOfByteInvalidBoundsThrows() {
        sink.writeUtf8("abc")
        sink.emit()
        assertFailsWith<IllegalArgumentException>("Expected failure: fromIndex < 0") {
            source.indexOf('a'.code.toByte(), -1)
        }
        assertFailsWith<IllegalArgumentException>("Expected failure: fromIndex > toIndex") {
            source.indexOf('a'.code.toByte(), 10, 0)
        }
    }

    @Test
    fun indexOfByteWithFromIndex() {
        sink.writeUtf8("aaa")
        sink.emit()
        assertEquals(0, source.indexOf('a'.code.toByte()))
        assertEquals(0, source.indexOf('a'.code.toByte(), 0))
        assertEquals(1, source.indexOf('a'.code.toByte(), 1))
        assertEquals(2, source.indexOf('a'.code.toByte(), 2))
    }

    @Test
    fun request() {
        with(sink) {
            writeUtf8("a")
            writeUtf8("b".repeat(Segment.SIZE))
            writeUtf8("c")
            emit()
        }
        assertTrue(source.request((Segment.SIZE + 2).toLong()))
        assertFalse(source.request((Segment.SIZE + 3).toLong()))
    }

    @Test
    fun requestZeroBytes() {
        assertTrue(source.request(0))
    }

    @Test
    fun requestNegativeNumberOfBytes() {
        assertFailsWith<IllegalArgumentException> { source.request(-1) }
    }

    @Test
    fun require() {
        with(sink) {
            writeUtf8("a")
            writeUtf8("b".repeat(Segment.SIZE))
            writeUtf8("c")
            emit()
        }
        source.require((Segment.SIZE + 2).toLong())
        assertFailsWith<JayoEOFException> {
            source.require((Segment.SIZE + 3).toLong())
        }
    }

    @Test
    fun requireZeroBytes() {
        source.require(0L) // should not throw
    }

    @Test
    fun requireNegativeNumberOfBytes() {
        assertFailsWith<IllegalArgumentException> { source.require(-1) }
    }

    @Test
    fun longHexString() {
        assertLongHexString("8000000000000000", Long.MIN_VALUE)
        assertLongHexString("fffffffffffffffe", -0x2L)
        assertLongHexString("FFFFFFFFFFFFFFFe", -0x2L)
        assertLongHexString("ffffffffffffffff", -0x1L)
        assertLongHexString("FFFFFFFFFFFFFFFF", -0x1L)
        assertLongHexString("0000000000000000", 0x0L)
        assertLongHexString("0000000000000001", 0x1L)
        assertLongHexString("7999999999999999", 0x7999999999999999L)

        assertLongHexString("FF", 0xFF)
        assertLongHexString("0000000000000001", 0x1)
    }

    @Test
    fun hexStringWithManyLeadingZeros() {
        assertLongHexString("00000000000000001", 0x1)
        assertLongHexString("0000000000000000ffffffffffffffff", -0x1L)
        assertLongHexString("00000000000000007fffffffffffffff", 0x7fffffffffffffffL)
        assertLongHexString("0".repeat(Segment.SIZE + 1) + "1", 0x1)
    }

    private fun assertLongHexString(s: String, expected: Long) {
        sink.writeUtf8(s)
        sink.emit()
        val actual = source.readHexadecimalUnsignedLong()
        assertEquals(expected, actual, "$s --> $expected")
    }

    @Test
    fun longHexStringAcrossSegment() {
        with(sink) {
            writeUtf8("a".repeat(Segment.SIZE - 8))
            writeUtf8("FFFFFFFFFFFFFFFF")
            emit()
        }
        source.skip((Segment.SIZE - 8).toLong())
        assertEquals(-1, source.readHexadecimalUnsignedLong())
    }

    @Test
    fun longHexTerminatedByNonDigit() {
        sink.writeUtf8("abcd,")
        sink.emit()
        assertEquals(0xabcdL, source.readHexadecimalUnsignedLong())
    }

    @Test
    fun longHexAlphabet() {
        sink.writeUtf8("7896543210abcdef")
        sink.emit()
        assertEquals(0x7896543210abcdefL, source.readHexadecimalUnsignedLong())
        sink.writeUtf8("ABCDEF")
        sink.emit()
        assertEquals(0xabcdefL, source.readHexadecimalUnsignedLong())
    }

    @Test
    fun longHexStringTooLongThrows() {
        val value = "fffffffffffffffff"
        sink.writeUtf8(value)
        sink.emit()

        val e = assertFailsWith<NumberFormatException> {
            source.readHexadecimalUnsignedLong()
        }
        assertEquals("Number too large: fffffffffffffffff", e.message)
        //assertEquals(value, source.readString())
    }

    @Test
    fun longHexStringTooShortThrows() {
        sink.writeUtf8(" ")
        sink.emit()

        val e = assertFailsWith<NumberFormatException> {
            source.readHexadecimalUnsignedLong()
        }
        assertEquals("Expected leading [0-9a-fA-F] character but was 0x20", e.message)
        assertEquals(" ", source.readUtf8())
    }

    @Test
    fun longHexEmptySourceThrows() {
        sink.writeUtf8("")
        sink.emit()
        assertFailsWith<JayoEOFException> { source.readHexadecimalUnsignedLong() }
    }

    @Test
    fun longDecimalString() {
        assertLongDecimalString("-9223372036854775808", Long.MIN_VALUE)
        assertLongDecimalString("-1", -1L)
        assertLongDecimalString("0", 0L)
        assertLongDecimalString("1", 1L)
        assertLongDecimalString("9223372036854775807", Long.MAX_VALUE)

        assertLongDecimalString("00000001", 1L)
        assertLongDecimalString("-000001", -1L)
    }

    private fun assertLongDecimalString(s: String, expected: Long) {
        sink.writeUtf8(s)
        sink.writeUtf8("zzz")
        sink.emit()
        val actual = source.readDecimalLong()
        assertEquals(expected, actual, "$s --> $expected")
        assertEquals("zzz", source.readUtf8())
    }

    @Test
    fun longDecimalStringAcrossSegment() {
        with(sink) {
            writeUtf8("a".repeat(Segment.SIZE - 8))
            writeUtf8("1234567890123456")
            writeUtf8("zzz")
            emit()
        }
        source.skip((Segment.SIZE - 8).toLong())
        assertEquals(1234567890123456L, source.readDecimalLong())
        assertEquals("zzz", source.readUtf8())
    }

    @Test
    fun longDecimalStringTooLongThrows() {
        val value = "12345678901234567890"
        sink.writeUtf8(value) // Too many digits.
        sink.emit()

        val e = assertFailsWith<NumberFormatException> {
            source.readDecimalLong()
        }
        assertEquals("Number too large: 12345678901234567890", e.message)
        //assertEquals(value, source.readString())
    }

    @Test
    fun longDecimalStringTooHighThrows() {
        val value = "9223372036854775808"
        sink.writeUtf8(value) // Right size but cannot fit.
        sink.emit()

        val e = assertFailsWith<NumberFormatException> {
            source.readDecimalLong()
        }
        assertEquals("Number too large: 9223372036854775808", e.message)
        //assertEquals(value, source.readString())
    }

    @Test
    fun longDecimalStringTooLowThrows() {
        val value = "-9223372036854775809"
        sink.writeUtf8(value) // Right size but cannot fit.
        sink.emit()

        val e = assertFailsWith<NumberFormatException> {
            source.readDecimalLong()
        }
        assertEquals("Number too large: -9223372036854775809", e.message)
        //assertEquals(value, source.readString())
    }

    @Test
    fun longDecimalStringTooShortThrows() {
        sink.writeUtf8(" ")
        sink.emit()

        val e = assertFailsWith<NumberFormatException> {
            source.readDecimalLong()
        }
        assertEquals("Expected a digit or '-' but was 0x20", e.message)
        assertEquals(" ", source.readUtf8())
    }

    @Test
    fun longDecimalEmptyThrows() {
        sink.writeUtf8("")
        sink.emit()
        assertFailsWith<JayoEOFException> {
            source.readDecimalLong()
        }
    }

    @Test
    fun longDecimalLoneDashThrows() {
        sink.writeUtf8("-")
        sink.emit()
        assertFailsWith<JayoEOFException> {
            source.readDecimalLong()
        }
        assertEquals("", source.readUtf8())
    }

    @Test
    fun longDecimalDashFollowedByNonDigitThrows() {
        sink.writeUtf8("- ")
        sink.emit()
        assertFailsWith<NumberFormatException> {
            source.readDecimalLong()
        }
        assertEquals(" ", source.readUtf8())
    }

    @Test
    fun codePoints() {
        with(sink) {
            writeByte(0x7f)
            emit()
            assertEquals(0x7f, source.readUtf8CodePoint().toLong())

            writeByte(0xdf.toByte())
            writeByte(0xbf.toByte())
            emit()
            assertEquals(0x07ff, source.readUtf8CodePoint().toLong())

            writeByte(0xef.toByte())
            writeByte(0xbf.toByte())
            writeByte(0xbf.toByte())
            emit()
            assertEquals(0xffff, source.readUtf8CodePoint().toLong())

            writeByte(0xf4.toByte())
            writeByte(0x8f.toByte())
            writeByte(0xbf.toByte())
            writeByte(0xbf.toByte())
            emit()
            assertEquals(0x10ffff, source.readUtf8CodePoint().toLong())
        }
    }

    @Test
    fun codePointsFromExhaustedSource() {
        with(sink) {
            writeByte(0xdf.toByte()) // a second byte is missing
            emit()
            assertFailsWith<JayoEOFException> { source.readUtf8CodePoint() }
            assertEquals(1, source.readByteArray().size)

            writeByte(0xe2.toByte())
            writeByte(0x98.toByte()) // a third byte is missing
            emit()
            assertFailsWith<JayoEOFException> { source.readUtf8CodePoint() }
            assertEquals(2, source.readByteArray().size)

            writeByte(0xf0.toByte())
            writeByte(0x9f.toByte())
            writeByte(0x92.toByte()) // a forth byte is missing
            emit()
            assertFailsWith<JayoEOFException> { source.readUtf8CodePoint() }
            assertEquals(3, source.readByteArray().size)
        }
    }

    @Test
    fun decimalStringWithManyLeadingZeros() {
        assertLongDecimalString("00000000000000001", 1)
        assertLongDecimalString("00000000000000009223372036854775807", Long.MAX_VALUE)
        assertLongDecimalString("-00000000000000009223372036854775808", Long.MIN_VALUE)
        assertLongDecimalString("0".repeat(Segment.SIZE + 1) + "1", 1)
    }

    @Test
    fun peek() {
        sink.writeUtf8("abcdefghi")
        sink.emit()

        assertEquals("abc", source.readUtf8(3))

        val peek = source.peek()
        assertEquals("def", peek.readUtf8(3))
        assertEquals("ghi", peek.readUtf8(3))
        assertFalse(peek.request(1))

        assertEquals("def", source.readUtf8(3))
    }

    @Test
    fun peekMultiple() {
        sink.writeUtf8("abcdefghi")
        sink.emit()

        assertEquals("abc", source.readUtf8(3))

        val peek1 = source.peek()
        val peek2 = source.peek()

        assertEquals("def", peek1.readUtf8(3))

        assertEquals("def", peek2.readUtf8(3))
        assertEquals("ghi", peek2.readUtf8(3))
        assertFalse(peek2.request(1))

        assertEquals("ghi", peek1.readUtf8(3))
        assertFalse(peek1.request(1))

        assertEquals("def", source.readUtf8(3))
    }

    @Test
    fun peekLarge() {
        sink.writeUtf8("abcdef")
        sink.writeUtf8("g".repeat(2 * Segment.SIZE))
        sink.writeUtf8("hij")
        sink.emit()

        assertEquals("abc", source.readUtf8(3))

        val peek = source.peek()
        assertEquals("def", peek.readUtf8(3))
        peek.skip((2 * Segment.SIZE).toLong())
        assertEquals("hij", peek.readUtf8(3))
        assertFalse(peek.request(1))

        assertEquals("def", source.readUtf8(3))
        source.skip((2 * Segment.SIZE).toLong())
        assertEquals("hij", source.readUtf8(3))
    }

    @Test
    fun peekInvalid() {
        sink.writeUtf8("abcdefghi")
        sink.emit()

        assertEquals("abc", source.readUtf8(3))

        val peek = source.peek()
        assertEquals("def", peek.readUtf8(3))
        assertEquals("ghi", peek.readUtf8(3))
        assertFalse(peek.request(1))

        assertEquals("def", source.readUtf8(3))

        val e = assertFailsWith<IllegalStateException> {
            peek.readUtf8()
        }
        assertEquals("Peek source is invalid because upstream source was used", e.message)
    }

    @Test
    open fun peekSegmentThenInvalid() {
        sink.writeUtf8("abc")
        sink.writeUtf8("d".repeat(2 * Segment.SIZE))
        sink.emit()

        assertEquals("abc", source.readUtf8(3))

        // Peek a little data and skip the rest of the upstream source
        val peek = source.peek()
        assertEquals("ddd", peek.readUtf8(3))
        source.transferTo(discardingSink())

        // Skip the rest of the buffered data
        assertFailsWith<RuntimeException> {
            peek.skip(getBufferFromSource(peek).byteSize())
        }
    }

    @Test
    fun peekDoesntReadTooMuch() {
        // 6 bytes in source's buffer plus 3 bytes upstream.
        sink.writeUtf8("abcdef")
        sink.emit()
        source.require(6L)
        sink.writeUtf8("ghi")
        sink.emit()

        val peek = source.peek()

        // Read 3 bytes. This reads some of the buffered data.
        assertTrue(peek.request(3))
        assertThat(getBufferFromSource(source).byteSize()).isGreaterThanOrEqualTo(6L)
        assertThat(getBufferFromSource(source).byteSize()).isGreaterThanOrEqualTo(6L)
        assertEquals("abc", peek.readUtf8(3L))

        // Read 3 more bytes. This exhausts the buffered data.
        assertTrue(peek.request(3))
        assertThat(getBufferFromSource(source).byteSize()).isGreaterThanOrEqualTo(6L)
        assertThat(getBufferFromSource(peek).byteSize()).isGreaterThanOrEqualTo(3L)
        assertEquals("def", peek.readUtf8(3L))

        // Read 3 more bytes. This draws new bytes.
        assertTrue(peek.request(3))
        assertEquals(9, getBufferFromSource(source).byteSize())
        assertEquals(3, getBufferFromSource(peek).byteSize())
        assertEquals("ghi", peek.readUtf8(3L))
    }

    @Test
    fun factorySegmentSizes() {
        sink.writeUtf8("abc")
        sink.emit()
        source.require(3)
        assertEquals(listOf(3), segmentSizes(getBufferFromSource(source)))
    }

    @Test
    fun readUtf8Line() {
        sink.writeUtf8("first line\nsecond line\n")
        sink.flush()
        assertEquals("first line", source.readUtf8Line())
        assertEquals("second line\n", source.readUtf8())
        assertEquals(null, source.readUtf8Line())

        sink.writeUtf8("\nnext line\n")
        sink.flush()
        assertEquals("", source.readUtf8Line())
        assertEquals("next line", source.readUtf8Line())

        sink.writeUtf8("There is no newline!")
        sink.flush()
        assertEquals("There is no newline!", source.readUtf8Line())

        sink.writeUtf8("Wot do u call it?\r\nWindows")
        sink.flush()
        assertEquals("Wot do u call it?", source.readUtf8Line())
        source.transferTo(discardingSink())

        sink.writeUtf8("reo\rde\red\n")
        sink.flush()
        assertEquals("reo\rde\red", source.readUtf8Line())
    }

    @Test
    fun readUtf8LineStrict() {
        sink.writeUtf8("first line\nsecond line\n")
        sink.flush()
        assertEquals("first line", source.readUtf8LineStrict())
        assertEquals("second line\n", source.readUtf8())
        assertFailsWith<JayoEOFException> { source.readUtf8LineStrict() }

        sink.writeUtf8("\nnext line\n")
        sink.flush()
        assertEquals("", source.readUtf8LineStrict())
        assertEquals("next line", source.readUtf8LineStrict())

        sink.writeUtf8("There is no newline!")
        sink.flush()
        assertFailsWith<JayoEOFException> { source.readUtf8LineStrict() }
        assertEquals("There is no newline!", source.readUtf8())

        sink.writeUtf8("Wot do u call it?\r\nWindows")
        sink.flush()
        assertEquals("Wot do u call it?", source.readUtf8LineStrict())
        source.transferTo(discardingSink())

        sink.writeUtf8("reo\rde\red\n")
        sink.flush()
        assertEquals("reo\rde\red", source.readUtf8LineStrict())

        sink.writeUtf8("line\n")
        sink.flush()
        assertFailsWith<JayoEOFException> { source.readUtf8LineStrict(3) }
        assertEquals("line", source.readUtf8LineStrict(4))
        assertTrue(source.exhausted())

        sink.writeUtf8("line\r\n")
        sink.flush()
        assertFailsWith<JayoEOFException> { source.readUtf8LineStrict(3) }
        assertEquals("line", source.readUtf8LineStrict(4))
        assertTrue(source.exhausted())

        sink.writeUtf8("line\n")
        sink.flush()
        assertEquals("line", source.readUtf8LineStrict(5))
        assertTrue(source.exhausted())
    }

    @Test
    fun readUnsignedByte() {
        with(sink) {
            writeByte(0)
            writeByte(-1)
            writeByte(-128)
            writeByte(127)
            flush()
        }

        assertEquals(0u, source.readUByte())
        assertEquals(255u, source.readUByte())
        assertEquals(128u, source.readUByte())
        assertEquals(127u, source.readUByte())
        assertTrue(source.exhausted())
    }

    @Test
    fun readTooShortUnsignedByteThrows() {
        assertFailsWith<JayoEOFException> { source.readUByte() }
    }

    @Test
    fun readUnsignedShort() {
        with(sink) {
            writeShort(0)
            writeShort(-1)
            writeShort(-32768)
            writeShort(32767)
            flush()
        }

        assertEquals(0u, source.readUShort())
        assertEquals(65535u, source.readUShort())
        assertEquals(32768u, source.readUShort())
        assertEquals(32767u, source.readUShort())
        assertTrue(source.exhausted())
    }

    @Test
    fun readUnsignedShortLe() {
        sink.write(byteArrayOf(0x12, 0x34))
        sink.flush()
        assertEquals(0x3412u, source.readUShortLe())
    }

    @Test
    fun readTooShortUnsignedShortThrows() {
        assertFailsWith<JayoEOFException> { source.readUShort() }
        sink.writeByte(0)
        sink.flush()
        assertFailsWith<JayoEOFException> { source.readUShort() }
        assertTrue(source.request(1))
    }

    @Test
    fun readTooShortUnsignedShortLeThrows() {
        assertFailsWith<JayoEOFException> { source.readUShortLe() }
        sink.writeByte(0)
        sink.flush()
        assertFailsWith<JayoEOFException> { source.readUShortLe() }
        assertTrue(source.request(1))
    }

    @Test
    fun readUnsignedInt() {
        with(sink) {
            writeInt(0)
            writeInt(-1)
            writeInt(Int.MIN_VALUE)
            writeInt(Int.MAX_VALUE)
            flush()
        }

        assertEquals(0u, source.readUInt())
        assertEquals(UInt.MAX_VALUE, source.readUInt())
        assertEquals(2147483648u, source.readUInt())
        assertEquals(Int.MAX_VALUE.toUInt(), source.readUInt())
        assertTrue(source.exhausted())
    }

    @Test
    fun readUnsignedIntLe() {
        sink.write(byteArrayOf(0x12, 0x34, 0x56, 0x78))
        sink.flush()
        assertEquals(0x78563412u, source.readUIntLe())
    }

    @Test
    fun readTooShortUnsignedIntThrows() {
        assertFailsWith<JayoEOFException> { source.readUInt() }
        sink.writeByte(0)
        sink.flush()
        assertFailsWith<JayoEOFException> { source.readUInt() }
        sink.writeByte(0)
        sink.flush()
        assertFailsWith<JayoEOFException> { source.readUInt() }
        sink.writeByte(0)
        sink.flush()
        assertFailsWith<JayoEOFException> { source.readUInt() }
        assertTrue(source.request(3))
    }

    @Test
    fun readTooShortUnsignedIntLeThrows() {
        assertFailsWith<JayoEOFException> { source.readUIntLe() }
        sink.writeByte(0)
        sink.flush()
        assertFailsWith<JayoEOFException> { source.readUIntLe() }
        sink.writeByte(0)
        sink.flush()
        assertFailsWith<JayoEOFException> { source.readUIntLe() }
        sink.writeByte(0)
        sink.flush()
        assertFailsWith<JayoEOFException> { source.readUIntLe() }
        assertTrue(source.request(3))
    }

    @Test
    fun readUnsignedLong() {
        with(sink) {
            writeLong(0)
            writeLong(-1)
            writeLong(Long.MIN_VALUE)
            writeLong(Long.MAX_VALUE)
            flush()
        }

        assertEquals(0u, source.readULong())
        assertEquals(ULong.MAX_VALUE, source.readULong())
        assertEquals(9223372036854775808u, source.readULong())
        assertEquals(Long.MAX_VALUE.toULong(), source.readULong())
        assertTrue(source.exhausted())
    }

    @Test
    fun readUnsignedLongLe() {
        sink.write(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0xff.toByte()))
        sink.flush()
        assertEquals(0xff07060504030201u, source.readULongLe())
    }

    @Test
    fun readTooShortUnsignedLongThrows() {
        assertFailsWith<JayoEOFException> { source.readULong() }
        for (i in 0 until 7) {
            sink.writeByte(0)
            sink.flush()
            assertFailsWith<JayoEOFException> { source.readULong() }
        }
        assertTrue(source.request(7))
    }

    @Test
    fun readTooShortUnsignedLongLeThrows() {
        assertFailsWith<JayoEOFException> { source.readULongLe() }
        for (i in 0 until 7) {
            sink.writeByte(0)
            sink.flush()
            assertFailsWith<JayoEOFException> { source.readULongLe() }
        }
        assertTrue(source.request(7))
    }

    @Test
    fun readByteString() {
        with(sink) {
            writeUtf8("abcd")
            writeUtf8("e".repeat(Segment.SIZE))
            emit()
        }
        assertEquals("abcd" + "e".repeat(Segment.SIZE), source.readByteString().decodeToUtf8())
    }

    @Test
    fun readByteStringPartial() {
        with(sink) {
            writeUtf8("abcd")
            writeUtf8("e".repeat(Segment.SIZE))
            emit()
        }
        assertEquals("abc", source.readByteString(3).decodeToUtf8())
        assertEquals("d", source.readByteString(1).decodeToUtf8())
    }

    @Test
    fun readByteStringTooShortThrows() {
        sink.writeUtf8("abc")
        sink.emit()
        assertFailsWith<JayoEOFException> { source.readByteString(4) }

        assertEquals("abc", source.readUtf8()) // The read shouldn't consume any data.
    }

    @Test
    fun readUtf8String() {
        with(sink) {
            writeUtf8("abcd")
            writeUtf8("e".repeat(Segment.SIZE))
            emit()
        }
        assertThat(source.readUtf8String().codePoints())
            .containsExactly('a'.code, 'b'.code, 'c'.code, 'd'.code, *IntArray(Segment.SIZE) { 'e'.code }.toTypedArray())
    }

    @Test
    fun readUtf8StringPartial() {
        with(sink) {
            writeUtf8("abcd")
            writeUtf8("e".repeat(Segment.SIZE))
            emit()
        }
        assertThat(source.readUtf8String(3).codePoints())
            .containsExactly('a'.code, 'b'.code, 'c'.code)
        assertThat(source.readUtf8String(1).codePoints())
            .containsExactly('d'.code)
    }

    @Test
    fun readUtf8StringTooShortThrows() {
        sink.writeUtf8("abc")
        sink.emit()
        assertFailsWith<JayoEOFException> { source.readUtf8String(4) }

        assertThat(source.readUtf8String().codePoints())
            .containsExactly('a'.code, 'b'.code, 'c'.code) // The read shouldn't consume any data.
    }

    @Test
    fun indexOfByteString() {
        assertEquals(-1, source.indexOf("flop".encodeToByteString()))

        sink.writeUtf8("flip flop")
        sink.emit()
        assertEquals(5, source.indexOf("flop".encodeToByteString()))
        source.readUtf8() // Clear stream.

        // Make sure we backtrack and resume searching after partial match.
        sink.writeUtf8("hi hi hi hey")
        sink.emit()
        assertEquals(3, source.indexOf("hi hi hey".encodeToByteString()))
    }

    @Test
    fun indexOfByteStringAtSegmentBoundary() {
        sink.writeUtf8("a".repeat(Segment.SIZE - 1))
        sink.writeUtf8("bcd")
        sink.emit()
        assertEquals(
            (Segment.SIZE - 3).toLong(),
            source.indexOf("aabc".encodeToByteString(), (Segment.SIZE - 4).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 3).toLong(),
            source.indexOf("aabc".encodeToByteString(), (Segment.SIZE - 3).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 2).toLong(),
            source.indexOf("abcd".encodeToByteString(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 2).toLong(),
            source.indexOf("abc".encodeToByteString(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 2).toLong(),
            source.indexOf("abc".encodeToByteString(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 2).toLong(),
            source.indexOf("ab".encodeToByteString(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 2).toLong(),
            source.indexOf("a".encodeToByteString(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 1).toLong(),
            source.indexOf("bc".encodeToByteString(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 1).toLong(),
            source.indexOf("b".encodeToByteString(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            Segment.SIZE.toLong(),
            source.indexOf("c".encodeToByteString(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            Segment.SIZE.toLong(),
            source.indexOf("c".encodeToByteString(), Segment.SIZE.toLong()),
        )
        assertEquals(
            (Segment.SIZE + 1).toLong(),
            source.indexOf("d".encodeToByteString(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            (Segment.SIZE + 1).toLong(),
            source.indexOf("d".encodeToByteString(), (Segment.SIZE + 1).toLong()),
        )
    }

    @Test
    fun indexOfDoesNotWrapAround() {
        sink.writeUtf8("a".repeat(Segment.SIZE - 1))
        sink.writeUtf8("bcd")
        sink.emit()
        assertEquals(-1, source.indexOf("abcda".encodeToByteString(), (Segment.SIZE - 3).toLong()))
    }

    @Test
    fun indexOfByteStringWithOffset() {
        assertEquals(-1, source.indexOf("flop".encodeToByteString(), 1))

        sink.writeUtf8("flop flip flop")
        sink.emit()
        assertEquals(10, source.indexOf("flop".encodeToByteString(), 1))
        source.readUtf8() // Clear stream

        // Make sure we backtrack and resume searching after partial match.
        sink.writeUtf8("hi hi hi hi hey")
        sink.emit()
        assertEquals(6, source.indexOf("hi hi hey".encodeToByteString(), 1))
    }

    @Test
    fun indexOfEmptyByteString() {
        assertEquals(0, source.indexOf(ByteString.EMPTY))

        sink.writeUtf8("blablabla")
        sink.emit()
        assertEquals(0, source.indexOf(ByteString.EMPTY))
    }

    @Test
    fun indexOfByteStringInvalidArgumentsThrows() {
        assertFailsWith<IllegalArgumentException> {
            source.indexOf("hi".encodeToByteString(), -1)
        }
    }

    @Test
    fun indexOfElement() {
        sink.writeUtf8("a").writeUtf8("b".repeat(Segment.SIZE)).writeUtf8("c")
        sink.emit()
        assertEquals(0, source.indexOfElement("DEFGaHIJK".encodeToByteString()))
        assertEquals(1, source.indexOfElement("DEFGHIJKb".encodeToByteString()))
        assertEquals((Segment.SIZE + 1).toLong(), source.indexOfElement("cDEFGHIJK".encodeToByteString()))
        assertEquals(1, source.indexOfElement("DEFbGHIc".encodeToByteString()))
        assertEquals(-1L, source.indexOfElement("DEFGHIJK".encodeToByteString()))
        assertEquals(-1L, source.indexOfElement("".encodeToByteString()))
    }

    @Test
    fun indexOfElementWithOffset() {
        sink.writeUtf8("a").writeUtf8("b".repeat(Segment.SIZE)).writeUtf8("c")
        sink.emit()
        assertEquals(-1, source.indexOfElement("DEFGaHIJK".encodeToByteString(), 1))
        assertEquals(15, source.indexOfElement("DEFGHIJKb".encodeToByteString(), 15))
    }

    @Test
    fun rangeEquals() {
        sink.writeUtf8("A man, a plan, a canal. Panama.")
        sink.emit()
        assertTrue(source.rangeEquals(7, "a plan".encodeToByteString()))
        assertTrue(source.rangeEquals(0, "A man".encodeToByteString()))
        assertTrue(source.rangeEquals(24, "Panama".encodeToByteString()))
        assertFalse(source.rangeEquals(24, "Panama. Panama. Panama.".encodeToByteString()))
    }

    @Test
    fun rangeEqualsWithOffsetAndCount() {
        sink.writeUtf8("A man, a plan, a canal. Panama.")
        sink.emit()
        assertTrue(source.rangeEquals(7, "aaa plannn".encodeToByteString(), 2, 6))
        assertTrue(source.rangeEquals(0, "AAA mannn".encodeToByteString(), 2, 5))
        assertTrue(source.rangeEquals(24, "PPPanamaaa".encodeToByteString(), 2, 6))
    }

    @Test
    fun rangeEqualsArgumentValidation() {
        // Negative source offset.
        assertFalse(source.rangeEquals(-1, "A".encodeToByteString()))
        // Negative bytes offset.
        assertFalse(source.rangeEquals(0, "A".encodeToByteString(), -1, 1))
        // Bytes offset longer than bytes length.
        assertFalse(source.rangeEquals(0, "A".encodeToByteString(), 2, 1))
        // Negative byte count.
        assertFalse(source.rangeEquals(0, "A".encodeToByteString(), 0, -1))
        // Byte count longer than bytes length.
        assertFalse(source.rangeEquals(0, "A".encodeToByteString(), 0, 2))
        // Bytes offset plus byte count longer than bytes length.
        assertFalse(source.rangeEquals(0, "A".encodeToByteString(), 1, 1))
    }

    @Test
    fun inputStream() {
        sink.writeUtf8("abc")
        sink.emit()
        val input: InputStream = source.asInputStream()
        val bytes = byteArrayOf('z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte())
        val read: Int = input.read(bytes)
        assertEquals(3, read)
        assertByteArrayEquals("abc", bytes)
        assertEquals(-1, input.read())
    }

    @Test
    fun inputStreamOffsetCount() {
        sink.writeUtf8("abcde")
        sink.emit()
        val input: InputStream = source.asInputStream()
        val bytes =
            byteArrayOf('z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte())
        val read: Int = input.read(bytes, 1, 3)
        assertEquals(3, read)
        assertByteArrayEquals("zabcz", bytes)
    }

    @Test
    fun inputStreamOffsetCountNBytes() {
        sink.writeUtf8("abcde")
        sink.emit()
        val input: InputStream = source.asInputStream()
        val bytes =
            byteArrayOf('z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte())
        val read: Int = input.readNBytes(bytes, 1, 3)
        assertEquals(3, read)
        assertByteArrayEquals("zabcz", bytes)
    }

    @Test
    fun inputStreamReadNbytes() {
        sink.writeUtf8("abcde")
        sink.emit()
        val input: InputStream = source.asInputStream()
        val bytes: ByteArray = input.readNBytes(3)
        assertByteArrayEquals("abc", bytes)
    }

    @Test
    fun inputStreamReadAllBytes() {
        sink.writeUtf8("abcde")
        sink.emit()
        val input: InputStream = source.asInputStream()
        val bytes: ByteArray = input.readAllBytes()
        assertByteArrayEquals("abcde", bytes)
    }

    @Test
    fun inputStreamSkip() {
        sink.writeUtf8("abcde")
        sink.emit()
        val input: InputStream = source.asInputStream()
        assertEquals(4, input.skip(4))
        assertEquals('e'.code, input.read())
        sink.writeUtf8("abcde")
        sink.emit()
        @Suppress("KotlinConstantConditions")
        assertEquals(0, input.skip(-42L)) // Try to skip when exhausted.
        assertEquals(5, input.skip(10)) // Try to skip too much.
        assertEquals(0, input.skip(1)) // Try to skip when exhausted.
    }

    @Test
    fun inputStreamSkipNBytes() {
        sink.writeUtf8("abcde")
        sink.emit()
        val input: InputStream = source.asInputStream()
        input.skipNBytes(4)
        assertEquals('e'.code, input.read())
        sink.writeUtf8("abcde")
        sink.emit()
        assertFailsWith<EOFException> { input.skipNBytes(10) } // Try to skip too much.
        assertFailsWith<EOFException> { input.skipNBytes(1) } // Try to skip when exhausted.
    }

    @Test
    fun inputStreamCharByChar() {
        sink.writeUtf8("abc")
        sink.emit()
        val input: InputStream = source.asInputStream()
        assertEquals('a'.code, input.read())
        assertEquals('b'.code, input.read())
        assertEquals('c'.code, input.read())
        assertEquals(-1, input.read())
    }

    @Test
    fun writeToStream() {
        sink.writeUtf8("hello, world!")
        sink.emit()
        val input: InputStream = source.asInputStream()
        val out = ByteArrayOutputStream()
        input.transferTo(out)
        val outString = String(out.toByteArray(), Charsets.UTF_8)
        assertEquals("hello, world!", outString)
        assertEquals(-1, input.read())
    }

    @Test
    fun inputStreamBounds() {
        sink.writeUtf8("a".repeat(100))
        sink.emit()
        val input: InputStream = source.asInputStream()
        assertFailsWith<IndexOutOfBoundsException> {
            input.read(ByteArray(100), 50, 51)
        }
        assertFailsWith<IndexOutOfBoundsException> {
            input.readNBytes(ByteArray(100), 50, 51)
        }
        assertFailsWith<IllegalArgumentException> {
            input.readNBytes(-1)
        }
    }

    @Test
    fun inputStreamForClosedSource() {
        if (source is Buffer) {
            return
        }

        sink.writeByte(0)
        sink.emit()

        val input = source.asInputStream()
        source.close()
        assertFailsWith<IOException> { input.read() }
        assertFailsWith<IOException> { input.readNBytes(1) }
        assertFailsWith<IOException> { input.readAllBytes() }
        assertFailsWith<IOException> { input.read(ByteArray(1)) }
        assertFailsWith<IOException> { input.read(ByteArray(10), 0, 1) }
        assertFailsWith<IOException> { input.readNBytes(ByteArray(10), 0, 1) }
        assertFailsWith<IOException> { input.skip(1L) }
    }

    @Test
    fun inputStreamClosesSource() {
        if (source is Buffer) {
            return
        }

        sink.writeByte(0)
        sink.emit()

        val input = source.asInputStream()
        input.close()

        assertFailsWith<IllegalStateException> { source.readByte() }
    }

    @Test
    fun inputStreamAvailable() {
        val input = source.asInputStream()
        assertEquals(0, input.available())

        sink.writeInt(42)
        sink.emit()
        assertTrue(source.request(4)) // fill the buffer

        assertEquals(4, input.available())

        input.read()
        assertEquals(3, input.available())

        source.readByte()
        assertEquals(2, input.available())

        sink.writeByte(0)
        sink.emit()

        val expectedBytes = if (source is Buffer) {
            3
        } else {
            2
        }
        assertEquals(expectedBytes, input.available())
    }

    @Test
    fun inputStreamAvailableForClosedSource() {
        if (source is Buffer) {
            return
        }

        val input = source.asInputStream()
        source.close()

        assertFailsWith<IOException> { input.available() }
    }

    @Test
    fun readNioBuffer() {
        val expected = "abcdefg"
        sink.writeUtf8("abcdefg")
        sink.emit()
        val nioByteBuffer: ByteBuffer = ByteBuffer.allocate(1024)
        val byteCount: Int = source.readAtMostTo(nioByteBuffer)
        assertEquals(expected.length, byteCount)
        assertEquals(expected.length, nioByteBuffer.position())
        assertEquals(nioByteBuffer.capacity(), nioByteBuffer.limit())
        nioByteBuffer.flip()
        val data = ByteArray(expected.length)
        nioByteBuffer.get(data)
        assertEquals(expected, String(data))
    }

    /** Note that this test crashes the VM on Android.  */
    @Test
    open fun readLargeNioBufferOnlyReadsOneSegment() {
        val expected: String = "a".repeat(SEGMENT_SIZE)
        sink.writeUtf8("a".repeat(SEGMENT_SIZE * 4))
        sink.emit()
        val nioByteBuffer: ByteBuffer = ByteBuffer.allocate(SEGMENT_SIZE * 3)
        val byteCount: Int = source.readAtMostTo(nioByteBuffer)
        assertEquals(expected.length, byteCount)
        assertEquals(expected.length, nioByteBuffer.position())
        assertEquals(nioByteBuffer.capacity(), nioByteBuffer.limit())
        nioByteBuffer.flip()
        val data = ByteArray(expected.length)
        nioByteBuffer.get(data)
        assertEquals(expected, String(data))
    }

    @Test
    fun readNioBufferFromEmptySource() {
        assertEquals(-1, source.readAtMostTo(ByteBuffer.allocate(10)))
    }

    @Test
    fun readSpecificCharsetPartial() {
        sink.write(
            ("0000007600000259000002c80000006c000000e40000007300000259000002" +
                    "cc000000720000006100000070000000740000025900000072").decodeHex()
        )
        sink.emit()
        assertEquals("vəˈläsə", source.readString(7 * 4, Charset.forName("utf-32")))
    }

    @Test
    fun readSpecificCharset() {
        sink.write(
            ("0000007600000259000002c80000006c000000e40000007300000259000002" +
                    "cc000000720000006100000070000000740000025900000072").decodeHex()
        )

        sink.emit()
        assertEquals("vəˈläsəˌraptər", source.readString(Charset.forName("utf-32")))
    }

    @Test
    fun readStringTooShortThrows() {
        sink.writeString("abc", Charsets.US_ASCII)
        sink.emit()
        assertFailsWith<JayoEOFException> {
            source.readString(4, Charsets.US_ASCII)
        }
        assertEquals("abc", source.readUtf8()) // The read shouldn't consume any data.
    }
}
