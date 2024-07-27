/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal

import jayo.*
import jayo.crypto.Digests
import jayo.crypto.Hmacs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HashingTests {
    @Test
    fun hashTest() {
        val bytes = ByteArray(SEGMENT_SIZE * 2 + 1) { 'a'.code.toByte() }
        val expectedMd5 = "8d075f723556e776f6f5dcf28e6889a7"

        // ByteString
        val byteString = bytes.toByteString()
        assertThat(byteString.hash(Digests.MD5).hex()).isEqualTo(expectedMd5)
        println()

        // Buffer
        val buffer = Buffer().write(bytes)
        assertThat(buffer.hash(Digests.MD5).hex()).isEqualTo(expectedMd5)
        
        // hash from reader
        assertThat((buffer as RawReader).hash(Digests.MD5).hex()).isEqualTo(expectedMd5)
    }

    @Test
    fun hMacTest() {
        val bytes = ByteArray(SEGMENT_SIZE *  2 + 1) { 'a'.code.toByte() }
        val key = "abc".encodeToByteString()
        val expectedMd5 = "0f2bbeb05b2327ec9b7cfb777cba1d75"

        // ByteString
        val byteString = bytes.toByteString()
        assertThat(byteString.hmac(Hmacs.HMAC_MD5, key).hex()).isEqualTo(expectedMd5)
        println()

        // Buffer
        val buffer = Buffer().write(bytes)
        assertThat(buffer.hmac(Hmacs.HMAC_MD5, key).hex()).isEqualTo(expectedMd5)

        // hash from reader
        assertThat((buffer as RawReader).hmac(Hmacs.HMAC_MD5, key).hex()).isEqualTo(expectedMd5)
    }
}