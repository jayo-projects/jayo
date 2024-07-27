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

import jayo.Utf8
import jayo.encodeToUtf8
import jayo.toUtf8

interface Utf8Factory {
    fun encodeUtf8(s: String): Utf8

    companion object {
        @JvmStatic
        val UTF8: Utf8Factory = object : Utf8Factory {
            override fun encodeUtf8(s: String) = s.encodeToUtf8()
        }

        @JvmStatic
        val UTF8_FROM_BYTES: Utf8Factory = object : Utf8Factory {
            override fun encodeUtf8(s: String) = s.encodeToByteArray().toUtf8()
        }

        @JvmStatic
        val UTF8_FROM_BYTES_NO_COMPACT_STRING: Utf8Factory = object : Utf8Factory {
            override fun encodeUtf8(s: String) =
                RealUtf8(s.encodeToByteArray(), false, false)
        }

        @JvmStatic
        val SEGMENTED_UTF8: Utf8Factory = object : Utf8Factory {
            override fun encodeUtf8(s: String) = RealBuffer().apply { writeUtf8(s) }.readUtf8()
        }

        @JvmStatic
        val UTF8_ONE_BYTE_PER_SEGMENT: Utf8Factory = object : Utf8Factory {
            override fun encodeUtf8(s: String) = makeUtf8Segments(s.encodeToUtf8())
        }
    }
}
