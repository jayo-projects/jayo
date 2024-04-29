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

import jayo.Utf8String
import jayo.encodeToUtf8String
import jayo.exceptions.JayoCharacterCodingException
import jayo.internal.Utf8Utils.UTF8_REPLACEMENT_CODE_POINT
import jayo.readUtf8String
import jayo.toUtf8String
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIterator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.stream.Stream

class Utf8StringTest {

    companion object {
        @JvmStatic
        fun parameters(): Stream<Arguments>? {
            return Stream.of(
                Arguments.of(Utf8StringFactory.UTF8_STRING, "Utf8String"),
                Arguments.of(Utf8StringFactory.UTF8_STRING_FROM_BYTES, "Utf8String (from bytes)"),
                Arguments.of(
                    Utf8StringFactory.UTF8_STRING_FROM_BYTES_NO_COMPACT_STRING,
                    "Utf8String (from bytes without compact string)"
                ),
                Arguments.of(Utf8StringFactory.SEGMENTED_UTF8_STRING, "SegmentedUtf8String"),
                Arguments.of(Utf8StringFactory.UTF8_ONE_BYTE_PER_SEGMENT, "SegmentedUtf8String (one-byte-at-a-time)"),
            )
        }

        private const val ASCII = "abcdef"
        private const val UTF8_NO_SURROGATE = "Cａfé \uD83C\uDF69!" // é is one code point.
        private const val UTF8_SURROGATES = "Cａfé \uD83C\uDF69!" // e is one code point, its accent is another.
        private const val LAST_3_BYTES_CHARACTER = "\uFFFF"
        private const val FIRST_4_BYTES_CHARACTER = "\uD800\uDC00"
        private const val LAST_4_BYTES_CHARACTER = "\uD803\uDFFF"
    }

    @Test
    fun arrayToByteString() {
        val actual = byteArrayOf(1, 2, 3, 4).toUtf8String()
        val expected = Utf8String.ofUtf8(1, 2, 3, 4)
        assertEquals(actual, expected)
    }

    @Test
    fun arraySubsetToByteString() {
        val actual = byteArrayOf(1, 2, 3, 4).toUtf8String(1, 2)
        val expected = Utf8String.ofUtf8(2, 3)
        assertEquals(actual, expected)
    }

    @Test
    fun byteBufferToByteString() {
        val actual = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)).toUtf8String()
        val expected = Utf8String.ofUtf8(1, 2, 3, 4)
        assertEquals(actual, expected)
    }

    @Test
    fun streamReadByteString() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        assertThrows<IllegalArgumentException> { stream.readUtf8String(-42) }
        val actual = stream.readUtf8String(4)
        val expected = Utf8String.ofUtf8(1, 2, 3, 4)
        assertEquals(actual, expected)
    }

    @Test
    fun substring() {
        val utf8String = ASCII.encodeToUtf8String()
        assertEquals(utf8String.substring(0, 3), "abc".encodeToUtf8String())
        assertEquals(utf8String.substring(3), "def".encodeToUtf8String())
        assertEquals(utf8String.substring(1, 5), "bcde".encodeToUtf8String())
    }

    @Test
    fun lengthEncodingErrors() {
        var utf8String = Utf8String.ofUtf8(0xc0.toByte())
        assertThrows<JayoCharacterCodingException> { utf8String.length() }
        utf8String = makeUtf8Segments(utf8String)
        assertThrows<JayoCharacterCodingException> { utf8String.length() }
        utf8String = Utf8String.ofUtf8(0xe2.toByte())
        assertThrows<JayoCharacterCodingException> { utf8String.length() }
        utf8String = makeUtf8Segments(utf8String)
        assertThrows<JayoCharacterCodingException> { utf8String.length() }
        utf8String = Utf8String.ofUtf8(0xf4.toByte())
        assertThrows<JayoCharacterCodingException> { utf8String.length() }
        utf8String = makeUtf8Segments(utf8String)
        assertThrows<JayoCharacterCodingException> { utf8String.length() }
        utf8String = Utf8String.ofUtf8(0xff.toByte())
        assertThrows<JayoCharacterCodingException> { utf8String.length() }
        utf8String = makeUtf8Segments(utf8String)
        assertThrows<JayoCharacterCodingException> { utf8String.length() }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun lengthAndDecodeUtf8(factory: Utf8StringFactory) {
        var utf8String = factory.encodeUtf8(ASCII)
        assertEquals(ASCII.length, utf8String.length())
        assertEquals(ASCII, utf8String.decodeToUtf8())
        utf8String = factory.encodeUtf8(UTF8_NO_SURROGATE)
        assertEquals(UTF8_NO_SURROGATE.length, utf8String.length())
        assertEquals(UTF8_NO_SURROGATE, utf8String.decodeToUtf8())
        utf8String = factory.encodeUtf8(UTF8_SURROGATES)
        assertEquals(UTF8_SURROGATES.length, utf8String.length())
        assertEquals(UTF8_SURROGATES, utf8String.decodeToUtf8())
        utf8String = factory.encodeUtf8(LAST_3_BYTES_CHARACTER)
        assertEquals(LAST_3_BYTES_CHARACTER.length, utf8String.length())
        assertEquals(LAST_3_BYTES_CHARACTER, utf8String.decodeToUtf8())
        utf8String = factory.encodeUtf8(FIRST_4_BYTES_CHARACTER)
        assertEquals(FIRST_4_BYTES_CHARACTER.length, utf8String.length())
        assertEquals(FIRST_4_BYTES_CHARACTER, utf8String.decodeToUtf8())
        utf8String = factory.encodeUtf8(LAST_4_BYTES_CHARACTER)
        assertEquals(LAST_4_BYTES_CHARACTER.length, utf8String.length())
        assertEquals(LAST_4_BYTES_CHARACTER, utf8String.decodeToUtf8())
    }

    @Test
    fun codePointsUtf8Replacement() {
        var utf8String = Utf8String.ofUtf8(0xc0.toByte())
        assertThat(utf8String.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8String = makeUtf8Segments(utf8String)
        assertThat(utf8String.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8String = Utf8String.ofUtf8(0xe2.toByte())
        assertThat(utf8String.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8String = makeUtf8Segments(utf8String)
        assertThat(utf8String.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8String = Utf8String.ofUtf8(0xf4.toByte())
        assertThat(utf8String.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8String = makeUtf8Segments(utf8String)
        assertThat(utf8String.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8String = Utf8String.ofUtf8(0xff.toByte())
        assertThat(utf8String.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8String = makeUtf8Segments(utf8String)
        assertThat(utf8String.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8String = Utf8String.ofUtf8(0xc0.toByte(), 0x01.toByte())
        assertThat(utf8String.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8String = makeUtf8Segments(utf8String)
        assertThat(utf8String.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8String = Utf8String.ofUtf8(0xc0.toByte(), 0x80.toByte())
        assertThat(utf8String.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8String = makeUtf8Segments(utf8String)
        assertThat(utf8String.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8String = Utf8String.ofUtf8(0xf4.toByte(), 0xb0.toByte(), 0x80.toByte(), 0x80.toByte())
        assertThat(utf8String.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8String = makeUtf8Segments(utf8String)
        assertThat(utf8String.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun codePoints(factory: Utf8StringFactory) {
        var utf8String = factory.encodeUtf8(ASCII)
        assertThat(utf8String.codePoints())
            .containsExactly('a'.code, 'b'.code, 'c'.code, 'd'.code, 'e'.code, 'f'.code)
        utf8String = factory.encodeUtf8(UTF8_NO_SURROGATE)
        assertThat(utf8String.codePoints())
            .containsExactly(
                'C'.code, 'ａ'.code, 'f'.code, 'é'.code, ' '.code,
                *"🍩".codePoints().toArray().toTypedArray(), '!'.code
            )
        utf8String = factory.encodeUtf8(UTF8_SURROGATES)
        assertThat(utf8String.codePoints())
            .containsExactly(
                'C'.code, 'ａ'.code, 'f'.code, 'e'.code, '́'.code, ' '.code,
                *"🍩".codePoints().toArray().toTypedArray(), '!'.code
            )
        // force generation of utf8 string
        utf8String.decodeToUtf8()
        assertThat(utf8String.codePoints())
            .containsExactly(
                'C'.code, 'ａ'.code, 'f'.code, 'e'.code, '́'.code, ' '.code,
                *"🍩".codePoints().toArray().toTypedArray(), '!'.code
            )
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun codePointsIterator(factory: Utf8StringFactory) {
        var utf8String = Utf8String.EMPTY
        assertThat(utf8String.codePoints().iterator().hasNext()).isFalse()
        assertThrows<NoSuchElementException> { utf8String.codePoints().iterator().nextInt() }
        utf8String = factory.encodeUtf8(ASCII)
        assertThatIterator(utf8String.codePoints().iterator()).toIterable()
            .containsExactly('a'.code, 'b'.code, 'c'.code, 'd'.code, 'e'.code, 'f'.code)
        utf8String = factory.encodeUtf8(UTF8_NO_SURROGATE)
        assertThatIterator(utf8String.codePoints().iterator()).toIterable()
            .containsExactly(
                'C'.code, 'ａ'.code, 'f'.code, 'é'.code, ' '.code,
                *"🍩".codePoints().toArray().toTypedArray(), '!'.code
            )
        utf8String = factory.encodeUtf8(UTF8_SURROGATES)
        assertThatIterator(utf8String.codePoints().iterator()).toIterable()
            .containsExactly(
                'C'.code, 'ａ'.code, 'f'.code, 'e'.code, '́'.code, ' '.code,
                *"🍩".codePoints().toArray().toTypedArray(), '!'.code
            )
    }
}
