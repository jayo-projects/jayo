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

import jayo.Utf8ByteString
import jayo.encodeToUtf8ByteString
import jayo.exceptions.JayoCharacterCodingException
import jayo.readUtf8ByteString
import jayo.toUtf8ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.stream.Stream

class Utf8ByteStringTest {

    companion object {
        @JvmStatic
        fun parameters(): Stream<Arguments>? {
            return Stream.of(
                Arguments.of(Utf8ByteStringFactory.UTF8_BYTE_STRING, "Utf8ByteString"),
                Arguments.of(Utf8ByteStringFactory.UTF8_BYTE_STRING_FROM_BYTES, "Utf8ByteString (from bytes)"),
                Arguments.of(
                    Utf8ByteStringFactory.UTF8_BYTE_STRING_FROM_BYTES_NO_COMPACT_STRING,
                    "Utf8ByteString (from bytes without compact string)"
                ),
                /*Arguments.of(Utf8ByteStringFactory.SEGMENTED_BYTE_STRING, "SegmentedUtf8ByteString"),
                Arguments.of(Utf8ByteStringFactory.ONE_BYTE_PER_SEGMENT, "SegmentedUtf8ByteString (one-byte-at-a-time)"),*/
            )
        }

        private const val ASCII = "abcdef"
        private const val UTF8_NO_SURROGATE = "Cａfé \uD83C\uDF69!" // é is one code point.
        private const val UTF8_SURROGATES = "Cａfé \uD83C\uDF69!" // e is one code point, its accent is another.
    }

    @Test
    fun arrayToByteString() {
        val actual = byteArrayOf(1, 2, 3, 4).toUtf8ByteString()
        val expected = Utf8ByteString.ofUtf8(1, 2, 3, 4)
        assertEquals(actual, expected)
    }

    @Test
    fun arraySubsetToByteString() {
        val actual = byteArrayOf(1, 2, 3, 4).toUtf8ByteString(1, 2)
        val expected = Utf8ByteString.ofUtf8(2, 3)
        assertEquals(actual, expected)
    }

    @Test
    fun byteBufferToByteString() {
        val actual = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)).toUtf8ByteString()
        val expected = Utf8ByteString.ofUtf8(1, 2, 3, 4)
        assertEquals(actual, expected)
    }

    @Test
    fun streamReadByteString() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val actual = stream.readUtf8ByteString(4)
        val expected = Utf8ByteString.ofUtf8(1, 2, 3, 4)
        assertEquals(actual, expected)
    }

    @Test
    fun substring() {
        val utf8ByteString = ASCII.encodeToUtf8ByteString()
        assertEquals(utf8ByteString.substring(0, 3), "abc".encodeToUtf8ByteString())
        assertEquals(utf8ByteString.substring(3), "def".encodeToUtf8ByteString())
        assertEquals(utf8ByteString.substring(1, 5), "bcde".encodeToUtf8ByteString())
    }

    @Test
    fun encodingErrors() {
        var utf8ByteString = Utf8ByteString.ofUtf8(0xc0.toByte())
        assertThrows<JayoCharacterCodingException> { utf8ByteString.length }
        utf8ByteString = Utf8ByteString.ofUtf8(0xe2.toByte())
        assertThrows<JayoCharacterCodingException> { utf8ByteString.length }
        utf8ByteString = Utf8ByteString.ofUtf8(0xf4.toByte())
        assertThrows<JayoCharacterCodingException> { utf8ByteString.length }
        utf8ByteString = Utf8ByteString.ofUtf8(0xff.toByte())
        assertThrows<JayoCharacterCodingException> { utf8ByteString.length }
        utf8ByteString = Utf8ByteString.ofUtf8(0xc0.toByte(), 0x01.toByte())
        assertThrows<JayoCharacterCodingException> { utf8ByteString.length }
        utf8ByteString = Utf8ByteString.ofUtf8(0xc0.toByte(), 0x80.toByte())
        assertThrows<JayoCharacterCodingException> { utf8ByteString.length }
        utf8ByteString = Utf8ByteString.ofUtf8(0xf4.toByte(), 0xb0.toByte(), 0x80.toByte(), 0x80.toByte())
        assertThrows<JayoCharacterCodingException> { utf8ByteString.length }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun lengthAndDecodeUtf8(factory: Utf8ByteStringFactory) {
        var utf8ByteString = factory.encodeUtf8(ASCII)
        assertEquals(6, utf8ByteString.length)
        assertEquals(ASCII, utf8ByteString.decodeToUtf8())
        utf8ByteString = factory.encodeUtf8(UTF8_NO_SURROGATE)
        assertEquals(8, utf8ByteString.length)
        assertEquals(UTF8_NO_SURROGATE, utf8ByteString.decodeToUtf8())
        utf8ByteString = factory.encodeUtf8(UTF8_SURROGATES)
        assertEquals(9, utf8ByteString.length)
        assertEquals(UTF8_SURROGATES, utf8ByteString.decodeToUtf8())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun charAt(factory: Utf8ByteStringFactory) {
        var utf8ByteString = factory.encodeUtf8(ASCII)
        assertEquals('d', utf8ByteString[3])
        assertEquals('e', utf8ByteString[4])
        utf8ByteString = factory.encodeUtf8(UTF8_NO_SURROGATE)
        assertEquals('é', utf8ByteString[3])
        assertEquals(' ', utf8ByteString[4])
        utf8ByteString = factory.encodeUtf8(UTF8_SURROGATES)
        assertEquals('e', utf8ByteString[3])
        assertEquals('́', utf8ByteString[4])
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun charAtErrors(factory: Utf8ByteStringFactory) {
        val utf8ByteString = factory.encodeUtf8(ASCII)
        assertThrows<IndexOutOfBoundsException> { utf8ByteString[-2] }
        assertThrows<IndexOutOfBoundsException> { utf8ByteString[42] }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun subSequence(factory: Utf8ByteStringFactory) {
        var utf8ByteString = factory.encodeUtf8(ASCII)
        assertThat(utf8ByteString.subSequence(2, 2)).isEmpty()
        assertThat(utf8ByteString.subSequence(1, 5).contentEquals(ASCII.subSequence(1, 5))).isTrue()
        utf8ByteString = factory.encodeUtf8(UTF8_NO_SURROGATE)
        assertThat(utf8ByteString.subSequence(1, 5)).isEqualTo(UTF8_NO_SURROGATE.subSequence(1, 5))
        utf8ByteString = factory.encodeUtf8(UTF8_SURROGATES)
        assertThat(utf8ByteString.subSequence(1, 5)).isEqualTo(UTF8_SURROGATES.subSequence(1, 5))
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun subSequenceErrors(factory: Utf8ByteStringFactory) {
        val utf8ByteString = factory.encodeUtf8(ASCII)
        assertThrows<IndexOutOfBoundsException> { utf8ByteString.subSequence(-3, -2) }
        assertThrows<IndexOutOfBoundsException> { utf8ByteString.subSequence(1, 0) }
        assertThrows<IndexOutOfBoundsException> { utf8ByteString.subSequence(1, 42) }
    }
}
