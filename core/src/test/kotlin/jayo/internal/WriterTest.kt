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
import jayo.bytestring.ByteString
import jayo.bytestring.Utf8
import jayo.bytestring.encodeToUtf8
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BufferWriterTest : AbstractWriterTest(WriterFactory.BUFFER)

class RealWriterTest : AbstractWriterTest(WriterFactory.REAL_BUFFERED_SINK)

abstract class AbstractWriterTest internal constructor(private val factory: WriterFactory) {
    private val data: Buffer = RealBuffer()
    private lateinit var writer: Writer

    @BeforeEach
    fun before() {
        writer = factory.create(data)
    }

    @AfterEach
    fun after() {
        writer.close()
    }

    @Test
    fun closeIsOpen() {
        assertTrue(writer.isOpen)

        if (writer is RealWriter) {
            writer.close()
            assertFalse(writer.isOpen)
        }
    }

    @Test
    fun writeByteArray() {
        val reader = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        writer.write(reader)
        writer.flush()
        assertEquals("Buffer(size=10 hex=00010203040506070809)", data.toString())
        data.clear()

        writer.write(reader, 3, 7)
        writer.flush()
        assertEquals("Buffer(size=7 hex=03040506070809)", data.toString())
        data.clear()

        writer.write(reader, 0, 3)
        writer.flush()
        assertEquals("Buffer(size=3 hex=000102)", data.toString())
        data.clear()

        assertFailsWith<IndexOutOfBoundsException> {
            writer.write(reader, -1, 1)
        }
        assertEquals(0, data.bytesAvailable())

        assertFailsWith<IndexOutOfBoundsException> {
            writer.write(reader, 1, reader.size + 1)
        }
        assertEquals(0, data.bytesAvailable())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.write(reader, 1, 1)
            }
        }
    }

    @Test
    fun writeNothing() {
        writer.write("")
        writer.flush()
        assertEquals(0, data.bytesAvailable())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.write("")
            }
        }
    }

    @Test
    fun writeByte() {
        writer.writeByte(0xba.toByte())
        writer.flush()
        assertEquals("Buffer(size=1 hex=ba)", data.toString())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.writeByte(0xba.toByte())
            }
        }
    }

    @Test
    fun writeBytes() {
        writer.writeByte(0xab.toByte())
        writer.writeByte(0xcd.toByte())
        writer.flush()
        assertEquals("Buffer(size=2 hex=abcd)", data.toString())
    }

    @Test
    fun writeLastByteInSegment() {
        writer.write("a".repeat(Segment.SIZE - 1))
        writer.writeByte(0x20)
        writer.writeByte(0x21)
        writer.flush()
        assertEquals(listOf(Segment.SIZE, 1), segmentSizes(data))
        assertEquals("a".repeat(Segment.SIZE - 1), data.readString(Segment.SIZE - 1L))
        assertEquals("Buffer(size=2 hex=2021)", data.toString())
    }

    @Test
    fun writeShort() {
        writer.writeShort(0xab01.toShort())
        writer.flush()
        assertEquals("Buffer(size=2 hex=ab01)", data.toString())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.writeShort(0xab01.toShort())
            }
        }
    }

    @Test
    fun writeShorts() {
        writer.writeShort(0xabcd.toShort())
        writer.writeShort(0x4321)
        writer.flush()
        assertEquals("Buffer(size=4 hex=abcd4321)", data.toString())
    }

    @Test
    fun writeShortLe() {
        writer.writeShortLe(0xcdab.toShort())
        writer.writeShortLe(0x2143)
        writer.flush()
        assertEquals("Buffer(size=4 hex=abcd4321)", data.toString())
    }

    @Test
    fun writeInt() {
        writer.writeInt(0x197760)
        writer.flush()
        assertEquals("Buffer(size=4 hex=00197760)", data.toString())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.writeInt(0x197760)
            }
        }
    }

    @Test
    fun writeInts() {
        writer.writeInt(-0x543210ff)
        writer.writeInt(-0x789abcdf)
        writer.flush()
        assertEquals("Buffer(size=8 hex=abcdef0187654321)", data.toString())
    }

    @Test
    fun writeLastIntegerInSegment() {
        writer.write("a".repeat(Segment.SIZE - 4))
        writer.writeInt(-0x543210ff)
        writer.writeInt(-0x789abcdf)
        writer.flush()
        assertEquals("a".repeat(Segment.SIZE - 4), data.readString(Segment.SIZE - 4L))
        assertEquals("Buffer(size=8 hex=abcdef0187654321)", data.toString())
    }

    @Test
    fun writeIntegerDoesNotQuiteFitInSegment() {
        writer.write("a".repeat(Segment.SIZE - 3))
        writer.writeInt(-0x543210ff)
        writer.writeInt(-0x789abcdf)
        writer.flush()
        assertEquals(listOf(Segment.SIZE - 3, 8), segmentSizes(data))
        assertEquals("a".repeat(Segment.SIZE - 3), data.readString(Segment.SIZE - 3L))
        assertEquals("Buffer(size=8 hex=abcdef0187654321)", data.toString())
    }

    @Test
    fun writeIntLe() {
        writer.writeIntLe(-0x543210ff)
        writer.writeIntLe(-0x789abcdf)
        writer.flush()
        assertEquals("Buffer(size=8 hex=01efcdab21436587)", data.toString())
    }

    @Test
    fun writeLong() {
        writer.writeLong(0x123456789abcdef0L)
        writer.flush()
        assertEquals("Buffer(size=8 hex=123456789abcdef0)", data.toString())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.writeLong(0x123456789abcdef0L)
            }
        }
    }

    @Test
    fun writeLongs() {
        writer.writeLong(-0x543210fe789abcdfL)
        writer.writeLong(-0x350145414f4ea400L)
        writer.flush()
        assertEquals("Buffer(size=16 hex=abcdef0187654321cafebabeb0b15c00)", data.toString())
    }

    @Test
    fun writeLongLe() {
        writer.writeLongLe(-0x543210fe789abcdfL)
        writer.writeLongLe(-0x350145414f4ea400L)
        writer.flush()
        assertEquals("Buffer(size=16 hex=2143658701efcdab005cb1b0bebafeca)", data.toString())
    }

    @Test
    fun writeAll() {
        val reader = RealBuffer()
        reader.write("abcdef")

        assertEquals(6, writer.transferFrom(reader))
        assertEquals(0, reader.bytesAvailable())
        writer.flush()
        assertEquals("abcdef", data.readString())
    }

    @Test
    fun writeAllExhausted() {
        val reader = RealBuffer()
        assertEquals(0, writer.transferFrom(reader))
        assertEquals(0, reader.bytesAvailable())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.transferFrom(reader)
            }
        }
    }

    @Test
    fun writeReader() {
        val reader = RealBuffer()
        reader.write("abcdef")

        // Force resolution of the reader method overload.
        writer.write(reader as RawReader, 4)
        writer.flush()
        assertEquals("abcd", data.readString())
        assertEquals("ef", reader.readString())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.write(reader as RawReader, 4)
            }
        }
    }

    @Test
    fun writeReaderReadsFully() {
        val reader = object : RawReader by RealBuffer() {
            override fun readAtMostTo(writer: Buffer, byteCount: Long): Long {
                writer.write("abcd")
                return 4
            }
        }

        writer.write(reader, 8)
        writer.flush()
        assertEquals("abcdabcd", data.readString())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.write(reader, 8)
            }
        }
    }

    @Test
    fun writeReaderPropagatesEof() {
        val reader: RawReader = RealBuffer().also { it.write("abcd") }

        assertFailsWith<JayoEOFException> {
            writer.write(reader, 8)
        }

        // Ensure that whatever was available was correctly written.
        writer.flush()
        assertEquals("abcd", data.readString())
    }

    @Test
    fun writeBufferThrowsIAE() {
        val reader = RealBuffer()
        reader.write("abcd")

        assertFailsWith<IndexOutOfBoundsException> {
            writer.write(reader, 8)
        }

        writer.flush()
        assertEquals("", data.readString())

        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.write(reader, 8)
            }
        }
    }

    @Test
    fun writeReaderWithNegativeBytesCount() {
        val reader: RawReader = RealBuffer().also { it.writeByte(0) }

        assertFailsWith<IllegalArgumentException> {
            writer.write(reader, -1L)
        }
    }

    @Test
    fun writeBufferWithNegativeBytesCount() {
        val reader = RealBuffer().also { it.writeByte(0) }

        assertFailsWith<IndexOutOfBoundsException> {
            writer.write(reader, -1L)
        }
    }

    @Test
    fun writeReaderWithZeroIsNoOp() {
        // This test ensures that a zero byte count never calls through to read the reader. It may be
        // tied to something like a socket which will potentially block trying to read a segment when
        // ultimately we don't want any data.
        val reader = object : RawReader by RealBuffer() {
            override fun readAtMostTo(writer: Buffer, byteCount: Long): Long {
                throw AssertionError()
            }
        }
        writer.write(reader, 0)
        assertEquals(0, data.bytesAvailable())
    }

    @Test
    fun closeEmitsBufferedBytes() {
        writer.writeByte('a'.code.toByte())
        writer.close()
        assertEquals('a', data.readByte().toInt().toChar())
    }

    @Test
    fun longDecimalString() {
        assertLongDecimalString("0", 0)
        assertLongDecimalString("-9223372036854775808", Long.MIN_VALUE)
        assertLongDecimalString("9223372036854775807", Long.MAX_VALUE)
        assertLongDecimalString("9", 9L)
        assertLongDecimalString("99", 99L)
        assertLongDecimalString("999", 999L)
        assertLongDecimalString("9999", 9999L)
        assertLongDecimalString("99999", 99999L)
        assertLongDecimalString("999999", 999999L)
        assertLongDecimalString("9999999", 9999999L)
        assertLongDecimalString("99999999", 99999999L)
        assertLongDecimalString("999999999", 999999999L)
        assertLongDecimalString("9999999999", 9999999999L)
        assertLongDecimalString("99999999999", 99999999999L)
        assertLongDecimalString("999999999999", 999999999999L)
        assertLongDecimalString("9999999999999", 9999999999999L)
        assertLongDecimalString("99999999999999", 99999999999999L)
        assertLongDecimalString("999999999999999", 999999999999999L)
        assertLongDecimalString("9999999999999999", 9999999999999999L)
        assertLongDecimalString("99999999999999999", 99999999999999999L)
        assertLongDecimalString("999999999999999999", 999999999999999999L)
        assertLongDecimalString("10", 10L)
        assertLongDecimalString("100", 100L)
        assertLongDecimalString("1000", 1000L)
        assertLongDecimalString("10000", 10000L)
        assertLongDecimalString("100000", 100000L)
        assertLongDecimalString("1000000", 1000000L)
        assertLongDecimalString("10000000", 10000000L)
        assertLongDecimalString("100000000", 100000000L)
        assertLongDecimalString("1000000000", 1000000000L)
        assertLongDecimalString("10000000000", 10000000000L)
        assertLongDecimalString("100000000000", 100000000000L)
        assertLongDecimalString("1000000000000", 1000000000000L)
        assertLongDecimalString("10000000000000", 10000000000000L)
        assertLongDecimalString("100000000000000", 100000000000000L)
        assertLongDecimalString("1000000000000000", 1000000000000000L)
        assertLongDecimalString("10000000000000000", 10000000000000000L)
        assertLongDecimalString("100000000000000000", 100000000000000000L)
        assertLongDecimalString("1000000000000000000", 1000000000000000000L)
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.writeDecimalLong(0L)
            }
        }
    }

    private fun assertLongDecimalString(string: String, value: Long) {
        with(writer) {
            writeDecimalLong(value)
            write("zzz")
            flush()
        }
        val expected = "${string}zzz"
        val actual = data.readString()
        assertEquals(expected, actual, "$value expected $expected but was $actual")
    }

    @Test
    fun writeUtf8String() {
        writer.write("0123456789")
        writer.flush()
        assertEquals("0123456789", data.readString())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.write("0123456789")
            }
        }
    }

    @Test
    fun writeUtf8CodePoint() {
        writer.writeUtf8CodePoint(0x10ffff)
        writer.flush()
        assertEquals(0x10ffff, data.readUtf8CodePoint())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.writeUtf8CodePoint(1)
            }
        }
    }

    @Test
    fun writeUByte() {
        writer.writeUByte(0xffu)
        writer.flush()
        assertEquals(-1, data.readByte())
    }

    @Test
    fun writeUShort() {
        writer.writeUShort(0xffffu)
        writer.flush()
        assertEquals(-1, data.readShort())
    }

    @Test
    fun writeUShortLe() {
        writer.writeUShortLe(0x1234u)
        writer.flush()
        assertEquals("Buffer(size=2 hex=3412)", data.toString())
    }

    @Test
    fun writeUInt() {
        writer.writeUInt(0xffffffffu)
        writer.flush()
        assertEquals(-1, data.readInt())
    }

    @Test
    fun writeUIntLe() {
        writer.writeUIntLe(0x12345678u)
        writer.flush()
        assertEquals("Buffer(size=4 hex=78563412)", data.toString())
    }

    @Test
    fun writeULong() {
        writer.writeULong(0xffffffffffffffffu)
        writer.flush()
        assertEquals(-1, data.readLong())
    }

    @Test
    fun writeULongLe() {
        writer.writeULongLe(0x1234567890abcdefu)
        writer.flush()
        assertEquals("Buffer(size=8 hex=efcdab9078563412)", data.toString())
    }

    @Test
    fun writeByteString() {
        writer.write("təˈranəˌsôr".encodeToUtf8())
        writer.flush()
        assertEquals(ByteString.of(*"74c999cb8872616ec999cb8c73c3b472".decodeHex()), data.readByteString())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.write("təˈranəˌsôr".encodeToUtf8())
            }
        }
    }

    @Test
    fun writeByteStringOffset() {
        writer.write("təˈranəˌsôr".encodeToUtf8(), 5, 5)
        writer.flush()
        assertEquals(ByteString.of(*"72616ec999".decodeHex()), data.readByteString())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.write("təˈranəˌsôr".encodeToUtf8(), 5, 5)
            }
        }
    }

    @Test
    fun writeUtf8() {
        writer.write("təˈranəˌsôr".encodeToUtf8())
        writer.flush()
        assertEquals(Utf8.of(*"74c999cb8872616ec999cb8c73c3b472".decodeHex()), data.readUtf8())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.write("təˈranəˌsôr".encodeToUtf8())
            }
        }
    }

    @Test
    fun writeUtf8Offset() {
        writer.write("təˈranəˌsôr".encodeToUtf8(), 5, 5)
        writer.flush()
        assertEquals(Utf8.of(*"72616ec999".decodeHex()), data.readUtf8())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.write("təˈranəˌsôr".encodeToUtf8(), 5, 5)
            }
        }
    }

    @Test
    fun outputStream() {
        val out: OutputStream = writer.asOutputStream()
        out.write('a'.code)
        out.write("b".repeat(9998).toByteArray(Charsets.UTF_8))
        out.write('c'.code)
        out.flush()
        assertEquals(("a" + "b".repeat(9998)) + "c", data.readString())
    }

    @Test
    fun outputStreamBounds() {
        val out: OutputStream = writer.asOutputStream()
        assertFailsWith<IndexOutOfBoundsException> {
            out.write(ByteArray(100), 50, 51)
        }
    }

    @Test
    fun writeToClosedOutputStream() {
        if (writer is Buffer) {
            return
        }
        val out = writer.asOutputStream()
        writer.close()
        assertFailsWith<IOException> { out.write(0) }
        assertFailsWith<IOException> { out.write(ByteArray(1)) }
        assertFailsWith<IOException> { out.write(ByteArray(42), 0, 1) }
        assertFailsWith<IOException> { out.flush() }
    }

    @Test
    fun outputStreamClosesWriter() {
        if (writer is Buffer) {
            return
        }

        val out = writer.asOutputStream()
        out.close()
        assertFailsWith<JayoClosedResourceException> { writer.writeByte(0) }
    }

    @Test
    fun writeNioBuffer() {
        val expected = "abcdefg"
        val nioByteBuffer: ByteBuffer = ByteBuffer.allocate(1024)
        nioByteBuffer.put("abcdefg".toByteArray(Charsets.UTF_8))
        nioByteBuffer.flip()
        val byteCount: Int = writer.transferFrom(nioByteBuffer)
        assertEquals(expected.length, byteCount)
        assertEquals(expected.length, nioByteBuffer.position())
        assertEquals(expected.length, nioByteBuffer.limit())
        writer.flush()
        assertEquals(expected, data.readString())
    }

    @Test
    fun writeLargeNioBufferWritesAllData() {
        val expected: String = "a".repeat(Segment.SIZE * 3)
        val nioByteBuffer: ByteBuffer = ByteBuffer.allocate(Segment.SIZE * 4)
        nioByteBuffer.put("a".repeat(Segment.SIZE * 3).toByteArray(Charsets.UTF_8))
        nioByteBuffer.flip()
        val byteCount: Int = writer.transferFrom(nioByteBuffer)
        assertEquals(expected.length, byteCount)
        assertEquals(expected.length, nioByteBuffer.position())
        assertEquals(expected.length, nioByteBuffer.limit())
        writer.flush()
        assertEquals(expected, data.readString())
    }

    @Test
    fun writeNioBufferToClosedWriter() {
        if (writer is Buffer) {
            return
        }
        writer.close()
        assertFailsWith<JayoClosedResourceException> {
            writer.transferFrom(ByteBuffer.allocate(10))
        }
    }

    @Test
    fun writeStringWithCharset() {
        writer.write("təˈranəˌsôr", Charset.forName("utf-32be"))
        writer.flush()
        val expected = "0000007400000259000002c800000072000000610000006e00000259000002cc00000073000000f400000072"
        assertArrayEquals(expected.decodeHex(), data.readByteArray())
        if (writer is RealWriter) {
            writer.close()
            assertFailsWith<JayoClosedResourceException> {
                writer.write("təˈranəˌsôr", Charset.forName("utf-32be"))
            }
        }
    }

    @Test
    fun writeLatin1StringWithCharset() {
        writer.write(LATIN1, Charsets.ISO_8859_1)
        writer.flush()
        assertArrayEquals(LATIN1.toByteArray(Charsets.ISO_8859_1), data.readByteArray())
    }
}
