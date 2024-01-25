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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.internal

import jayo.Buffer
import jayo.ByteString
import jayo.internal.TestUtil.deepCopy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import java.util.stream.Stream
import kotlin.test.*

class BufferCursorTest {

    companion object {
        @JvmStatic
        fun parameters(): Stream<Arguments>? {
            return Stream.of(
                Arguments.of(BufferFactory.EMPTY, "EmptyBuffer"),
                Arguments.of(BufferFactory.SMALL_BUFFER, "SmallBuffer"),
                Arguments.of(BufferFactory.SMALL_SEGMENTED_BUFFER, "SmallSegmentedBuffer"),
                Arguments.of(BufferFactory.LARGE_BUFFER, "LargeBuffer"),
                Arguments.of(BufferFactory.LARGE_BUFFER_WITH_RANDOM_LAYOUT, "LargeBufferWithRandomLayout"),
            )
        }
    }

    @Test
    fun apiExample() {
        val buffer = Buffer()
        buffer.readAndWriteUnsafe().use { cursor ->
            cursor.resizeBuffer(1000000)
            do {
                Arrays.fill(cursor.data, cursor.start, cursor.end, 'x'.code.toByte())
            } while (cursor.next() != -1)
            cursor.seek(3)
            cursor.data!![cursor.start] = 'o'.code.toByte()
            cursor.seek(1)
            cursor.data!![cursor.start] = 'o'.code.toByte()
            cursor.resizeBuffer(4)
        }
        assertEquals(Buffer().writeUtf8("xoxo").readByteString(), buffer.readByteString())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun accessSegmentBySegment(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        buffer.readUnsafe().use { cursor ->
            val actual = Buffer()
            while (cursor.next().toLong() != -1L) {
                actual.write(cursor.data!!, cursor.start, cursor.end - cursor.start)
            }
            assertEquals(buffer.readByteString(), actual.readByteString())
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun seekToNegativeOneSeeksBeforeFirstSegment(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        buffer.readUnsafe().use { cursor ->
            cursor.seek(-1L)
            assertEquals(-1, cursor.offset)
            assertNull(cursor.data)
            assertEquals(-1, cursor.start.toLong())
            assertEquals(-1, cursor.end.toLong())
            cursor.next()
            assertEquals(0, cursor.offset)
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun accessByteByByte(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        buffer.readUnsafe().use { cursor ->
            val actual = ByteArray(buffer.size.toInt())
            for (i in 0 until buffer.size) {
                cursor.seek(i)
                actual[i.toInt()] = cursor.data!![cursor.start]
            }
            assertEquals(ByteString.of(*actual), buffer.snapshot())
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun accessByteByByteReverse(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        buffer.readUnsafe().use { cursor ->
            val actual = ByteArray(buffer.size.toInt())
            for (i in (buffer.size - 1).toInt() downTo 0) {
                cursor.seek(i.toLong())
                actual[i] = cursor.data!![cursor.start]
            }
            assertEquals(ByteString.of(*actual), buffer.snapshot())
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun accessByteByByteAlwaysResettingToZero(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        buffer.readUnsafe().use { cursor ->
            val actual = ByteArray(buffer.size.toInt())
            for (i in 0 until buffer.size) {
                cursor.seek(i)
                actual[i.toInt()] = cursor.data!![cursor.start]
                cursor.seek(0L)
            }
            assertEquals(ByteString.of(*actual), buffer.snapshot())
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun segmentBySegmentNavigation(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        val cursor = buffer.readUnsafe()
        assertEquals(-1, cursor.offset)
        try {
            var lastOffset = cursor.offset
            while (cursor.next().toLong() != -1L) {
                assertTrue(cursor.offset > lastOffset)
                lastOffset = cursor.offset
            }
            assertEquals(buffer.size, cursor.offset)
            assertNull(cursor.data)
            assertEquals(-1, cursor.start.toLong())
            assertEquals(-1, cursor.end.toLong())
        } finally {
            cursor.close()
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun seekWithinSegment(bufferFactory: BufferFactory) {
        assumeTrue(bufferFactory === BufferFactory.SMALL_SEGMENTED_BUFFER)
        val buffer = bufferFactory.newBuffer()
        assertEquals("abcdefghijkl", buffer.clone().readUtf8())
        buffer.readUnsafe().use { cursor ->
            assertEquals(2, cursor.seek(5).toLong()) // 2 for 2 bytes left in the segment: "fg".
            assertEquals(5, cursor.offset)
            assertEquals(2, (cursor.end - cursor.start).toLong())
            assertEquals(
                'd'.code.toLong(),
                Char(cursor.data!![cursor.start - 2].toUShort()).code.toLong()
            ) // Out of bounds!
            assertEquals(
                'e'.code.toLong(),
                Char(cursor.data!![cursor.start - 1].toUShort()).code.toLong()
            ) // Out of bounds!
            assertEquals('f'.code.toLong(), Char(cursor.data!![cursor.start].toUShort()).code.toLong())
            assertEquals('g'.code.toLong(), Char(cursor.data!![cursor.start + 1].toUShort()).code.toLong())
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun acquireAndRelease(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        val cursor = Buffer.UnsafeCursor.create()

        // Nothing initialized before acquire.
        assertEquals(-1, cursor.offset)
        assertNull(cursor.data)
        assertEquals(-1, cursor.start.toLong())
        assertEquals(-1, cursor.end.toLong())
        buffer.readUnsafe(cursor)
        cursor.close()

        // Nothing initialized after close.
        assertEquals(-1, cursor.offset)
        assertNull(cursor.data)
        assertEquals(-1, cursor.start.toLong())
        assertEquals(-1, cursor.end.toLong())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun doubleAcquire(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        buffer.readUnsafe().use { cursor ->
            assertFailsWith<IllegalStateException> {
                buffer.readUnsafe(cursor)
            }
        }
    }

    @Test
    fun releaseWithoutAcquire() {
        val cursor = Buffer.UnsafeCursor.create()
        assertFailsWith<IllegalStateException> {
            cursor.close()
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun releaseAfterRelease(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        val cursor = buffer.readUnsafe()
        cursor.close()
        assertFailsWith<IllegalStateException> {
            cursor.close()
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun enlarge(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        val expected = deepCopy(buffer)
        expected.writeUtf8("abc")
        buffer.readAndWriteUnsafe().use { cursor ->
            assertEquals(originalSize, cursor.resizeBuffer(originalSize + 3))
            cursor.seek(originalSize)
            cursor.data!![cursor.start] = 'a'.code.toByte()
            cursor.seek(originalSize + 1)
            cursor.data!![cursor.start] = 'b'.code.toByte()
            cursor.seek(originalSize + 2)
            cursor.data!![cursor.start] = 'c'.code.toByte()
        }
        assertEquals(expected.readByteString(), buffer.readByteString())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun enlargeByManySegments(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        val expected = deepCopy(buffer)
        expected.writeUtf8("x".repeat(1000000))
        buffer.readAndWriteUnsafe().use { cursor ->
            cursor.resizeBuffer(originalSize + 1000000)
            cursor.seek(originalSize)
            do {
                Arrays.fill(cursor.data, cursor.start, cursor.end, 'x'.code.toByte())
            } while (cursor.next() != -1)
        }
        assertEquals(expected.readByteString(), buffer.readByteString())
    }

    @Test
    fun resizeNotAcquired() {
        val cursor = Buffer.UnsafeCursor.create()
        assertFailsWith<IllegalStateException> {
            cursor.resizeBuffer(10)
        }
    }

    @Test
    fun expandNotAcquired() {
        val cursor = Buffer.UnsafeCursor.create()
        assertFailsWith<IllegalStateException> {
            cursor.expandBuffer(10)
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun resizeAcquiredReadOnly(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()

        buffer.readUnsafe().use { cursor ->
            assertFailsWith<IllegalStateException> {
                cursor.resizeBuffer(10)
            }
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun expandAcquiredReadOnly(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        buffer.readUnsafe().use { cursor ->
            assertFailsWith<IllegalStateException> {
                cursor.expandBuffer(10)
            }
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun shrink(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        assumeTrue(buffer.size > 3)
        val originalSize = buffer.size
        val expected = Buffer()
        deepCopy(buffer).copyTo(expected, 0, originalSize - 3)
        buffer.readAndWriteUnsafe().use { cursor ->
            assertEquals(originalSize, cursor.resizeBuffer(originalSize - 3))
        }
        assertEquals(expected.readByteString(), buffer.readByteString())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun shrinkByManySegments(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        assertTrue(buffer.size <= 1000000)
        val originalSize = buffer.size
        val toShrink = Buffer()
        toShrink.writeUtf8("x".repeat(1000000))
        deepCopy(buffer).copyTo(toShrink, 0, originalSize)
        val cursor = Buffer.UnsafeCursor.create()
        toShrink.readAndWriteUnsafe(cursor)
        try {
            cursor.resizeBuffer(originalSize)
        } finally {
            cursor.close()
        }
        val expected = Buffer()
        expected.writeUtf8("x".repeat(originalSize.toInt()))
        assertEquals(expected.readByteString(), toShrink.readByteString())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun shrinkAdjustOffset(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        assumeTrue(buffer.size > 4)
        buffer.readAndWriteUnsafe().use { cursor ->
            cursor.seek(buffer.size - 1)
            cursor.resizeBuffer(3)
            assertEquals(3, cursor.offset)
            assertNull(cursor.data)
            assertEquals(-1, cursor.start.toLong())
            assertEquals(-1, cursor.end.toLong())
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun resizeToSameSizeSeeksToEnd(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        buffer.readAndWriteUnsafe().use { cursor ->
            cursor.seek(buffer.size / 2)
            assertEquals(originalSize, buffer.size)
            cursor.resizeBuffer(originalSize)
            assertEquals(originalSize, buffer.size)
            assertEquals(originalSize, cursor.offset)
            assertNull(cursor.data)
            assertEquals(-1, cursor.start.toLong())
            assertEquals(-1, cursor.end.toLong())
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun resizeEnlargeMovesCursorToOldSize(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        val expected = deepCopy(buffer)
        expected.writeUtf8("a")
        buffer.readAndWriteUnsafe().use { cursor ->
            cursor.seek(buffer.size / 2)
            assertEquals(originalSize, buffer.size)
            cursor.resizeBuffer(originalSize + 1)
            assertEquals(originalSize, cursor.offset)
            assertNotNull(cursor.data)
            assertNotEquals(-1, cursor.start.toLong())
            assertEquals((cursor.start + 1).toLong(), cursor.end.toLong())
            cursor.data!![cursor.start] = 'a'.code.toByte()
        }
        assertEquals(expected.readByteString(), buffer.readByteString())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun resizeShrinkMovesCursorToEnd(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        assumeTrue(buffer.size > 0)
        val originalSize = buffer.size
        buffer.readAndWriteUnsafe().use { cursor ->
            cursor.seek(buffer.size / 2)
            assertEquals(originalSize, buffer.size)
            cursor.resizeBuffer(originalSize - 1)
            assertEquals(originalSize - 1, cursor.offset)
            assertNull(cursor.data)
            assertEquals(-1, cursor.start.toLong())
            assertEquals(-1, cursor.end.toLong())
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun expand(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        val expected = deepCopy(buffer)
        expected.writeUtf8("abcde")
        buffer.readAndWriteUnsafe().use { cursor ->
            cursor.expandBuffer(5)
            for (i in 0..4) {
                cursor.data!![cursor.start + i] = ('a'.code + i).toByte()
            }
            cursor.resizeBuffer(originalSize + 5)
        }
        assertEquals(expected.readByteString(), buffer.readByteString())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun expandSameSegment(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        assumeTrue(originalSize > 0)
        buffer.readAndWriteUnsafe().use { cursor ->
            cursor.seek(originalSize - 1)
            val originalEnd = cursor.end
            assumeTrue(originalEnd < SEGMENT_SIZE)
            val addedByteCount = cursor.expandBuffer(1)
            assertEquals((SEGMENT_SIZE - originalEnd).toLong(), addedByteCount)
            assertEquals(originalSize + addedByteCount, buffer.size)
            assertEquals(originalSize, cursor.offset)
            assertEquals(originalEnd.toLong(), cursor.start.toLong())
            assertEquals(SEGMENT_SIZE.toLong(), cursor.end.toLong())
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun expandNewSegment(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        buffer.readAndWriteUnsafe().use { cursor ->
            val addedByteCount = cursor.expandBuffer(SEGMENT_SIZE)
            assertEquals(SEGMENT_SIZE.toLong(), addedByteCount)
            assertEquals(originalSize, cursor.offset)
            assertEquals(0, cursor.start.toLong())
            assertEquals(SEGMENT_SIZE.toLong(), cursor.end.toLong())
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun expandMovesOffsetToOldSize(bufferFactory: BufferFactory) {
        val buffer = bufferFactory.newBuffer()
        val originalSize = buffer.size
        buffer.readAndWriteUnsafe().use { cursor ->
            cursor.seek(buffer.size / 2)
            assertEquals(originalSize, buffer.size)
            val addedByteCount = cursor.expandBuffer(5)
            assertEquals(originalSize + addedByteCount, buffer.size)
            assertEquals(originalSize, cursor.offset)
        }
    }
}
