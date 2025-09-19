/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal

import jayo.Buffer
import jayo.RawReader
import jayo.bytestring.encodeToByteString
import jayo.bytestring.toByteString
import jayo.crypto.JdkDigest
import jayo.crypto.JdkHmac
import jayo.hash
import jayo.hmac
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HashingTests {
    @Test
    fun hashTest() {
        val bytes = ByteArray(Segment.SIZE * 2 + 1) { 'a'.code.toByte() }
        val expectedMd5 = "3ac15f278019c332ab4395eb3b1167b8"

        // ByteString
        val byteString = bytes.toByteString()
        assertThat(byteString.hash(JdkDigest.MD5).hex()).isEqualTo(expectedMd5)
        println()

        // Buffer
        val buffer = Buffer().write(bytes)
        assertThat(buffer.hash(JdkDigest.MD5).hex()).isEqualTo(expectedMd5)
        
        // hash from reader
        assertThat((buffer as RawReader).hash(JdkDigest.MD5).hex()).isEqualTo(expectedMd5)
    }

    @Test
    fun hMacTest() {
        val bytes = ByteArray(Segment.SIZE *  2 + 1) { 'a'.code.toByte() }
        val key = "abc".encodeToByteString()
        val expectedMd5 = "2d6bd1f82825302aa6ed6cdac51771ff"

        // ByteString
        val byteString = bytes.toByteString()
        assertThat(byteString.hmac(JdkHmac.HMAC_MD5, key).hex()).isEqualTo(expectedMd5)
        println()

        // Buffer
        val buffer = Buffer().write(bytes)
        assertThat(buffer.hmac(JdkHmac.HMAC_MD5, key).hex()).isEqualTo(expectedMd5)

        // hash from reader
        assertThat((buffer as RawReader).hmac(JdkHmac.HMAC_MD5, key).hex()).isEqualTo(expectedMd5)
    }
}