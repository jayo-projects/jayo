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
        val bytes = ByteArray(SEGMENT_SIZE + 1) { 'a'.code.toByte() }
        val expectedMd5 = "b7652d9bca37038c342cc0c492dd70f9"

        // ByteString
        val byteString = bytes.toByteString()
        assertThat(byteString.hash(Digests.MD5).hex()).isEqualTo(expectedMd5)
        println()

        // Buffer
        val buffer = Buffer().write(bytes)
        assertThat(buffer.hash(Digests.MD5).hex()).isEqualTo(expectedMd5)
        
        // hash from source
        assertThat((buffer as RawSource).hash(Digests.MD5).hex()).isEqualTo(expectedMd5)
    }

    @Test
    fun hMacTest() {
        val bytes = ByteArray(SEGMENT_SIZE + 1) { 'a'.code.toByte() }
        val key = "abc".encodeToByteString()
        val expectedMd5 = "ce0e17dc73c5261dc55e9f8884f49474"

        // ByteString
        val byteString = bytes.toByteString()
        assertThat(byteString.hmac(Hmacs.HMAC_MD5, key).hex()).isEqualTo(expectedMd5)
        println()

        // Buffer
        val buffer = Buffer().write(bytes)
        assertThat(buffer.hmac(Hmacs.HMAC_MD5, key).hex()).isEqualTo(expectedMd5)

        // hash from source
        assertThat((buffer as RawSource).hmac(Hmacs.HMAC_MD5, key).hex()).isEqualTo(expectedMd5)
    }
}