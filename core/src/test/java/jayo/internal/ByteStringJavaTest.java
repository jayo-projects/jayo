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

package jayo.internal;

import kotlin.text.Charsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import jayo.ByteString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static jayo.internal.TestUtil.assertByteArraysEquals;
import static jayo.internal.TestUtil.assertEquivalent;

public final class ByteStringJavaTest {
    public static Stream<Arguments> parameters() {
        return Stream.of(
                Arguments.of(ByteStringFactory.getBYTE_STRING(), "ByteString"),
                Arguments.of(ByteStringFactory.getHEAP_SEGMENTED_BYTE_STRING(), "HeapSegmentedByteString"),
                Arguments.of(ByteStringFactory.getNATIVE_SEGMENTED_BYTE_STRING(), "NativeSegmentedByteString"),
                Arguments.of(ByteStringFactory.getHEAP_ONE_BYTE_PER_SEGMENT(), "HeapSegmentedByteString (one-at-a-time)"),
                Arguments.of(ByteStringFactory.getNATIVE_ONE_BYTE_PER_SEGMENT(), "NativeSegmentedByteString (one-at-a-time)")
        );
    }

    @Test
    public void ofCopy() {
        byte[] bytes = "Hello, World!".getBytes(Charsets.UTF_8);
        ByteString byteString = ByteString.of(bytes);
        // Verify that the bytes were copied out.
        bytes[4] = (byte) 'a';
        assertEquals("Hello, World!", byteString.decodeToString());
    }

    @Test
    public void ofCopyRange() {
        byte[] bytes = "Hello, World!".getBytes(Charsets.UTF_8);
        ByteString byteString = ByteString.of(bytes, 2, 9);
        // Verify that the bytes were copied out.
        bytes[4] = (byte) 'a';
        assertEquals("llo, Worl", byteString.decodeToString());
    }

    @Test
    public void ofByteBuffer() {
        byte[] bytes = "Hello, World!".getBytes(Charsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.position(2).limit(11);
        ByteString byteString = ByteString.of(byteBuffer);
        // Verify that the bytes were copied out.
        byteBuffer.put(4, (byte) 'a');
        assertEquals("llo, Worl", byteString.decodeToString());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void getByte(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("ab12");
        assertEquals(byteString.getSize(), 2);
        assertEquals(-85, byteString.get(0));
        assertEquals(18, byteString.get(1));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void getByteOutOfBounds(ByteStringFactory factory) {
        ByteString byteString = factory.decodeHex("ab12");
        assertThatThrownBy(() -> byteString.get(2))
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
        assertEquals(byteString.decodeToString(), bronzeHorseman);
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
        assertEquals(ByteString.encodeUtf8("ab"), ByteString.read(in, 2).toAsciiLowercase());
        assertEquals(ByteString.encodeUtf8("c"), ByteString.read(in, 1).toAsciiLowercase());
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
        assertEquals(ByteString.encodeUtf8("ab"), factory.encodeUtf8("AB").toAsciiLowercase());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void toAsciiStartsLowercaseEndsUppercase(ByteStringFactory factory) {
        assertEquals(ByteString.encodeUtf8("abcd"), factory.encodeUtf8("abCD").toAsciiLowercase());
    }

    @Test
    public void readAndToUppercase() {
        InputStream in = new ByteArrayInputStream("abc".getBytes(Charsets.UTF_8));
        assertEquals(ByteString.encodeUtf8("AB"), ByteString.read(in, 2).toAsciiUppercase());
        assertEquals(ByteString.encodeUtf8("C"), ByteString.read(in, 1).toAsciiUppercase());
        assertEquals(ByteString.EMPTY, ByteString.read(in, 0).toAsciiUppercase());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void toAsciiStartsUppercaseEndsLowercase(ByteStringFactory factory) {
        assertEquals(ByteString.encodeUtf8("ABCD"), factory.encodeUtf8("ABcd").toAsciiUppercase());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void substring(ByteStringFactory factory) {
        ByteString byteString = factory.encodeUtf8("Hello, World!");

        assertEquals(byteString.substring(0), byteString);
        assertEquals(byteString.substring(0, 5), ByteString.encodeUtf8("Hello"));
        assertEquals(byteString.substring(7), ByteString.encodeUtf8("World!"));
        assertEquals(byteString.substring(6, 6), ByteString.encodeUtf8(""));
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
        assertEquals("", ByteString.decodeBase64("====").decodeToString());
        assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64("AAAA====").decodeToString());
    }

    @Test
    public void decodeBase64() {
        assertEquals("", ByteString.decodeBase64("").decodeToString());
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
                        + "N1cCBpbiB0aGUgcG93ZXIuLi4=").decodeToString());
        // Uses two encoding styles. Malformed, but supported as a side-effect.
        assertEquals(ByteString.decodeHex("ffffff"), ByteString.decodeBase64("__//"));
    }

    @Test
    public void decodeBase64WithWhitespace() {
        assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA AA ").decodeToString());
        assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA A\r\nA ").decodeToString());
        assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64("AA AA").decodeToString());
        assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA AA ").decodeToString());
        assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA A\r\nA ").decodeToString());
        assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64("A    AAA").decodeToString());
        assertEquals("", ByteString.decodeBase64("    ").decodeToString());
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

//    @ParameterizedTest
//    @MethodSource("parameters")
//    public void toStringOnShortText(ByteStringFactory factory) {
//        assertEquals("[text=Tyrannosaur]",
//                factory.encodeUtf8("Tyrannosaur").toString());
//        assertEquals("[text=təˈranəˌsôr]",
//                factory.decodeHex("74c999cb8872616ec999cb8c73c3b472").toString());
//    }
//
//    @ParameterizedTest
//    @MethodSource("parameters")
//    public void toStringOnLongTextIsTruncated(ByteStringFactory factory) {
//        String raw = "Um, I'll tell you the problem with the scientific power that you're using here, "
//                + "it didn't require any discipline to attain it. You read what others had done and you "
//                + "took the next step. You didn't earn the knowledge for yourselves, so you don't take any "
//                + "responsibility for it. You stood on the shoulders of geniuses to accomplish something "
//                + "as fast as you could, and before you even knew what you had, you patented it, and "
//                + "packaged it, and slapped it on a plastic lunchbox, and now you're selling it, you wanna "
//                + "sell it.";
//        assertEquals("[size=517 text=Um, I'll tell you the problem with the scientific power that "
//                + "you…]", factory.encodeUtf8(raw).toString());
//        String war = "Սｍ, I'll 𝓽𝖾ll ᶌօ𝘂 ᴛℎ℮ 𝜚𝕣०ｂl𝖾ｍ ｗі𝕥𝒽 𝘵𝘩𝐞 𝓼𝙘𝐢𝔢𝓷𝗍𝜄𝚏𝑖ｃ 𝛠𝝾ｗ𝚎𝑟 𝕥ｈ⍺𝞃 𝛄𝓸𝘂'𝒓𝗲 υ𝖘𝓲𝗇ɡ 𝕙𝚎𝑟ｅ, "
//                + "𝛊𝓽 ⅆ𝕚𝐝𝝿'𝗍 𝔯𝙚𝙦ᴜ𝜾𝒓𝘦 𝔞𝘯𝐲 ԁ𝜄𝑠𝚌ι𝘱lι𝒏ｅ 𝑡𝜎 𝕒𝚝𝖙𝓪і𝞹 𝔦𝚝. 𝒀ο𝗎 𝔯𝑒⍺𝖉 ｗ𝐡𝝰𝔱 𝞂𝞽һ𝓮𝓇ƽ հ𝖺𝖉 ⅾ𝛐𝝅ⅇ 𝝰πԁ 𝔂ᴑᴜ 𝓉ﮨ၀𝚔 "
//                + "т𝒽𝑒 𝗇𝕖ⅹ𝚝 𝔰𝒕е𝓅. 𝘠ⲟ𝖚 𝖉ⅰԁ𝝕'τ 𝙚𝚊ｒ𝞹 𝘵Ꮒ𝖾 𝝒𝐧هｗl𝑒𝖉ƍ𝙚 𝓯૦ｒ 𝔂𝞼𝒖𝕣𝑠𝕖l𝙫𝖊𝓼, 𐑈о ｙ𝘰𝒖 ⅆە𝗇'ｔ 𝜏α𝒌𝕖 𝛂𝟉ℽ "
//                + "𝐫ⅇ𝗌ⲣ๐ϖ𝖘ꙇᖯ𝓲l𝓲𝒕𝘆 𝐟𝞼𝘳 𝚤𝑡. 𝛶𝛔𝔲 ｓ𝕥σσ𝐝 ﮩ𝕟 𝒕𝗁𝔢 𝘴𝐡𝜎ᴜlⅾ𝓮𝔯𝚜 𝛐𝙛 ᶃ𝚎ᴨᎥս𝚜𝘦𝓈 𝓽𝞸 ａ𝒄𝚌𝞸ｍρl𝛊ꜱ𝐡 𝓈𝚘ｍ𝚎𝞃𝔥⍳𝞹𝔤 𝐚𝗌 𝖋ａ𝐬𝒕 "
//                + "αｓ γ𝛐𝕦 𝔠ﻫ𝛖lԁ, 𝚊π𝑑 Ь𝑒𝙛૦𝓇𝘦 𝓎٥𝖚 ⅇｖℯ𝝅 𝜅ո𝒆ｗ ｗ𝗵𝒂𝘁 ᶌ੦𝗎 ｈ𝐚𝗱, 𝜸ﮨ𝒖 𝓹𝝰𝔱𝖾𝗇𝓽𝔢ⅆ і𝕥, 𝚊𝜛𝓭 𝓹𝖺ⅽϰ𝘢ℊеᏧ 𝑖𝞃, "
//                + "𝐚𝛑ꓒ 𝙨l𝔞р𝘱𝔢𝓭 ɩ𝗍 ہ𝛑 𝕒 ｐl𝛂ѕᴛ𝗂𝐜 l𝞄ℼ𝔠𝒽𝑏ﮪ⨯, 𝔞ϖ𝒹 ｎ𝛔ｗ 𝛾𝐨𝞄'𝗿𝔢 ꜱ℮ll𝙞ｎɡ ɩ𝘁, 𝙮𝕠𝛖 ｗ𝑎ℼ𝚗𝛂 𝕤𝓮ll 𝙞𝓉.";
//        assertEquals("[size=1496 text=Սｍ, I'll 𝓽𝖾ll ᶌօ𝘂 ᴛℎ℮ 𝜚𝕣०ｂl𝖾ｍ ｗі𝕥𝒽 𝘵𝘩𝐞 𝓼𝙘𝐢𝔢𝓷𝗍𝜄𝚏𝑖ｃ 𝛠𝝾ｗ𝚎𝑟 𝕥ｈ⍺𝞃 "
//                + "𝛄𝓸𝘂…]", factory.encodeUtf8(war).toString());
//    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void toStringOnTextWithNewlines(ByteStringFactory factory) {
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

//    @ParameterizedTest
//    @MethodSource("parameters")
//    public void toStringOnLongDataIsTruncated(ByteStringFactory factory) {
//        ByteString byteString = factory.decodeHex(""
//                + "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55"
//                + "4bf0b54023c29b624de9ef9c2f931efc580f9afba1");
//        assertEquals("ByteString(size=65 hex="
//                + "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55"
//                + "4bf0b54023c29b624de9ef9c2f931efc580f9afb…]", byteString.toString());
//    }

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
    public void get(ByteStringFactory factory) {
        final var actual = factory.encodeUtf8("abc");
        assertEquals(3, actual.getSize());
        assertEquals(actual.get(0), (byte) 'a');
        assertEquals(actual.get(1), (byte) 'b');
        assertEquals(actual.get(2), (byte) 'c');
        assertThatThrownBy(() -> actual.get(-1))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> actual.get(3))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }
}
