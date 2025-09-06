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

import jayo.bytestring.ByteString
import jayo.bytestring.decodeHex
import jayo.bytestring.encodeToByteString
import jayo.internal.RealBuffer
import jayo.internal.makeSegments

interface ByteStringFactory {
    fun decodeHex(hex: String): ByteString
    fun encodeUtf8(s: String): ByteString
    val isUtf8: Boolean

    companion object {
        @JvmStatic
        val BYTE_STRING: ByteStringFactory = object : ByteStringFactory {
            override fun decodeHex(hex: String) = hex.decodeHex()
            override fun encodeUtf8(s: String) = s.encodeToByteString()
            override val isUtf8: Boolean get() = false
        }

        @JvmStatic
        val SEGMENTED_BYTE_STRING: ByteStringFactory = object : ByteStringFactory {
            override fun decodeHex(hex: String) = RealBuffer().apply { write(hex.decodeHex()) }.snapshot()
            override fun encodeUtf8(s: String) = RealBuffer().apply { write(s) }.snapshot()
            override val isUtf8: Boolean get() = false
        }

        @JvmStatic
        val ONE_BYTE_PER_SEGMENT: ByteStringFactory = object : ByteStringFactory {
            override fun decodeHex(hex: String) = makeSegments(hex.decodeHex())
            override fun encodeUtf8(s: String) = makeSegments(s.encodeToByteString(Charsets.UTF_8))
            override val isUtf8: Boolean get() = false
        }
    }
}
