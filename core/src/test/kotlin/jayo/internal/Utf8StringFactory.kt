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

import jayo.Utf8String
import jayo.encodeToUtf8String
import jayo.toUtf8String

interface Utf8StringFactory {
    fun encodeUtf8(s: String): Utf8String

    companion object {
        @JvmStatic
        val UTF8_STRING: Utf8StringFactory = object : Utf8StringFactory {
            override fun encodeUtf8(s: String) = s.encodeToUtf8String()
        }

        @JvmStatic
        val UTF8_STRING_FROM_BYTES: Utf8StringFactory = object : Utf8StringFactory {
            override fun encodeUtf8(s: String) = s.encodeToByteArray().toUtf8String()
        }

        @JvmStatic
        val UTF8_STRING_FROM_BYTES_NO_COMPACT_STRING: Utf8StringFactory = object : Utf8StringFactory {
            override fun encodeUtf8(s: String) =
                RealUtf8String(s.encodeToByteArray(), false, false)
        }

        @JvmStatic
        val SEGMENTED_UTF8_STRING: Utf8StringFactory = object : Utf8StringFactory {
            override fun encodeUtf8(s: String) = RealBuffer().apply { writeUtf8(s) }.utf8Snapshot()
        }

        @JvmStatic
        val UTF8_ONE_BYTE_PER_SEGMENT: Utf8StringFactory = object : Utf8StringFactory {
            override fun encodeUtf8(s: String) = makeUtf8Segments(s.encodeToUtf8String())
        }
    }
}
