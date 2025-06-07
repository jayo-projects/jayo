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

import jayo.JayoCharacterCodingException
import jayo.bytestring.Utf8
import jayo.bytestring.encodeToUtf8
import jayo.bytestring.readUtf8
import jayo.bytestring.toAscii
import jayo.internal.TestUtil.UTF8_REPLACEMENT_CODE_POINT
import jayo.internal.makeUtf8Segments
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

class Utf8Test {

    companion object {
        @JvmStatic
        fun parameters(): Stream<Arguments>? {
            return Stream.of(
                Arguments.of(Utf8Factory.UTF8, "Utf8"),
                Arguments.of(Utf8Factory.UTF8_FROM_BYTES, "Utf8 (from bytes)"),
                Arguments.of(
                    Utf8Factory.UTF8_FROM_BYTES_NO_COMPACT_STRING,
                    "Utf8 (from bytes without compact string)"
                ),
                Arguments.of(Utf8Factory.SEGMENTED_UTF8, "SegmentedUtf8"),
                Arguments.of(Utf8Factory.UTF8_ONE_BYTE_PER_SEGMENT, "SegmentedUtf8 (one-byte-at-a-time)"),
                Arguments.of(Utf8Factory.ASCII, "Ascii"),
                Arguments.of(Utf8Factory.ASCII_FROM_BYTES, "Ascii (from bytes)"),
                Arguments.of(Utf8Factory.SEGMENTED_ASCII, "SegmentedAscii"),
                Arguments.of(Utf8Factory.ASCII_ONE_BYTE_PER_SEGMENT, "SegmentedAscii (one-byte-at-a-time)"),
            )
        }

        private const val ASCII = "abcdef"
        private const val UTF8_NO_SURROGATE = "Cａfé \uD83C\uDF69!" // é is one code point.
        private const val UTF8_SURROGATES = "Cａfé \uD83C\uDF69!" // e is one code point, its accent is another.
        private const val LAST_3_BYTES_CHARACTER = "\uFFFF"
        private const val FIRST_4_BYTES_CHARACTER = "\uD800\uDC00"
        private const val LAST_4_BYTES_CHARACTER = "\uD803\uDFFF"
        private const val UTF8 = ("Սｍ, I'll 𝓽𝖾ll ᶌօ𝘂 ᴛℎ℮ 𝜚𝕣०ｂl𝖾ｍ ｗі𝕥𝒽 𝘵𝘩𝐞 𝓼𝙘𝐢𝔢𝓷𝗍𝜄𝚏𝑖ｃ 𝛠𝝾ｗ𝚎𝑟 𝕥ｈ⍺𝞃 𝛄𝓸𝘂'𝒓𝗲 υ𝖘𝓲𝗇ɡ 𝕙𝚎𝑟ｅ, "
        + "𝛊𝓽 ⅆ𝕚𝐝𝝿'𝗍 𝔯𝙚𝙦ᴜ𝜾𝒓𝘦 𝔞𝘯𝐲 ԁ𝜄𝑠𝚌ι𝘱lι𝒏ｅ 𝑡𝜎 𝕒𝚝𝖙𝓪і𝞹 𝔦𝚝. 𝒀ο𝗎 𝔯𝑒⍺𝖉 ｗ𝐡𝝰𝔱 𝞂𝞽һ𝓮𝓇ƽ հ𝖺𝖉 ⅾ𝛐𝝅ⅇ 𝝰πԁ 𝔂ᴑᴜ 𝓉ﮨ၀𝚔 "
        + "т𝒽𝑒 𝗇𝕖ⅹ𝚝 𝔰𝒕е𝓅. 𝘠ⲟ𝖚 𝖉ⅰԁ𝝕'τ 𝙚𝚊ｒ𝞹 𝘵Ꮒ𝖾 𝝒𝐧هｗl𝑒𝖉ƍ𝙚 𝓯૦ｒ 𝔂𝞼𝒖𝕣𝑠𝕖l𝙫𝖊𝓼, 𐑈о ｙ𝘰𝒖 ⅆە𝗇'ｔ 𝜏α𝒌𝕖 𝛂𝟉ℽ "
        + "𝐫ⅇ𝗌ⲣ๐ϖ𝖘ꙇᖯ𝓲l𝓲𝒕𝘆 𝐟𝞼𝘳 𝚤𝑡. 𝛶𝛔𝔲 ｓ𝕥σσ𝐝 ﮩ𝕟 𝒕𝗁𝔢 𝘴𝐡𝜎ᴜlⅾ𝓮𝔯𝚜 𝛐𝙛 ᶃ𝚎ᴨᎥս𝚜𝘦𝓈 𝓽𝞸 ａ𝒄𝚌𝞸ｍρl𝛊ꜱ𝐡 𝓈𝚘ｍ𝚎𝞃𝔥⍳𝞹𝔤 𝐚𝗌 𝖋ａ𝐬𝒕 "
        + "αｓ γ𝛐𝕦 𝔠ﻫ𝛖lԁ, 𝚊π𝑑 Ь𝑒𝙛૦𝓇𝘦 𝓎٥𝖚 ⅇｖℯ𝝅 𝜅ո𝒆ｗ ｗ𝗵𝒂𝘁 ᶌ੦𝗎 ｈ𝐚𝗱, 𝜸ﮨ𝒖 𝓹𝝰𝔱𝖾𝗇𝓽𝔢ⅆ і𝕥, 𝚊𝜛𝓭 𝓹𝖺ⅽϰ𝘢ℊеᏧ 𝑖𝞃, "
        + "𝐚𝛑ꓒ 𝙨l𝔞р𝘱𝔢𝓭 ɩ𝗍 ہ𝛑 𝕒 ｐl𝛂ѕᴛ𝗂𝐜 l𝞄ℼ𝔠𝒽𝑏ﮪ⨯, 𝔞ϖ𝒹 ｎ𝛔ｗ 𝛾𝐨𝞄'𝗿𝔢 ꜱ℮ll𝙞ｎɡ ɩ𝘁, 𝙮𝕠𝛖 ｗ𝑎ℼ𝚗𝛂 𝕤𝓮ll 𝙞𝓉.")
    }

    @Test
    fun arrayToUtf8() {
        val actual = byteArrayOf(1, 2, 3, 4).toAscii()
        val expected = Utf8.of(1, 2, 3, 4)
        assertEquals(actual, expected)
    }

    @Test
    fun arraySubsetToUtf8() {
        val actual = byteArrayOf(1, 2, 3, 4).toAscii(1, 2)
        val expected = Utf8.of(2, 3)
        assertEquals(actual, expected)
    }

    @Test
    fun byteBufferToUtf8() {
        val actual = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)).toAscii()
        val expected = Utf8.of(1, 2, 3, 4)
        assertEquals(actual, expected)
    }

    @Test
    fun streamReadUtf8() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        assertThrows<IllegalArgumentException> { stream.readUtf8(-42) }
        val actual = stream.readUtf8(4)
        val expected = Utf8.of(1, 2, 3, 4)
        assertEquals(actual, expected)
    }

    @Test
    fun lengthEncodingErrors() {
        var utf8 = Utf8.of(0xc0.toByte())
        assertThrows<JayoCharacterCodingException> { utf8.length() }
        utf8 = makeUtf8Segments(utf8)
        assertThrows<JayoCharacterCodingException> { utf8.length() }
        utf8 = Utf8.of(0xe2.toByte())
        assertThrows<JayoCharacterCodingException> { utf8.length() }
        utf8 = makeUtf8Segments(utf8)
        assertThrows<JayoCharacterCodingException> { utf8.length() }
        utf8 = Utf8.of(0xf4.toByte())
        assertThrows<JayoCharacterCodingException> { utf8.length() }
        utf8 = makeUtf8Segments(utf8)
        assertThrows<JayoCharacterCodingException> { utf8.length() }
        utf8 = Utf8.of(0xff.toByte())
        assertThrows<JayoCharacterCodingException> { utf8.length() }
        utf8 = makeUtf8Segments(utf8)
        assertThrows<JayoCharacterCodingException> { utf8.length() }
    }

    @Test
    fun codePointsUtf8Replacement() {
        var utf8 = Utf8.of(0xc0.toByte())
        assertThat(utf8.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8 = makeUtf8Segments(utf8)
        assertThat(utf8.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8 = Utf8.of(0xe2.toByte())
        assertThat(utf8.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8 = makeUtf8Segments(utf8)
        assertThat(utf8.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8 = Utf8.of(0xf4.toByte())
        assertThat(utf8.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8 = makeUtf8Segments(utf8)
        assertThat(utf8.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8 = Utf8.of(0xff.toByte())
        assertThat(utf8.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8 = makeUtf8Segments(utf8)
        assertThat(utf8.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8 = Utf8.of(0xc0.toByte(), 0x01.toByte())
        assertThat(utf8.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8 = makeUtf8Segments(utf8)
        assertThat(utf8.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8 = Utf8.of(0xc0.toByte(), 0x80.toByte())
        assertThat(utf8.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8 = makeUtf8Segments(utf8)
        assertThat(utf8.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8 = Utf8.of(0xf4.toByte(), 0xb0.toByte(), 0x80.toByte(), 0x80.toByte())
        assertThat(utf8.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
        utf8 = makeUtf8Segments(utf8)
        assertThat(utf8.codePoints())
            .containsExactly(UTF8_REPLACEMENT_CODE_POINT)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun substring(factory: Utf8Factory) {
        val utf8 = factory.encodeUtf8(ASCII)
        assertEquals(utf8.substring(0, 3), "abc".encodeToUtf8())
        assertEquals(utf8.substring(3), "def".encodeToUtf8())
        assertEquals(utf8.substring(1, 5), "bcde".encodeToUtf8())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun lengthAndDecodeUtf8(factory: Utf8Factory) {
        var utf8 = factory.encodeUtf8(ASCII)
        assertEquals(ASCII.length, utf8.length())
        assertEquals(ASCII, utf8.decodeToString())
        if (!factory.isAscii) {
            utf8 = factory.encodeUtf8(UTF8_NO_SURROGATE)
            assertEquals(UTF8_NO_SURROGATE.length, utf8.length())
            assertEquals(UTF8_NO_SURROGATE, utf8.decodeToString())
            utf8 = factory.encodeUtf8(UTF8_SURROGATES)
            assertEquals(UTF8_SURROGATES.length, utf8.length())
            assertEquals(UTF8_SURROGATES, utf8.decodeToString())
            utf8 = factory.encodeUtf8(LAST_3_BYTES_CHARACTER)
            assertEquals(LAST_3_BYTES_CHARACTER.length, utf8.length())
            assertEquals(LAST_3_BYTES_CHARACTER, utf8.decodeToString())
            utf8 = factory.encodeUtf8(FIRST_4_BYTES_CHARACTER)
            assertEquals(FIRST_4_BYTES_CHARACTER.length, utf8.length())
            assertEquals(FIRST_4_BYTES_CHARACTER, utf8.decodeToString())
            utf8 = factory.encodeUtf8(LAST_4_BYTES_CHARACTER)
            assertEquals(LAST_4_BYTES_CHARACTER.length, utf8.length())
            assertEquals(LAST_4_BYTES_CHARACTER, utf8.decodeToString())

            if (!factory.isOneBytePerSegment) {
                val length = 20_000
                val part = UTF8

                // Make all the strings the same length for comparison
                val builder = StringBuilder()
                while (builder.length < length) {
                    builder.append(part)
                }
                val value = builder.toString()

                utf8 = factory.encodeUtf8(value)
                assertEquals(value.length, utf8.length())
                assertEquals(value, utf8.decodeToString())
            }
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun codePoints(factory: Utf8Factory) {
        var utf8 = factory.encodeUtf8(ASCII)
        assertThat(utf8.codePoints())
            .containsExactly('a'.code, 'b'.code, 'c'.code, 'd'.code, 'e'.code, 'f'.code)
        if (!factory.isAscii) {
            utf8 = factory.encodeUtf8(UTF8_NO_SURROGATE)
            assertThat(utf8.codePoints())
                .containsExactly(
                    'C'.code, 'ａ'.code, 'f'.code, 'é'.code, ' '.code,
                    *"🍩".codePoints().toArray().toTypedArray(), '!'.code
                )
            utf8 = factory.encodeUtf8(UTF8_SURROGATES)
            assertThat(utf8.codePoints())
                .containsExactly(
                    'C'.code, 'ａ'.code, 'f'.code, 'e'.code, '́'.code, ' '.code,
                    *"🍩".codePoints().toArray().toTypedArray(), '!'.code
                )
            // force generation of utf8 string
            utf8.decodeToString()
            assertThat(utf8.codePoints())
                .containsExactly(
                    'C'.code, 'ａ'.code, 'f'.code, 'e'.code, '́'.code, ' '.code,
                    *"🍩".codePoints().toArray().toTypedArray(), '!'.code
                )
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun codePointsIterator(factory: Utf8Factory) {
        var utf8 = Utf8.EMPTY
        assertThat(utf8.codePoints().iterator().hasNext()).isFalse()
        assertThrows<NoSuchElementException> { utf8.codePoints().iterator().nextInt() }
        utf8 = factory.encodeUtf8(ASCII)
        assertThatIterator(utf8.codePoints().iterator()).toIterable()
            .containsExactly('a'.code, 'b'.code, 'c'.code, 'd'.code, 'e'.code, 'f'.code)
        if (!factory.isAscii) {
            utf8 = factory.encodeUtf8(UTF8_NO_SURROGATE)
            assertThatIterator(utf8.codePoints().iterator()).toIterable()
                .containsExactly(
                    'C'.code, 'ａ'.code, 'f'.code, 'é'.code, ' '.code,
                    *"🍩".codePoints().toArray().toTypedArray(), '!'.code
                )
            utf8 = factory.encodeUtf8(UTF8_SURROGATES)
            assertThatIterator(utf8.codePoints().iterator()).toIterable()
                .containsExactly(
                    'C'.code, 'ａ'.code, 'f'.code, 'e'.code, '́'.code, ' '.code,
                    *"🍩".codePoints().toArray().toTypedArray(), '!'.code
                )
        }
    }
}
