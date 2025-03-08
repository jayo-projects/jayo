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

import jayo.bytestring.Utf8;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static jayo.internal.TestUtil.assertEquivalent;
import static org.junit.jupiter.api.Assertions.*;

public final class Utf8JavaTest {
    public static Stream<Arguments> parameters() {
        return Stream.of(
                Arguments.of(Utf8Factory.getUTF8(), "Utf8"),
                Arguments.of(
                        Utf8Factory.getUTF8_FROM_BYTES(),
                        "Utf8 (from bytes)"),
                Arguments.of(
                        Utf8Factory.getUTF8_FROM_BYTES_NO_COMPACT_STRING(),
                        "Utf8 (from bytes without compact string)"),
                Arguments.of(Utf8Factory.getSEGMENTED_UTF8(), "SegmentedUtf8"),
                Arguments.of(Utf8Factory.getUTF8_ONE_BYTE_PER_SEGMENT(),
                        "SegmentedUtf8 (one-byte-at-a-time)"),
                Arguments.of(
                        Utf8Factory.getASCII_FROM_BYTES(),
                        "Ascii (from bytes)"),
                Arguments.of(
                        Utf8Factory.getASCII_FROM_BYTES_NO_COMPACT_STRING(),
                        "Ascii (from bytes without compact string)"),
                Arguments.of(Utf8Factory.getSEGMENTED_ASCII(), "SegmentedAscii"),
                Arguments.of(Utf8Factory.getASCII_ONE_BYTE_PER_SEGMENT(),
                        "SegmentedAscii (one-byte-at-a-time)")
        );
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void javaSerializationTestNonEmpty(Utf8Factory factory) {
        if (!factory.isAscii()) {
            String bronzeHorseman = "На берегу пустынных волн";
            Utf8 byteString = factory.encodeUtf8(bronzeHorseman);
            assertEquivalent(byteString, TestUtil.reserialize(byteString));
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void startsWithString(Utf8Factory factory) {
        Utf8 byteString = factory.encodeUtf8("112233");
        assertTrue(byteString.startsWith(""));
        assertTrue(byteString.startsWith("11"));
        assertTrue(byteString.startsWith("1122"));
        assertTrue(byteString.startsWith("112233"));
        assertFalse(byteString.startsWith("2233"));
        assertFalse(byteString.startsWith("11223344"));
        assertFalse(byteString.startsWith("112244"));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void endsWithString(Utf8Factory factory) {
        Utf8 byteString = factory.encodeUtf8("112233");
        assertTrue(byteString.endsWith(""));
        assertTrue(byteString.endsWith("33"));
        assertTrue(byteString.endsWith("2233"));
        assertTrue(byteString.endsWith("112233"));
        assertFalse(byteString.endsWith("1122"));
        assertFalse(byteString.endsWith("00112233"));
        assertFalse(byteString.endsWith("002233"));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void indexOfString(Utf8Factory factory) {
        Utf8 byteString = factory.encodeUtf8("112233");
        assertEquals(0, byteString.indexOf("112233"));
        assertEquals(0, byteString.indexOf("1122"));
        assertEquals(0, byteString.indexOf("11"));
        assertEquals(0, byteString.indexOf("11", 0));
        assertEquals(0, byteString.indexOf(""));
        assertEquals(0, byteString.indexOf("", 0));
        assertEquals(2, byteString.indexOf("2233"));
        assertEquals(2, byteString.indexOf("22"));
        assertEquals(2, byteString.indexOf("22", 1));
        assertEquals(1, byteString.indexOf("", 1));
        assertEquals(4, byteString.indexOf("33"));
        assertEquals(4, byteString.indexOf("33", 2));
        assertEquals(2, byteString.indexOf("", 2));
        assertEquals(3, byteString.indexOf("", 3));
        assertEquals(-1, byteString.indexOf("112233", 1));
        assertEquals(-1, byteString.indexOf("44"));
        assertEquals(-1, byteString.indexOf("11223344"));
        assertEquals(-1, byteString.indexOf("112244"));
        assertEquals(-1, byteString.indexOf("112233", 1));
        assertEquals(-1, byteString.indexOf("2233", 3));
        assertEquals(-1, byteString.indexOf("33", 5));
        assertEquals(-1, byteString.indexOf("", 7));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void lastIndexOfString(Utf8Factory factory) {
        Utf8 byteString = factory.encodeUtf8("112233");
        assertEquals(0, byteString.lastIndexOf("112233"));
        assertEquals(0, byteString.lastIndexOf("1122"));
        assertEquals(0, byteString.lastIndexOf("11"));
        assertEquals(0, byteString.lastIndexOf("11", 3));
        assertEquals(0, byteString.lastIndexOf("11", 0));
        assertEquals(0, byteString.lastIndexOf("", 0));
        assertEquals(3, byteString.lastIndexOf("2"));
        assertEquals(2, byteString.lastIndexOf("22"));
        assertEquals(2, byteString.lastIndexOf("22", 3));
        assertEquals(1, byteString.lastIndexOf("", 1));
        assertEquals(4, byteString.lastIndexOf("33"));
        assertEquals(4, byteString.lastIndexOf("33", 5));
        assertEquals(2, byteString.lastIndexOf("", 2));
        assertEquals(3, byteString.lastIndexOf("", 3));
        assertEquals(6, byteString.lastIndexOf(""));
        assertEquals(-1, byteString.lastIndexOf("22", 1));
        assertEquals(-1, byteString.lastIndexOf("112233", -1));
        assertEquals(-1, byteString.lastIndexOf("112233", -2));
        assertEquals(-1, byteString.lastIndexOf("44"));
        assertEquals(-1, byteString.lastIndexOf("11223344"));
        assertEquals(-1, byteString.lastIndexOf("112244"));
        assertEquals(-1, byteString.lastIndexOf("2233", 0));
        assertEquals(-1, byteString.lastIndexOf("33", 1));
        assertEquals(-1, byteString.lastIndexOf("", -1));
    }
}
