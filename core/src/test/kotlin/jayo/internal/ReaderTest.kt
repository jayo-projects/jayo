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
import jayo.JayoEOFException
import jayo.internal.TestUtil.assertByteArrayEquals
import jayo.internal.Utils.getBufferFromReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Tag
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

class RealAsyncReaderTest : AbstractReaderTest(ReaderFactory.REAL_ASYNC_SOURCE)

class BufferReaderTest : AbstractReaderTest(ReaderFactory.BUFFER)

class BufferedReaderTest : AbstractReaderTest(ReaderFactory.BUFFERED_SOURCE)

class RealReaderTest : AbstractReaderTest(ReaderFactory.REAL_SOURCE)

class PeekBufferTest : AbstractReaderTest(ReaderFactory.PEEK_BUFFER)

class PeekReaderTest : AbstractReaderTest(ReaderFactory.PEEK_SOURCE)

class PeekAsyncReaderTest : AbstractReaderTest(ReaderFactory.PEEK_ASYNC_SOURCE)

abstract class AbstractReaderTest internal constructor(private val factory: ReaderFactory) {
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

    private lateinit var writer: Writer
    private lateinit var reader: Reader

    @BeforeEach
    fun before() {
        val pipe = factory.pipe()
        writer = pipe.writer
        reader = pipe.reader
    }

    @AfterEach
    fun after() {
        try {
            reader.close()
            writer.close()
        } catch (e: Exception) { /*ignored*/
            e.printStackTrace()
        }
    }

    @Test
    fun exhausted() {
        assertTrue(reader.exhausted())

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.exhausted()
            }
        }
    }

    @Test
    fun readBytes() {
        writer.write(byteArrayOf(0xab.toByte(), 0xcd.toByte()))
        writer.emit()
        assertEquals(0xab, (reader.readByte() and 0xff).toLong())
        assertEquals(0xcd, (reader.readByte() and 0xff).toLong())
        assertTrue(reader.exhausted())

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.readByte()
            }
        }
    }

    @Test
    fun readByteTooShortThrows() {
        assertFailsWith<JayoEOFException> {
            reader.readByte()
        }
    }

    @Test
    fun readShort() {
        writer.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x01.toByte()))
        writer.emit()
        assertEquals(0xabcd.toShort().toLong(), reader.readShort().toLong())
        assertEquals(0xef01.toShort().toLong(), reader.readShort().toLong())
        assertTrue(reader.exhausted())

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.readShort()
            }
        }
    }

    @Test
    fun readShortLe() {
        writer.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x10.toByte()))
        writer.emit()
        assertEquals(0xcdab.toShort().toLong(), reader.readShortLe().toLong())
        assertEquals(0x10ef.toShort().toLong(), reader.readShortLe().toLong())
        assertTrue(reader.exhausted())
    }

    @Test
    fun readShortSplitAcrossMultipleSegments() {
        writer.write("a".repeat(Segment.SIZE - 1))
        writer.write(byteArrayOf(0xab.toByte(), 0xcd.toByte()))
        writer.emit()
        reader.skip((Segment.SIZE - 1).toLong())
        assertEquals(0xabcd.toShort().toLong(), reader.readShort().toLong())
        assertTrue(reader.exhausted())
    }

    @Test
    fun readShortTooShortThrows() {
        writer.writeShort(Short.MAX_VALUE)
        writer.emit()
        reader.readByte()
        assertFailsWith<JayoEOFException> {
            reader.readShort()
        }
        assertEquals(1, reader.readByteArray().size)
    }

    @Test
    fun readShortLeTooShortThrows() {
        writer.writeShortLe(Short.MAX_VALUE)
        writer.emit()
        reader.readByte()
        assertFailsWith<JayoEOFException> {
            reader.readShortLe()
        }
        assertEquals(1, reader.readByteArray().size)
    }

    @Test
    fun readInt() {
        writer.write(
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
        writer.emit()
        assertEquals(-0x543210ff, reader.readInt().toLong())
        assertEquals(-0x789abcdf, reader.readInt().toLong())
        assertTrue(reader.exhausted())

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.readInt()
            }
        }
    }

    @Test
    fun readIntLe() {
        writer.write(
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
        writer.emit()
        assertEquals(0x10efcdab, reader.readIntLe().toLong())
        assertEquals(0x21436587, reader.readIntLe().toLong())
        assertTrue(reader.exhausted())
    }

    @Test
    fun readIntSplitAcrossMultipleSegments() {
        writer.write("a".repeat(Segment.SIZE - 3))
        writer.write(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 0xef.toByte(), 0x01.toByte()))
        writer.emit()
        reader.skip((Segment.SIZE - 3).toLong())
        assertEquals(-0x543210ff, reader.readInt().toLong())
        assertTrue(reader.exhausted())
    }

    @Test
    fun readIntTooShortThrows() {
        writer.writeInt(Int.MAX_VALUE)
        writer.emit()
        reader.readByte()
        assertFailsWith<JayoEOFException> {
            reader.readInt()
        }
        assertEquals(3, reader.readByteArray().size)
    }

    @Test
    fun readIntLeTooShortThrows() {
        writer.writeIntLe(Int.MAX_VALUE)
        writer.emit()
        reader.readByte()
        assertFailsWith<JayoEOFException> {
            reader.readIntLe()
        }
        assertEquals(3, reader.readByteArray().size)
    }

    @Test
    fun readLong() {
        writer.write(
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
        writer.emit()
        assertEquals(-0x543210ef789abcdfL, reader.readLong())
        assertEquals(0x3647586912233445L, reader.readLong())
        assertTrue(reader.exhausted())

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.readLong()
            }
        }
    }

    @Test
    fun readLongLe() {
        writer.write(
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
        writer.emit()
        assertEquals(0x2143658710efcdabL, reader.readLongLe())
        assertEquals(0x4534231269584736L, reader.readLongLe())
        assertTrue(reader.exhausted())
    }

    @Test
    fun readLongSplitAcrossMultipleSegments() {
        writer.write("a".repeat(Segment.SIZE - 7))
        writer.write(
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
        writer.emit()
        reader.skip((Segment.SIZE - 7).toLong())
        assertEquals(-0x543210fe789abcdfL, reader.readLong())
        assertTrue(reader.exhausted())
    }

    @Test
    fun readLongTooShortThrows() {
        writer.writeLong(Long.MAX_VALUE)
        writer.emit()
        reader.readByte()
        assertFailsWith<JayoEOFException> {
            reader.readLong()
        }
        assertEquals(7, reader.readByteArray().size)
    }

    @Test
    fun readLongLeTooShortThrows() {
        writer.writeLongLe(Long.MAX_VALUE)
        writer.emit()
        reader.readByte()
        assertFailsWith<JayoEOFException> {
            reader.readLongLe()
        }
        assertEquals(7, reader.readByteArray().size)
    }

    @Test
    fun transferTo() {
        getBufferFromReader(reader).write("abc")
        writer.write("def")
        writer.emit()

        val writer = RealBuffer()
        assertEquals(6, reader.transferTo(writer))
        assertEquals("abcdef", writer.readString())
        assertTrue(reader.exhausted())

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.transferTo(writer)
            }
        }
    }

    @Test
    fun transferToExhausted() {
        val mockWriter = MockWriter()
        assertEquals(0, reader.transferTo(mockWriter))
        assertTrue(reader.exhausted())
        mockWriter.assertLog()
    }

    @Test
    fun readExhaustedReader() {
        val writer = RealBuffer()
        writer.write("a".repeat(10))
        assertEquals(-1, reader.readAtMostTo(writer, 10))
        assertEquals(10, writer.bytesAvailable())
        assertTrue(reader.exhausted())

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.readAtMostTo(writer, 10)
            }
        }
    }

    @Test
    fun readZeroBytesFromReader() {
        val writer = RealBuffer()
        writer.write("a".repeat(10))

        // Either 0 or -1 is reasonable here. For consistency with Android's
        // ByteArrayInputStream we return 0.
        assertEquals(-1, reader.readAtMostTo(writer, 0))
        assertEquals(10, writer.bytesAvailable())
        assertTrue(reader.exhausted())
    }

    @Test
    fun readNegativeBytesFromReader() {
        assertFailsWith<IllegalArgumentException> {
            reader.readAtMostTo(RealBuffer(), -1L)
        }
    }

    @Test
    fun readFromClosedReader() {
        if (reader is Buffer) {
            return
        }

        reader.close()
        assertFailsWith<JayoClosedResourceException> {
            reader.readAtMostTo(RealBuffer(), 1L)
        }
    }

    @Test
    fun readAtMostToBufferFromReaderWithFilledBuffer() {
        writer.writeByte(42)
        writer.flush()

        reader.request(1)
        assertEquals(1, reader.readAtMostTo(RealBuffer(), 128))
    }

    @Test
    fun readAtMostToNonEmptyBufferFromReaderWithFilledBuffer() {
        val expectedReadSize = 123

        writer.write(ByteArray(expectedReadSize))
        writer.flush()

        reader.request(1)
        val buffer = RealBuffer().also { it.write(ByteArray(Segment.SIZE - expectedReadSize)) }
        assertEquals(expectedReadSize.toLong(), reader.readAtMostTo(buffer, Segment.SIZE.toLong()))

        assertTrue(reader.exhausted())
        writer.write(ByteArray(expectedReadSize))
        writer.flush()

        reader.request(1)
        buffer.clear()
        assertEquals(42L, reader.readAtMostTo(buffer, 42L))
    }

    @Test
    fun readAtMostToByteArrayFromReaderWithFilledBuffer() {
        writer.writeByte(42)
        writer.flush()

        reader.request(1)
        assertEquals(1, reader.readAtMostTo(ByteArray(128)))
    }

    @Test
    fun readToWriter() {
        writer.write("a".repeat(10000))
        writer.emit()
        val writer = RealBuffer()
        reader.readTo(writer, 9999)
        assertEquals("a".repeat(9999), writer.readString())
        assertEquals("a", reader.readString())

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.readTo(writer, 9999)
            }
        }
    }

    @Test
    fun readToWriterTooShortThrows() {
        writer.write("Hi")
        writer.emit()
        val writer = RealBuffer()
        assertFailsWith<JayoEOFException> {
            reader.readTo(writer, 5)
        }

        // Verify we read all that we could from the reader.
        assertEquals("Hi", writer.readString())
        assertTrue(reader.exhausted())
    }

    @Test
    fun readToWriterWithNegativeByteCount() {
        val writer = RealBuffer()
        assertFailsWith<IllegalArgumentException> {
            reader.readTo(writer, -1)
        }
    }

    @Test
    fun readToWriterZeroBytes() {
        writer.write("test")
        writer.flush()
        val writer = RealBuffer()
        reader.readTo(writer, 0)
        assertEquals(0, writer.bytesAvailable())
        assertEquals("test", reader.readString())
    }

    @Test
    fun readToByteArray() {
        val data = RealBuffer()
        data.write("Hello")
        data.write("e".repeat(Segment.SIZE))

        val expected = data.copy().readByteArray()
        writer.write(data, data.bytesAvailable())
        writer.emit()

        val writer = ByteArray(Segment.SIZE + 5)
        reader.readTo(writer)
        assertArrayEquals(expected, writer)

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.readTo(writer)
            }
        }
    }

    @Test
    fun readToByteArraySubrange() {
        val buffer = RealBuffer()
        val reader: Reader = buffer

        val writer = ByteArray(8)

        buffer.write("hello")
        reader.readTo(writer, 0, 3)
        assertContentEquals(byteArrayOf('h'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 0, 0, 0, 0, 0), writer)
        assertEquals("lo", reader.readString())

        writer.fill(0)
        buffer.write("hello")
        reader.readTo(writer, 3, 5)
        assertContentEquals(
            byteArrayOf(
                0, 0, 0, 'h'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte(),
                'o'.code.toByte()
            ), writer
        )
        assertTrue(reader.exhausted())

        writer.fill(0)
        buffer.write("hello")
        reader.readTo(writer, 3, 1)
        assertContentEquals(byteArrayOf(0, 0, 0, 'h'.code.toByte(), 0, 0, 0, 0), writer)
        assertEquals("ello", reader.readString())
    }

    @Test
    fun readToByteArrayInvalidArguments() {
        val reader: Reader = RealBuffer()
        val writer = ByteArray(32)

        assertFailsWith<IndexOutOfBoundsException> { reader.readTo(writer, 2, -1) }
        assertFailsWith<IndexOutOfBoundsException> { reader.readTo(writer, -1, 2) }
        assertFailsWith<IndexOutOfBoundsException> { reader.readTo(writer, 33, 34) }
        assertFailsWith<IndexOutOfBoundsException> { reader.readTo(writer, 0, 33) }
    }

    @Test
    fun readToByteArrayTooShortThrows() {
        writer.write("Hello")
        writer.emit()

        val array = ByteArray(6)
        assertFailsWith<JayoEOFException> {
            reader.readTo(array)
        }

        // Verify we read all that we could from the reader.
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

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.readTo(array)
            }
        }
    }

    @Test
    fun readAtMostToByteArray() {
        writer.write("abcd")
        writer.emit()

        val writer = ByteArray(3)
        val read = reader.readAtMostTo(writer)
        assertEquals(3, read.toLong())
        val expected = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
        assertArrayEquals(expected, writer)

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.readAtMostTo(writer)
            }
        }
    }

    @Test
    fun readAtMostToByteArrayNotEnough() {
        writer.write("abcd")
        writer.emit()

        val writer = ByteArray(5)
        val read = reader.readAtMostTo(writer)
        assertEquals(4, read.toLong())
        val expected =
            byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 'd'.code.toByte(), 0)
        assertArrayEquals(expected, writer)
    }

    @Test
    fun readAtMostToByteArrayOffsetAndCount() {
        writer.write("abcd")
        writer.emit()

        val writer = ByteArray(7)
        val bytesToRead = 3
        val read = reader.readAtMostTo(writer, 2, bytesToRead)
        assertEquals(3, read.toLong())
        val expected =
            byteArrayOf(0, 0, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0, 0)
        assertArrayEquals(expected, writer)

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.readAtMostTo(writer, 2, bytesToRead)
            }
        }
    }

    @Test
    fun readAtMostToByteArrayFromOffset() {
        writer.write("abcd")
        writer.emit()

        val writer = ByteArray(7)
        val read = reader.readAtMostTo(writer, 4, 3)
        assertEquals(3, read.toLong())
        val expected =
            byteArrayOf(0, 0, 0, 0, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())
        assertArrayEquals(expected, writer)
    }

    @Test
    fun readAtMostToByteArrayWithInvalidArguments() {
        writer.write(ByteArray(10))
        writer.emit()

        val writer = ByteArray(4)

        assertFailsWith<IndexOutOfBoundsException> {
            reader.readAtMostTo(writer, 4, 1)
        }

        assertFailsWith<IndexOutOfBoundsException> {
            reader.readAtMostTo(writer, 1, 5)
        }

        assertFailsWith<IndexOutOfBoundsException> {
            reader.readAtMostTo(writer, -1, 2)
        }
    }

    @Test
    fun readByteArray() {
        val string = "abcd" + "e".repeat(Segment.SIZE)
        writer.write(string)
        writer.emit()
        assertArrayEquals(string.toByteArray(), reader.readByteArray())

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.readByteArray()
            }
        }
    }

    @Test
    fun readByteArrayEmpty() {
        assertEquals("[]", reader.readByteArray(0).contentToString())
    }

    @Test
    fun readByteArrayPartial() {
        writer.write("abcd")
        writer.emit()
        assertEquals("[97, 98, 99]", reader.readByteArray(3).contentToString())
        assertEquals("d", reader.readString(1))

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.readByteArray(3)
            }
        }
    }

    @Test
    fun readByteArrayTooShortThrows() {
        writer.write("abc")
        writer.emit()
        assertFailsWith<JayoEOFException> {
            reader.readByteArray(4)
        }

        assertEquals("abc", reader.readString()) // The read shouldn't consume any data.
    }

    @Test
    fun readByteArrayWithNegativeSizeThrows() {
        assertFailsWith<IllegalArgumentException> { reader.readByteArray(-20) }
    }

    @Test
    open fun readUtf8StringSpansSegments() {
        writer.write("a".repeat(Segment.SIZE * 2))
        writer.emit()
        reader.skip((Segment.SIZE - 1).toLong())
        assertEquals("aa", reader.readString(2))

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.readString(2)
            }
        }
    }

    @Test
    fun readUtf8StringSegment() {
        writer.write("a".repeat(Segment.SIZE))
        writer.emit()
        assertEquals("a".repeat(Segment.SIZE), reader.readString(Segment.SIZE.toLong()))
    }

    @Test
    fun readUtf8StringPartialBuffer() {
        writer.write("a".repeat(Segment.SIZE + 20))
        writer.emit()
        assertEquals("a".repeat(Segment.SIZE + 10), reader.readString((Segment.SIZE + 10).toLong()))
    }

    @Test
    open fun readUtf8StringEntireBuffer() {
        writer.write("a".repeat(Segment.SIZE * 2))
        writer.emit()
        assertEquals("a".repeat(Segment.SIZE * 2), reader.readString())

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.readString()
            }
        }
    }

    @Test
    fun readUtf8StringTooShortThrows() {
        writer.write("abc")
        writer.emit()
        assertFailsWith<JayoEOFException> {
            reader.readString(4L)
        }

        assertEquals("abc", reader.readString()) // The read shouldn't consume any data.
    }

    @Test
    fun skip() {
        writer.write("a")
        writer.write("b".repeat(Segment.SIZE))
        writer.write("c")
        writer.emit()
        reader.skip(1)
        assertEquals('b'.code.toLong(), (reader.readByte() and 0xff).toLong())
        reader.skip((Segment.SIZE - 2).toLong())
        assertEquals('b'.code.toLong(), (reader.readByte() and 0xff).toLong())
        reader.skip(1)
        assertTrue(reader.exhausted())

        if (reader is RealReader) {
            reader.close()
            assertFailsWith<JayoClosedResourceException> {
                reader.skip(1)
            }
        }
    }

    @Test
    fun skipInsufficientData() {
        writer.write("a")
        writer.emit()
        assertFailsWith<JayoEOFException> {
            reader.skip(2)
        }
    }

    @Test
    fun skipNegativeNumberOfBytes() {
        assertFailsWith<IllegalArgumentException> { reader.skip(-1) }
    }

    // this test is a good race-condition test, do it several times !
    @RepeatedTest(50)
    @Tag("no-ci")
    fun indexOf() {
        // The segment is empty.
        assertEquals(-1, reader.indexOf('a'.code.toByte()))

        // The segment has one value.
        writer.write("a") // a
        writer.emit()
        assertEquals(0, reader.indexOf('a'.code.toByte()))
        assertEquals(-1, reader.indexOf('b'.code.toByte()))

        // The segment has lots of data.
        writer.write("b".repeat(Segment.SIZE - 2)) // ab...b
        writer.emit()
        assertEquals(0, reader.indexOf('a'.code.toByte()))
        assertEquals(1, reader.indexOf('b'.code.toByte()))
        assertEquals(-1, reader.indexOf('c'.code.toByte()))

        // The segment doesn't start at 0, it starts at 2.
        reader.skip(2) // b...b
        assertEquals(-1, reader.indexOf('a'.code.toByte()))
        assertEquals(0, reader.indexOf('b'.code.toByte()))
        assertEquals(-1, reader.indexOf('c'.code.toByte()))

        // The segment is full.
        writer.write("c") // b...bc
        writer.emit()
        assertEquals(-1, reader.indexOf('a'.code.toByte()))
        assertEquals(0, reader.indexOf('b'.code.toByte()))
        assertEquals((Segment.SIZE - 3).toLong(), reader.indexOf('c'.code.toByte()))

        // The segment doesn't start at 2, it starts at 4.
        reader.skip(2) // b...bc
        assertEquals(-1, reader.indexOf('a'.code.toByte()))
        assertEquals(0, reader.indexOf('b'.code.toByte()))
        assertEquals((Segment.SIZE - 5).toLong(), reader.indexOf('c'.code.toByte()))

        // Two segments.
        writer.write("d") // b...bcd, d is in the 2nd segment.
        writer.emit()
        assertEquals((Segment.SIZE - 4).toLong(), reader.indexOf('d'.code.toByte()))
        assertEquals(-1, reader.indexOf('e'.code.toByte()))
    }

    @Test
    fun indexOfByteWithStartOffset() {
        with(writer) {
            write("a")
            write("b".repeat(Segment.SIZE))
            write("c")
            emit()
        }
        assertEquals(-1, reader.indexOf('a'.code.toByte(), 1))
        assertEquals(15, reader.indexOf('b'.code.toByte(), 15))
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
        writer.write(bytes)
        writer.emit()

        assertEquals(p.toLong(), reader.indexOf(c, 0, size.toLong()))
        assertEquals(p.toLong(), reader.indexOf(c, 0, (p + 1).toLong()))
        assertEquals(p.toLong(), reader.indexOf(c, p.toLong(), size.toLong()))
        assertEquals(p.toLong(), reader.indexOf(c, p.toLong(), (p + 1).toLong()))
        assertEquals(p.toLong(), reader.indexOf(c, (p / 2).toLong(), (p * 2 + 1).toLong()))
        assertEquals(-1, reader.indexOf(c, 0, (p / 2).toLong()))
        assertEquals(-1, reader.indexOf(c, 0, p.toLong()))
        assertEquals(-1, reader.indexOf(c, 0, 0))
        assertEquals(-1, reader.indexOf(c, p.toLong(), p.toLong()))
    }

    @Test
    fun indexOfByteInvalidBoundsThrows() {
        writer.write("abc")
        writer.emit()
        assertFailsWith<IllegalArgumentException>("Expected failure: fromIndex < 0") {
            reader.indexOf('a'.code.toByte(), -1)
        }
        assertFailsWith<IllegalArgumentException>("Expected failure: fromIndex > toIndex") {
            reader.indexOf('a'.code.toByte(), 10, 0)
        }
    }

    @Test
    fun indexOfByteWithFromIndex() {
        writer.write("aaa")
        writer.emit()
        assertEquals(0, reader.indexOf('a'.code.toByte()))
        assertEquals(0, reader.indexOf('a'.code.toByte(), 0))
        assertEquals(1, reader.indexOf('a'.code.toByte(), 1))
        assertEquals(2, reader.indexOf('a'.code.toByte(), 2))
    }

    @Test
    fun request() {
        with(writer) {
            write("a")
            write("b".repeat(Segment.SIZE))
            write("c")
            emit()
        }
        assertTrue(reader.request((Segment.SIZE + 2).toLong()))
        assertFalse(reader.request((Segment.SIZE + 3).toLong()))
    }

    @Test
    fun requestZeroBytes() {
        assertTrue(reader.request(0))
    }

    @Test
    fun requestNegativeNumberOfBytes() {
        assertFailsWith<IllegalArgumentException> { reader.request(-1) }
    }

    @Test
    fun require() {
        with(writer) {
            write("a")
            write("b".repeat(Segment.SIZE))
            write("c")
            emit()
        }
        reader.require((Segment.SIZE + 2).toLong())
        assertFailsWith<JayoEOFException> {
            reader.require((Segment.SIZE + 3).toLong())
        }
    }

    @Test
    fun requireZeroBytes() {
        reader.require(0L) // should not throw
    }

    @Test
    fun requireNegativeNumberOfBytes() {
        assertFailsWith<IllegalArgumentException> { reader.require(-1) }
    }

    // this test is a good race-condition test, do it several times !
    @RepeatedTest(50)
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
        writer.write(s)
        writer.emit()
        val actual = reader.readHexadecimalUnsignedLong()
        assertEquals(expected, actual, "$s --> $expected")
    }

    @Test
    fun longHexStringAcrossSegment() {
        with(writer) {
            write("a".repeat(Segment.SIZE - 8))
            write("FFFFFFFFFFFFFFFF")
            emit()
        }
        reader.skip((Segment.SIZE - 8).toLong())
        assertEquals(-1, reader.readHexadecimalUnsignedLong())
    }

    @Test
    fun longHexTerminatedByNonDigit() {
        writer.write("abcd,")
        writer.emit()
        assertEquals(0xabcdL, reader.readHexadecimalUnsignedLong())
    }

    // this test is a good race-condition test, do it several times !
    @RepeatedTest(50)
    fun longHexAlphabet() {
        writer.write("7896543210abcdef")
        writer.emit()
        assertEquals(0x7896543210abcdefL, reader.readHexadecimalUnsignedLong())
        writer.write("ABCDEF")
        writer.emit()
        assertEquals(0xabcdefL, reader.readHexadecimalUnsignedLong())
    }

    @Test
    fun longHexStringTooLongThrows() {
        val value = "fffffffffffffffff"
        writer.write(value)
        writer.emit()

        val e = assertFailsWith<NumberFormatException> {
            reader.readHexadecimalUnsignedLong()
        }
        assertEquals("Number too large: fffffffffffffffff", e.message)
        //assertEquals(value, reader.readString())
    }

    @Test
    fun longHexStringTooShortThrows() {
        writer.write(" ")
        writer.emit()

        val e = assertFailsWith<NumberFormatException> {
            reader.readHexadecimalUnsignedLong()
        }
        assertEquals("Expected leading [0-9a-fA-F] character but was 0x20", e.message)
        assertEquals(" ", reader.readString())
    }

    @Test
    fun longHexEmptyReaderThrows() {
        writer.write("")
        writer.emit()
        assertFailsWith<JayoEOFException> { reader.readHexadecimalUnsignedLong() }
    }

    // this test is a good race-condition test, do it several times !
    @RepeatedTest(50)
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
        writer.write(s)
        writer.write("zzz")
        writer.emit()
        val actual = reader.readDecimalLong()
        assertEquals(expected, actual, "$s --> $expected")
        assertEquals("zzz", reader.readString())
        assertTrue(reader.exhausted())
    }

    @Test
    fun longDecimalStringAcrossSegment() {
        with(writer) {
            write("a".repeat(Segment.SIZE - 8))
            write("1234567890123456")
            write("zzz")
            emit()
        }
        reader.skip((Segment.SIZE - 8).toLong())
        assertEquals(1234567890123456L, reader.readDecimalLong())
        assertEquals("zzz", reader.readString())
    }

    @Test
    fun longDecimalStringTooLongThrows() {
        val value = "12345678901234567890"
        writer.write(value) // Too many digits.
        writer.emit()

        val e = assertFailsWith<NumberFormatException> {
            reader.readDecimalLong()
        }
        assertEquals("Number too large: 12345678901234567890", e.message)
        //assertEquals(value, reader.readString())
    }

    @Test
    fun longDecimalStringTooHighThrows() {
        val value = "9223372036854775808"
        writer.write(value) // Right size but cannot fit.
        writer.emit()

        val e = assertFailsWith<NumberFormatException> {
            reader.readDecimalLong()
        }
        assertEquals("Number too large: 9223372036854775808", e.message)
        //assertEquals(value, reader.readString())
    }

    @Test
    fun longDecimalStringTooLowThrows() {
        val value = "-9223372036854775809"
        writer.write(value) // Right size but cannot fit.
        writer.emit()

        val e = assertFailsWith<NumberFormatException> {
            reader.readDecimalLong()
        }
        assertEquals("Number too large: -9223372036854775809", e.message)
        //assertEquals(value, reader.readString())
    }

    @Test
    fun longDecimalStringTooShortThrows() {
        writer.write(" ")
        writer.emit()

        val e = assertFailsWith<NumberFormatException> {
            reader.readDecimalLong()
        }
        assertEquals("Expected a digit or '-' but was 0x20", e.message)
        assertEquals(" ", reader.readString())
    }

    @Test
    fun longDecimalEmptyThrows() {
        writer.write("")
        writer.emit()
        assertFailsWith<JayoEOFException> {
            reader.readDecimalLong()
        }
    }

    @Test
    fun longDecimalLoneDashThrows() {
        writer.write("-")
        writer.emit()
        assertFailsWith<JayoEOFException> {
            reader.readDecimalLong()
        }
        assertEquals("", reader.readString())
    }

    @Test
    fun longDecimalDashFollowedByNonDigitThrows() {
        writer.write("- ")
        writer.emit()
        assertFailsWith<NumberFormatException> {
            reader.readDecimalLong()
        }
        assertEquals(" ", reader.readString())
    }

    @Test
    fun codePoints() {
        with(writer) {
            writeByte(0x7f)
            emit()
            assertEquals(0x7f, reader.readUtf8CodePoint().toLong())

            writeByte(0xdf.toByte())
            writeByte(0xbf.toByte())
            emit()
            assertEquals(0x07ff, reader.readUtf8CodePoint().toLong())

            writeByte(0xef.toByte())
            writeByte(0xbf.toByte())
            writeByte(0xbf.toByte())
            emit()
            assertEquals(0xffff, reader.readUtf8CodePoint().toLong())

            writeByte(0xf4.toByte())
            writeByte(0x8f.toByte())
            writeByte(0xbf.toByte())
            writeByte(0xbf.toByte())
            emit()
            assertEquals(0x10ffff, reader.readUtf8CodePoint().toLong())
        }
    }

    @Test
    fun codePointsFromExhaustedReader() {
        with(writer) {
            writeByte(0xdf.toByte()) // a second byte is missing
            emit()
            assertFailsWith<JayoEOFException> { reader.readUtf8CodePoint() }
            assertEquals(1, reader.readByteArray().size)

            writeByte(0xe2.toByte())
            writeByte(0x98.toByte()) // a third byte is missing
            emit()
            assertFailsWith<JayoEOFException> { reader.readUtf8CodePoint() }
            assertEquals(2, reader.readByteArray().size)

            writeByte(0xf0.toByte())
            writeByte(0x9f.toByte())
            writeByte(0x92.toByte()) // a forth byte is missing
            emit()
            assertFailsWith<JayoEOFException> { reader.readUtf8CodePoint() }
            assertEquals(3, reader.readByteArray().size)
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
        writer.write("abcdefghi")
        writer.emit()

        assertEquals("abc", reader.readString(3))

        val peek = reader.peek()
        assertEquals("def", peek.readString(3))
        assertEquals("ghi", peek.readString(3))
        assertFalse(peek.request(1))

        assertEquals("def", reader.readString(3))
    }

    @Test
    fun peekMultiple() {
        writer.write("abcdefghi")
        writer.emit()

        assertEquals("abc", reader.readString(3))

        val peek1 = reader.peek()
        val peek2 = reader.peek()

        assertEquals("def", peek1.readString(3))

        assertEquals("def", peek2.readString(3))
        assertEquals("ghi", peek2.readString(3))
        assertFalse(peek2.request(1))

        assertEquals("ghi", peek1.readString(3))
        assertFalse(peek1.request(1))

        assertEquals("def", reader.readString(3))
    }

    @Test
    fun peekLarge() {
        writer.write("abcdef")
        writer.write("g".repeat(2 * Segment.SIZE))
        writer.write("hij")
        writer.emit()

        assertEquals("abc", reader.readString(3))

        val peek = reader.peek()
        assertEquals("def", peek.readString(3))
        peek.skip((2 * Segment.SIZE).toLong())
        assertEquals("hij", peek.readString(3))
        assertFalse(peek.request(1))

        assertEquals("def", reader.readString(3))
        reader.skip((2 * Segment.SIZE).toLong())
        assertEquals("hij", reader.readString(3))
    }

    @Test
    fun peekInvalid() {
        writer.write("abcdefghi")
        writer.emit()

        assertEquals("abc", reader.readString(3))

        val peek = reader.peek()
        assertEquals("def", peek.readString(3))
        assertEquals("ghi", peek.readString(3))
        assertFalse(peek.request(1))

        assertEquals("def", reader.readString(3))

        val e = assertFailsWith<IllegalStateException> {
            peek.readString()
        }
        assertEquals("Peek reader is invalid because upstream reader was used", e.message)
    }

    @Test
    open fun peekSegmentThenInvalid() {
        writer.write("abc")
        writer.write("d".repeat(2 * Segment.SIZE))
        writer.emit()

        assertEquals("abc", reader.readString(3))

        // Peek a little data and skip the rest of the upstream reader
        val peek = reader.peek()
        assertEquals("ddd", peek.readString(3))
        reader.transferTo(discardingWriter())

        // Skip the rest of the buffered data
        peek.skip(getBufferFromReader(peek).bytesAvailable())
        assertFailsWith<RuntimeException> {
            peek.readByte()
        }
    }

    @Test
    fun peekDoesntReadTooMuch() {
        // 6 bytes in reader's buffer plus 3 bytes upstream.
        writer.write("abcdef")
        writer.emit()
        reader.require(6L)
        writer.write("ghi")
        writer.emit()

        val peek = reader.peek()

        // Read 3 bytes. This reads some of the buffered data.
        assertTrue(peek.request(3))
        assertThat(getBufferFromReader(reader).bytesAvailable()).isGreaterThanOrEqualTo(6L)
        assertThat(getBufferFromReader(reader).bytesAvailable()).isGreaterThanOrEqualTo(6L)
        assertEquals("abc", peek.readString(3L))

        // Read 3 more bytes. This exhausts the buffered data.
        assertTrue(peek.request(3))
        assertThat(getBufferFromReader(reader).bytesAvailable()).isGreaterThanOrEqualTo(6L)
        assertThat(getBufferFromReader(peek).bytesAvailable()).isGreaterThanOrEqualTo(3L)
        assertEquals("def", peek.readString(3L))

        // Read 3 more bytes. This draws new bytes.
        assertTrue(peek.request(3))
        assertEquals(9, getBufferFromReader(reader).bytesAvailable())
        assertEquals(3, getBufferFromReader(peek).bytesAvailable())
        assertEquals("ghi", peek.readString(3L))
    }

    @Test
    fun factorySegmentSizes() {
        writer.write("abc")
        writer.emit()
        reader.require(3)
        assertEquals(listOf(3), segmentSizes(getBufferFromReader(reader)))
    }

    @Test
    fun readLine() {
        writer.write("first line\nsecond line\n")
        writer.flush()
        assertEquals("first line", reader.readLine())
        assertEquals("second line\n", reader.readString())
        assertEquals(null, reader.readLine())

        writer.write("\nnext line\n")
        writer.flush()
        assertEquals("", reader.readLine())
        assertEquals("next line", reader.readLine())

        writer.write("There is no newline!")
        writer.flush()
        assertEquals("There is no newline!", reader.readLine())

        writer.write("Wot do u call it?\r\nWindows")
        writer.flush()
        assertEquals("Wot do u call it?", reader.readLine())
        reader.transferTo(discardingWriter())

        writer.write("reo\rde\red\n")
        writer.flush()
        assertEquals("reo\rde\red", reader.readLine())
    }

    @Test
    fun readLineStrict() {
        writer.write("first line\nsecond line\n")
        writer.flush()
        assertEquals("first line", reader.readLineStrict())
        assertEquals("second line\n", reader.readString())
        assertFailsWith<JayoEOFException> { reader.readLineStrict() }

        writer.write("\nnext line\n")
        writer.flush()
        assertEquals("", reader.readLineStrict())
        assertEquals("next line", reader.readLineStrict())

        writer.write("There is no newline!")
        writer.flush()
        assertFailsWith<JayoEOFException> { reader.readLineStrict() }
        assertEquals("There is no newline!", reader.readString())

        writer.write("Wot do u call it?\r\nWindows")
        writer.flush()
        assertEquals("Wot do u call it?", reader.readLineStrict())
        reader.transferTo(discardingWriter())

        writer.write("reo\rde\red\n")
        writer.flush()
        assertEquals("reo\rde\red", reader.readLineStrict())

        writer.write("line\n")
        writer.flush()
        assertFailsWith<JayoEOFException> { reader.readLineStrict(3) }
        assertEquals("line", reader.readLineStrict(4))
        assertTrue(reader.exhausted())

        writer.write("line\r\n")
        writer.flush()
        assertFailsWith<JayoEOFException> { reader.readLineStrict(3) }
        assertEquals("line", reader.readLineStrict(4))
        assertTrue(reader.exhausted())

        writer.write("line\n")
        writer.flush()
        assertEquals("line", reader.readLineStrict(5))
        assertTrue(reader.exhausted())
    }

    @Test
    fun readUnsignedByte() {
        with(writer) {
            writeByte(0)
            writeByte(-1)
            writeByte(-128)
            writeByte(127)
            flush()
        }

        assertEquals(0u, reader.readUByte())
        assertEquals(255u, reader.readUByte())
        assertEquals(128u, reader.readUByte())
        assertEquals(127u, reader.readUByte())
        assertTrue(reader.exhausted())
    }

    @Test
    fun readTooShortUnsignedByteThrows() {
        assertFailsWith<JayoEOFException> { reader.readUByte() }
    }

    @Test
    fun readUnsignedShort() {
        with(writer) {
            writeShort(0)
            writeShort(-1)
            writeShort(-32768)
            writeShort(32767)
            flush()
        }

        assertEquals(0u, reader.readUShort())
        assertEquals(65535u, reader.readUShort())
        assertEquals(32768u, reader.readUShort())
        assertEquals(32767u, reader.readUShort())
        assertTrue(reader.exhausted())
    }

    @Test
    fun readUnsignedShortLe() {
        writer.write(byteArrayOf(0x12, 0x34))
        writer.flush()
        assertEquals(0x3412u, reader.readUShortLe())
    }

    @Test
    fun readTooShortUnsignedShortThrows() {
        assertFailsWith<JayoEOFException> { reader.readUShort() }
        writer.writeByte(0)
        writer.flush()
        assertFailsWith<JayoEOFException> { reader.readUShort() }
        assertTrue(reader.request(1))
    }

    @Test
    fun readTooShortUnsignedShortLeThrows() {
        assertFailsWith<JayoEOFException> { reader.readUShortLe() }
        writer.writeByte(0)
        writer.flush()
        assertFailsWith<JayoEOFException> { reader.readUShortLe() }
        assertTrue(reader.request(1))
    }

    @Test
    fun readUnsignedInt() {
        with(writer) {
            writeInt(0)
            writeInt(-1)
            writeInt(Int.MIN_VALUE)
            writeInt(Int.MAX_VALUE)
            flush()
        }

        assertEquals(0u, reader.readUInt())
        assertEquals(UInt.MAX_VALUE, reader.readUInt())
        assertEquals(2147483648u, reader.readUInt())
        assertEquals(Int.MAX_VALUE.toUInt(), reader.readUInt())
        assertTrue(reader.exhausted())
    }

    @Test
    fun readUnsignedIntLe() {
        writer.write(byteArrayOf(0x12, 0x34, 0x56, 0x78))
        writer.flush()
        assertEquals(0x78563412u, reader.readUIntLe())
    }

    @Test
    fun readTooShortUnsignedIntThrows() {
        assertFailsWith<JayoEOFException> { reader.readUInt() }
        writer.writeByte(0)
        writer.flush()
        assertFailsWith<JayoEOFException> { reader.readUInt() }
        writer.writeByte(0)
        writer.flush()
        assertFailsWith<JayoEOFException> { reader.readUInt() }
        writer.writeByte(0)
        writer.flush()
        assertFailsWith<JayoEOFException> { reader.readUInt() }
        assertTrue(reader.request(3))
    }

    @Test
    fun readTooShortUnsignedIntLeThrows() {
        assertFailsWith<JayoEOFException> { reader.readUIntLe() }
        writer.writeByte(0)
        writer.flush()
        assertFailsWith<JayoEOFException> { reader.readUIntLe() }
        writer.writeByte(0)
        writer.flush()
        assertFailsWith<JayoEOFException> { reader.readUIntLe() }
        writer.writeByte(0)
        writer.flush()
        assertFailsWith<JayoEOFException> { reader.readUIntLe() }
        assertTrue(reader.request(3))
    }

    @Test
    fun readUnsignedLong() {
        with(writer) {
            writeLong(0)
            writeLong(-1)
            writeLong(Long.MIN_VALUE)
            writeLong(Long.MAX_VALUE)
            flush()
        }

        assertEquals(0u, reader.readULong())
        assertEquals(ULong.MAX_VALUE, reader.readULong())
        assertEquals(9223372036854775808u, reader.readULong())
        assertEquals(Long.MAX_VALUE.toULong(), reader.readULong())
        assertTrue(reader.exhausted())
    }

    @Test
    fun readUnsignedLongLe() {
        writer.write(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0xff.toByte()))
        writer.flush()
        assertEquals(0xff07060504030201u, reader.readULongLe())
    }

    @Test
    fun readTooShortUnsignedLongThrows() {
        assertFailsWith<JayoEOFException> { reader.readULong() }
        repeat(7) {
            writer.writeByte(0)
            writer.flush()
            assertFailsWith<JayoEOFException> { reader.readULong() }
        }
        assertTrue(reader.request(7))
    }

    @Test
    fun readTooShortUnsignedLongLeThrows() {
        assertFailsWith<JayoEOFException> { reader.readULongLe() }
        repeat(7) {
            writer.writeByte(0)
            writer.flush()
            assertFailsWith<JayoEOFException> { reader.readULongLe() }
        }
        assertTrue(reader.request(7))
    }

    @Test
    fun readByteString() {
        with(writer) {
            write("abcd")
            write("e".repeat(Segment.SIZE))
            emit()
        }
        assertEquals("abcd" + "e".repeat(Segment.SIZE), reader.readByteString().decodeToString())
    }

    @Test
    fun readByteStringPartial() {
        val eeee = "e".repeat(Segment.SIZE)
        with(writer) {
            write("abcd")
            write(eeee)
            write("abcd")
            emit()
        }
        assertEquals("abc", reader.readByteString(3).decodeToString())
        assertEquals("d", reader.readByteString(1).decodeToString())
        assertEquals(eeee, reader.readByteString(Segment.SIZE.toLong()).decodeToString())
    }

    @Test
    fun readByteStringTooShortThrows() {
        writer.write("abc")
        writer.emit()
        assertFailsWith<JayoEOFException> { reader.readByteString(4) }

        assertEquals("abc", reader.readString()) // The read shouldn't consume any data.
    }

    @Test
    fun readUtf8String() {
        with(writer) {
            write("abcd")
            write("e".repeat(Segment.SIZE))
            emit()
        }
        assertThat(reader.readString().codePoints())
            .containsExactly(
                'a'.code,
                'b'.code,
                'c'.code,
                'd'.code,
                *IntArray(Segment.SIZE) { 'e'.code }.toTypedArray()
            )
    }

    @Test
    fun readUtf8Partial() {
        with(writer) {
            write("abcd")
            write("e".repeat(Segment.SIZE))
            emit()
        }
        assertThat(reader.readString(3).codePoints())
            .containsExactly('a'.code, 'b'.code, 'c'.code)
        assertThat(reader.readString(1).codePoints())
            .containsExactly('d'.code)
    }

    @Test
    fun readUtf8TooShortThrows() {
        writer.write("abc")
        writer.emit()
        assertFailsWith<JayoEOFException> { reader.readUtf8(4) }

        assertThat(reader.readUtf8().codePoints())
            .containsExactly('a'.code, 'b'.code, 'c'.code) // The read shouldn't consume any data.
    }

    @Test
    fun readAsciiTooShortThrows() {
        writer.write("abc")
        writer.emit()
        assertFailsWith<JayoEOFException> { reader.readAscii(4) }

        assertThat(reader.readAscii().codePoints())
            .containsExactly('a'.code, 'b'.code, 'c'.code) // The read shouldn't consume any data.
    }

    @Test
    fun indexOfByteString() {
        assertEquals(-1, reader.indexOf("flop".encodeToUtf8()))

        writer.write("flip flop")
        writer.emit()
        assertEquals(5, reader.indexOf("flop".encodeToUtf8()))
        reader.readString() // Clear stream.

        // Make sure we backtrack and resume searching after partial match.
        writer.write("hi hi hi hey")
        writer.emit()
        assertEquals(3, reader.indexOf("hi hi hey".encodeToUtf8()))
    }

    @Test
    fun indexOfByteStringAtSegmentBoundary() {
        writer.write("a".repeat(Segment.SIZE - 1))
        writer.write("bcd")
        writer.emit()
        assertEquals(
            (Segment.SIZE - 3).toLong(),
            reader.indexOf("aabc".encodeToUtf8(), (Segment.SIZE - 4).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 3).toLong(),
            reader.indexOf("aabc".encodeToUtf8(), (Segment.SIZE - 3).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 2).toLong(),
            reader.indexOf("abcd".encodeToUtf8(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 2).toLong(),
            reader.indexOf("abc".encodeToUtf8(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 2).toLong(),
            reader.indexOf("abc".encodeToUtf8(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 2).toLong(),
            reader.indexOf("ab".encodeToUtf8(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 2).toLong(),
            reader.indexOf("a".encodeToUtf8(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 1).toLong(),
            reader.indexOf("bc".encodeToUtf8(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            (Segment.SIZE - 1).toLong(),
            reader.indexOf("b".encodeToUtf8(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            Segment.SIZE.toLong(),
            reader.indexOf("c".encodeToUtf8(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            Segment.SIZE.toLong(),
            reader.indexOf("c".encodeToUtf8(), Segment.SIZE.toLong()),
        )
        assertEquals(
            (Segment.SIZE + 1).toLong(),
            reader.indexOf("d".encodeToUtf8(), (Segment.SIZE - 2).toLong()),
        )
        assertEquals(
            (Segment.SIZE + 1).toLong(),
            reader.indexOf("d".encodeToUtf8(), (Segment.SIZE + 1).toLong()),
        )
    }

    @Test
    fun indexOfDoesNotWrapAround() {
        writer.write("a".repeat(Segment.SIZE - 1))
        writer.write("bcd")
        writer.emit()
        assertEquals(-1, reader.indexOf("abcda".encodeToUtf8(), (Segment.SIZE - 3).toLong()))
    }

    @Test
    fun indexOfByteStringWithOffset() {
        assertEquals(-1, reader.indexOf("flop".encodeToUtf8(), 1))

        writer.write("flop flip flop")
        writer.emit()
        assertEquals(10, reader.indexOf("flop".encodeToUtf8(), 1))
        reader.readString() // Clear stream

        // Make sure we backtrack and resume searching after partial match.
        writer.write("hi hi hi hi hey")
        writer.emit()
        assertEquals(6, reader.indexOf("hi hi hey".encodeToUtf8(), 1))
    }

    @Test
    fun indexOfEmptyByteString() {
        assertEquals(0, reader.indexOf(ByteString.EMPTY))

        writer.write("blablabla")
        writer.emit()
        assertEquals(0, reader.indexOf(ByteString.EMPTY))
    }

    @Test
    fun indexOfByteStringInvalidArgumentsThrows() {
        assertFailsWith<IllegalArgumentException> {
            reader.indexOf("hi".encodeToUtf8(), -1)
        }
    }

    @Test
    fun indexOfElement() {
        writer.write("a").write("b".repeat(Segment.SIZE)).write("c")
        writer.emit()
        assertEquals(0, reader.indexOfElement("DEFGaHIJK".encodeToUtf8()))
        assertEquals(1, reader.indexOfElement("DEFGHIJKb".encodeToUtf8()))
        assertEquals((Segment.SIZE + 1).toLong(), reader.indexOfElement("cDEFGHIJK".encodeToUtf8()))
        assertEquals(1, reader.indexOfElement("DEFbGHIc".encodeToUtf8()))
        assertEquals(-1L, reader.indexOfElement("DEFGHIJK".encodeToUtf8()))
        assertEquals(-1L, reader.indexOfElement("".encodeToUtf8()))
    }

    @Test
    fun indexOfElementWithOffset() {
        writer.write("a").write("b".repeat(Segment.SIZE)).write("c")
        writer.emit()
        assertEquals(-1, reader.indexOfElement("DEFGaHIJK".encodeToUtf8(), 1))
        assertEquals(15, reader.indexOfElement("DEFGHIJKb".encodeToUtf8(), 15))
    }

    @Test
    fun rangeEquals() {
        writer.write("A man, a plan, a canal. Panama.")
        writer.emit()
        assertTrue(reader.rangeEquals(7, "a plan".encodeToUtf8()))
        assertTrue(reader.rangeEquals(0, "A man".encodeToUtf8()))
        assertTrue(reader.rangeEquals(24, "Panama".encodeToUtf8()))
        assertFalse(reader.rangeEquals(24, "Panama. Panama. Panama.".encodeToUtf8()))
    }

    @Test
    fun rangeEqualsWithOffsetAndCount() {
        writer.write("A man, a plan, a canal. Panama.")
        writer.emit()
        assertTrue(reader.rangeEquals(7, "aaa plannn".encodeToUtf8(), 2, 6))
        assertTrue(reader.rangeEquals(0, "AAA mannn".encodeToUtf8(), 2, 5))
        assertTrue(reader.rangeEquals(24, "PPPanamaaa".encodeToUtf8(), 2, 6))
    }

    @Test
    fun rangeEqualsArgumentValidation() {
        // Negative reader offset.
        assertFalse(reader.rangeEquals(-1, "A".encodeToUtf8()))
        // Negative bytes offset.
        assertFalse(reader.rangeEquals(0, "A".encodeToUtf8(), -1, 1))
        // Bytes offset longer than bytes length.
        assertFalse(reader.rangeEquals(0, "A".encodeToUtf8(), 2, 1))
        // Negative byte count.
        assertFalse(reader.rangeEquals(0, "A".encodeToUtf8(), 0, -1))
        // Byte count longer than bytes length.
        assertFalse(reader.rangeEquals(0, "A".encodeToUtf8(), 0, 2))
        // Bytes offset plus byte count longer than bytes length.
        assertFalse(reader.rangeEquals(0, "A".encodeToUtf8(), 1, 1))
    }

    @Test
    fun inputStream() {
        writer.write("abc")
        writer.emit()
        val input: InputStream = reader.asInputStream()
        val bytes = byteArrayOf('z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte())
        val read: Int = input.read(bytes)
        assertEquals(3, read)
        assertByteArrayEquals("abc", bytes)
        assertEquals(-1, input.read())
    }

    @Test
    fun inputStreamOffsetCount() {
        writer.write("abcde")
        writer.emit()
        val input: InputStream = reader.asInputStream()
        val bytes =
            byteArrayOf('z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte())
        val read: Int = input.read(bytes, 1, 3)
        assertEquals(3, read)
        assertByteArrayEquals("zabcz", bytes)
    }

    @Test
    fun inputStreamOffsetCountNBytes() {
        writer.write("abcde")
        writer.emit()
        val input: InputStream = reader.asInputStream()
        val bytes =
            byteArrayOf('z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte(), 'z'.code.toByte())
        val read: Int = input.readNBytes(bytes, 1, 3)
        assertEquals(3, read)
        assertByteArrayEquals("zabcz", bytes)
    }

    @Test
    fun inputStreamReadNbytes() {
        writer.write("abcde")
        writer.emit()
        val input: InputStream = reader.asInputStream()
        val bytes: ByteArray = input.readNBytes(3)
        assertByteArrayEquals("abc", bytes)
    }

    @Test
    fun inputStreamReadAllBytes() {
        writer.write("abcde")
        writer.emit()
        val input: InputStream = reader.asInputStream()
        val bytes: ByteArray = input.readAllBytes()
        assertByteArrayEquals("abcde", bytes)
    }

    @Test
    fun inputStreamSkip() {
        writer.write("abcde")
        writer.emit()
        val input: InputStream = reader.asInputStream()
        assertEquals(4, input.skip(4))
        assertEquals('e'.code, input.read())
        writer.write("abcde")
        writer.emit()
        @Suppress("KotlinConstantConditions")
        assertEquals(0, input.skip(-42L)) // Try to skip negative count.
        assertEquals(5, input.skip(10)) // Try to skip too much.
        assertEquals(0, input.skip(1)) // Try to skip when exhausted.
    }

    @Test
    fun inputStreamSkipNBytes() {
        writer.write("abcde")
        writer.emit()
        val input: InputStream = reader.asInputStream()
        input.skipNBytes(4)
        assertEquals('e'.code, input.read())
        writer.write("abcde")
        writer.emit()
        assertFailsWith<EOFException> { input.skipNBytes(10) } // Try to skip too much.
        assertFailsWith<EOFException> { input.skipNBytes(1) } // Try to skip when exhausted.
    }

    @Test
    fun inputStreamCharByChar() {
        writer.write("abc")
        writer.emit()
        val input: InputStream = reader.asInputStream()
        assertEquals('a'.code, input.read())
        assertEquals('b'.code, input.read())
        assertEquals('c'.code, input.read())
        assertEquals(-1, input.read())
    }

    @Test
    fun writeToStream() {
        writer.write("hello, world!")
        writer.emit()
        val input: InputStream = reader.asInputStream()
        val out = ByteArrayOutputStream()
        input.transferTo(out)
        val outString = String(out.toByteArray(), Charsets.UTF_8)
        assertEquals("hello, world!", outString)
        assertEquals(-1, input.read())
    }

    @Test
    fun inputStreamBounds() {
        writer.write("a".repeat(100))
        writer.emit()
        val input: InputStream = reader.asInputStream()
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
    fun inputStreamForClosedReader() {
        if (reader is Buffer) {
            return
        }

        writer.writeByte(0)
        writer.emit()

        val input = reader.asInputStream()
        reader.close()
        assertFailsWith<IOException> { input.read() }
        assertFailsWith<IOException> { input.readNBytes(1) }
        assertFailsWith<IOException> { input.readAllBytes() }
        assertFailsWith<IOException> { input.read(ByteArray(1)) }
        assertFailsWith<IOException> { input.read(ByteArray(10), 0, 1) }
        assertFailsWith<IOException> { input.readNBytes(ByteArray(10), 0, 1) }
        assertFailsWith<IOException> { input.skip(1L) }
    }

    @Test
    fun inputStreamClosesReader() {
        if (reader is Buffer) {
            return
        }

        writer.writeByte(0)
        writer.emit()

        val input = reader.asInputStream()
        input.close()

        assertFailsWith<JayoClosedResourceException> { reader.readByte() }
    }

    @Test
    fun inputStreamAvailable() {
        val input = reader.asInputStream()
        assertEquals(0, input.available())

        writer.writeInt(42)
        writer.emit()
        assertTrue(reader.request(4)) // fill the buffer

        assertEquals(4, input.available())

        input.read()
        assertEquals(3, input.available())

        reader.readByte()
        assertEquals(2, input.available())

        writer.writeByte(0)
        writer.emit()

        val expectedBytes = if (reader is Buffer) {
            3
        } else {
            2
        }
        assertEquals(expectedBytes, input.available())
    }

    @Test
    fun inputStreamAvailableForClosedReader() {
        if (reader is Buffer) {
            return
        }

        val input = reader.asInputStream()
        reader.close()

        assertFailsWith<IOException> { input.available() }
    }

    @Test
    fun readNioBuffer() {
        val expected = "abcdefg"
        writer.write("abcdefg")
        writer.emit()
        val nioByteBuffer: ByteBuffer = ByteBuffer.allocate(1024)
        val byteCount: Int = reader.readAtMostTo(nioByteBuffer)
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
        val expected: String = "a".repeat(Segment.SIZE)
        writer.write("a".repeat(Segment.SIZE * 4))
        writer.emit()
        val nioByteBuffer: ByteBuffer = ByteBuffer.allocate(Segment.SIZE * 3)
        val byteCount: Int = reader.readAtMostTo(nioByteBuffer)
        assertEquals(expected.length, byteCount)
        assertEquals(expected.length, nioByteBuffer.position())
        assertEquals(nioByteBuffer.capacity(), nioByteBuffer.limit())
        nioByteBuffer.flip()
        val data = ByteArray(expected.length)
        nioByteBuffer.get(data)
        assertEquals(expected, String(data))
    }

    @Test
    fun readNioBufferFromEmptyReader() {
        assertEquals(-1, reader.readAtMostTo(ByteBuffer.allocate(10)))
    }

    @Test
    fun readSpecificCharsetPartial() {
        writer.write(
            ("0000007600000259000002c80000006c000000e40000007300000259000002" +
                    "cc000000720000006100000070000000740000025900000072").decodeHex()
        )
        writer.emit()
        assertEquals("vəˈläsə", reader.readString(7 * 4, Charset.forName("utf-32")))
    }

    @Test
    fun readSpecificCharset() {
        writer.write(
            ("0000007600000259000002c80000006c000000e40000007300000259000002" +
                    "cc000000720000006100000070000000740000025900000072").decodeHex()
        )

        writer.emit()
        assertEquals("vəˈläsəˌraptər", reader.readString(Charset.forName("utf-32")))
    }

    @Test
    fun readStringTooShortThrows() {
        writer.write("abc", Charsets.US_ASCII)
        writer.emit()
        assertFailsWith<JayoEOFException> {
            reader.readString(4, Charsets.US_ASCII)
        }
        assertEquals("abc", reader.readString()) // The read shouldn't consume any data.
    }

    @Test
    fun readIntoNonemptyWriter() {
        for (i in 0 until Segment.SIZE) {
            before()
            val toWrite = "God help us, we're in the hands of engineers ($i)."
            writer.write(toWrite)
            val buffer = Buffer().write("a".repeat(i)).emit()
            reader.readAtMostTo(buffer, Int.MAX_VALUE.toLong())
            buffer.skip(i.toLong())
            assertEquals(toWrite, buffer.readString())
            after()
        }
    }
}
