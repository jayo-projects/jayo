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

package jayo.internal;

import jayo.ByteString;
import jayo.Utf8ByteString;
import kotlin.text.Charsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static jayo.internal.TestUtil.assertByteArraysEquals;
import static jayo.internal.TestUtil.assertEquivalent;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

public final class Utf8ByteStringJavaTest {
    public static Stream<Arguments> parameters() {
        return Stream.of(
                Arguments.of(Utf8ByteStringFactory.getUTF8_BYTE_STRING(), "Utf8ByteString"),
                Arguments.of(
                        Utf8ByteStringFactory.getUTF8_BYTE_STRING_FROM_BYTES(),
                        "Utf8ByteString (from bytes)"),
                Arguments.of(
                        Utf8ByteStringFactory.getUTF8_BYTE_STRING_FROM_BYTES_NO_COMPACT_STRING(),
                        "Utf8ByteString (from bytes without compact string)"
                )/*,
                Arguments.of(Utf8ByteStringFactory.getSEGMENTED_BYTE_STRING(), "SegmentedByteString"),
                Arguments.of(Utf8ByteStringFactory.getONE_BYTE_PER_SEGMENT(), "SegmentedByteString (one-byte-at-a-time)")*/
        );
    }

    @Test
    public void ofCopy() {
        byte[] bytes = "Hello, World!".getBytes(Charsets.UTF_8);
        ByteString byteString = Utf8ByteString.ofUtf8(bytes);
        // Verify that the bytes were copied out.
        bytes[4] = (byte) 'a';
        assertEquals("Hello, World!", byteString.decodeToUtf8());
    }

    @Test
    public void ofCopyRange() {
        byte[] bytes = "Hello, World!".getBytes(Charsets.UTF_8);
        ByteString byteString = Utf8ByteString.ofUtf8(bytes, 2, 9);
        // Verify that the bytes were copied out.
        bytes[4] = (byte) 'a';
        assertEquals("llo, Worl", byteString.decodeToUtf8());
    }

    @Test
    public void ofByteBuffer() {
        byte[] bytes = "Hello, World!".getBytes(Charsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.position(2).limit(11);
        ByteString byteString = Utf8ByteString.ofUtf8(byteBuffer);
        // Verify that the bytes were copied out.
        byteBuffer.put(4, (byte) 'a');
        assertEquals("llo, Worl", byteString.decodeToUtf8());
    }

    private final String bronzeHorseman = "На берегу пустынных волн";

    @ParameterizedTest
    @MethodSource("parameters")
    public void utf8(Utf8ByteStringFactory factory) {
        ByteString byteString = factory.encodeUtf8(bronzeHorseman);
        assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.getBytes(Charsets.UTF_8));
        assertEquals(byteString, Utf8ByteString.ofUtf8(bronzeHorseman.getBytes(Charsets.UTF_8)));
        assertEquals(byteString.decodeToUtf8(), bronzeHorseman);
    }

    @Test
    public void encodeNullCharset() {
        try {
            ByteString.encode("hello", null);
            fail();
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void encodeNullString() {
        try {
            ByteString.encode(null, StandardCharsets.UTF_8);
            fail();
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void decodeNullCharset() {
        try {
            ByteString.of().decodeToString(null);
            fail();
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void encodeDecodeStringUtf8() {
        Charset utf8 = StandardCharsets.UTF_8;
        ByteString byteString = Utf8ByteString.encodeUtf8(bronzeHorseman);
        assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.getBytes(utf8));
        assertEquals(byteString, ByteString.decodeHex("d09dd0b020d0b1d0b5d180d0b5d0b3d18320d0bfd183d181"
                + "d182d18bd0bdd0bdd18bd18520d0b2d0bed0bbd0bd"));
        assertEquals(bronzeHorseman, byteString.decodeToString(utf8));
    }

    @Test
    public void read() {
        InputStream in = new ByteArrayInputStream("abc".getBytes(Charsets.UTF_8));
        assertEquals(ByteString.decodeHex("6162"), Utf8ByteString.readUtf8(in, 2));
        assertEquals(ByteString.decodeHex("63"), Utf8ByteString.readUtf8(in, 1));
        assertEquals(ByteString.of(), Utf8ByteString.readUtf8(in, 0));
    }

    @Test
    public void readAndToLowercase() {
        InputStream in = new ByteArrayInputStream("ABC".getBytes(Charsets.UTF_8));
        assertEquals(Utf8ByteString.encodeUtf8("ab"), Utf8ByteString.readUtf8(in, 2).toAsciiLowercase());
        assertEquals(Utf8ByteString.encodeUtf8("c"), Utf8ByteString.readUtf8(in, 1).toAsciiLowercase());
        assertEquals(ByteString.EMPTY, Utf8ByteString.readUtf8(in, 0).toAsciiLowercase());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void toAsciiLowerCaseNoUppercase(Utf8ByteStringFactory factory) {
        ByteString s = factory.encodeUtf8("a1_+");
        assertEquals(s, s.toAsciiLowercase());
        if (factory == Utf8ByteStringFactory.getUTF8_BYTE_STRING()) {
            assertSame(s, s.toAsciiLowercase());
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void toAsciiAllUppercase(Utf8ByteStringFactory factory) {
        assertEquals(Utf8ByteString.encodeUtf8("ab"), factory.encodeUtf8("AB").toAsciiLowercase());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void toAsciiStartsLowercaseEndsUppercase(Utf8ByteStringFactory factory) {
        assertEquals(Utf8ByteString.encodeUtf8("abcd"), factory.encodeUtf8("abCD").toAsciiLowercase());
    }

    @Test
    public void readAndToUppercase() {
        InputStream in = new ByteArrayInputStream("abc".getBytes(Charsets.UTF_8));
        assertEquals(Utf8ByteString.encodeUtf8("AB"), Utf8ByteString.readUtf8(in, 2).toAsciiUppercase());
        assertEquals(Utf8ByteString.encodeUtf8("C"), Utf8ByteString.readUtf8(in, 1).toAsciiUppercase());
        assertEquals(ByteString.EMPTY, Utf8ByteString.readUtf8(in, 0).toAsciiUppercase());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void toAsciiStartsUppercaseEndsLowercase(Utf8ByteStringFactory factory) {
        assertEquals(Utf8ByteString.encodeUtf8("ABCD"), factory.encodeUtf8("ABcd").toAsciiUppercase());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void substring(Utf8ByteStringFactory factory) {
        ByteString byteString = factory.encodeUtf8("Hello, World!");

        assertEquals(byteString.substring(0), byteString);
        assertEquals(byteString.substring(0, 5), Utf8ByteString.encodeUtf8("Hello"));
        assertEquals(byteString.substring(7), Utf8ByteString.encodeUtf8("World!"));
        assertEquals(byteString.substring(6, 6), Utf8ByteString.encodeUtf8(""));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void substringWithInvalidBounds(Utf8ByteStringFactory factory) {
        ByteString byteString = factory.encodeUtf8("Hello, World!");

        try {
            byteString.substring(-1);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            byteString.substring(0, 14);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            byteString.substring(8, 7);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void encodeBase64(Utf8ByteStringFactory factory) {
        assertEquals("", factory.encodeUtf8("").base64());
        assertEquals("AA==", factory.encodeUtf8("\u0000").base64());
        assertEquals("AAA=", factory.encodeUtf8("\u0000\u0000").base64());
        assertEquals("AAAA", factory.encodeUtf8("\u0000\u0000\u0000").base64());
        assertEquals("SG93IG1hbnkgbGluZXMgb2YgY29kZSBhcmUgdGhlcmU/ICdib3V0IDIgbWlsbGlvbi4=",
                factory.encodeUtf8("How many lines of code are there? 'bout 2 million.").base64());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void encodeBase64Url(Utf8ByteStringFactory factory) {
        assertEquals("", factory.encodeUtf8("").base64Url());
        assertEquals("AA==", factory.encodeUtf8("\u0000").base64Url());
        assertEquals("AAA=", factory.encodeUtf8("\u0000\u0000").base64Url());
        assertEquals("AAAA", factory.encodeUtf8("\u0000\u0000\u0000").base64Url());
        assertEquals("SG93IG1hbnkgbGluZXMgb2YgY29kZSBhcmUgdGhlcmU_ICdib3V0IDIgbWlsbGlvbi4=",
                factory.encodeUtf8("How many lines of code are there? 'bout 2 million.").base64Url());
    }

    @Test
    public void encodeHex() {
        assertEquals("000102", Utf8ByteString.ofUtf8((byte) 0x0, (byte) 0x1, (byte) 0x2).hex());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void toStringOnLongTextIsTruncated(Utf8ByteStringFactory factory) {
        String raw = "Um, I'll tell you the problem with the scientific power that you're using here, "
                + "it didn't require any discipline to attain it. You read what others had done and you "
                + "took the next step. You didn't earn the knowledge for yourselves, so you don't take any "
                + "responsibility for it. You stood on the shoulders of geniuses to accomplish something "
                + "as fast as you could, and before you even knew what you had, you patented it, and "
                + "packaged it, and slapped it on a plastic lunchbox, and now you're selling it, you wanna "
                + "sell it.";
        assertEquals(raw, factory.encodeUtf8(raw).toString());
        String war = "Սｍ, I'll 𝓽𝖾ll ᶌօ𝘂 ᴛℎ℮ 𝜚𝕣०ｂl𝖾ｍ ｗі𝕥𝒽 𝘵𝘩𝐞 𝓼𝙘𝐢𝔢𝓷𝗍𝜄𝚏𝑖ｃ 𝛠𝝾ｗ𝚎𝑟 𝕥ｈ⍺𝞃 𝛄𝓸𝘂'𝒓𝗲 υ𝖘𝓲𝗇ɡ 𝕙𝚎𝑟ｅ, "
                + "𝛊𝓽 ⅆ𝕚𝐝𝝿'𝗍 𝔯𝙚𝙦ᴜ𝜾𝒓𝘦 𝔞𝘯𝐲 ԁ𝜄𝑠𝚌ι𝘱lι𝒏ｅ 𝑡𝜎 𝕒𝚝𝖙𝓪і𝞹 𝔦𝚝. 𝒀ο𝗎 𝔯𝑒⍺𝖉 ｗ𝐡𝝰𝔱 𝞂𝞽һ𝓮𝓇ƽ հ𝖺𝖉 ⅾ𝛐𝝅ⅇ 𝝰πԁ 𝔂ᴑᴜ 𝓉ﮨ၀𝚔 "
                + "т𝒽𝑒 𝗇𝕖ⅹ𝚝 𝔰𝒕е𝓅. 𝘠ⲟ𝖚 𝖉ⅰԁ𝝕'τ 𝙚𝚊ｒ𝞹 𝘵Ꮒ𝖾 𝝒𝐧هｗl𝑒𝖉ƍ𝙚 𝓯૦ｒ 𝔂𝞼𝒖𝕣𝑠𝕖l𝙫𝖊𝓼, 𐑈о ｙ𝘰𝒖 ⅆە𝗇'ｔ 𝜏α𝒌𝕖 𝛂𝟉ℽ "
                + "𝐫ⅇ𝗌ⲣ๐ϖ𝖘ꙇᖯ𝓲l𝓲𝒕𝘆 𝐟𝞼𝘳 𝚤𝑡. 𝛶𝛔𝔲 ｓ𝕥σσ𝐝 ﮩ𝕟 𝒕𝗁𝔢 𝘴𝐡𝜎ᴜlⅾ𝓮𝔯𝚜 𝛐𝙛 ᶃ𝚎ᴨᎥս𝚜𝘦𝓈 𝓽𝞸 ａ𝒄𝚌𝞸ｍρl𝛊ꜱ𝐡 𝓈𝚘ｍ𝚎𝞃𝔥⍳𝞹𝔤 𝐚𝗌 𝖋ａ𝐬𝒕 "
                + "αｓ γ𝛐𝕦 𝔠ﻫ𝛖lԁ, 𝚊π𝑑 Ь𝑒𝙛૦𝓇𝘦 𝓎٥𝖚 ⅇｖℯ𝝅 𝜅ո𝒆ｗ ｗ𝗵𝒂𝘁 ᶌ੦𝗎 ｈ𝐚𝗱, 𝜸ﮨ𝒖 𝓹𝝰𝔱𝖾𝗇𝓽𝔢ⅆ і𝕥, 𝚊𝜛𝓭 𝓹𝖺ⅽϰ𝘢ℊеᏧ 𝑖𝞃, "
                + "𝐚𝛑ꓒ 𝙨l𝔞р𝘱𝔢𝓭 ɩ𝗍 ہ𝛑 𝕒 ｐl𝛂ѕᴛ𝗂𝐜 l𝞄ℼ𝔠𝒽𝑏ﮪ⨯, 𝔞ϖ𝒹 ｎ𝛔ｗ 𝛾𝐨𝞄'𝗿𝔢 ꜱ℮ll𝙞ｎɡ ɩ𝘁, 𝙮𝕠𝛖 ｗ𝑎ℼ𝚗𝛂 𝕤𝓮ll 𝙞𝓉.";
        assertEquals(war, factory.encodeUtf8(war).toString());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void javaSerializationTestNonEmpty(Utf8ByteStringFactory factory) {
        ByteString byteString = factory.encodeUtf8(bronzeHorseman);
        assertEquivalent(byteString, TestUtil.reserialize(byteString));
    }

    @Test
    public void asByteBuffer() {
        assertEquals(0x42, Utf8ByteString.ofUtf8((byte) 0x41, (byte) 0x42, (byte) 0x43).asByteBuffer().get(1));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void getByte(Utf8ByteStringFactory factory) {
        final var actual = factory.encodeUtf8("abc");
        assertEquals(3, actual.byteSize());
        assertEquals(actual.getByte(0), (byte) 'a');
        assertEquals(actual.getByte(1), (byte) 'b');
        assertEquals(actual.getByte(2), (byte) 'c');
        assertThatThrownBy(() -> actual.getByte(-1))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> actual.getByte(3))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }
}
