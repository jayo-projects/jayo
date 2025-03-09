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

import jayo.bytestring.Ascii
import jayo.bytestring.encodeToAscii
import jayo.bytestring.toAscii
import jayo.internal.RealAscii
import jayo.internal.RealBuffer
import jayo.internal.makeAsciiSegments

interface AsciiFactory {
    fun encodeAscii(s: String): Ascii

    companion object {
        @JvmStatic
        val ASCII: AsciiFactory = object : AsciiFactory {
            override fun encodeAscii(s: String) = s.encodeToAscii()
        }

        @JvmStatic
        val ASCII_FROM_BYTES: AsciiFactory = object : AsciiFactory {
            override fun encodeAscii(s: String) = s.encodeToByteArray().toAscii()
        }

        @JvmStatic
        val ASCII_FROM_BYTES_NO_COMPACT_STRING: AsciiFactory = object : AsciiFactory {
            override fun encodeAscii(s: String) = RealAscii(s.encodeToByteArray())
        }

        @JvmStatic
        val SEGMENTED_ASCII: AsciiFactory = object : AsciiFactory {
            override fun encodeAscii(s: String) = RealBuffer().apply { write(s) }.readAscii()
        }

        @JvmStatic
        val ASCII_ONE_BYTE_PER_SEGMENT: AsciiFactory = object : AsciiFactory {
            override fun encodeAscii(s: String) = makeAsciiSegments(s.encodeToAscii())
        }
    }
}
