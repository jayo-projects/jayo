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

package jayo.internal.bytestring

import jayo.bytestring.Ascii
import jayo.bytestring.encodeToAscii
import jayo.bytestring.readAscii
import jayo.bytestring.toAscii
import jayo.internal.TestUtil.ASCII_REPLACEMENT_CODE_POINT
import jayo.internal.makeAsciiSegments
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

class AsciiTest {

    companion object {
        @JvmStatic
        fun parameters(): Stream<Arguments>? {
            return Stream.of(
                Arguments.of(AsciiFactory.ASCII, "Ascii"),
                Arguments.of(AsciiFactory.ASCII_FROM_BYTES, "Ascii (from bytes)"),
                Arguments.of(
                    AsciiFactory.ASCII_FROM_BYTES_NO_COMPACT_STRING,
                    "Ascii (from bytes without compact string)"
                ),
                Arguments.of(AsciiFactory.SEGMENTED_ASCII, "SegmentedAscii"),
                Arguments.of(AsciiFactory.ASCII_ONE_BYTE_PER_SEGMENT, "SegmentedAscii (one-byte-at-a-time)"),
            )
        }

        private const val ASCII = "abcdef"
    }

    @Test
    fun arrayToAscii() {
        val actual = byteArrayOf(1, 2, 3, 4).toAscii()
        val expected = Ascii.of(1, 2, 3, 4)
        assertEquals(actual, expected)
    }

    @Test
    fun arraySubsetToAscii() {
        val actual = byteArrayOf(1, 2, 3, 4).toAscii(1, 2)
        val expected = Ascii.of(2, 3)
        assertEquals(actual, expected)
    }

    @Test
    fun byteBufferToAscii() {
        val actual = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)).toAscii()
        val expected = Ascii.of(1, 2, 3, 4)
        assertEquals(actual, expected)
    }

    @Test
    fun streamReadAscii() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        assertThrows<IllegalArgumentException> { stream.readAscii(-42) }
        val actual = stream.readAscii(4)
        val expected = Ascii.of(1, 2, 3, 4)
        assertEquals(actual, expected)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun substring(factory: AsciiFactory) {
        val ascii = factory.encodeAscii(ASCII)
        assertEquals(ascii.substring(0, 3), "abc".encodeToAscii())
        assertEquals(ascii.substring(3), "def".encodeToAscii())
        assertEquals(ascii.substring(1, 5), "bcde".encodeToAscii())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun lengthAndDecodeAscii(factory: AsciiFactory) {
        var ascii = factory.encodeAscii(ASCII)
        assertEquals(ASCII.length, ascii.length)
        assertEquals(ASCII, ascii.decodeToString())
    }

    @Test
    fun codePointsAsciiReplacement() {
        var ascii = Ascii.of(0xc0.toByte())
        assertThat(ascii.codePoints())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
        ascii = makeAsciiSegments(ascii)
        assertThat(ascii.codePoints())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
        ascii = Ascii.of(0xe2.toByte())
        assertThat(ascii.codePoints())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
        ascii = makeAsciiSegments(ascii)
        assertThat(ascii.codePoints())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
        ascii = Ascii.of(0xf4.toByte())
        assertThat(ascii.codePoints())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
        ascii = makeAsciiSegments(ascii)
        assertThat(ascii.codePoints())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
        ascii = Ascii.of(0xff.toByte())
        assertThat(ascii.codePoints())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
        ascii = makeAsciiSegments(ascii)
        assertThat(ascii.codePoints())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun codePoints(factory: AsciiFactory) {
        val ascii = factory.encodeAscii(ASCII)
        assertThat(ascii.codePoints())
            .containsExactly('a'.code, 'b'.code, 'c'.code, 'd'.code, 'e'.code, 'f'.code)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun codePointsIterator(factory: AsciiFactory) {
        var ascii = Ascii.EMPTY
        assertThat(ascii.codePoints().iterator().hasNext()).isFalse()
        assertThrows<NoSuchElementException> { ascii.codePoints().iterator().nextInt() }
        ascii = factory.encodeAscii(ASCII)
        assertThatIterator(ascii.codePoints().iterator()).toIterable()
            .containsExactly('a'.code, 'b'.code, 'c'.code, 'd'.code, 'e'.code, 'f'.code)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun subSequence(factory: AsciiFactory) {
        val ascii = factory.encodeAscii(ASCII)
        assertThat(ascii.subSequence(0, 3)).contains("abc")
        assertThat(ascii.subSequence(1, 5)).contains("bcde")
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun charAt(factory: AsciiFactory) {
        val ascii = factory.encodeAscii("abcdé")
        assertThat(ascii[1]).isEqualTo('b')
        assertThat(ascii[4]).isEqualTo('?')
    }

    @Test
    fun charsAsciiReplacement() {
        var ascii = Ascii.of(0xc0.toByte())
        assertThat(ascii.chars())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
        ascii = makeAsciiSegments(ascii)
        assertThat(ascii.chars())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
        ascii = Ascii.of(0xe2.toByte())
        assertThat(ascii.chars())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
        ascii = makeAsciiSegments(ascii)
        assertThat(ascii.chars())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
        ascii = Ascii.of(0xf4.toByte())
        assertThat(ascii.chars())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
        ascii = makeAsciiSegments(ascii)
        assertThat(ascii.chars())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
        ascii = Ascii.of(0xff.toByte())
        assertThat(ascii.chars())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
        ascii = makeAsciiSegments(ascii)
        assertThat(ascii.chars())
            .containsExactly(ASCII_REPLACEMENT_CODE_POINT)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun chars(factory: AsciiFactory) {
        val ascii = factory.encodeAscii(ASCII)
        assertThat(ascii.chars())
            .containsExactly('a'.code, 'b'.code, 'c'.code, 'd'.code, 'e'.code, 'f'.code)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun charsIterator(factory: AsciiFactory) {
        var ascii = Ascii.EMPTY
        assertThat(ascii.chars().iterator().hasNext()).isFalse()
        assertThrows<NoSuchElementException> { ascii.chars().iterator().nextInt() }
        ascii = factory.encodeAscii(ASCII)
        assertThatIterator(ascii.chars().iterator()).toIterable()
            .containsExactly('a'.code, 'b'.code, 'c'.code, 'd'.code, 'e'.code, 'f'.code)
    }
}
