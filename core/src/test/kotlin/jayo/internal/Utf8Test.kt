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

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import jayo.ByteString.of
import jayo.decodeHex
import jayo.exceptions.JayoEOFException
import jayo.internal.TestUtil.REPLACEMENT_CODE_POINT
import jayo.utf8Size
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.text.Charsets.UTF_8

class Utf8Test {
    @Test
    fun oneByteCharacters() {
        assertEncoded("00", 0x00) // Smallest 1-byte character.
        assertEncoded("20", ' '.code)
        assertEncoded("7e", '~'.code)
        assertEncoded("7f", 0x7f) // Largest 1-byte character.
    }

    @Test
    fun twoByteCharacters() {
        assertEncoded("c280", 0x0080) // Smallest 2-byte character.
//        assertEncoded("c3bf", 0x00ff)
//        assertEncoded("c480", 0x0100)
//        assertEncoded("dfbf", 0x07ff) // Largest 2-byte character.
    }

    @Test
    fun threeByteCharacters() {
        assertEncoded("e0a080", 0x0800) // Smallest 3-byte character.
        assertEncoded("e0bfbf", 0x0fff)
        assertEncoded("e18080", 0x1000)
        assertEncoded("e1bfbf", 0x1fff)
        assertEncoded("ed8080", 0xd000)
        assertEncoded("ed9fbf", 0xd7ff) // Largest character lower than the min surrogate.
        assertEncoded("ee8080", 0xe000) // Smallest character greater than the max surrogate.
        assertEncoded("eebfbf", 0xefff)
        assertEncoded("ef8080", 0xf000)
        assertEncoded("efbfbf", 0xffff) // Largest 3-byte character.
    }

    @Test
    fun fourByteCharacters() {
        assertEncoded("f0908080", 0x010000) // Smallest surrogate pair.
        assertEncoded("f48fbfbf", 0x10ffff) // Largest code point expressible by UTF-16.
    }

    @Test
    fun danglingHighSurrogate() {
        assertStringEncoded("3f", "\ud800") // "?"
    }

    @Test
    fun lowSurrogateWithoutHighSurrogate() {
        assertStringEncoded("3f", "\udc00") // "?"
    }

    @Test
    fun highSurrogateFollowedByNonSurrogate() {
        assertStringEncoded("3f61", "\ud800\u0061") // "?a": Following character is too low.
        assertStringEncoded("3fee8080", "\ud800\ue000") // "?\ue000": Following character is too high.
    }

    @Test
    fun doubleLowSurrogate() {
        assertStringEncoded("3f3f", "\udc00\udc00") // "??"
    }

    @Test
    fun doubleHighSurrogate() {
        assertStringEncoded("3f3f", "\ud800\ud800") // "??"
    }

    @Test
    fun highSurrogateLowSurrogate() {
        assertStringEncoded("3f3f", "\udc00\ud800") // "??"
    }

    @Test
    fun multipleSegmentString() {
        val a = "a".repeat(SEGMENT_SIZE + SEGMENT_SIZE + 1)
        val encoded = RealBuffer().writeUtf8(a)
        val expected = RealBuffer().write(a.toByteArray(UTF_8))
        assertEquals(expected.readByteString(), encoded.readByteString())
    }

    @Test
    fun stringSpansSegments() {
        val buffer = RealBuffer()
        val a = "a".repeat(SEGMENT_SIZE - 1)
        val b = "bb"
        val c = "c".repeat(SEGMENT_SIZE - 1)
        buffer.writeUtf8(a)
        buffer.writeUtf8(b)
        buffer.writeUtf8(c)
        assertEquals(a + b + c, buffer.readUtf8())
    }

    @Test
    fun readEmptyBufferThrowsEofException() {
        val buffer = RealBuffer()
        assertThrows<JayoEOFException> { buffer.readUtf8CodePoint()  }
    }

    @Test
    fun readLeadingContinuationByteReturnsReplacementCharacter() {
        val buffer = RealBuffer()
        buffer.writeByte(0xbf.toByte())
        assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
        assertTrue(buffer.exhausted())
    }

    @Test
    fun readMissingContinuationBytesThrowsEofException() {
        val buffer = RealBuffer()
        buffer.writeByte(0xdf.toByte())
        assertThrows<JayoEOFException> { buffer.readUtf8CodePoint() }
        assertFalse(buffer.exhausted()) // Prefix byte wasn't consumed.
    }

    @Test
    fun readTooLargeCodepointReturnsReplacementCharacter() {
        // 5-byte and 6-byte code points are not supported.
        val buffer = RealBuffer()
        buffer.write("f888808080".decodeHex())
        assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
        assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
        assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
        assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
        assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
        assertTrue(buffer.exhausted())
    }

    @Test
    fun readNonContinuationBytesReturnsReplacementCharacter() {
        // Use a non-continuation byte where a continuation byte is expected.
        val buffer = RealBuffer()
        buffer.write("df20".decodeHex())
        assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
        assertEquals(0x20, buffer.readUtf8CodePoint().toLong()) // Non-continuation character not consumed.
        assertTrue(buffer.exhausted())
    }

    @Test
    fun readCodePointBeyondUnicodeMaximum() {
        // A 4-byte encoding with data above the U+10ffff Unicode maximum.
        val buffer = RealBuffer()
        buffer.write("f4908080".decodeHex())
        assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
        assertTrue(buffer.exhausted())
    }

    @Test
    fun readSurrogateCodePoint() {
        val buffer = RealBuffer()
        buffer.write("eda080".decodeHex())
        assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
        assertTrue(buffer.exhausted())
        buffer.write("edbfbf".decodeHex())
        assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
        assertTrue(buffer.exhausted())
    }

    @Test
    fun readOverlongCodePoint() {
        // Use 2 bytes to encode data that only needs 1 byte.
        val buffer = RealBuffer()
        buffer.write("c080".decodeHex())
        assertEquals(REPLACEMENT_CODE_POINT.toLong(), buffer.readUtf8CodePoint().toLong())
        assertTrue(buffer.exhausted())
    }

    @Test
    fun writeSurrogateCodePoint() {
        assertStringEncoded("ed9fbf", "\ud7ff") // Below lowest surrogate is okay.
        assertStringEncoded("3f", "\ud800") // Lowest surrogate gets '?'.
        assertStringEncoded("3f", "\udfff") // Highest surrogate gets '?'.
        assertStringEncoded("ee8080", "\ue000") // Above highest surrogate is okay.
    }

    @Test
    fun writeCodePointBeyondUnicodeMaximum() {
        val buffer = RealBuffer()
        try {
            buffer.writeUtf8CodePoint(0x110000)
            fail()
        } catch (expected: IllegalArgumentException) {
            assertEquals("Unexpected code point: 0x110000", expected.message)
        }
    }

    @Test
    fun size() {
        assertEquals(0, "".utf8Size())
        assertEquals(3, "abc".utf8Size())
        assertEquals(16, "təˈranəˌsôr".utf8Size())
    }

    private fun assertEncoded(hex: String, vararg codePoints: Int) {
        assertCodePointEncoded(hex, *codePoints)
        assertCodePointDecoded(hex, *codePoints)
        assertStringEncoded(hex, String(codePoints, 0, codePoints.size))
    }

    private fun assertCodePointEncoded(hex: String, vararg codePoints: Int) {
        val buffer = RealBuffer()
        for (codePoint in codePoints) {
            buffer.writeUtf8CodePoint(codePoint)
        }
        assertEquals(buffer.readByteString(), hex.decodeHex())
    }

    private fun assertCodePointDecoded(hex: String, vararg codePoints: Int) {
        val buffer = RealBuffer().write(hex.decodeHex())
        for (codePoint in codePoints) {
            assertEquals(codePoint.toLong(), buffer.readUtf8CodePoint().toLong())
        }
        assertTrue(buffer.exhausted())
    }

    private fun assertStringEncoded(hex: String, string: String) {
        val expectedUtf8 = hex.decodeHex()

        // Confirm our expectations are consistent with the platform.
        val platformUtf8 = of(*string.toByteArray(charset("UTF-8")))
        assertEquals(expectedUtf8, platformUtf8)

        // Confirm our implementation matches those expectations.
        val actualUtf8 = RealBuffer().writeUtf8(string).readByteString()
        assertEquals(expectedUtf8, actualUtf8)

        // Confirm we are consistent when writing one code point at a time.
        val bufferUtf8 = RealBuffer()
        var i = 0
        while (i < string.length) {
            val c = string.codePointAt(i)
            bufferUtf8.writeUtf8CodePoint(c)
            i += Character.charCount(c)
        }
        assertEquals(expectedUtf8, bufferUtf8.readByteString())

        // Confirm we are consistent when measuring lengths.
        assertEquals(expectedUtf8.size.toLong(), string.utf8Size())
    }
}
