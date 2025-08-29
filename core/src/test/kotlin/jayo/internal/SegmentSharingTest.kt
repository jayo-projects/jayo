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

import jayo.bytestring.ByteString
import jayo.bytestring.encodeToByteString
import jayo.internal.JavaTestUtil.takeAllPoolSegments
import jayo.internal.TestUtil.assertEquivalent
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.*
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
        assertEquivalent(byteString, (xs + ys + zs).encodeToByteString(Charsets.UTF_8))
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
        assertEquals(xs + ys + zs, out.readString())
    }

    /**
     * Snapshots share their backing byte arrays with the reader buffers. Those byte arrays must not be recycled,
     * otherwise the new writer could corrupt the segment.
     */
    @Test
    fun snapshotSegmentsAreNotRecycled() {
        val buffer = RealBuffer()
        buffer.write("abc")
        val snapshot = buffer.snapshot()

        // Confirm that clearing the buffer doesn't release its segments.
        val bufferHead = buffer.head!!
        takeAllPoolSegments() // Make room for new segments.
        buffer.clear()

        assertEquals("abc", snapshot.decodeToString())
        assertFalse(bufferHead in takeAllPoolSegments())
    }

    /**
     * Clones share their backing byte arrays with the reader buffers. Those byte arrays must not be recycled, otherwise
     * the new writer could corrupt the segment.
     */
    @Test
    fun cloneSegmentsAreNotRecycled() {
        val buffer = bufferWithSegments(xs, ys, zs)
        val clone = buffer.clone()

        // While locking the pool, confirm that clearing the buffer doesn't release its segments.
        val bufferHead = (buffer as RealBuffer).head!!
        takeAllPoolSegments() // Make room for new segments.
        buffer.clear()
        assertTrue(bufferHead !in takeAllPoolSegments())

        val cloneHead = (clone as RealBuffer).head!!
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
        bufferA.write("abc")
        val bufferB = bufferA.clone()
        bufferA.write("def")
        bufferB.write("DEF")
        assertEquals("abcdef", bufferA.readString())
        assertEquals("abcDEF", bufferB.readString())
    }

    @Test
    fun concatenateSegmentsCanCombine() {
        val bufferA = RealBuffer().write(ys).write(us)
        assertEquals(ys, bufferA.readString(ys.length.toLong()))
        val bufferB = RealBuffer().write(vs).write(ws)
        val bufferC = bufferA.clone()
        bufferA.writeFrom(bufferB, vs.length.toLong())
        bufferC.write(xs)

        assertEquals(us + vs, bufferA.readString())
        assertEquals(ws, bufferB.readString())
        assertEquals(us + xs, bufferC.readString())
    }

    @Test
    fun shareAndSplit() {
        val bufferA = RealBuffer().write("xxxx")
        val snapshot = bufferA.snapshot() // Share the segment.
        val bufferB = RealBuffer()
        bufferB.writeFrom(bufferA, 2) // Split the shared segment in two.
        bufferB.write("yy") // Append to the first half of the shared segment.
        assertEquals("xxxx", snapshot.decodeToString())
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
        val bufferB = RealBuffer().write(us)
        bufferB.write(snapshot)
        assertEquivalent(bufferB, RealBuffer().write(us + xs + ys))
    }

    @Test
    fun copyToSegmentSharing() {
        val bufferA = bufferWithSegments(ws, xs + "aaaa", ys, "bbbb$zs")
        val bufferB = bufferWithSegments(us)
        bufferA.copyTo(bufferB, (ws.length + xs.length).toLong(), (4 + ys.length + 4).toLong())
        assertEquivalent(bufferB, RealBuffer().write(us + "aaaa" + ys + "bbbb"))
    }
}

private val us = "u".repeat(Segment.SIZE / 2 - 2)
private val vs = "v".repeat(Segment.SIZE / 2 - 1)
private val ws = "w".repeat(Segment.SIZE / 2)
private val xs = "x".repeat(Segment.SIZE / 2 + 1)
private val ys = "y".repeat(Segment.SIZE / 2 + 2)
private val zs = "z".repeat(Segment.SIZE / 2 + 3)
