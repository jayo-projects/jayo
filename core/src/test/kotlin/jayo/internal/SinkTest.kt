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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class BufferSinkTest : AbstractSinkTest(SinkFactory.BUFFER)

class RealSinkTest : AbstractSinkTest(SinkFactory.REAL_BUFFERED_SINK)

class RealAsyncSinkTest : AbstractSinkTest(SinkFactory.REAL_ASYNC_BUFFERED_SINK)

abstract class AbstractSinkTest internal constructor(private val factory: SinkFactory) {
    private val data: Buffer = RealBuffer()
    private lateinit var sink: Sink

    @BeforeEach
    fun before() {
        sink = factory.create(data)
    }

    @AfterEach
    fun after() {
        sink.close()
    }

    @Test
    fun writeByteArray() {
        val source = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        sink.write(source)
        sink.flush()
        assertEquals("Buffer(size=10 hex=00010203040506070809)", data.toString())
        data.clear()

        sink.write(source, 3, 7)
        sink.flush()
        assertEquals("Buffer(size=7 hex=03040506070809)", data.toString())
        data.clear()

        sink.write(source, 0, 3)
        sink.flush()
        assertEquals("Buffer(size=3 hex=000102)", data.toString())
        data.clear()

        assertFailsWith<IndexOutOfBoundsException> {
            sink.write(source, -1, 1)
        }
        assertEquals(0, data.byteSize())

        assertFailsWith<IndexOutOfBoundsException> {
            sink.write(source, 1, source.size + 1)
        }
        assertEquals(0, data.byteSize())
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.write(source, 1, 1)
            }
        }
    }

    @Test
    fun writeNothing() {
        sink.writeUtf8("")
        sink.flush()
        assertEquals(0, data.byteSize())
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.writeUtf8("")
            }
        }
    }

    @Test
    fun writeByte() {
        sink.writeByte(0xba.toByte())
        sink.flush()
        assertEquals("Buffer(size=1 hex=ba)", data.toString())
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.writeByte(0xba.toByte())
            }
        }
    }

    @Test
    fun writeBytes() {
        sink.writeByte(0xab.toByte())
        sink.writeByte(0xcd.toByte())
        sink.flush()
        assertEquals("Buffer(size=2 hex=abcd)", data.toString())
    }

    @Test
    fun writeLastByteInSegment() {
        sink.writeUtf8("a".repeat(Segment.SIZE - 1))
        sink.writeByte(0x20)
        sink.writeByte(0x21)
        sink.flush()
        assertEquals(listOf(Segment.SIZE, 1), segmentSizes(data))
        assertEquals("a".repeat(Segment.SIZE - 1), data.readUtf8(Segment.SIZE - 1L))
        assertEquals("Buffer(size=2 hex=2021)", data.toString())
    }

    @Test
    fun writeShort() {
        sink.writeShort(0xab01.toShort())
        sink.flush()
        assertEquals("Buffer(size=2 hex=ab01)", data.toString())
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.writeShort(0xab01.toShort())
            }
        }
    }

    @Test
    fun writeShorts() {
        sink.writeShort(0xabcd.toShort())
        sink.writeShort(0x4321)
        sink.flush()
        assertEquals("Buffer(size=4 hex=abcd4321)", data.toString())
    }

    @Test
    fun writeShortLe() {
        sink.writeShortLe(0xcdab.toShort())
        sink.writeShortLe(0x2143)
        sink.flush()
        assertEquals("Buffer(size=4 hex=abcd4321)", data.toString())
    }

    @Test
    fun writeInt() {
        sink.writeInt(0x197760)
        sink.flush()
        assertEquals("Buffer(size=4 hex=00197760)", data.toString())
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.writeInt(0x197760)
            }
        }
    }

    @Test
    fun writeInts() {
        sink.writeInt(-0x543210ff)
        sink.writeInt(-0x789abcdf)
        sink.flush()
        assertEquals("Buffer(size=8 hex=abcdef0187654321)", data.toString())
    }

    @Test
    fun writeLastIntegerInSegment() {
        sink.writeUtf8("a".repeat(Segment.SIZE - 4))
        sink.writeInt(-0x543210ff)
        sink.writeInt(-0x789abcdf)
        sink.flush()
        assertEquals(listOf(Segment.SIZE, 4), segmentSizes(data))
        assertEquals("a".repeat(Segment.SIZE - 4), data.readUtf8(Segment.SIZE - 4L))
        assertEquals("Buffer(size=8 hex=abcdef0187654321)", data.toString())
    }

    @Test
    fun writeIntegerDoesNotQuiteFitInSegment() {
        sink.writeUtf8("a".repeat(Segment.SIZE - 3))
        sink.writeInt(-0x543210ff)
        sink.writeInt(-0x789abcdf)
        sink.flush()
        assertEquals(listOf(Segment.SIZE - 3, 8), segmentSizes(data))
        assertEquals("a".repeat(Segment.SIZE - 3), data.readUtf8(Segment.SIZE - 3L))
        assertEquals("Buffer(size=8 hex=abcdef0187654321)", data.toString())
    }

    @Test
    fun writeIntLe() {
        sink.writeIntLe(-0x543210ff)
        sink.writeIntLe(-0x789abcdf)
        sink.flush()
        assertEquals("Buffer(size=8 hex=01efcdab21436587)", data.toString())
    }

    @Test
    fun writeLong() {
        sink.writeLong(0x123456789abcdef0L)
        sink.flush()
        assertEquals("Buffer(size=8 hex=123456789abcdef0)", data.toString())
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.writeLong(0x123456789abcdef0L)
            }
        }
    }

    @Test
    fun writeLongs() {
        sink.writeLong(-0x543210fe789abcdfL)
        sink.writeLong(-0x350145414f4ea400L)
        sink.flush()
        assertEquals("Buffer(size=16 hex=abcdef0187654321cafebabeb0b15c00)", data.toString())
    }

    @Test
    fun writeLongLe() {
        sink.writeLongLe(-0x543210fe789abcdfL)
        sink.writeLongLe(-0x350145414f4ea400L)
        sink.flush()
        assertEquals("Buffer(size=16 hex=2143658701efcdab005cb1b0bebafeca)", data.toString())
    }

    @Test
    fun writeAll() {
        val source = RealBuffer()
        source.writeUtf8("abcdef")

        assertEquals(6, sink.transferFrom(source))
        assertEquals(0, source.byteSize())
        sink.flush()
        assertEquals("abcdef", data.readUtf8())
    }

    @Test
    fun writeAllExhausted() {
        val source = RealBuffer()
        assertEquals(0, sink.transferFrom(source))
        assertEquals(0, source.byteSize())
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.transferFrom(source)
            }
        }
    }

    @Test
    fun writeSource() {
        val source = RealBuffer()
        source.writeUtf8("abcdef")

        // Force resolution of the source method overload.
        sink.write(source as RawSource, 4)
        sink.flush()
        assertEquals("abcd", data.readUtf8())
        assertEquals("ef", source.readUtf8())
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.write(source as RawSource, 4)
            }
        }
    }

    @Test
    fun writeSourceReadsFully() {
        val source = object : RawSource by RealBuffer() {
            override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
                sink.writeUtf8("abcd")
                return 4
            }
        }

        sink.write(source, 8)
        sink.flush()
        assertEquals("abcdabcd", data.readUtf8())
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.write(source, 8)
            }
        }
    }

    @Test
    fun writeSourcePropagatesEof() {
        val source: RawSource = RealBuffer().also { it.writeUtf8("abcd") }

        assertFailsWith<JayoEOFException> {
            sink.write(source, 8)
        }

        // Ensure that whatever was available was correctly written.
        sink.flush()
        assertEquals("abcd", data.readUtf8())
    }

    @Test
    fun writeBufferThrowsIAE() {
        val source = RealBuffer()
        source.writeUtf8("abcd")

        assertFailsWith<IndexOutOfBoundsException> {
            sink.write(source, 8)
        }

        sink.flush()
        assertEquals("", data.readUtf8())

        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.write(source, 8)
            }
        }
    }

    @Test
    fun writeSourceWithNegativeBytesCount() {
        val source: RawSource = RealBuffer().also { it.writeByte(0) }

        assertFailsWith<IllegalArgumentException> {
            sink.write(source, -1L)
        }
    }

    @Test
    fun writeBufferWithNegativeBytesCount() {
        val source = RealBuffer().also { it.writeByte(0) }

        assertFailsWith<IndexOutOfBoundsException> {
            sink.write(source, -1L)
        }
    }

    @Test
    fun writeSourceWithZeroIsNoOp() {
        // This test ensures that a zero byte count never calls through to read the source. It may be
        // tied to something like a socket which will potentially block trying to read a segment when
        // ultimately we don't want any data.
        val source = object : RawSource by RealBuffer() {
            override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
                throw AssertionError()
            }
        }
        sink.write(source, 0)
        assertEquals(0, data.byteSize())
    }

    @Test
    fun closeEmitsBufferedBytes() {
        sink.writeByte('a'.code.toByte())
        sink.close()
        assertEquals('a', data.readByte().toInt().toChar())
    }

    /**
     * This test hard codes the results of Long.toString() because that function rounds large values
     * when using Kotlin/JS IR. https://youtrack.jetbrains.com/issue/KT-39891
     */
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
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.writeDecimalLong(0L)
            }
        }
    }

    private fun assertLongDecimalString(string: String, value: Long) {
        with(sink) {
            writeDecimalLong(value)
            writeUtf8("zzz")
            flush()
        }
        val expected = "${string}zzz"
        val actual = data.readUtf8()
        assertEquals(expected, actual, "$value expected $expected but was $actual")
    }

    @Test
    fun writeUtf8FromRange() {
        sink.writeUtf8("0123456789", 4, 7)
        sink.flush()
        assertEquals("456", data.readUtf8())
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.writeUtf8("0123456789", 4, 7)
            }
        }
    }

    @Test
    fun writeUtf8WithInvalidIndexes() {
        assertFailsWith<IndexOutOfBoundsException> { sink.writeUtf8("hello", -1, 2) }
        assertFailsWith<IndexOutOfBoundsException> { sink.writeUtf8("hello", 0, 6) }
        assertFailsWith<IllegalArgumentException> { sink.writeUtf8("hello", 6, 5) }
    }

    @Test
    fun writeUtf8CodePoint() {
        sink.writeUtf8CodePoint(0x10ffff)
        sink.flush()
        assertEquals(0x10ffff, data.readUtf8CodePoint())
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.writeUtf8CodePoint(1)
            }
        }
    }

    @Test
    fun writeUByte() {
        sink.writeUByte(0xffu)
        sink.flush()
        assertEquals(-1, data.readByte())
    }

    @Test
    fun writeUShort() {
        sink.writeUShort(0xffffu)
        sink.flush()
        assertEquals(-1, data.readShort())
    }

    @Test
    fun writeUShortLe() {
        sink.writeUShortLe(0x1234u)
        sink.flush()
        assertEquals("Buffer(size=2 hex=3412)", data.toString())
    }

    @Test
    fun writeUInt() {
        sink.writeUInt(0xffffffffu)
        sink.flush()
        assertEquals(-1, data.readInt())
    }

    @Test
    fun writeUIntLe() {
        sink.writeUIntLe(0x12345678u)
        sink.flush()
        assertEquals("Buffer(size=4 hex=78563412)", data.toString())
    }

    @Test
    fun writeULong() {
        sink.writeULong(0xffffffffffffffffu)
        sink.flush()
        assertEquals(-1, data.readLong())
    }

    @Test
    fun writeULongLe() {
        sink.writeULongLe(0x1234567890abcdefu)
        sink.flush()
        assertEquals("Buffer(size=8 hex=efcdab9078563412)", data.toString())
    }

    @Test
    fun writeByteString() {
        sink.write("təˈranəˌsôr".encodeToByteString())
        sink.flush()
        assertEquals(ByteString.of(*"74c999cb8872616ec999cb8c73c3b472".decodeHex()), data.readByteString())
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.write("təˈranəˌsôr".encodeToByteString())
            }
        }
    }

    @Test
    fun writeByteStringOffset() {
        sink.write("təˈranəˌsôr".encodeToByteString(), 5, 5)
        sink.flush()
        assertEquals(ByteString.of(*"72616ec999".decodeHex()), data.readByteString())
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.write("təˈranəˌsôr".encodeToByteString(), 5, 5)
            }
        }
    }

    @Test
    fun outputStream() {
        val out: OutputStream = sink.asOutputStream()
        out.write('a'.code)
        out.write("b".repeat(9998).toByteArray(Charsets.UTF_8))
        out.write('c'.code)
        out.flush()
        assertEquals(("a" + "b".repeat(9998)) + "c", data.readUtf8())
    }

    @Test
    fun outputStreamBounds() {
        val out: OutputStream = sink.asOutputStream()
        assertFailsWith<IndexOutOfBoundsException> {
            out.write(ByteArray(100), 50, 51)
        }
    }

    @Test
    fun writeToClosedOutputStream() {
        if (sink is Buffer) {
            return
        }
        val out = sink.asOutputStream()
        sink.close()
        assertFailsWith<IOException> { out.write(0) }
        assertFailsWith<IOException> { out.write(ByteArray(1)) }
        assertFailsWith<IOException> { out.write(ByteArray(42), 0, 1) }
        assertFailsWith<IOException> { out.flush() }
    }

    @Test
    fun outputStreamClosesSink() {
        if (sink is Buffer) {
            return
        }

        val out = sink.asOutputStream()
        out.close()
        assertFailsWith<IllegalStateException> { sink.writeByte(0) }
    }

    @Test
    fun writeNioBuffer() {
        val expected = "abcdefg"
        val nioByteBuffer: ByteBuffer = ByteBuffer.allocate(1024)
        nioByteBuffer.put("abcdefg".toByteArray(Charsets.UTF_8))
        nioByteBuffer.flip()
        val byteCount: Int = sink.transferFrom(nioByteBuffer)
        assertEquals(expected.length, byteCount)
        assertEquals(expected.length, nioByteBuffer.position())
        assertEquals(expected.length, nioByteBuffer.limit())
        sink.flush()
        assertEquals(expected, data.readUtf8())
    }

    @Test
    fun writeLargeNioBufferWritesAllData() {
        val expected: String = "a".repeat(SEGMENT_SIZE * 3)
        val nioByteBuffer: ByteBuffer = ByteBuffer.allocate(SEGMENT_SIZE * 4)
        nioByteBuffer.put("a".repeat(SEGMENT_SIZE * 3).toByteArray(Charsets.UTF_8))
        nioByteBuffer.flip()
        val byteCount: Int = sink.transferFrom(nioByteBuffer)
        assertEquals(expected.length, byteCount)
        assertEquals(expected.length, nioByteBuffer.position())
        assertEquals(expected.length, nioByteBuffer.limit())
        sink.flush()
        assertEquals(expected, data.readUtf8())
    }

    @Test
    fun writeNioBufferToClosedSink() {
        if (sink is Buffer) {
            return
        }
        sink.close()
        assertFailsWith<IllegalStateException> {
            sink.transferFrom(ByteBuffer.allocate(10))
        }
    }

    @Test
    fun writeStringWithCharset() {
        sink.writeString("təˈranəˌsôr", Charset.forName("utf-32be"))
        sink.flush()
        val expected = "0000007400000259000002c800000072000000610000006e00000259000002cc00000073000000f400000072"
        assertArrayEquals(expected.decodeHex(), data.readByteArray())
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.writeString("təˈranəˌsôr", Charset.forName("utf-32be"))
            }
        }
    }

    @Test
    fun writeSubstringWithCharset() {
        sink.writeString("təˈranəˌsôr", 3, 7, Charset.forName("utf-32be"))
        sink.flush()
        assertArrayEquals("00000072000000610000006e00000259".decodeHex(), data.readByteArray())
        if (sink is RealSink) {
            sink.close()
            assertFailsWith<IllegalStateException> {
                sink.writeString("təˈranəˌsôr", 3, 7, Charset.forName("utf-32be"))
            }
        }
    }

    @Test
    fun writeUtf8SubstringWithCharset() {
        sink.writeString("təˈranəˌsôr", 3, 7, Charset.forName("utf-8"))
        sink.flush()
        assertArrayEquals("ranə".toByteArray(Charsets.UTF_8), data.readByteArray())
    }

    @Test
    fun writeLatin1StringWithCharset() {
        sink.writeString(LATIN1, Charsets.ISO_8859_1)
        sink.flush()
        assertArrayEquals(LATIN1.toByteArray(Charsets.ISO_8859_1), data.readByteArray())
    }

    @Test
    fun bufferRealSinkReturnsSameObject() {
        val sink1 = (Buffer() as RawSink).buffered()
        val sink2 = sink1.buffered()
        assertSame(sink1, sink2)
    }
}
