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
import jayo.decodeHex
import jayo.encodeToByteString
import jayo.encodeToUtf8

interface ByteStringFactory {
    fun decodeHex(hex: String): ByteString

    fun encodeUtf8(s: String): ByteString

    companion object {
        @JvmStatic
        val BYTE_STRING: ByteStringFactory = object : ByteStringFactory {
            override fun decodeHex(hex: String) = hex.decodeHex()
            override fun encodeUtf8(s: String) = s.encodeToByteString(Charsets.UTF_8)
        }

        @JvmStatic
        val SEGMENTED_BYTE_STRING: ByteStringFactory = object : ByteStringFactory {
            override fun decodeHex(hex: String) = RealBuffer().apply { write(hex.decodeHex()) }.snapshot()
            override fun encodeUtf8(s: String) = RealBuffer().apply { write(s) }.snapshot()
        }

        @JvmStatic
        val ONE_BYTE_PER_SEGMENT: ByteStringFactory = object : ByteStringFactory {
            override fun decodeHex(hex: String) = makeSegments(hex.decodeHex())
            override fun encodeUtf8(s: String) = makeSegments(s.encodeToByteString(Charsets.UTF_8))
        }

        @JvmStatic
        val UTF8: ByteStringFactory = object : ByteStringFactory {
            override fun decodeHex(hex: String) = hex.decodeHex()
            override fun encodeUtf8(s: String) = s.encodeToUtf8()
        }

        @JvmStatic
        val SEGMENTED_UTF8: ByteStringFactory = object : ByteStringFactory {
            override fun decodeHex(hex: String) = RealBuffer().apply { write(hex.decodeHex()) }.readByteString()
            override fun encodeUtf8(s: String) = RealBuffer().apply { write(s) }.readUtf8()
        }

        @JvmStatic
        val UTF8_ONE_BYTE_PER_SEGMENT: ByteStringFactory = object : ByteStringFactory {
            override fun decodeHex(hex: String) = makeSegments(hex.decodeHex())
            override fun encodeUtf8(s: String) = makeUtf8Segments(s.encodeToUtf8())
        }

        @JvmStatic
        val SEGMENTED_ASCII: ByteStringFactory = object : ByteStringFactory {
            override fun decodeHex(hex: String) = RealBuffer().apply { write(hex.decodeHex()) }.readByteString()
            override fun encodeUtf8(s: String) = RealBuffer().apply { write(s) }.readAscii()
        }

        @JvmStatic
        val ASCII_ONE_BYTE_PER_SEGMENT: ByteStringFactory = object : ByteStringFactory {
            override fun decodeHex(hex: String) = makeSegments(hex.decodeHex())
            override fun encodeUtf8(s: String) = makeAsciiSegments(s.encodeToUtf8())
        }
    }
}
