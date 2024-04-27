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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import jayo.*
import jayo.crypto.Digests
import jayo.crypto.Hmacs
import jayo.exceptions.JayoEOFException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BufferTest {
    @Test
    fun copyToBuffer() {
        val source = RealBuffer()
        source.write("party".encodeToByteString())

        val target = RealBuffer()
        source.copyTo(target)
        assertThat(target.readByteString().decodeToUtf8()).isEqualTo("party")
        assertThat(source.readByteString().decodeToUtf8()).isEqualTo("party")
    }

    @Test
    fun copyToBufferWithOffset() {
        val source = RealBuffer()
        source.write("party".encodeToByteString())

        val target = RealBuffer()
        source.copyTo(target, 2L)
        assertThat(target.readByteString().decodeToUtf8()).isEqualTo("rty")
        assertThat(source.readByteString().decodeToUtf8()).isEqualTo("party")
    }

    @Test
    fun copyToBufferWithByteCount() {
        val source = RealBuffer()
        source.write("party".encodeToByteString())

        val target = RealBuffer()
        source.copyTo(target, 0L, 3L)
        assertThat(target.readByteString().decodeToUtf8()).isEqualTo("par")
        assertThat(source.readByteString().decodeToUtf8()).isEqualTo("party")
    }

    @Test
    fun copyToBufferWithOffsetAndByteCount() {
        val source = RealBuffer()
        source.write("party".encodeToByteString())

        val target = RealBuffer()
        source.copyTo(target, 1L, 3L)
        assertThat(target.readByteString().decodeToUtf8()).isEqualTo("art")
        assertThat(source.readByteString().decodeToUtf8()).isEqualTo("party")
    }

    @Test
    fun completeSegmentByteCountOnEmptyBuffer() {
        val buffer = RealBuffer()
        assertThat(buffer.completeSegmentByteCount()).isEqualTo(0)
    }

    @Test
    fun completeSegmentByteCountOnBufferWithFullSegments() {
        val buffer = RealBuffer()
        buffer.writeUtf8("a".repeat(Segment.SIZE * 4))
        assertThat(buffer.completeSegmentByteCount()).isEqualTo(Segment.SIZE * 4L)
    }

    @Test
    fun completeSegmentByteCountOnBufferWithIncompleteTailSegment() {
        val buffer = RealBuffer()
        buffer.writeUtf8("a".repeat(Segment.SIZE * 4 - 10))
        assertThat(buffer.completeSegmentByteCount()).isEqualTo(Segment.SIZE * 3L)
    }

    @Test
    fun testHash() {
        val buffer = RealBuffer().apply { write("Kevin".encodeToByteString()) }
        with(buffer) {
            assertThat(hash(Digests.MD5).hex()).isEqualTo("f1cd318e412b5f7226e5f377a9544ff7")
            assertThat(hash(Digests.SHA_1).hex()).isEqualTo("e043899daa0c7add37bc99792b2c045d6abbc6dc")
            assertThat(hash(Digests.SHA_224).hex())
                .isEqualTo("35e1fa1b770f696e95666b66ff1d040ab4a0421dae005d048a5647c5")
            assertThat(hash(Digests.SHA_256).hex())
                .isEqualTo("0e4dd66217fc8d2e298b78c8cd9392870dcd065d0ff675d0edff5bcd227837e9")
            assertThat(hash(Digests.SHA_384).hex())
                .isEqualTo(
                    "45824b5d3cc0dd249144875b15833d117c11fa775e06ec1fe19988b3347395013a49d78c8056653d06ba196c1a94a160"
                )
            assertThat(hash(Digests.SHA_512).hex()).isEqualTo(
                "483676b93c4417198b465083d196ec6a9fab8d004515874b8ff47e041f5f56303cc08179625030b8b5b721c09149a18f0f5" +
                        "9e64e7ae099518cea78d3d83167e1"
            )
            assertThat(hash(Digests.SHA_512_224).hex())
                .isEqualTo("730ceb5e4e968eba3aa3fe8aeaf6e08761b94917db83a44a64e20159")
            assertThat(hash(Digests.SHA_512_256).hex())
                .isEqualTo("4f023d8f32c539d712f9f2dfb3719fbc4c980c27abb7382e988bf4fbd4f1caa9")
            assertThat(hash(Digests.SHA3_224).hex())
                .isEqualTo("8e1cb3e2802de9986b6624bac295fa507ccdc6efe7edaa22122cd120")
            assertThat(hash(Digests.SHA3_256).hex())
                .isEqualTo("95547a916f3e4c214fa80a3d78e86faa92e3f6703f5e713c1d176e029116ce6f")
            assertThat(hash(Digests.SHA3_384).hex())
                .isEqualTo(
                    "451ceac3054d4a5859a79bfbf6e6a1c2223f5fcecb883333d98eff63c84f2bd1c797bf7fe449e000f488173bd09daf19"
                )
            assertThat(hash(Digests.SHA3_512).hex()).isEqualTo(
                "d925dc48347d016fd1dc3907378c95c0c860a75332d2e673f743d970bbea89d1e77a07a4a9a64290146ba273bb262d1dd2" +
                        "37bace6761293a75dbf39f72815da8"
            )
        }
    }

    @Test
    fun testHMac() {
        val buffer = RealBuffer().apply { write("Kevin".encodeToByteString()) }
        val key = "Brandon".encodeToByteString()
        with(buffer) {
            assertThat(hmac(Hmacs.HMAC_MD5, key).hex()).isEqualTo("cd5478da9993e894de891a6d680a88fb")
            assertThat(hmac(Hmacs.HMAC_SHA_1, key).hex()).isEqualTo("46eedc331e6f92c801808fd5bfc5424afe659402")
            assertThat(hmac(Hmacs.HMAC_SHA_224, key).hex())
                .isEqualTo("3f1ade07df5bfbb4be786f93ca898fb5993e7af71577f13a2ebbc253")
            assertThat(hmac(Hmacs.HMAC_SHA_256, key).hex())
                .isEqualTo("5eaf69955f51d61665e28ce16acbf7e5e1b6a2d1f62b3b4bad1aa0913a349e77")
            assertThat(hmac(Hmacs.HMAC_SHA_384, key).hex())
                .isEqualTo(
                    "a62272c16f97153b5ce36a6f61f999d925efeed91bf9aac76799ef9c02991ec644f4d8a332275278f78478d5cb9ae6b8"
                )
            assertThat(hmac(Hmacs.HMAC_SHA_512, key).hex()).isEqualTo(
                "06edf87929601bd8a1124d996b774881e55e36cf70c58e26d44c1a7bf596ba3b8e1d8b018275791a441a0b5edb86abf394bd" +
                        "8081a6da8e51e39521b346780dde"
            )
            assertThat(hmac(Hmacs.HMAC_SHA_512_224, key).hex())
                .isEqualTo("0692adbe44b74f90dc0ee4e8f280a86ba190cb783f78c159bea06a9e")
            assertThat(hmac(Hmacs.HMAC_SHA_512_256, key).hex())
                .isEqualTo("552d369db150748af9db5c0d621bce8ba7c86807ea5293ed10a2c3f7129b410d")
            assertThat(hmac(Hmacs.HMAC_SHA3_224, key).hex())
                .isEqualTo("0e6451625aaa6b5c11bd664a0dddd8883a17980be6b3440532719c75")
            assertThat(hmac(Hmacs.HMAC_SHA3_256, key).hex())
                .isEqualTo("5e772f25f1e3ff180cfb8b8a20f31afc43b1fbf86d01c5a74b28608801d53a2e")
            assertThat(hmac(Hmacs.HMAC_SHA3_384, key).hex())
                .isEqualTo(
                    "0fdc46f226ad27c730a36adfd23c7f2a82dcafdd92fa546730391ef9e2595b73b82f489e0b3e638a2d42d2191c69b031"
                )
            assertThat(hmac(Hmacs.HMAC_SHA3_512, key).hex()).isEqualTo(
                "9b9f9fc58cb6ae835a74d4d9ab51e1583027130315b5aaf497dd51b6dacbae7f9e141a2ecdcbfce337f031f2ce83a58fdfc" +
                        "ee27a373ff408f792326a69a900cc"
            )
        }
    }

    @Test
    fun get() {
        val actual = RealBuffer().writeUtf8("abc")
        assertThat(actual[0]).isEqualTo('a'.code.toByte())
        assertThat(actual[1]).isEqualTo('b'.code.toByte())
        assertThat(actual[2]).isEqualTo('c'.code.toByte())
        assertThatThrownBy { actual[-1] }
            .isInstanceOf(IndexOutOfBoundsException::class.java)
        assertThatThrownBy { actual[3] }
            .isInstanceOf(IndexOutOfBoundsException::class.java)
    }

    @Test
    fun copyToOutputStream() {
        val source = RealBuffer()
        source.writeUtf8("party")

        val target = RealBuffer()
        source.copyTo(target.asOutputStream())
        assertThat(target.readUtf8()).isEqualTo("party")
        assertThat(source.readUtf8()).isEqualTo("party")
    }

    @Test
    fun copyToOutputStreamWithOffset() {
        val source = RealBuffer()
        source.writeUtf8("party")

        val target = RealBuffer()
        source.copyTo(target.asOutputStream(), 2L)
        assertThat(target.readUtf8()).isEqualTo("rty")
        assertThat(source.readUtf8()).isEqualTo("party")
    }

    @Test
    fun copyToOutputStreamWithByteCount() {
        val source = RealBuffer()
        source.writeUtf8("party")

        val target = RealBuffer()
        source.copyTo(target.asOutputStream(), 0L, 3L)
        assertThat(target.readUtf8()).isEqualTo("par")
        assertThat(source.readUtf8()).isEqualTo("party")
    }

    @Test
    fun copyToOutputStreamWithOffsetAndByteCount() {
        val source = RealBuffer()
        source.writeUtf8("party")

        val target = RealBuffer()
        source.copyTo(target.asOutputStream(), 1L, 3L)
        assertThat(target.readUtf8()).isEqualTo("art")
        assertThat(source.readUtf8()).isEqualTo("party")
    }

    @Test
    fun copyToOutputStreamMultiSegments() {
        val source = RealBuffer()
        source.writeUtf8(
            "a".repeat(Segment.SIZE + 5) +
                    "b".repeat(Segment.SIZE) + 5
        )

        val target = RealBuffer()
        source.copyTo(target.asOutputStream(), 5L, Segment.SIZE.toLong())
        assertThat(target.readUtf8()).isEqualTo("a".repeat(Segment.SIZE))
        assertThat(source.readUtf8()).isEqualTo(
            "a".repeat(Segment.SIZE + 5) +
                    "b".repeat(Segment.SIZE) + 5
        )
    }

    @Test
    fun writeToOutputStream() {
        val source = RealBuffer()
        source.writeUtf8("party")

        val target = RealBuffer()
        source.readTo(target.asOutputStream())
        assertThat(target.readUtf8()).isEqualTo("party")
        assertThat(source.readUtf8()).isEqualTo("")
    }

    @Test
    fun writeToOutputStreamWithByteCount() {
        val source = RealBuffer()
        source.writeUtf8("party")

        val target = RealBuffer()
        source.readTo(target.asOutputStream(), 3L)
        assertThat(target.readUtf8()).isEqualTo("par")
        assertThat(source.readUtf8()).isEqualTo("ty")
    }

    @Test
    fun writeToOutputStreamMultiSegments() {
        val source = RealBuffer()
        source.writeUtf8(
            "a".repeat(Segment.SIZE + 5) +
                    "b".repeat(Segment.SIZE + 5)
        )

        val target = RealBuffer()
        source.readTo(target.asOutputStream(), Segment.SIZE.toLong())
        assertThat(target.readUtf8()).isEqualTo("a".repeat(Segment.SIZE))
        assertThat(source.readUtf8()).isEqualTo("aaaaa" + "b".repeat(Segment.SIZE + 5))
    }

    @Test
    fun readAndWriteUtf8() {
        val buffer = RealBuffer()
        buffer.writeUtf8("ab")
        assertEquals(2, buffer.byteSize())
        buffer.writeUtf8("cdef")
        assertEquals(6, buffer.byteSize())
        assertEquals("abcd", buffer.readUtf8(4))
        assertEquals(2, buffer.byteSize())
        assertEquals("ef", buffer.readUtf8(2))
        assertEquals(0, buffer.byteSize())
        assertFailsWith<JayoEOFException> {
            buffer.readUtf8(1)
        }
    }

    @Test
    fun bufferToString() {
        assertEquals("Buffer(size=0)", RealBuffer().toString())

        assertEquals(
            "Buffer(size=10 hex=610d0a620a630d645c65)",
            RealBuffer().also { it.writeUtf8("a\r\nb\nc\rd\\e") }.toString()
        )

        assertEquals(
            "Buffer(size=11 hex=547972616e6e6f73617572)",
            RealBuffer().also { it.writeUtf8("Tyrannosaur") }.toString()
        )

        assertEquals(
            "Buffer(size=16 hex=74c999cb8872616ec999cb8c73c3b472)",
            RealBuffer().also { it.write("74c999cb8872616ec999cb8c73c3b472".decodeHex()) }.toString()
        )

        assertEquals(
            "Buffer(size=64 hex=00000000000000000000000000000000000000000000000000000000000000000000000" +
                    "000000000000000000000000000000000000000000000000000000000)",
            RealBuffer().also { it.write(ByteArray(64)) }.toString()
        )

        assertEquals(
            "Buffer(size=66 hex=000000000000000000000000000000000000000000000000000000000000" +
                    "00000000000000000000000000000000000000000000000000000000000000000000…)",
            RealBuffer().also { it.write(ByteArray(66)) }.toString()
        )
    }

    @Test
    fun multipleSegmentBuffers() {
        val buffer = RealBuffer()
        buffer.writeUtf8('a'.repeat(1000))
        buffer.writeUtf8('b'.repeat(2500))
        buffer.writeUtf8('c'.repeat(5000))
        buffer.writeUtf8('d'.repeat(10000))
        buffer.writeUtf8('e'.repeat(25000))
        buffer.writeUtf8('f'.repeat(50000))

        assertEquals('a'.repeat(999), buffer.readUtf8(999)) // a...a
        assertEquals("a" + 'b'.repeat(2500) + "c", buffer.readUtf8(2502)) // ab...bc
        assertEquals('c'.repeat(4998), buffer.readUtf8(4998)) // c...c
        assertEquals("c" + 'd'.repeat(10000) + "e", buffer.readUtf8(10002)) // cd...de
        assertEquals('e'.repeat(24998), buffer.readUtf8(24998)) // e...e
        assertEquals("e" + 'f'.repeat(50000), buffer.readUtf8(50001)) // ef...f
        assertEquals(0, buffer.byteSize())
    }

    @Test
    fun moveBytesBetweenBuffersShareSegment() {
        val size = Segment.SIZE / 2 - 1
        val segmentSizes = moveBytesBetweenBuffers('a'.repeat(size), 'b'.repeat(size))
        assertEquals(listOf(size * 2), segmentSizes)
    }

    @Test
    fun moveBytesBetweenBuffersReassignSegment() {
        val size = Segment.SIZE / 2 + 1
        val segmentSizes = moveBytesBetweenBuffers('a'.repeat(size), 'b'.repeat(size))
        assertEquals(listOf(size, size), segmentSizes)
    }

    @Test
    fun moveBytesBetweenBuffersMultipleSegments() {
        val size = 3 * Segment.SIZE + 1
        val segmentSizes = moveBytesBetweenBuffers('a'.repeat(size), 'b'.repeat(size))
        assertEquals(
            listOf(
                Segment.SIZE, Segment.SIZE, Segment.SIZE, 1,
                Segment.SIZE, Segment.SIZE, Segment.SIZE, 1
            ),
            segmentSizes
        )
    }

    private fun moveBytesBetweenBuffers(vararg contents: String): List<Int> {
        val expected = StringBuilder()
        val buffer = RealBuffer()
        for (s in contents) {
            val source = RealBuffer()
            source.writeUtf8(s)
            buffer.transferFrom(source)
            expected.append(s)
        }
        val segmentSizes = segmentSizes(buffer)
        assertEquals(expected.toString(), buffer.readUtf8(expected.length.toLong()))
        return segmentSizes
    }

    /** The big part of source's first segment is being moved.  */
    @Test
    fun writeSplitSourceBufferLeft() {
        val writeSize = Segment.SIZE / 2 + 1

        val sink = RealBuffer()
        sink.writeUtf8('b'.repeat(Segment.SIZE - 10))

        val source = RealBuffer()
        source.writeUtf8('a'.repeat(Segment.SIZE * 2))
        sink.write(source, writeSize.toLong())

        assertEquals(listOf(Segment.SIZE - 10, writeSize), segmentSizes(sink))
        assertEquals(listOf(Segment.SIZE - writeSize, Segment.SIZE), segmentSizes(source))
    }

    /** The big part of source's first segment is staying put.  */
    @Test
    fun writeSplitSourceBufferRight() {
        val writeSize = Segment.SIZE / 2 - 1

        val sink = RealBuffer()
        sink.writeUtf8('b'.repeat(Segment.SIZE - 10))

        val source = RealBuffer()
        source.writeUtf8('a'.repeat(Segment.SIZE * 2))
        sink.write(source, writeSize.toLong())

        assertEquals(listOf(Segment.SIZE - 10, writeSize), segmentSizes(sink))
        assertEquals(listOf(Segment.SIZE - writeSize, Segment.SIZE), segmentSizes(source))
    }

    @Test
    fun writePrefixDoesntSplit() {
        val sink = RealBuffer()
        sink.writeUtf8('b'.repeat(10))

        val source = RealBuffer()
        source.writeUtf8('a'.repeat(Segment.SIZE * 2))
        sink.write(source, 20)

        assertEquals(listOf(30), segmentSizes(sink))
        assertEquals(listOf(Segment.SIZE - 20, Segment.SIZE), segmentSizes(source))
        assertEquals(30, sink.byteSize())
        assertEquals((Segment.SIZE * 2 - 20).toLong(), source.byteSize())
    }

    @Test
    fun writePrefixDoesntSplitButRequiresCompact() {
        val sink = RealBuffer()
        sink.writeUtf8('b'.repeat(Segment.SIZE - 10)) // limit = size - 10
        sink.readUtf8((Segment.SIZE - 20).toLong()) // pos = size = 20

        val source = RealBuffer()
        source.writeUtf8('a'.repeat(Segment.SIZE * 2))
        sink.write(source, 20)

        assertEquals(listOf(30), segmentSizes(sink))
        assertEquals(listOf(Segment.SIZE - 20, Segment.SIZE), segmentSizes(source))
        assertEquals(30, sink.byteSize())
        assertEquals((Segment.SIZE * 2 - 20).toLong(), source.byteSize())
    }

    @Test
    fun writeSourceWithNegativeNumberOfBytes() {
        val sink = RealBuffer()
        val source: Source = RealBuffer()

        assertFailsWith<IllegalArgumentException> { sink.write(source, -1L) }
    }

    @Test
    fun moveAllRequestedBytesWithRead() {
        val sink = RealBuffer()
        sink.writeUtf8('a'.repeat(10))

        val source = RealBuffer()
        source.writeUtf8('b'.repeat(15))

        assertEquals(10, source.readAtMostTo(sink, 10))
        assertEquals(20, sink.byteSize())
        assertEquals(5, source.byteSize())
        assertEquals('a'.repeat(10) + 'b'.repeat(10), sink.readUtf8(20))
    }

    @Test
    fun moveFewerThanRequestedBytesWithRead() {
        val sink = RealBuffer()
        sink.writeUtf8('a'.repeat(10))

        val source = RealBuffer()
        source.writeUtf8('b'.repeat(20))

        assertEquals(20, source.readAtMostTo(sink, 25))
        assertEquals(30, sink.byteSize())
        assertEquals(0, source.byteSize())
        assertEquals('a'.repeat(10) + 'b'.repeat(20), sink.readUtf8(30))
    }

    @Test
    fun indexOfWithOffset() {
        val buffer = RealBuffer()
        val halfSegment = Segment.SIZE / 2
        buffer.writeUtf8('a'.repeat(halfSegment))
        buffer.writeUtf8('b'.repeat(halfSegment))
        buffer.writeUtf8('c'.repeat(halfSegment))
        buffer.writeUtf8('d'.repeat(halfSegment))
        assertEquals(0, buffer.indexOf('a'.code.toByte(), 0))
        assertEquals((halfSegment - 1).toLong(), buffer.indexOf('a'.code.toByte(), (halfSegment - 1).toLong()))
        assertEquals(halfSegment.toLong(), buffer.indexOf('b'.code.toByte(), (halfSegment - 1).toLong()))
        assertEquals((halfSegment * 2).toLong(), buffer.indexOf('c'.code.toByte(), (halfSegment - 1).toLong()))
        assertEquals((halfSegment * 3).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment - 1).toLong()))
        assertEquals((halfSegment * 3).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment * 2).toLong()))
        assertEquals((halfSegment * 3).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment * 3).toLong()))
        assertEquals((halfSegment * 4 - 1).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment * 4 - 1).toLong()))
    }

    @Test
    fun byteAt() {
        val buffer = RealBuffer()
        buffer.writeUtf8("a")
        buffer.writeUtf8('b'.repeat(Segment.SIZE))
        buffer.writeUtf8("c")
        assertEquals('a'.code.toLong(), buffer[0].toLong())
        assertEquals('a'.code.toLong(), buffer[0].toLong()) // getByte doesn't mutate!
        assertEquals('c'.code.toLong(), buffer[buffer.byteSize() - 1].toLong())
        assertEquals('b'.code.toLong(), buffer[buffer.byteSize() - 2].toLong())
        assertEquals('b'.code.toLong(), buffer[buffer.byteSize() - 3].toLong())
    }

    @Test
    fun getByteOfEmptyBuffer() {
        val buffer = RealBuffer()
        assertFailsWith<IndexOutOfBoundsException> {
            buffer[0]
        }
    }

    @Test
    fun getByteByInvalidIndex() {
        val buffer = RealBuffer().also { it.write(ByteArray(10)) }

        assertFailsWith<IndexOutOfBoundsException> { buffer[-1] }
        assertFailsWith<IndexOutOfBoundsException> { buffer[buffer.byteSize()] }
    }

    @Test
    fun writePrefixToEmptyBuffer() {
        val sink = RealBuffer()
        val source = RealBuffer()
        source.writeUtf8("abcd")
        sink.write(source, 2)
        assertEquals("ab", sink.readUtf8(2))
    }

    // Buffer don't override equals and hashCode
    @Test
    fun equalsAndHashCode() {
        val a = RealBuffer().also { it.writeUtf8("dog") }
        assertEquals(a, a)

        val b = RealBuffer().also { it.writeUtf8("hotdog") }
        assertTrue(a != b)

        b.readUtf8(3) // Leaves b containing 'dog'.
        assertTrue(a != b)
    }

    /**
     * When writing data that's already buffered, there's no reason to page the
     * data by segment.
     */
    @Test
    fun readAllWritesAllSegmentsAtOnce() {
        val write1 = RealBuffer()
        write1.writeUtf8(
            'a'.repeat(Segment.SIZE) +
                    'b'.repeat(Segment.SIZE) +
                    'c'.repeat(Segment.SIZE)
        )

        val source = RealBuffer()
        source.writeUtf8(
            'a'.repeat(Segment.SIZE) +
                    'b'.repeat(Segment.SIZE) +
                    'c'.repeat(Segment.SIZE)
        )

        val mockSink = MockSink()

        assertEquals((Segment.SIZE * 3).toLong(), source.transferTo(mockSink))
        assertEquals(0, source.byteSize())
        mockSink.assertLog("write($write1, ${write1.byteSize()})")
    }

    @Test
    fun writeAllMultipleSegments() {
        val source = RealBuffer().also { it.writeUtf8('a'.repeat(Segment.SIZE * 3)) }
        val sink = RealBuffer()

        assertEquals((Segment.SIZE * 3).toLong(), sink.transferFrom(source))
        assertEquals(0, source.byteSize())
        assertEquals('a'.repeat(Segment.SIZE * 3), sink.readUtf8())
    }

    @Test
    fun copyTo() {
        val source = RealBuffer()
        source.writeUtf8("party")

        val target = RealBuffer()
        source.copyTo(target, 1, 3)

        assertEquals("art", target.readUtf8())
        assertEquals("party", source.readUtf8())
    }

    @Test
    fun copyToAll() {
        val source = RealBuffer()
        source.writeUtf8("hello")

        val target = RealBuffer()
        source.copyTo(target)

        assertEquals("hello", source.readUtf8())
        assertEquals("hello", target.readUtf8())
    }

    @Test
    fun copyToWithOnlyStartIndex() {
        val source = RealBuffer()
        source.writeUtf8("hello")

        val target = RealBuffer()
        source.copyTo(target, 1, source.byteSize() - 1)

        assertEquals("hello", source.readUtf8())
        assertEquals("ello", target.readUtf8())
    }

    @Test
    fun copyToWithOnlyEndIndex() {
        val source = RealBuffer()
        source.writeUtf8("hello")

        val target = RealBuffer()
        source.copyTo(target, 0, 1)
        assertEquals("hello", source.readUtf8())
        assertEquals("h", target.readUtf8())
    }

    @Test
    fun copyToOnSegmentBoundary() {
        val aStr = 'a'.repeat(Segment.SIZE)
        val bs = 'b'.repeat(Segment.SIZE)
        val cs = 'c'.repeat(Segment.SIZE)
        val ds = 'd'.repeat(Segment.SIZE)

        val source = RealBuffer()
        source.writeUtf8(aStr)
        source.writeUtf8(bs)
        source.writeUtf8(cs)

        val target = RealBuffer()
        target.writeUtf8(ds)

        source.copyTo(target, aStr.length.toLong(), (bs.length + cs.length).toLong())
        assertEquals(ds + bs + cs, target.readUtf8())
    }

    @Test
    fun copyToOffSegmentBoundary() {
        val aStr = 'a'.repeat(Segment.SIZE - 1)
        val bs = 'b'.repeat(Segment.SIZE + 2)
        val cs = 'c'.repeat(Segment.SIZE - 4)
        val ds = 'd'.repeat(Segment.SIZE + 8)

        val source = RealBuffer()
        source.writeUtf8(aStr)
        source.writeUtf8(bs)
        source.writeUtf8(cs)

        val target = RealBuffer()
        target.writeUtf8(ds)

        source.copyTo(target, aStr.length.toLong(), (bs.length + cs.length).toLong())
        assertEquals(ds + bs + cs, target.readUtf8())
    }

    @Test
    fun copyToSourceAndTargetCanBeTheSame() {
        val aStr = 'a'.repeat(Segment.SIZE)
        val bs = 'b'.repeat(Segment.SIZE)

        val source = RealBuffer()
        source.writeUtf8(aStr)
        source.writeUtf8(bs)

        source.copyTo(source, 0, source.byteSize())
        assertEquals(aStr + bs + aStr + bs, source.readUtf8())
    }

    @Test
    fun copyToEmptySource() {
        val source = RealBuffer()
        val target = RealBuffer().also { it.writeUtf8("aaa") }
        source.copyTo(target, 0L, 0L)
        assertEquals("", source.readUtf8())
        assertEquals("aaa", target.readUtf8())
    }

    @Test
    fun copyToEmptyTarget() {
        val source = RealBuffer().also { it.writeUtf8("aaa") }
        val target = RealBuffer()
        source.copyTo(target, 0L, 3L)
        assertEquals("aaa", source.readUtf8())
        assertEquals("aaa", target.readUtf8())
    }

    @Test
    fun cloneDoesNotObserveWritesToOriginal() {
        val original = RealBuffer()
        val clone: Buffer = original.copy()
        original.writeUtf8("abc")
        assertEquals(0, clone.byteSize())
    }

    @Test
    fun cloneDoesNotObserveReadsFromOriginal() {
        val original = RealBuffer()
        original.writeUtf8("abc")
        val clone: Buffer = original.copy()
        assertEquals("abc", original.readUtf8(3))
        assertEquals(3, clone.byteSize())
        assertEquals("ab", clone.readUtf8(2))
    }

    @Test
    fun originalDoesNotObserveWritesToClone() {
        val original = RealBuffer()
        val clone: Buffer = original.copy()
        clone.writeUtf8("abc")
        assertEquals(0, original.byteSize())
    }

    @Test
    fun originalDoesNotObserveReadsFromClone() {
        val original = RealBuffer()
        original.writeUtf8("abc")
        val clone: Buffer = original.copy()
        assertEquals("abc", clone.readUtf8(3))
        assertEquals(3, original.byteSize())
        assertEquals("ab", original.readUtf8(2))
    }

    @Test
    fun cloneMultipleSegments() {
        val original = RealBuffer()
        original.writeUtf8("a".repeat(SEGMENT_SIZE * 3))
        val clone: Buffer = original.copy()
        original.writeUtf8("b".repeat(SEGMENT_SIZE * 3))
        clone.writeUtf8("c".repeat(SEGMENT_SIZE * 3))

        assertEquals(
            "a".repeat(SEGMENT_SIZE * 3) + "b".repeat(SEGMENT_SIZE * 3),
            original.readUtf8((SEGMENT_SIZE * 6).toLong())
        )
        assertEquals(
            "a".repeat(SEGMENT_SIZE * 3) + "c".repeat(SEGMENT_SIZE * 3),
            clone.readUtf8((SEGMENT_SIZE * 6).toLong())
        )
    }

    @Test
    fun readAndWriteToSelf() {
        val buffer = RealBuffer().also { it.writeByte(1) }
        val src: Source = buffer
        val dst: Sink = buffer

        assertFailsWith<IllegalArgumentException> { src.transferTo(dst) }
        assertFailsWith<IllegalArgumentException> { dst.transferFrom(src) }
        assertFailsWith<IllegalArgumentException> { src.readAtMostTo(buffer, 1) }
        assertFailsWith<IllegalArgumentException> { src.readTo(dst, 1) }
        assertFailsWith<IllegalArgumentException> { dst.write(buffer, 1) }
        assertFailsWith<IllegalArgumentException> { dst.write(src, 1) }
    }

    @Test
    fun transferCopy() {
        val buffer = RealBuffer().also { it.writeByte(42) }
        val copy = buffer.copy()
        copy.transferTo(buffer)
        assertArrayEquals(byteArrayOf(42, 42), buffer.readByteArray())
    }

    @Test
    fun snapshot() {
        val buffer = RealBuffer()
        assertEquals(ByteString.EMPTY, buffer.snapshot())
        buffer.writeUtf8("hello")
        assertEquals("hello".encodeToByteString(), buffer.snapshot())
        buffer.clear()
        assertEquals(ByteString.EMPTY, buffer.snapshot())
    }

    @Test
    fun copyToSkippingSegments() {
        val source = RealBuffer()
        source.writeUtf8("a".repeat(SEGMENT_SIZE * 2))
        source.writeUtf8("b".repeat(SEGMENT_SIZE * 2))
        val out = ByteArrayOutputStream()
        source.copyTo(out, SEGMENT_SIZE * 2 + 1L, 3L)
        assertEquals("bbb", out.toString())
        assertEquals(
            "a".repeat(SEGMENT_SIZE * 2) + "b".repeat(SEGMENT_SIZE * 2),
            source.readUtf8(SEGMENT_SIZE * 4L)
        )
    }

    @Test
    fun copyToStream() {
        val buffer = RealBuffer().also { it.writeUtf8("hello, world!") }
        val out = ByteArrayOutputStream()
        buffer.copyTo(out)
        val outString = String(out.toByteArray(), Charsets.UTF_8)
        assertEquals("hello, world!", outString)
        assertEquals("hello, world!", buffer.readUtf8())
    }

    @Test
    fun writeToSpanningSegments() {
        val buffer = RealBuffer()
        buffer.writeUtf8("a".repeat(SEGMENT_SIZE * 2))
        buffer.writeUtf8("b".repeat(SEGMENT_SIZE * 2))
        val out = ByteArrayOutputStream()
        buffer.skip(10)
        buffer.readTo(out, SEGMENT_SIZE * 3L)
        assertEquals("a".repeat(SEGMENT_SIZE * 2 - 10) + "b".repeat(SEGMENT_SIZE + 10), out.toString())
        assertEquals("b".repeat(SEGMENT_SIZE - 10), buffer.readUtf8(buffer.byteSize()))
    }

    @Test
    fun writeToStream() {
        val buffer = RealBuffer().also { it.writeUtf8("hello, world!") }
        val out = ByteArrayOutputStream()
        buffer.readTo(out)
        val outString = String(out.toByteArray(), Charsets.UTF_8)
        assertEquals("hello, world!", outString)
        assertEquals(0, buffer.byteSize())
    }

    @Test
    fun readFromStream() {
        val input: InputStream = ByteArrayInputStream("hello, world!".toByteArray(Charsets.UTF_8))
        val buffer = RealBuffer()
        buffer.transferFrom(input)
        val out = buffer.readUtf8()
        assertEquals("hello, world!", out)
    }

    @Test
    fun readFromSpanningSegments() {
        val input: InputStream = ByteArrayInputStream("hello, world!".toByteArray(Charsets.UTF_8))
        val buffer = RealBuffer().also { it.writeUtf8("a".repeat(SEGMENT_SIZE - 10)) }
        buffer.transferFrom(input)
        val out = buffer.readUtf8()
        assertEquals("a".repeat(SEGMENT_SIZE - 10) + "hello, world!", out)
    }

    @Test
    fun readFromStreamWithCount() {
        val input: InputStream = ByteArrayInputStream("hello, world!".toByteArray(Charsets.UTF_8))
        val buffer = RealBuffer()
        buffer.write(input, 10)
        val out = buffer.readUtf8()
        assertEquals("hello, wor", out)
    }

    @Test
    fun readFromStreamThrowsEOFOnExhaustion() {
        val input = ByteArrayInputStream("hello, world!".toByteArray(Charsets.UTF_8))
        val buffer = RealBuffer()
        assertFailsWith<JayoEOFException> {
            buffer.write(input, input.available() + 1L)
        }
    }

    @Test
    fun readFromStreamWithNegativeBytesCount() {
        assertFailsWith<IllegalArgumentException> {
            RealBuffer().write(ByteArrayInputStream(ByteArray(1)), -1)
        }
    }

    @Test
    fun readFromDoesNotLeaveEmptyTailSegment() {
        val buffer = RealBuffer()
        buffer.transferFrom(ByteArrayInputStream(ByteArray(SEGMENT_SIZE)))
        assertNoEmptySegments(buffer)
    }

    @Test
    fun bufferInputStreamByteByByte() {
        val source = RealBuffer()
        source.writeUtf8("abc")
        val input: InputStream = source.asInputStream()
        assertEquals(3, input.available())
        assertEquals('a'.code, input.read())
        assertEquals('b'.code, input.read())
        assertEquals('c'.code, input.read())
        assertEquals(-1, input.read())
        assertEquals(0, input.available())
    }

    @Test
    fun bufferInputStreamBulkReads() {
        val source = RealBuffer()
        source.writeUtf8("abc")
        val byteArray = ByteArray(4)
        Arrays.fill(byteArray, (-5).toByte())
        val input: InputStream = source.asInputStream()
        assertEquals(3, input.read(byteArray))
        assertEquals("[97, 98, 99, -5]", byteArray.contentToString())
        Arrays.fill(byteArray, (-7).toByte())
        assertEquals(-1, input.read(byteArray))
        assertEquals("[-7, -7, -7, -7]", byteArray.contentToString())
    }

    @Test
    fun copyToOutputStreamWithStartIndex() {
        val source = RealBuffer()
        source.writeUtf8("party")

        val target = RealBuffer()
        source.copyTo(target.asOutputStream(), 2)
        assertEquals("rty", target.readUtf8())
        assertEquals("party", source.readUtf8())
    }

    @Test
    fun copyToOutputStreamWithEndIndex() {
        val source = RealBuffer()
        source.writeUtf8("party")

        val target = RealBuffer()
        source.copyTo(target.asOutputStream(), 0, 3)
        assertEquals("par", target.readUtf8())
        assertEquals("party", source.readUtf8())
    }

    @Test
    fun copyToOutputStreamWithIndices() {
        val source = RealBuffer()
        source.writeUtf8("party")

        val target = RealBuffer()
        source.copyTo(target.asOutputStream(), 1, 3)
        assertEquals("art", target.readUtf8())
        assertEquals("party", source.readUtf8())
    }

    @Test
    fun copyToOutputStreamWithEmptyRange() {
        val source = RealBuffer()
        source.writeUtf8("hello")

        val target = RealBuffer()
        source.copyTo(target.asOutputStream(), 1, 0)
        assertEquals("hello", source.readUtf8())
        assertEquals("", target.readUtf8())
    }

    @Test
    fun readToOutputStream() {
        val source = RealBuffer()
        source.writeUtf8("party")

        val target = RealBuffer()
        source.readTo(target.asOutputStream())
        assertEquals("party", target.readUtf8())
        assertEquals("", source.readUtf8())
    }

    @Test
    fun readToOutputStreamWithByteCount() {
        val source = RealBuffer()
        source.writeUtf8("party")

        val target = RealBuffer()
        source.readTo(target.asOutputStream(), 3)
        assertEquals("par", target.readUtf8())
        assertEquals("ty", source.readUtf8())
    }

    @Test
    fun readEmptyBufferToByteBuffer() {
        val bb = ByteBuffer.allocate(128)
        val buffer = RealBuffer()

        assertEquals(-1, buffer.readAtMostTo(bb))
    }
}
