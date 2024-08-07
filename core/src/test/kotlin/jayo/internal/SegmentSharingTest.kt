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

import jayo.ByteString
import jayo.encodeToByteString
import jayo.internal.JavaTestUtil.takeAllPoolSegments
import jayo.internal.TestUtil.assertEquivalent
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tests behavior optimized by sharing segments between buffers and byte strings.  */
class SegmentSharingTest {
    @Test
    fun snapshotOfEmptyBuffer() {
        val snapshot = RealBuffer().snapshot()
        assertEquivalent(snapshot, ByteString.EMPTY)
    }

    @Test
    fun snapshotsAreEquivalent() {
        val byteString = bufferWithSegments(xs, ys, zs).snapshot()
        assertEquivalent(byteString, bufferWithSegments(xs, ys + zs).snapshot())
        assertEquivalent(byteString, bufferWithSegments(xs + ys + zs).snapshot())
        assertEquivalent(byteString, (xs + ys + zs).encodeToByteString())
    }

    @Test
    fun snapshotGetByte() {
        val byteString = bufferWithSegments(xs, ys, zs).snapshot()
        assertEquals('x', byteString.getByte(0).toInt().toChar())
        assertEquals('x', byteString.getByte(xs.length - 1).toInt().toChar())
        assertEquals('y', byteString.getByte(xs.length).toInt().toChar())
        assertEquals('y', byteString.getByte(xs.length + ys.length - 1).toInt().toChar())
        assertEquals('z', byteString.getByte(xs.length + ys.length).toInt().toChar())
        assertEquals('z', byteString.getByte(xs.length + ys.length + zs.length - 1).toInt().toChar())
        assertThatThrownBy { byteString.getByte(-1) }
            .isInstanceOf(IndexOutOfBoundsException::class.java)

        assertThatThrownBy { byteString.getByte(xs.length + ys.length + zs.length) }
            .isInstanceOf(IndexOutOfBoundsException::class.java)

    }

    @Test
    fun snapshotWriteToOutputStream() {
        val byteString = bufferWithSegments(xs, ys, zs).snapshot()
        val out = RealBuffer()
        byteString.write(out.asOutputStream())
        assertEquals(xs + ys + zs, out.readUtf8String())
    }

    /**
     * Snapshots share their backing byte arrays with the reader buffers. Those byte arrays must not
     * be recycled, otherwise the new writer could corrupt the segment.
     */
    @Test
    fun snapshotSegmentsAreNotRecycled() {
        val buffer = bufferWithSegments(xs, ys, zs)
        val snapshot = buffer.snapshot()
        assertEquals(xs + ys + zs, snapshot.decodeToUtf8())

        // Confirm that clearing the buffer doesn't release its segments.
        val bufferHead = (buffer as RealBuffer).segmentQueue.headVolatile()!!
        takeAllPoolSegments() // Make room for new segments.
        buffer.clear()
        assertTrue(bufferHead !in takeAllPoolSegments())
    }

    /**
     * Clones share their backing byte arrays with the reader buffers. Those byte arrays must not
     * be recycled, otherwise the new writer could corrupt the segment.
     */
    @Test
    fun cloneSegmentsAreNotRecycled() {
        val buffer = bufferWithSegments(xs, ys, zs)
        val clone = buffer.clone()

        // While locking the pool, confirm that clearing the buffer doesn't release its segments.
        val bufferHead = (buffer as RealBuffer).segmentQueue.headVolatile()!!
        takeAllPoolSegments() // Make room for new segments.
        buffer.clear()
        assertTrue(bufferHead !in takeAllPoolSegments())

        val cloneHead = (clone as RealBuffer).segmentQueue.headVolatile()!!
        takeAllPoolSegments() // Make room for new segments.
        clone.clear()
        assertTrue(cloneHead !in takeAllPoolSegments())
    }

    @Test
    fun snapshotJavaSerialization() {
        val byteString = bufferWithSegments(xs, ys, zs).snapshot()
        assertEquivalent(byteString, TestUtil.reserialize(byteString))
    }

    @Test
    fun clonesAreEquivalent() {
        val bufferA = bufferWithSegments(xs, ys, zs)
        val bufferB = bufferA.clone()
        assertEquivalent(bufferA, bufferB)
        assertEquivalent(bufferA, bufferWithSegments(xs + ys, zs))
    }

    /** Even though some segments are shared, clones can be mutated independently.  */
    @Test
    fun mutateAfterClone() {
        val bufferA = RealBuffer()
        bufferA.writeUtf8("abc")
        val bufferB = bufferA.clone()
        bufferA.writeUtf8("def")
        bufferB.writeUtf8("DEF")
        assertEquals("abcdef", bufferA.readUtf8String())
        assertEquals("abcDEF", bufferB.readUtf8String())
    }

    @Test
    fun concatenateSegmentsCanCombine() {
        val bufferA = RealBuffer().writeUtf8(ys).writeUtf8(us)
        assertEquals(ys, bufferA.readUtf8String(ys.length.toLong()))
        val bufferB = RealBuffer().writeUtf8(vs).writeUtf8(ws)
        val bufferC = bufferA.clone()
        bufferA.write(bufferB, vs.length.toLong())
        bufferC.writeUtf8(xs)

        assertEquals(us + vs, bufferA.readUtf8String())
        assertEquals(ws, bufferB.readUtf8String())
        assertEquals(us + xs, bufferC.readUtf8String())
    }

    @Test
    fun shareAndSplit() {
        val bufferA = RealBuffer().writeUtf8("xxxx")
        val snapshot = bufferA.snapshot() // Share the segment.
        val bufferB = RealBuffer()
        bufferB.write(bufferA, 2) // Split the shared segment in two.
        bufferB.writeUtf8("yy") // Append to the first half of the shared segment.
        assertEquals("xxxx", snapshot.decodeToUtf8())
    }

    @Test
    fun appendSnapshotToEmptyBuffer() {
        val bufferA = bufferWithSegments(xs, ys)
        val snapshot = bufferA.snapshot()
        val bufferB = RealBuffer()
        bufferB.write(snapshot)
        assertEquivalent(bufferB, bufferA)
    }

    @Test
    fun appendSnapshotToNonEmptyBuffer() {
        val bufferA = bufferWithSegments(xs, ys)
        val snapshot = bufferA.snapshot()
        val bufferB = RealBuffer().writeUtf8(us)
        bufferB.write(snapshot)
        assertEquivalent(bufferB, RealBuffer().writeUtf8(us + xs + ys))
    }

    @Test
    fun copyToSegmentSharing() {
        val bufferA = bufferWithSegments(ws, xs + "aaaa", ys, "bbbb$zs")
        val bufferB = bufferWithSegments(us)
        bufferA.copyTo(bufferB, (ws.length + xs.length).toLong(), (4 + ys.length + 4).toLong())
        assertEquivalent(bufferB, RealBuffer().writeUtf8(us + "aaaa" + ys + "bbbb"))
    }
}

private val us = "u".repeat(Segment.SIZE / 2 - 2)
private val vs = "v".repeat(Segment.SIZE / 2 - 1)
private val ws = "w".repeat(Segment.SIZE / 2)
private val xs = "x".repeat(Segment.SIZE / 2 + 1)
private val ys = "y".repeat(Segment.SIZE / 2 + 2)
private val zs = "z".repeat(Segment.SIZE / 2 + 3)
