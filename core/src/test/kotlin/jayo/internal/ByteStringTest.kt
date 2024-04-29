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

import jayo.ByteString
import jayo.crypto.Digests
import jayo.crypto.Hmacs
import jayo.encodeToByteString
import jayo.readByteString
import jayo.toByteString
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.stream.Stream

class ByteStringTest {

    companion object {
        @JvmStatic
        fun parameters(): Stream<Arguments>? {
            return Stream.of(
                Arguments.of(ByteStringFactory.BYTE_STRING, "ByteString"),
                Arguments.of(ByteStringFactory.UTF8_STRING, "Utf8String"),
                Arguments.of(ByteStringFactory.SEGMENTED_BYTE_STRING, "SegmentedByteString"),
                Arguments.of(ByteStringFactory.ONE_BYTE_PER_SEGMENT, "SegmentedByteString (one-byte-at-a-time)"),
                Arguments.of(ByteStringFactory.SEGMENTED_UTF8_STRING, "SegmentedUtf8String"),
                Arguments.of(ByteStringFactory.UTF8_ONE_BYTE_PER_SEGMENT, "SegmentedUtf8String (one-byte-at-a-time)"),
            )
        }
    }

    @Test
    fun arrayToByteString() {
        val actual = byteArrayOf(1, 2, 3, 4).toByteString()
        val expected = ByteString.of(1, 2, 3, 4)
        assertEquals(actual, expected)
    }

    @Test
    fun arraySubsetToByteString() {
        val actual = byteArrayOf(1, 2, 3, 4).toByteString(1, 2)
        val expected = ByteString.of(2, 3)
        assertEquals(actual, expected)
    }

    @Test
    fun byteBufferToByteString() {
        val actual = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)).toByteString()
        val expected = ByteString.of(1, 2, 3, 4)
        assertEquals(actual, expected)
    }

    @Test
    fun streamReadByteString() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val actual = stream.readByteString(4)
        val expected = ByteString.of(1, 2, 3, 4)
        assertEquals(actual, expected)
    }

    @Test
    fun substring() {
        val byteString = "abcdef".encodeToByteString()
        assertEquals(byteString.substring(0, 3), "abc".encodeToByteString())
        assertEquals(byteString.substring(3), "def".encodeToByteString())
        assertEquals(byteString.substring(1, 5), "bcde".encodeToByteString())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun testHash(factory: ByteStringFactory) = with(factory.encodeUtf8("Kevin")) {
        assertThat(hash(Digests.MD5).hex()).isEqualTo("f1cd318e412b5f7226e5f377a9544ff7")
        assertThat(hash(Digests.SHA_1).hex()).isEqualTo("e043899daa0c7add37bc99792b2c045d6abbc6dc")
        assertThat(hash(Digests.SHA_224).hex())
            .isEqualTo("35e1fa1b770f696e95666b66ff1d040ab4a0421dae005d048a5647c5")
        assertThat(hash(Digests.SHA_256).hex())
            .isEqualTo("0e4dd66217fc8d2e298b78c8cd9392870dcd065d0ff675d0edff5bcd227837e9")
        assertThat(hash(Digests.SHA_384).hex())
            .isEqualTo(
                "45824b5d3cc0dd249144875b15833d117c11fa775e06ec1fe19988b3347395013a49d78c8056653d06ba196c1a94a160"
            )
        assertThat(hash(Digests.SHA_512).hex()).isEqualTo(
            "483676b93c4417198b465083d196ec6a9fab8d004515874b8ff47e041f5f56303cc08179625030b8b5b721c09149a18f0f5" +
                    "9e64e7ae099518cea78d3d83167e1"
        )
        assertThat(hash(Digests.SHA_512_224).hex())
            .isEqualTo("730ceb5e4e968eba3aa3fe8aeaf6e08761b94917db83a44a64e20159")
        assertThat(hash(Digests.SHA_512_256).hex())
            .isEqualTo("4f023d8f32c539d712f9f2dfb3719fbc4c980c27abb7382e988bf4fbd4f1caa9")
        assertThat(hash(Digests.SHA3_224).hex())
            .isEqualTo("8e1cb3e2802de9986b6624bac295fa507ccdc6efe7edaa22122cd120")
        assertThat(hash(Digests.SHA3_256).hex())
            .isEqualTo("95547a916f3e4c214fa80a3d78e86faa92e3f6703f5e713c1d176e029116ce6f")
        assertThat(hash(Digests.SHA3_384).hex())
            .isEqualTo(
                "451ceac3054d4a5859a79bfbf6e6a1c2223f5fcecb883333d98eff63c84f2bd1c797bf7fe449e000f488173bd09daf19"
            )
        assertThat(hash(Digests.SHA3_512).hex()).isEqualTo(
            "d925dc48347d016fd1dc3907378c95c0c860a75332d2e673f743d970bbea89d1e77a07a4a9a64290146ba273bb262d1dd2" +
                    "37bace6761293a75dbf39f72815da8"
        )
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun testHMac(factory: ByteStringFactory) = with(factory.encodeUtf8("Kevin")) {
        val key = "Brandon".encodeToByteString()
        assertThat(hmac(Hmacs.HMAC_MD5, key).hex()).isEqualTo("cd5478da9993e894de891a6d680a88fb")
        assertThat(hmac(Hmacs.HMAC_SHA_1, key).hex()).isEqualTo("46eedc331e6f92c801808fd5bfc5424afe659402")
        assertThat(hmac(Hmacs.HMAC_SHA_224, key).hex())
            .isEqualTo("3f1ade07df5bfbb4be786f93ca898fb5993e7af71577f13a2ebbc253")
        assertThat(hmac(Hmacs.HMAC_SHA_256, key).hex())
            .isEqualTo("5eaf69955f51d61665e28ce16acbf7e5e1b6a2d1f62b3b4bad1aa0913a349e77")
        assertThat(hmac(Hmacs.HMAC_SHA_384, key).hex())
            .isEqualTo(
                "a62272c16f97153b5ce36a6f61f999d925efeed91bf9aac76799ef9c02991ec644f4d8a332275278f78478d5cb9ae6b8"
            )
        assertThat(hmac(Hmacs.HMAC_SHA_512, key).hex()).isEqualTo(
            "06edf87929601bd8a1124d996b774881e55e36cf70c58e26d44c1a7bf596ba3b8e1d8b018275791a441a0b5edb86abf394bd" +
                    "8081a6da8e51e39521b346780dde"
        )
        assertThat(hmac(Hmacs.HMAC_SHA_512_224, key).hex())
            .isEqualTo("0692adbe44b74f90dc0ee4e8f280a86ba190cb783f78c159bea06a9e")
        assertThat(hmac(Hmacs.HMAC_SHA_512_256, key).hex())
            .isEqualTo("552d369db150748af9db5c0d621bce8ba7c86807ea5293ed10a2c3f7129b410d")
        assertThat(hmac(Hmacs.HMAC_SHA3_224, key).hex())
            .isEqualTo("0e6451625aaa6b5c11bd664a0dddd8883a17980be6b3440532719c75")
        assertThat(hmac(Hmacs.HMAC_SHA3_256, key).hex())
            .isEqualTo("5e772f25f1e3ff180cfb8b8a20f31afc43b1fbf86d01c5a74b28608801d53a2e")
        assertThat(hmac(Hmacs.HMAC_SHA3_384, key).hex())
            .isEqualTo(
                "0fdc46f226ad27c730a36adfd23c7f2a82dcafdd92fa546730391ef9e2595b73b82f489e0b3e638a2d42d2191c69b031"
            )
        assertThat(hmac(Hmacs.HMAC_SHA3_512, key).hex()).isEqualTo(
            "9b9f9fc58cb6ae835a74d4d9ab51e1583027130315b5aaf497dd51b6dacbae7f9e141a2ecdcbfce337f031f2ce83a58fdfc" +
                    "ee27a373ff408f792326a69a900cc"
        )
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun copyInto(factory: ByteStringFactory) {
        val byteString = factory.encodeUtf8("abcdefgh")
        val byteArray = "WwwwXxxxYyyyZzzz".encodeToByteArray()
        byteString.copyInto(0, byteArray, 0, 5)
        assertEquals("abcdexxxYyyyZzzz", byteArray.decodeToString())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun copyIntoFullRange(factory: ByteStringFactory) {
        val byteString = factory.encodeUtf8("abcdefghijklmnop")
        val byteArray = "WwwwXxxxYyyyZzzz".encodeToByteArray()
        byteString.copyInto(0, byteArray, 0, 16)
        assertEquals("abcdefghijklmnop", byteArray.decodeToString())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun copyIntoWithTargetOffset(factory: ByteStringFactory) {
        val byteString = factory.encodeUtf8("abcdefgh")
        val byteArray = "WwwwXxxxYyyyZzzz".encodeToByteArray()
        byteString.copyInto(0, byteArray, 11, 5)
        assertEquals("WwwwXxxxYyyabcde", byteArray.decodeToString())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun copyIntoWithSourceOffset(factory: ByteStringFactory) {
        val byteString = factory.encodeUtf8("abcdefgh")
        val byteArray = "WwwwXxxxYyyyZzzz".encodeToByteArray()
        byteString.copyInto(3, byteArray, 0, 5)
        assertEquals("defghxxxYyyyZzzz", byteArray.decodeToString())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun copyIntoWithAllParameters(factory: ByteStringFactory) {
        val byteString = factory.encodeUtf8("abcdefgh")
        val byteArray = "WwwwXxxxYyyyZzzz".encodeToByteArray()
        byteString.copyInto(3, byteArray, 11, 5)
        assertEquals("WwwwXxxxYyydefgh", byteArray.decodeToString())
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun copyIntoBoundsChecks(factory: ByteStringFactory) {
        val byteString = factory.encodeUtf8("abcdefgh")
        val byteArray = "WwwwXxxxYyyyZzzz".encodeToByteArray()
        assertThatThrownBy {
            byteString.copyInto(-1, byteArray, 1, 1)
        }.isInstanceOf(IndexOutOfBoundsException::class.java)
        assertThatThrownBy {
            byteString.copyInto(9, byteArray, 0, 0)
        }.isInstanceOf(IndexOutOfBoundsException::class.java)
        assertThatThrownBy {
            byteString.copyInto(1, byteArray, -1, 1)
        }.isInstanceOf(IndexOutOfBoundsException::class.java)
        assertThatThrownBy {
            byteString.copyInto(1, byteArray, 17, 1)
        }.isInstanceOf(IndexOutOfBoundsException::class.java)
        assertThatThrownBy {
            byteString.copyInto(7, byteArray, 1, 2)
        }.isInstanceOf(IndexOutOfBoundsException::class.java)
        assertThatThrownBy {
            byteString.copyInto(1, byteArray, 15, 2)
        }.isInstanceOf(IndexOutOfBoundsException::class.java)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun copyEmptyAtBounds(factory: ByteStringFactory) {
        val byteString = factory.encodeUtf8("abcdefgh")
        val byteArray = "WwwwXxxxYyyyZzzz".encodeToByteArray()
        byteString.copyInto(0, byteArray, 0, 0)
        assertEquals("WwwwXxxxYyyyZzzz", byteArray.decodeToString())
        byteString.copyInto(0, byteArray, 16, 0)
        assertEquals("WwwwXxxxYyyyZzzz", byteArray.decodeToString())
        byteString.copyInto(8, byteArray, 0, 0)
        assertEquals("WwwwXxxxYyyyZzzz", byteArray.decodeToString())
    }
}
