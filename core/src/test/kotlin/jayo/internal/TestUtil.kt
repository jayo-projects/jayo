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

package jayo.internal

import jayo.Buffer
import jayo.ByteString
import jayo.encodeToByteString
import org.junit.jupiter.api.Assertions.*
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.*

val SEGMENT_SIZE = Segment.SIZE

object TestUtil {
    // Necessary to make an internal member visible to Java.
    @JvmField
    val SEGMENT_POOL_MAX_SIZE = SegmentPool.MAX_SIZE
    const val REPLACEMENT_CODE_POINT: Int = Utils.UTF8_REPLACEMENT_CODE_POINT

    @JvmStatic
    fun segmentPoolByteCount() = SegmentPool.getByteCount()

    @JvmStatic
    fun segmentSizes(buffer: Buffer): List<Int> = jayo.internal.segmentSizes(buffer)

    @JvmStatic
    fun assertNoEmptySegments(buffer: Buffer) {
        assertTrue(segmentSizes(buffer).all { it != 0 }, "Expected all segments to be non-empty")
    }

    @JvmStatic
    fun assertByteArraysEquals(a: ByteArray, b: ByteArray) {
        assertEquals(a.contentToString(), b.contentToString())
    }

    @JvmStatic
    fun assertByteArrayEquals(expectedUtf8: String, b: ByteArray) {
        assertEquals(expectedUtf8, b.toString(Charsets.UTF_8))
    }

    @JvmStatic
    fun assertEquivalent(b1: ByteString, b2: ByteString) {
        // Equals.
        assertTrue(b1 == b2)
        assertTrue(b1 == b1)
        assertTrue(b2 == b1)

        // Hash code.
        assertEquals(b1.hashCode().toLong(), b2.hashCode().toLong())
        assertEquals(b1.hashCode().toLong(), b1.hashCode().toLong())
        assertEquals(b1.toString(), b2.toString())

        // Content.
        assertEquals(b1.size, b2.size)
        val b2Bytes = b2.toByteArray()
        for (i in b2Bytes.indices) {
            val b = b2Bytes[i]
            assertEquals(b.toLong(), b1[i].toLong())
        }
        assertByteArraysEquals(b1.toByteArray(), b2Bytes)

        // Doesn't equal a different byte string.
        assertFalse(b1 == Any())
        if (b2Bytes.size > 0) {
            val b3Bytes = b2Bytes.clone()
            b3Bytes[b3Bytes.size - 1]++
            val b3 = ByteString.of(*b3Bytes)
            assertFalse(b1 == b3)
            assertFalse(b1.hashCode() == b3.hashCode())
        } else {
            val b3 = "a".encodeToByteString()
            assertFalse(b1 == b3)
            assertFalse(b1.hashCode() == b3.hashCode())
        }
    }

    @JvmStatic
    fun assertEquivalent(b1: Buffer, b2: Buffer) {
        // Equals.
//        assertTrue(b1 == b2)
//        assertTrue(b1 == b1)
//        assertTrue(b2 == b1)

        // Hash code.
//        assertEquals(b1.hashCode().toLong(), b2.hashCode().toLong())
//        assertEquals(b1.hashCode().toLong(), b1.hashCode().toLong())
//        assertEquals(b1.toString(), b2.toString())

        // Content.
        assertEquals(b1.size, b2.size)
        val buffer = RealBuffer()
        b2.copyTo(buffer, 0, b2.size)
        val b2Bytes = b2.readByteArray()
        for (i in b2Bytes.indices) {
            val b = b2Bytes[i]
            assertEquals(b.toLong(), b1[i.toLong()].toLong())
        }

        // Doesn't equal a different buffer.
        assertFalse(b1 == Any())
        if (b2Bytes.size > 0) {
            val b3Bytes = b2Bytes.clone()
            b3Bytes[b3Bytes.size - 1]++
            val b3 = RealBuffer().write(b3Bytes)
            assertFalse(b1 == b3)
            assertFalse(b1.hashCode() == b3.hashCode())
        } else {
            val b3 = RealBuffer().writeUtf8("a")
            assertFalse(b1 == b3)
            assertFalse(b1.hashCode() == b3.hashCode())
        }
    }

    /** Serializes original to bytes, then deserializes those bytes and returns the result.  */
    // Assume serialization doesn't change types.
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T : Serializable> reserialize(original: T): T {
        val buffer = RealBuffer()
        val out = ObjectOutputStream(buffer.asOutputStream())
        out.writeObject(original)
        val input = ObjectInputStream(buffer.asInputStream())
        return input.readObject() as T
    }

    /** Returns a copy of `buffer` with no segments with `original`.  */
    @JvmStatic
    fun deepCopy(original: Buffer): Buffer {
        val result = RealBuffer()
        if (original.size == 0L) {
            return result
        }
        if (original !is RealBuffer) {
            throw IllegalArgumentException()
        }
        
        var s = original.segmentQueue.next
        while (s !== original.segmentQueue) {
            result.segmentQueue.addTail(s!!.unsharedCopy())
            s = s.next
        }
        result.segmentQueue.incrementSize(original.size)

        return result
    }

    /**
     * Returns a new buffer containing the data in `data` and a segment
     * layout determined by `dice`.
     */
    @JvmStatic
    fun bufferWithRandomSegmentLayout(dice: Random, data: ByteArray): Buffer {
        val result = Buffer()

        // Writing to result directly will yield packed segments. Instead, write to
        // other buffers, then write those buffers to result.
        var pos = 0
        var byteCount: Int
        while (pos < data.size) {
            byteCount = Segment.SIZE / 2 + dice.nextInt(Segment.SIZE / 2)
            if (byteCount > data.size - pos) byteCount = data.size - pos
            val offset = dice.nextInt(Segment.SIZE - byteCount)

            val buffer = Buffer()
            buffer.write(ByteArray(offset))
            buffer.write(data, pos, byteCount)
            buffer.skip(offset.toLong())

            result.write(buffer, byteCount.toLong())
            pos += byteCount
        }

        return result
    }

    /** Remove all segments from the pool and return them as a list. */
    @JvmStatic
    fun takeAllPoolSegments(): List<Segment> {
        val result = mutableListOf<Segment>()
        while (SegmentPool.getByteCount() > 0) {
            result += SegmentPool.take()
        }
        return result
    }

    @JvmStatic
    fun newThread(task: Runnable): Thread {
        return Utils.threadBuilder("").unstarted(task)
    }

    @JvmStatic
    fun assumeNotWindows() = assertFalse(System.getProperty("os.name").lowercase(Locale.getDefault()).contains("win"))
}
