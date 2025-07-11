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
import jayo.crypto.JdkDigest
import jayo.crypto.JdkHmac
import jayo.JayoEOFException
import jayo.bytestring.ByteString
import jayo.bytestring.encodeToUtf8
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
    fun closeIsOpenAreNop() {
        val buffer = RealBuffer()
        assertThat(buffer.isOpen).isTrue()
        buffer.close()
        assertThat(buffer.isOpen).isTrue()
    }

    @Test
    fun copyToBuffer() {
        val reader = RealBuffer()
        reader.write("party".encodeToUtf8())

        val target = RealBuffer()
        reader.copyTo(target)
        assertThat(target.readByteString().decodeToString()).isEqualTo("party")
        assertThat(reader.readByteString().decodeToString()).isEqualTo("party")
    }

    @Test
    fun copyToBufferWithOffset() {
        val reader = RealBuffer()
        reader.write("party".encodeToUtf8())

        val target = RealBuffer()
        reader.copyTo(target, 2L)
        assertThat(target.readByteString().decodeToString()).isEqualTo("rty")
        assertThat(reader.readByteString().decodeToString()).isEqualTo("party")
    }

    @Test
    fun copyToBufferWithByteCount() {
        val reader = RealBuffer()
        reader.write("party".encodeToUtf8())

        val target = RealBuffer()
        reader.copyTo(target, 0L, 3L)
        assertThat(target.readByteString().decodeToString()).isEqualTo("par")
        assertThat(reader.readByteString().decodeToString()).isEqualTo("party")
    }

    @Test
    fun copyToBufferWithOffsetAndByteCount() {
        val reader = RealBuffer()
        reader.write("party".encodeToUtf8())

        val target = RealBuffer()
        reader.copyTo(target, 1L, 3L)
        assertThat(target.readByteString().decodeToString()).isEqualTo("art")
        assertThat(reader.readByteString().decodeToString()).isEqualTo("party")
    }

    @Test
    fun completeSegmentByteCountOnEmptyBuffer() {
        val buffer = RealBuffer()
        assertThat(buffer.completeSegmentByteCount()).isEqualTo(0)
    }

    @Test
    fun completeSegmentByteCountOnBufferWithFullSegments() {
        val buffer = RealBuffer()
        buffer.write("a".repeat(Segment.SIZE * 4))
        assertThat(buffer.completeSegmentByteCount()).isEqualTo(Segment.SIZE * 4L)
    }

    @Test
    fun completeSegmentByteCountOnBufferWithIncompleteTailSegment() {
        val buffer = RealBuffer()
        buffer.write("a".repeat(Segment.SIZE * 4 - 10))
        assertThat(buffer.completeSegmentByteCount()).isEqualTo(Segment.SIZE * 3L)
    }

    @Test
    fun testHash() {
        val buffer = RealBuffer().apply { write("Kevin".encodeToUtf8()) }
        with(buffer) {
            assertThat(hash(JdkDigest.MD5).hex()).isEqualTo("f1cd318e412b5f7226e5f377a9544ff7")
            assertThat(hash(JdkDigest.SHA_1).hex()).isEqualTo("e043899daa0c7add37bc99792b2c045d6abbc6dc")
            assertThat(hash(JdkDigest.SHA_224).hex())
                .isEqualTo("35e1fa1b770f696e95666b66ff1d040ab4a0421dae005d048a5647c5")
            assertThat(hash(JdkDigest.SHA_256).hex())
                .isEqualTo("0e4dd66217fc8d2e298b78c8cd9392870dcd065d0ff675d0edff5bcd227837e9")
            assertThat(hash(JdkDigest.SHA_384).hex())
                .isEqualTo(
                    "45824b5d3cc0dd249144875b15833d117c11fa775e06ec1fe19988b3347395013a49d78c8056653d06ba196c1a94a160"
                )
            assertThat(hash(JdkDigest.SHA_512).hex()).isEqualTo(
                "483676b93c4417198b465083d196ec6a9fab8d004515874b8ff47e041f5f56303cc08179625030b8b5b721c09149a18f0f5" +
                        "9e64e7ae099518cea78d3d83167e1"
            )
            assertThat(hash(JdkDigest.SHA_512_224).hex())
                .isEqualTo("730ceb5e4e968eba3aa3fe8aeaf6e08761b94917db83a44a64e20159")
            assertThat(hash(JdkDigest.SHA_512_256).hex())
                .isEqualTo("4f023d8f32c539d712f9f2dfb3719fbc4c980c27abb7382e988bf4fbd4f1caa9")
            assertThat(hash(JdkDigest.SHA3_224).hex())
                .isEqualTo("8e1cb3e2802de9986b6624bac295fa507ccdc6efe7edaa22122cd120")
            assertThat(hash(JdkDigest.SHA3_256).hex())
                .isEqualTo("95547a916f3e4c214fa80a3d78e86faa92e3f6703f5e713c1d176e029116ce6f")
            assertThat(hash(JdkDigest.SHA3_384).hex())
                .isEqualTo(
                    "451ceac3054d4a5859a79bfbf6e6a1c2223f5fcecb883333d98eff63c84f2bd1c797bf7fe449e000f488173bd09daf19"
                )
            assertThat(hash(JdkDigest.SHA3_512).hex()).isEqualTo(
                "d925dc48347d016fd1dc3907378c95c0c860a75332d2e673f743d970bbea89d1e77a07a4a9a64290146ba273bb262d1dd2" +
                        "37bace6761293a75dbf39f72815da8"
            )
        }
    }

    @Test
    fun testHMac() {
        val buffer = RealBuffer().apply { write("Kevin".encodeToUtf8()) }
        val key = "Brandon".encodeToUtf8()
        with(buffer) {
            assertThat(hmac(JdkHmac.HMAC_MD5, key).hex()).isEqualTo("cd5478da9993e894de891a6d680a88fb")
            assertThat(hmac(JdkHmac.HMAC_SHA_1, key).hex()).isEqualTo("46eedc331e6f92c801808fd5bfc5424afe659402")
            assertThat(hmac(JdkHmac.HMAC_SHA_224, key).hex())
                .isEqualTo("3f1ade07df5bfbb4be786f93ca898fb5993e7af71577f13a2ebbc253")
            assertThat(hmac(JdkHmac.HMAC_SHA_256, key).hex())
                .isEqualTo("5eaf69955f51d61665e28ce16acbf7e5e1b6a2d1f62b3b4bad1aa0913a349e77")
            assertThat(hmac(JdkHmac.HMAC_SHA_384, key).hex())
                .isEqualTo(
                    "a62272c16f97153b5ce36a6f61f999d925efeed91bf9aac76799ef9c02991ec644f4d8a332275278f78478d5cb9ae6b8"
                )
            assertThat(hmac(JdkHmac.HMAC_SHA_512, key).hex()).isEqualTo(
                "06edf87929601bd8a1124d996b774881e55e36cf70c58e26d44c1a7bf596ba3b8e1d8b018275791a441a0b5edb86abf394bd" +
                        "8081a6da8e51e39521b346780dde"
            )
            assertThat(hmac(JdkHmac.HMAC_SHA_512_224, key).hex())
                .isEqualTo("0692adbe44b74f90dc0ee4e8f280a86ba190cb783f78c159bea06a9e")
            assertThat(hmac(JdkHmac.HMAC_SHA_512_256, key).hex())
                .isEqualTo("552d369db150748af9db5c0d621bce8ba7c86807ea5293ed10a2c3f7129b410d")
            assertThat(hmac(JdkHmac.HMAC_SHA3_224, key).hex())
                .isEqualTo("0e6451625aaa6b5c11bd664a0dddd8883a17980be6b3440532719c75")
            assertThat(hmac(JdkHmac.HMAC_SHA3_256, key).hex())
                .isEqualTo("5e772f25f1e3ff180cfb8b8a20f31afc43b1fbf86d01c5a74b28608801d53a2e")
            assertThat(hmac(JdkHmac.HMAC_SHA3_384, key).hex())
                .isEqualTo(
                    "0fdc46f226ad27c730a36adfd23c7f2a82dcafdd92fa546730391ef9e2595b73b82f489e0b3e638a2d42d2191c69b031"
                )
            assertThat(hmac(JdkHmac.HMAC_SHA3_512, key).hex()).isEqualTo(
                "9b9f9fc58cb6ae835a74d4d9ab51e1583027130315b5aaf497dd51b6dacbae7f9e141a2ecdcbfce337f031f2ce83a58fdfc" +
                        "ee27a373ff408f792326a69a900cc"
            )
        }
    }

    @Test
    fun getByte() {
        val actual = RealBuffer().write("abc")
        assertThat(actual.getByte(0)).isEqualTo('a'.code.toByte())
        assertThat(actual.getByte(1)).isEqualTo('b'.code.toByte())
        assertThat(actual.getByte(2)).isEqualTo('c'.code.toByte())
        assertThatThrownBy { actual.getByte(-1) }
            .isInstanceOf(IndexOutOfBoundsException::class.java)
        assertThatThrownBy { actual.getByte(3) }
            .isInstanceOf(IndexOutOfBoundsException::class.java)
    }

    @Test
    fun copyToOutputStream() {
        val reader = RealBuffer()
        reader.write("party")

        val target = RealBuffer()
        reader.copyTo(target.asOutputStream())
        assertThat(target.readString()).isEqualTo("party")
        assertThat(reader.readString()).isEqualTo("party")
    }

    @Test
    fun copyToOutputStreamWithOffset() {
        val reader = RealBuffer()
        reader.write("party")

        val target = RealBuffer()
        reader.copyTo(target.asOutputStream(), 2L)
        assertThat(target.readString()).isEqualTo("rty")
        assertThat(reader.readString()).isEqualTo("party")
    }

    @Test
    fun copyToOutputStreamWithByteCount() {
        val reader = RealBuffer()
        reader.write("party")

        val target = RealBuffer()
        reader.copyTo(target.asOutputStream(), 0L, 3L)
        assertThat(target.readString()).isEqualTo("par")
        assertThat(reader.readString()).isEqualTo("party")
    }

    @Test
    fun copyToOutputStreamWithOffsetAndByteCount() {
        val reader = RealBuffer()
        reader.write("party")

        val target = RealBuffer()
        reader.copyTo(target.asOutputStream(), 1L, 3L)
        assertThat(target.readString()).isEqualTo("art")
        assertThat(reader.readString()).isEqualTo("party")
    }

    @Test
    fun copyToOutputStreamMultiSegments() {
        val reader = RealBuffer()
        reader.write(
            "a".repeat(Segment.SIZE + 5) +
                    "b".repeat(Segment.SIZE) + 5
        )

        val target = RealBuffer()
        reader.copyTo(target.asOutputStream(), 5L, Segment.SIZE.toLong())
        assertThat(target.readString()).isEqualTo("a".repeat(Segment.SIZE))
        assertThat(reader.readString()).isEqualTo(
            "a".repeat(Segment.SIZE + 5) +
                    "b".repeat(Segment.SIZE) + 5
        )
    }

    @Test
    fun writeToOutputStream() {
        val reader = RealBuffer()
        reader.write("party")

        val target = RealBuffer()
        reader.readTo(target.asOutputStream())
        assertThat(target.readString()).isEqualTo("party")
        assertThat(reader.readString()).isEqualTo("")
    }

    @Test
    fun writeToOutputStreamWithByteCount() {
        val reader = RealBuffer()
        reader.write("party")

        val target = RealBuffer()
        reader.readTo(target.asOutputStream(), 3L)
        assertThat(target.readString()).isEqualTo("par")
        assertThat(reader.readString()).isEqualTo("ty")
    }

    @Test
    fun writeToOutputStreamMultiSegments() {
        val reader = RealBuffer()
        reader.write(
            "a".repeat(Segment.SIZE + 5) +
                    "b".repeat(Segment.SIZE + 5)
        )

        val target = RealBuffer()
        reader.readTo(target.asOutputStream(), Segment.SIZE.toLong())
        assertThat(target.readString()).isEqualTo("a".repeat(Segment.SIZE))
        assertThat(reader.readString()).isEqualTo("aaaaa" + "b".repeat(Segment.SIZE + 5))
    }

    @Test
    fun readAndWriteUtf8() {
        val buffer = RealBuffer()
        buffer.write("ab")
        assertEquals(2, buffer.bytesAvailable())
        buffer.write("cdef")
        assertEquals(6, buffer.bytesAvailable())
        assertEquals("abcd", buffer.readString(4))
        assertEquals(2, buffer.bytesAvailable())
        assertEquals("ef", buffer.readString(2))
        assertEquals(0, buffer.bytesAvailable())
        assertFailsWith<JayoEOFException> {
            buffer.readString(1)
        }
    }

    @Test
    fun bufferToString() {
        assertEquals("Buffer(size=0)", RealBuffer().toString())

        assertEquals(
            "Buffer(size=10 hex=610d0a620a630d645c65)",
            RealBuffer().also { it.write("a\r\nb\nc\rd\\e") }.toString()
        )

        assertEquals(
            "Buffer(size=11 hex=547972616e6e6f73617572)",
            RealBuffer().also { it.write("Tyrannosaur") }.toString()
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
        buffer.write('a'.repeat(1000))
        buffer.write('b'.repeat(2500))
        buffer.write('c'.repeat(5000))
        buffer.write('d'.repeat(10000))
        buffer.write('e'.repeat(25000))
        buffer.write('f'.repeat(50000))

        assertEquals('a'.repeat(999), buffer.readString(999)) // a...a
        assertEquals("a" + 'b'.repeat(2500) + "c", buffer.readString(2502)) // ab...bc
        assertEquals('c'.repeat(4998), buffer.readString(4998)) // c...c
        assertEquals("c" + 'd'.repeat(10000) + "e", buffer.readString(10002)) // cd...de
        assertEquals('e'.repeat(24998), buffer.readString(24998)) // e...e
        assertEquals("e" + 'f'.repeat(50000), buffer.readString(50001)) // ef...f
        assertEquals(0, buffer.bytesAvailable())
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
            val reader = RealBuffer()
            reader.write(s)
            buffer.transferFrom(reader)
            expected.append(s)
        }
        val segmentSizes = segmentSizes(buffer)
        assertEquals(expected.toString(), buffer.readString(expected.length.toLong()))
        return segmentSizes
    }

    /** The big part of reader's first segment is being moved.  */
    @Test
    fun writeSplitReaderBufferLeft() {
        val writeSize = Segment.SIZE / 2 + 1

        val writer = RealBuffer()
        writer.write('b'.repeat(Segment.SIZE - 10))

        val reader = RealBuffer()
        reader.write('a'.repeat(Segment.SIZE * 2))
        writer.write(reader, writeSize.toLong())

        assertEquals(listOf(Segment.SIZE - 10, writeSize), segmentSizes(writer))
        assertEquals(listOf(Segment.SIZE - writeSize, Segment.SIZE), segmentSizes(reader))
    }

    /** The big part of reader's first segment is staying put.  */
    @Test
    fun writeSplitReaderBufferRight() {
        val writeSize = Segment.SIZE / 2 - 1

        val writer = RealBuffer()
        writer.write('b'.repeat(Segment.SIZE - 10))

        val reader = RealBuffer()
        reader.write('a'.repeat(Segment.SIZE * 2))
        writer.write(reader, writeSize.toLong())

        assertEquals(listOf(Segment.SIZE - 10, writeSize), segmentSizes(writer))
        assertEquals(listOf(Segment.SIZE - writeSize, Segment.SIZE), segmentSizes(reader))
    }

    @Test
    fun writePrefixDoesntSplit() {
        val writer = RealBuffer()
        writer.write('b'.repeat(10))

        val reader = RealBuffer()
        reader.write('a'.repeat(Segment.SIZE * 2))
        writer.write(reader, 20)

        assertEquals(listOf(30), segmentSizes(writer))
        assertEquals(listOf(Segment.SIZE - 20, Segment.SIZE), segmentSizes(reader))
        assertEquals(30, writer.bytesAvailable())
        assertEquals((Segment.SIZE * 2 - 20).toLong(), reader.bytesAvailable())
    }

    @Test
    fun writePrefixDoesntSplitButRequiresCompact() {
        val writer = RealBuffer()
        writer.write('b'.repeat(Segment.SIZE - 10)) // limit = size - 10
        writer.readString((Segment.SIZE - 20).toLong()) // pos = size = 20

        val reader = RealBuffer()
        reader.write('a'.repeat(Segment.SIZE * 2))
        writer.write(reader, 20)

        assertEquals(listOf(30), segmentSizes(writer))
        assertEquals(listOf(Segment.SIZE - 20, Segment.SIZE), segmentSizes(reader))
        assertEquals(30, writer.bytesAvailable())
        assertEquals((Segment.SIZE * 2 - 20).toLong(), reader.bytesAvailable())
    }

    @Test
    fun writeReaderWithNegativeNumberOfBytes() {
        val writer = RealBuffer()
        val reader: Reader = RealBuffer()

        assertFailsWith<IllegalArgumentException> { writer.write(reader, -1L) }
    }

    @Test
    fun moveAllRequestedBytesWithRead() {
        val writer = RealBuffer()
        writer.write('a'.repeat(10))

        val reader = RealBuffer()
        reader.write('b'.repeat(15))

        assertEquals(10, reader.readAtMostTo(writer, 10))
        assertEquals(20, writer.bytesAvailable())
        assertEquals(5, reader.bytesAvailable())
        assertEquals('a'.repeat(10) + 'b'.repeat(10), writer.readString(20))
    }

    @Test
    fun moveFewerThanRequestedBytesWithRead() {
        val writer = RealBuffer()
        writer.write('a'.repeat(10))

        val reader = RealBuffer()
        reader.write('b'.repeat(20))

        assertEquals(20, reader.readAtMostTo(writer, 25))
        assertEquals(30, writer.bytesAvailable())
        assertEquals(0, reader.bytesAvailable())
        assertEquals('a'.repeat(10) + 'b'.repeat(20), writer.readString(30))
    }

    @Test
    fun indexOfWithOffset() {
        val buffer = RealBuffer()
        val halfSegment = Segment.SIZE / 2
        buffer.write('a'.repeat(halfSegment))
        buffer.write('b'.repeat(halfSegment))
        buffer.write('c'.repeat(halfSegment))
        buffer.write('d'.repeat(halfSegment))
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
        buffer.write("a")
        buffer.write('b'.repeat(Segment.SIZE))
        buffer.write("c")
        assertEquals('a'.code.toLong(), buffer.getByte(0).toLong())
        assertEquals('a'.code.toLong(), buffer.getByte(0).toLong()) // getByte doesn't mutate!
        assertEquals('c'.code.toLong(), buffer.getByte(buffer.bytesAvailable() - 1).toLong())
        assertEquals('b'.code.toLong(), buffer.getByte(buffer.bytesAvailable() - 2).toLong())
        assertEquals('b'.code.toLong(), buffer.getByte(buffer.bytesAvailable() - 3).toLong())
    }

    @Test
    fun getByteOfEmptyBuffer() {
        val buffer = RealBuffer()
        assertFailsWith<IndexOutOfBoundsException> {
            buffer.getByte(0)
        }
    }

    @Test
    fun getByteByInvalidIndex() {
        val buffer = RealBuffer().also { it.write(ByteArray(10)) }

        assertFailsWith<IndexOutOfBoundsException> { buffer.getByte(-1) }
        assertFailsWith<IndexOutOfBoundsException> { buffer.getByte(buffer.bytesAvailable()) }
    }

    @Test
    fun writePrefixToEmptyBuffer() {
        val writer = RealBuffer()
        val reader = RealBuffer()
        reader.write("abcd")
        writer.write(reader, 2)
        assertEquals("ab", writer.readString(2))
    }

    // Buffer don't override equals and hashCode
    @Test
    fun equalsAndHashCode() {
        val a = RealBuffer().also { it.write("dog") }
        assertEquals(a, a)

        val b = RealBuffer().also { it.write("hotdog") }
        assertTrue(a != b)

        b.readString(3) // Leaves b containing 'dog'.
        assertTrue(a != b)
    }

    /**
     * When writing data that's already buffered, there's no reason to page the
     * data by segment.
     */
    @Test
    fun readAllWritesAllSegmentsAtOnce() {
        val write1 = RealBuffer()
        write1.write(
            'a'.repeat(Segment.SIZE) +
                    'b'.repeat(Segment.SIZE) +
                    'c'.repeat(Segment.SIZE)
        )

        val reader = RealBuffer()
        reader.write(
            'a'.repeat(Segment.SIZE) +
                    'b'.repeat(Segment.SIZE) +
                    'c'.repeat(Segment.SIZE)
        )

        val mockWriter = MockWriter()

        assertEquals((Segment.SIZE * 3).toLong(), reader.transferTo(mockWriter))
        assertEquals(0, reader.bytesAvailable())
        mockWriter.assertLog("write($write1, ${write1.bytesAvailable()})")
    }

    @Test
    fun writeAllMultipleSegments() {
        val reader = RealBuffer().also { it.write('a'.repeat(Segment.SIZE * 3)) }
        val writer = RealBuffer()

        assertEquals((Segment.SIZE * 3).toLong(), writer.transferFrom(reader))
        assertEquals(0, reader.bytesAvailable())
        assertEquals('a'.repeat(Segment.SIZE * 3), writer.readString())
    }

    @Test
    fun copyTo() {
        val reader = RealBuffer()
        reader.write("party")

        val target = RealBuffer()
        reader.copyTo(target, 1, 3)

        assertEquals("art", target.readString())
        assertEquals("party", reader.readString())
    }

    @Test
    fun copyToAll() {
        val reader = RealBuffer()
        reader.write("hello")

        val target = RealBuffer()
        reader.copyTo(target)

        assertEquals("hello", reader.readString())
        assertEquals("hello", target.readString())
    }

    @Test
    fun copyToWithOnlyStartIndex() {
        val reader = RealBuffer()
        reader.write("hello")

        val target = RealBuffer()
        reader.copyTo(target, 1, reader.bytesAvailable() - 1)

        assertEquals("hello", reader.readString())
        assertEquals("ello", target.readString())
    }

    @Test
    fun copyToWithOnlyEndIndex() {
        val reader = RealBuffer()
        reader.write("hello")

        val target = RealBuffer()
        reader.copyTo(target, 0, 1)
        assertEquals("hello", reader.readString())
        assertEquals("h", target.readString())
    }

    @Test
    fun copyToOnSegmentBoundary() {
        val aStr = 'a'.repeat(Segment.SIZE)
        val bs = 'b'.repeat(Segment.SIZE)
        val cs = 'c'.repeat(Segment.SIZE)
        val ds = 'd'.repeat(Segment.SIZE)

        val reader = RealBuffer()
        reader.write(aStr)
        reader.write(bs)
        reader.write(cs)

        val target = RealBuffer()
        target.write(ds)

        reader.copyTo(target, aStr.length.toLong(), (bs.length + cs.length).toLong())
        assertEquals(ds + bs + cs, target.readString())
    }

    @Test
    fun copyToOffSegmentBoundary() {
        val aStr = 'a'.repeat(Segment.SIZE - 1)
        val bs = 'b'.repeat(Segment.SIZE + 2)
        val cs = 'c'.repeat(Segment.SIZE - 4)
        val ds = 'd'.repeat(Segment.SIZE + 8)

        val reader = RealBuffer()
        reader.write(aStr)
        reader.write(bs)
        reader.write(cs)

        val target = RealBuffer()
        target.write(ds)

        reader.copyTo(target, aStr.length.toLong(), (bs.length + cs.length).toLong())
        assertEquals(ds + bs + cs, target.readString())
    }

    @Test
    fun copyToReaderAndTargetCanBeTheSame() {
        val aStr = 'a'.repeat(Segment.SIZE)
        val bs = 'b'.repeat(Segment.SIZE)

        val reader = RealBuffer()
        reader.write(aStr)
        reader.write(bs)

        reader.copyTo(reader, 0, reader.bytesAvailable())
        assertEquals(aStr + bs + aStr + bs, reader.readString())
    }

    @Test
    fun copyToEmptyReader() {
        val reader = RealBuffer()
        val target = RealBuffer().also { it.write("aaa") }
        reader.copyTo(target, 0L, 0L)
        assertEquals("", reader.readString())
        assertEquals("aaa", target.readString())
    }

    @Test
    fun copyToEmptyTarget() {
        val reader = RealBuffer().also { it.write("aaa") }
        val target = RealBuffer()
        reader.copyTo(target, 0L, 3L)
        assertEquals("aaa", reader.readString())
        assertEquals("aaa", target.readString())
    }

    @Test
    fun cloneDoesNotObserveWritesToOriginal() {
        val original = RealBuffer()
        val clone: Buffer = original.clone()
        original.write("abc")
        assertEquals(0, clone.bytesAvailable())
    }

    @Test
    fun cloneDoesNotObserveReadsFromOriginal() {
        val original = RealBuffer()
        original.write("abc")
        val clone: Buffer = original.clone()
        assertEquals("abc", original.readString(3))
        assertEquals(3, clone.bytesAvailable())
        assertEquals("ab", clone.readString(2))
    }

    @Test
    fun originalDoesNotObserveWritesToClone() {
        val original = RealBuffer()
        val clone: Buffer = original.clone()
        clone.write("abc")
        assertEquals(0, original.bytesAvailable())
    }

    @Test
    fun originalDoesNotObserveReadsFromClone() {
        val original = RealBuffer()
        original.write("abc")
        val clone: Buffer = original.clone()
        assertEquals("abc", clone.readString(3))
        assertEquals(3, original.bytesAvailable())
        assertEquals("ab", original.readString(2))
    }

    @Test
    fun cloneMultipleSegments() {
        val original = RealBuffer()
        original.write("a".repeat(Segment.SIZE * 3))
        val clone: Buffer = original.clone()
        original.write("b".repeat(Segment.SIZE * 3))
        clone.write("c".repeat(Segment.SIZE * 3))

        assertEquals(
            "a".repeat(Segment.SIZE * 3) + "b".repeat(Segment.SIZE * 3),
            original.readString((Segment.SIZE * 6).toLong())
        )
        assertEquals(
            "a".repeat(Segment.SIZE * 3) + "c".repeat(Segment.SIZE * 3),
            clone.readString((Segment.SIZE * 6).toLong())
        )
    }

    @Test
    fun readAndWriteToSelf() {
        val buffer = RealBuffer().also { it.writeByte(1) }
        val source: Reader = buffer
        val destination: Writer = buffer

        assertFailsWith<IllegalArgumentException> { source.transferTo(destination) }
        assertFailsWith<IllegalArgumentException> { destination.transferFrom(source) }
        assertFailsWith<IllegalArgumentException> { source.readAtMostTo(buffer, 1) }
        assertFailsWith<IllegalArgumentException> { source.readTo(destination, 1) }
        assertFailsWith<IllegalArgumentException> { destination.write(buffer, 1) }
        assertFailsWith<IllegalArgumentException> { destination.write(source, 1) }
    }

    @Test
    fun transferClone() {
        val buffer = RealBuffer().also { it.writeByte(42) }
        val copy = buffer.clone()
        copy.transferTo(buffer)
        assertArrayEquals(byteArrayOf(42, 42), buffer.readByteArray())
    }

    @Test
    fun snapshot() {
        val buffer = RealBuffer()
        assertEquals(ByteString.EMPTY, buffer.snapshot())
        buffer.write("hello")
        assertEquals("hello".encodeToUtf8(), buffer.snapshot())
        buffer.clear()
        assertEquals(ByteString.EMPTY, buffer.snapshot())
    }

    @Test
    fun copyToSkippingSegments() {
        val reader = RealBuffer()
        reader.write("a".repeat(Segment.SIZE * 2))
        reader.write("b".repeat(Segment.SIZE * 2))
        val out = ByteArrayOutputStream()
        reader.copyTo(out, Segment.SIZE * 2 + 1L, 3L)
        assertEquals("bbb", out.toString())
        assertEquals(
            "a".repeat(Segment.SIZE * 2) + "b".repeat(Segment.SIZE * 2),
            reader.readString(Segment.SIZE * 4L)
        )
    }

    @Test
    fun copyToStream() {
        val buffer = RealBuffer().also { it.write("hello, world!") }
        val out = ByteArrayOutputStream()
        buffer.copyTo(out)
        val outString = String(out.toByteArray(), Charsets.UTF_8)
        assertEquals("hello, world!", outString)
        assertEquals("hello, world!", buffer.readString())
    }

    @Test
    fun writeToSpanningSegments() {
        val buffer = RealBuffer()
        buffer.write("a".repeat(Segment.SIZE * 2))
        buffer.write("b".repeat(Segment.SIZE * 2))
        val out = ByteArrayOutputStream()
        buffer.skip(10)
        buffer.readTo(out, Segment.SIZE * 3L)
        assertEquals("a".repeat(Segment.SIZE * 2 - 10) + "b".repeat(Segment.SIZE + 10), out.toString())
        assertEquals("b".repeat(Segment.SIZE - 10), buffer.readString(buffer.bytesAvailable()))
    }

    @Test
    fun writeToStream() {
        val buffer = RealBuffer().also { it.write("hello, world!") }
        val out = ByteArrayOutputStream()
        buffer.readTo(out)
        val outString = String(out.toByteArray(), Charsets.UTF_8)
        assertEquals("hello, world!", outString)
        assertEquals(0, buffer.bytesAvailable())
    }

    @Test
    fun readFromStream() {
        val input: InputStream = ByteArrayInputStream("hello, world!".toByteArray(Charsets.UTF_8))
        val buffer = RealBuffer()
        buffer.transferFrom(input)
        val out = buffer.readString()
        assertEquals("hello, world!", out)
    }

    @Test
    fun readFromSpanningSegments() {
        val input: InputStream = ByteArrayInputStream("hello, world!".toByteArray(Charsets.UTF_8))
        val buffer = RealBuffer().also { it.write("a".repeat(Segment.SIZE - 10)) }
        buffer.transferFrom(input)
        val out = buffer.readString()
        assertEquals("a".repeat(Segment.SIZE - 10) + "hello, world!", out)
    }

    @Test
    fun readFromStreamWithCount() {
        val input: InputStream = ByteArrayInputStream("hello, world!".toByteArray(Charsets.UTF_8))
        val buffer = RealBuffer()
        buffer.write(input, 10)
        val out = buffer.readString()
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
        buffer.transferFrom(ByteArrayInputStream(ByteArray(Segment.SIZE)))
        assertNoEmptySegments(buffer)
    }

    @Test
    fun bufferInputStreamByteByByte() {
        val reader = RealBuffer()
        reader.write("abc")
        val input: InputStream = reader.asInputStream()
        assertEquals(3, input.available())
        assertEquals('a'.code, input.read())
        assertEquals('b'.code, input.read())
        assertEquals('c'.code, input.read())
        assertEquals(-1, input.read())
        assertEquals(0, input.available())
    }

    @Test
    fun bufferInputStreamBulkReads() {
        val reader = RealBuffer()
        reader.write("abc")
        val byteArray = ByteArray(4)
        Arrays.fill(byteArray, (-5).toByte())
        val input: InputStream = reader.asInputStream()
        assertEquals(3, input.read(byteArray))
        assertEquals("[97, 98, 99, -5]", byteArray.contentToString())
        Arrays.fill(byteArray, (-7).toByte())
        assertEquals(-1, input.read(byteArray))
        assertEquals("[-7, -7, -7, -7]", byteArray.contentToString())
    }

    @Test
    fun copyToOutputStreamWithStartIndex() {
        val reader = RealBuffer()
        reader.write("party")

        val target = RealBuffer()
        reader.copyTo(target.asOutputStream(), 2)
        assertEquals("rty", target.readString())
        assertEquals("party", reader.readString())
    }

    @Test
    fun copyToOutputStreamWithEndIndex() {
        val reader = RealBuffer()
        reader.write("party")

        val target = RealBuffer()
        reader.copyTo(target.asOutputStream(), 0, 3)
        assertEquals("par", target.readString())
        assertEquals("party", reader.readString())
    }

    @Test
    fun copyToOutputStreamWithIndices() {
        val reader = RealBuffer()
        reader.write("party")

        val target = RealBuffer()
        reader.copyTo(target.asOutputStream(), 1, 3)
        assertEquals("art", target.readString())
        assertEquals("party", reader.readString())
    }

    @Test
    fun copyToOutputStreamWithEmptyRange() {
        val reader = RealBuffer()
        reader.write("hello")

        val target = RealBuffer()
        reader.copyTo(target.asOutputStream(), 1, 0)
        assertEquals("hello", reader.readString())
        assertEquals("", target.readString())
    }

    @Test
    fun readToOutputStream() {
        val reader = RealBuffer()
        reader.write("party")

        val target = RealBuffer()
        reader.readTo(target.asOutputStream())
        assertEquals("party", target.readString())
        assertEquals("", reader.readString())
    }

    @Test
    fun readToOutputStreamWithByteCount() {
        val reader = RealBuffer()
        reader.write("party")

        val target = RealBuffer()
        reader.readTo(target.asOutputStream(), 3)
        assertEquals("par", target.readString())
        assertEquals("ty", reader.readString())
    }

    @Test
    fun readEmptyBufferToByteBuffer() {
        val bb = ByteBuffer.allocate(128)
        val buffer = RealBuffer()

        assertEquals(-1, buffer.readAtMostTo(bb))
    }
}
