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
import jayo.Utf8String;
import kotlin.text.Charsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import static jayo.internal.TestUtil.assertByteArraysEquals;
import static jayo.internal.TestUtil.assertEquivalent;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

public final class ByteStringJavaTest {
    public static Stream<Arguments> parameters() {
        return Stream.of(
                Arguments.of(ByteStringFactory.getBYTE_STRING(), "ByteString"),
                Arguments.of(ByteStringFactory.getUTF8_STRING(), "Utf8String"),
                Arguments.of(ByteStringFactory.getSEGMENTED_BYTE_STRING(), "SegmentedByteString"),
                Arguments.of(ByteStringFactory.getONE_BYTE_PER_SEGMENT(),
                        "SegmentedByteString (one-byte-at-a-time)"),
                Arguments.of(ByteStringFactory.getSEGMENTED_UTF8_STRING(), "SegmentedUtf8String"),
                Arguments.of(ByteStringFactory.getUTF8_ONE_BYTE_PER_SEGMENT(),
                        "SegmentedUtf8String (one-byte-at-a-time)")
        );
    }

    @Test
    public void ofCopy() {
        byte[] bytes = "Hello, World!".getBytes(Charsets.UTF_8);
        ByteString byteString = ByteString.of(bytes);
        // Verify that the bytes were copied out.
        bytes[4] = (byte) 'a';
        assertEquals("Hello, World!", byteString.decodeToUtf8());
    }

    @Test
    public void ofCopyRange() {
        byte[] bytes = "Hello, World!".getBytes(Charsets.UTF_8);
        ByteString byteString = ByteString.of(bytes, 2, 9);
        // Verify that the bytes were copied out.
        bytes[4] = (byte) 'a';
        assertEquals("llo, Worl", byteString.decodeToUtf8());
    }

    @Test
    public void ofByteBuffer() {
        byte[] bytes = "Hello, World!".getBytes(Charsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.position(2).limit(11);
        ByteString byteString = ByteString.of(byteBuffer);
        // Verify that the bytes were copied out.
        byteBuffer.put(4, (byte) 'a');
        assertEquals("llo, Worl", byteString.decodeToUtf8());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void byteAtByte(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("ab12");
        assertEquals(byteString.byteSize(), 2);
        assertEquals(-85, byteString.getByte(0));
        assertEquals(18, byteString.getByte(1));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void byteAtByteOutOfBounds(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("ab12");
        assertThatThrownBy(() -> byteString.getByte(2))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void startsWithByteString(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("112233");
        assertTrue(byteString.startsWith(ByteString.decodeHex("")));
        assertTrue(byteString.startsWith(ByteString.decodeHex("11")));
        assertTrue(byteString.startsWith(ByteString.decodeHex("1122")));
        assertTrue(byteString.startsWith(ByteString.decodeHex("112233")));
        assertFalse(byteString.startsWith(ByteString.decodeHex("2233")));
        assertFalse(byteString.startsWith(ByteString.decodeHex("11223344")));
        assertFalse(byteString.startsWith(ByteString.decodeHex("112244")));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void endsWithByteString(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("112233");
        assertTrue(byteString.endsWith(ByteString.decodeHex("")));
        assertTrue(byteString.endsWith(ByteString.decodeHex("33")));
        assertTrue(byteString.endsWith(ByteString.decodeHex("2233")));
        assertTrue(byteString.endsWith(ByteString.decodeHex("112233")));
        assertFalse(byteString.endsWith(ByteString.decodeHex("1122")));
        assertFalse(byteString.endsWith(ByteString.decodeHex("00112233")));
        assertFalse(byteString.endsWith(ByteString.decodeHex("002233")));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void startsWithByteArray(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("112233");
        assertTrue(byteString.startsWith(ByteString.decodeHex("").toByteArray()));
        assertTrue(byteString.startsWith(ByteString.decodeHex("11").toByteArray()));
        assertTrue(byteString.startsWith(ByteString.decodeHex("1122").toByteArray()));
        assertTrue(byteString.startsWith(ByteString.decodeHex("112233").toByteArray()));
        assertFalse(byteString.startsWith(ByteString.decodeHex("2233").toByteArray()));
        assertFalse(byteString.startsWith(ByteString.decodeHex("11223344").toByteArray()));
        assertFalse(byteString.startsWith(ByteString.decodeHex("112244").toByteArray()));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void endsWithByteArray(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("112233");
        assertTrue(byteString.endsWith(ByteString.decodeHex("").toByteArray()));
        assertTrue(byteString.endsWith(ByteString.decodeHex("33").toByteArray()));
        assertTrue(byteString.endsWith(ByteString.decodeHex("2233").toByteArray()));
        assertTrue(byteString.endsWith(ByteString.decodeHex("112233").toByteArray()));
        assertFalse(byteString.endsWith(ByteString.decodeHex("1122").toByteArray()));
        assertFalse(byteString.endsWith(ByteString.decodeHex("00112233").toByteArray()));
        assertFalse(byteString.endsWith(ByteString.decodeHex("002233").toByteArray()));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void indexOfByteString(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("112233");
        assertEquals(0, byteString.indexOf(ByteString.decodeHex("112233")));
        assertEquals(0, byteString.indexOf(ByteString.decodeHex("1122")));
        assertEquals(0, byteString.indexOf(ByteString.decodeHex("11")));
        assertEquals(0, byteString.indexOf(ByteString.decodeHex("11"), 0));
        assertEquals(0, byteString.indexOf(ByteString.decodeHex("")));
        assertEquals(0, byteString.indexOf(ByteString.decodeHex(""), 0));
        assertEquals(1, byteString.indexOf(ByteString.decodeHex("2233")));
        assertEquals(1, byteString.indexOf(ByteString.decodeHex("22")));
        assertEquals(1, byteString.indexOf(ByteString.decodeHex("22"), 1));
        assertEquals(1, byteString.indexOf(ByteString.decodeHex(""), 1));
        assertEquals(2, byteString.indexOf(ByteString.decodeHex("33")));
        assertEquals(2, byteString.indexOf(ByteString.decodeHex("33"), 2));
        assertEquals(2, byteString.indexOf(ByteString.decodeHex(""), 2));
        assertEquals(3, byteString.indexOf(ByteString.decodeHex(""), 3));
        assertEquals(-1, byteString.indexOf(ByteString.decodeHex("112233"), 1));
        assertEquals(-1, byteString.indexOf(ByteString.decodeHex("44")));
        assertEquals(-1, byteString.indexOf(ByteString.decodeHex("11223344")));
        assertEquals(-1, byteString.indexOf(ByteString.decodeHex("112244")));
        assertEquals(-1, byteString.indexOf(ByteString.decodeHex("112233"), 1));
        assertEquals(-1, byteString.indexOf(ByteString.decodeHex("2233"), 2));
        assertEquals(-1, byteString.indexOf(ByteString.decodeHex("33"), 3));
        assertEquals(-1, byteString.indexOf(ByteString.decodeHex(""), 4));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void indexOfWithOffset(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("112233112233");
        assertEquals(0, byteString.indexOf(ByteString.decodeHex("112233"), -1));
        assertEquals(0, byteString.indexOf(ByteString.decodeHex("112233"), 0));
        assertEquals(0, byteString.indexOf(ByteString.decodeHex("112233")));
        assertEquals(3, byteString.indexOf(ByteString.decodeHex("112233"), 1));
        assertEquals(3, byteString.indexOf(ByteString.decodeHex("112233"), 2));
        assertEquals(3, byteString.indexOf(ByteString.decodeHex("112233"), 3));
        assertEquals(-1, byteString.indexOf(ByteString.decodeHex("112233"), 4));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void indexOfByteArray(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("112233");
        assertEquals(0, byteString.indexOf(ByteString.decodeHex("112233").toByteArray()));
        assertEquals(1, byteString.indexOf(ByteString.decodeHex("2233").toByteArray()));
        assertEquals(2, byteString.indexOf(ByteString.decodeHex("33").toByteArray()));
        assertEquals(-1, byteString.indexOf(ByteString.decodeHex("112244").toByteArray()));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void lastIndexOfByteString(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("112233");
        assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("112233")));
        assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("1122")));
        assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("11")));
        assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("11"), 3));
        assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("11"), 0));
        assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex(""), 0));
        assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex("2233")));
        assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex("22")));
        assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex("22"), 3));
        assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex("22"), 1));
        assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex(""), 1));
        assertEquals(2, byteString.lastIndexOf(ByteString.decodeHex("33")));
        assertEquals(2, byteString.lastIndexOf(ByteString.decodeHex("33"), 3));
        assertEquals(2, byteString.lastIndexOf(ByteString.decodeHex("33"), 2));
        assertEquals(2, byteString.lastIndexOf(ByteString.decodeHex(""), 2));
        assertEquals(3, byteString.lastIndexOf(ByteString.decodeHex(""), 3));
        assertEquals(3, byteString.lastIndexOf(ByteString.decodeHex("")));
        assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("112233"), -1));
        assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("112233"), -2));
        assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("44")));
        assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("11223344")));
        assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("112244")));
        assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("2233"), 0));
        assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("33"), 1));
        assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex(""), -1));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void lastIndexOfByteArray(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("112233");
        //assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("112233").toByteArray()));
        assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex("2233").toByteArray()));
        assertEquals(2, byteString.lastIndexOf(ByteString.decodeHex("33").toByteArray()));
        assertEquals(3, byteString.lastIndexOf(ByteString.decodeHex("").toByteArray()));
    }

    @SuppressWarnings("SelfEquals")
    @ParameterizedTest
    @MethodSource("parameters")
    public void equals(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("000102");
        assertEquals(byteString, byteString);
        assertEquals(byteString, ByteString.decodeHex("000102"));
        assertEquals(factory.decodeHex(""), ByteString.EMPTY);
        assertEquals(factory.decodeHex(""), ByteString.of());
        assertEquals(ByteString.EMPTY, factory.decodeHex(""));
        assertEquals(ByteString.of(), factory.decodeHex(""));
        assertNotEquals(byteString, new Object());
        assertNotEquals(byteString, ByteString.decodeHex("000201"));
    }

    private final String bronzeHorseman = "На берегу пустынных волн";

    @ParameterizedTest
    @MethodSource("parameters")
    public void utf8(ByteStringFactory factory) {
        ByteString byteString = factory.encodeUtf8(bronzeHorseman);
        assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.getBytes(Charsets.UTF_8));
        assertEquals(byteString, ByteString.of(bronzeHorseman.getBytes(Charsets.UTF_8)));
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
        ByteString byteString = ByteString.encode(bronzeHorseman, utf8);
        assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.getBytes(utf8));
        assertEquals(byteString, ByteString.decodeHex("d09dd0b020d0b1d0b5d180d0b5d0b3d18320d0bfd183d181"
                + "d182d18bd0bdd0bdd18bd18520d0b2d0bed0bbd0bd"));
        assertEquals(bronzeHorseman, byteString.decodeToString(utf8));
    }

    @Test
    public void encodeDecodeStringUtf16be() {
        Charset utf16be = StandardCharsets.UTF_16BE;
        ByteString byteString = ByteString.encode(bronzeHorseman, utf16be);
        assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.getBytes(utf16be));
        assertEquals(byteString, ByteString.decodeHex("041d043000200431043504400435043304430020043f0443"
                + "04410442044b043d043d044b044500200432043e043b043d"));
        assertEquals(bronzeHorseman, byteString.decodeToString(utf16be));
    }

    @Test
    public void encodeDecodeStringUtf32be() {
        Charset utf32be = Charset.forName("UTF-32BE");
        ByteString byteString = ByteString.encode(bronzeHorseman, utf32be);
        assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.getBytes(utf32be));
        assertEquals(byteString, ByteString.decodeHex("0000041d0000043000000020000004310000043500000440"
                + "000004350000043300000443000000200000043f0000044300000441000004420000044b0000043d0000043d"
                + "0000044b0000044500000020000004320000043e0000043b0000043d"));
        assertEquals(bronzeHorseman, byteString.decodeToString(utf32be));
    }

    @Test
    public void encodeDecodeStringAsciiIsLossy() {
        Charset ascii = StandardCharsets.US_ASCII;
        ByteString byteString = ByteString.encode(bronzeHorseman, ascii);
        assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.getBytes(ascii));
        assertEquals(byteString,
                ByteString.decodeHex("3f3f203f3f3f3f3f3f203f3f3f3f3f3f3f3f3f203f3f3f3f"));
        assertEquals("?? ?????? ????????? ????", byteString.decodeToString(ascii));
    }

    @Test
    public void decodeMalformedStringReturnsReplacementCharacter() {
        Charset utf16be = StandardCharsets.UTF_16BE;
        String string = ByteString.decodeHex("04").decodeToString(utf16be);
        assertEquals("\ufffd", string);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testHashCode(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("0102");
        assertEquals(byteString.hashCode(), byteString.hashCode());
        assertEquals(byteString.hashCode(), ByteString.decodeHex("0102").hashCode());
    }

    @Test
    public void read() {
        InputStream in = new ByteArrayInputStream("abc".getBytes(Charsets.UTF_8));
        assertEquals(ByteString.decodeHex("6162"), ByteString.read(in, 2));
        assertEquals(ByteString.decodeHex("63"), ByteString.read(in, 1));
        assertEquals(ByteString.of(), ByteString.read(in, 0));
    }

    @Test
    public void readAndToLowercase() {
        InputStream in = new ByteArrayInputStream("ABC".getBytes(Charsets.UTF_8));
        assertEquals(Utf8String.encodeUtf8("ab"), ByteString.read(in, 2).toAsciiLowercase());
        assertEquals(Utf8String.encodeUtf8("c"), ByteString.read(in, 1).toAsciiLowercase());
        assertEquals(ByteString.EMPTY, ByteString.read(in, 0).toAsciiLowercase());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void toAsciiLowerCaseNoUppercase(ByteStringFactory factory) {
        ByteString s = factory.encodeUtf8("a1_+");
        assertEquals(s, s.toAsciiLowercase());
        if (factory == ByteStringFactory.getBYTE_STRING()) {
            assertSame(s, s.toAsciiLowercase());
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void toAsciiAllUppercase(ByteStringFactory factory) {
        assertEquals(Utf8String.encodeUtf8("ab"), factory.encodeUtf8("AB").toAsciiLowercase());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void toAsciiStartsLowercaseEndsUppercase(ByteStringFactory factory) {
        assertEquals(Utf8String.encodeUtf8("abcd"), factory.encodeUtf8("aBcD").toAsciiLowercase());
    }

    @Test
    public void readAndToUppercase() {
        InputStream in = new ByteArrayInputStream("abc".getBytes(Charsets.UTF_8));
        assertEquals(Utf8String.encodeUtf8("AB"), ByteString.read(in, 2).toAsciiUppercase());
        assertEquals(Utf8String.encodeUtf8("C"), ByteString.read(in, 1).toAsciiUppercase());
        assertEquals(ByteString.EMPTY, ByteString.read(in, 0).toAsciiUppercase());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void toAsciiStartsUppercaseEndsLowercase(ByteStringFactory factory) {
        assertEquals(Utf8String.encodeUtf8("ABCD"), factory.encodeUtf8("AbCd").toAsciiUppercase());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void substring(ByteStringFactory factory) {
        ByteString byteString = factory.encodeUtf8("Hello, World!");

        assertEquals(byteString.substring(0), byteString);
        assertEquals(byteString.substring(0, 5), Utf8String.encodeUtf8("Hello"));
        assertEquals(byteString.substring(7), Utf8String.encodeUtf8("World!"));
        assertEquals(byteString.substring(6, 6), Utf8String.encodeUtf8(""));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void substringWithInvalidBounds(ByteStringFactory factory) {
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
    public void write(ByteStringFactory factory) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        factory.decodeHex("616263").write(out);
        assertByteArraysEquals(new byte[]{0x61, 0x62, 0x63}, out.toByteArray());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void encodeBase64(ByteStringFactory factory) {
        assertEquals("", factory.encodeUtf8("").base64());
        assertEquals("AA==", factory.encodeUtf8("\u0000").base64());
        assertEquals("AAA=", factory.encodeUtf8("\u0000\u0000").base64());
        assertEquals("AAAA", factory.encodeUtf8("\u0000\u0000\u0000").base64());
        assertEquals("SG93IG1hbnkgbGluZXMgb2YgY29kZSBhcmUgdGhlcmU/ICdib3V0IDIgbWlsbGlvbi4=",
                factory.encodeUtf8("How many lines of code are there? 'bout 2 million.").base64());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void encodeBase64Url(ByteStringFactory factory) {
        assertEquals("", factory.encodeUtf8("").base64Url());
        assertEquals("AA==", factory.encodeUtf8("\u0000").base64Url());
        assertEquals("AAA=", factory.encodeUtf8("\u0000\u0000").base64Url());
        assertEquals("AAAA", factory.encodeUtf8("\u0000\u0000\u0000").base64Url());
        assertEquals("SG93IG1hbnkgbGluZXMgb2YgY29kZSBhcmUgdGhlcmU_ICdib3V0IDIgbWlsbGlvbi4=",
                factory.encodeUtf8("How many lines of code are there? 'bout 2 million.").base64Url());
    }

    @Test
    public void ignoreUnnecessaryPadding() {
        assertEquals("", ByteString.decodeBase64("====").decodeToUtf8());
        assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64("AAAA====").decodeToUtf8());
    }

    @Test
    public void decodeBase64() {
        assertEquals("", ByteString.decodeBase64("").decodeToUtf8());
        assertNull(ByteString.decodeBase64("/===")); // Can't do anything with 6 bits!
        assertEquals(ByteString.decodeHex("ff"), ByteString.decodeBase64("//=="));
        assertEquals(ByteString.decodeHex("ff"), ByteString.decodeBase64("__=="));
        assertEquals(ByteString.decodeHex("ffff"), ByteString.decodeBase64("///="));
        assertEquals(ByteString.decodeHex("ffff"), ByteString.decodeBase64("___="));
        assertEquals(ByteString.decodeHex("ffffff"), ByteString.decodeBase64("////"));
        assertEquals(ByteString.decodeHex("ffffff"), ByteString.decodeBase64("____"));
        assertEquals(ByteString.decodeHex("ffffffffffff"), ByteString.decodeBase64("////////"));
        assertEquals(ByteString.decodeHex("ffffffffffff"), ByteString.decodeBase64("________"));
        assertEquals("What's to be scared about? It's just a little hiccup in the power...",
                ByteString.decodeBase64("V2hhdCdzIHRvIGJlIHNjYXJlZCBhYm91dD8gSXQncyBqdXN0IGEgbGl0dGxlIGhpY2"
                        + "N1cCBpbiB0aGUgcG93ZXIuLi4=").decodeToUtf8());
        // Uses two encoding styles. Malformed, but supported as a side-effect.
        assertEquals(ByteString.decodeHex("ffffff"), ByteString.decodeBase64("__//"));
    }

    @Test
    public void decodeBase64WithWhitespace() {
        assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA AA ").decodeToUtf8());
        assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA A\r\nA ").decodeToUtf8());
        assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64("AA AA").decodeToUtf8());
        assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA AA ").decodeToUtf8());
        assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA A\r\nA ").decodeToUtf8());
        assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64("A    AAA").decodeToUtf8());
        assertEquals("", ByteString.decodeBase64("    ").decodeToUtf8());
    }

    @Test
    public void encodeHex() {
        assertEquals("000102", ByteString.of((byte) 0x0, (byte) 0x1, (byte) 0x2).hex());
    }

    @Test
    public void decodeHex() {
        assertEquals(ByteString.of((byte) 0x0, (byte) 0x1, (byte) 0x2), ByteString.decodeHex("000102"));
    }

    @Test
    public void decodeHexOddNumberOfChars() {
        try {
            ByteString.decodeHex("aaa");
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void decodeHexInvalidChar() {
        try {
            ByteString.decodeHex("a\u0000");
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void toStringOnEmpty(ByteStringFactory factory) {
        assertEquals("ByteString(size=0)", factory.decodeHex("").toString());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void toStringOnTextWithNewlines(ByteStringFactory factory) {
        if (factory == ByteStringFactory.getUTF8_STRING()) {
            return;
        }
        // Instead of emitting a literal newline in the toString(), these are escaped as "\n".
        assertEquals("ByteString(size=10 hex=610d0a620a630d645c65)",
                factory.encodeUtf8("a\r\nb\nc\rd\\e").toString());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void toStringOnData(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex(
                "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55"
                        + "4bf0b54023c29b624de9ef9c2f931efc580f9afb");
        assertEquals("ByteString(size=64 hex="
                + "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55"
                + "4bf0b54023c29b624de9ef9c2f931efc580f9afb)", byteString.toString());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void javaSerializationTestNonEmpty(ByteStringFactory factory) {
        ByteString byteString = factory.encodeUtf8(bronzeHorseman);
        assertEquivalent(byteString, TestUtil.reserialize(byteString));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void javaSerializationTestEmpty(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("");
        assertEquivalent(byteString, TestUtil.reserialize(byteString));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void compareToSingleBytes(ByteStringFactory factory) {
        List<ByteString> originalByteStrings = Arrays.asList(
                factory.decodeHex("00"),
                factory.decodeHex("01"),
                factory.decodeHex("7e"),
                factory.decodeHex("7f"),
                factory.decodeHex("80"),
                factory.decodeHex("81"),
                factory.decodeHex("fe"),
                factory.decodeHex("ff"));

        List<ByteString> sortedByteStrings = new ArrayList<>(originalByteStrings);
        Collections.shuffle(sortedByteStrings, new Random(0));
        Collections.sort(sortedByteStrings);

        assertEquals(originalByteStrings, sortedByteStrings);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void compareToMultipleBytes(ByteStringFactory factory) {
        List<ByteString> originalByteStrings = Arrays.asList(
                factory.decodeHex(""),
                factory.decodeHex("00"),
                factory.decodeHex("0000"),
                factory.decodeHex("000000"),
                factory.decodeHex("00000000"),
                factory.decodeHex("0000000000"),
                factory.decodeHex("0000000001"),
                factory.decodeHex("000001"),
                factory.decodeHex("00007f"),
                factory.decodeHex("0000ff"),
                factory.decodeHex("000100"),
                factory.decodeHex("000101"),
                factory.decodeHex("007f00"),
                factory.decodeHex("00ff00"),
                factory.decodeHex("010000"),
                factory.decodeHex("010001"),
                factory.decodeHex("01007f"),
                factory.decodeHex("0100ff"),
                factory.decodeHex("010100"),
                factory.decodeHex("01010000"),
                factory.decodeHex("0101000000"),
                factory.decodeHex("0101000001"),
                factory.decodeHex("010101"),
                factory.decodeHex("7f0000"),
                factory.decodeHex("7f0000ffff"),
                factory.decodeHex("ffffff"));

        List<ByteString> sortedByteStrings = new ArrayList<>(originalByteStrings);
        Collections.shuffle(sortedByteStrings, new Random(0));
        Collections.sort(sortedByteStrings);

        assertEquals(originalByteStrings, sortedByteStrings);
    }

    @Test
    public void asByteBuffer() {
        assertEquals(0x42, ByteString.of((byte) 0x41, (byte) 0x42, (byte) 0x43).asByteBuffer().get(1));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void getByte(ByteStringFactory factory) {
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
