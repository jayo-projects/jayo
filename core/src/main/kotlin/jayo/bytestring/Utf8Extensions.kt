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

@file:JvmName("-Utf8") // A leading '-' hides this class from Java.

package jayo.bytestring

import java.io.InputStream
import java.nio.ByteBuffer

/**
 * @return a new [Utf8] containing a copy of `byteCount` bytes of this byte array starting at `offset`. Do not
 * provide values for `byteCount` and `offset` if you want a full copy of this byte array.
 */
public fun ByteArray.toUtf8(
    offset: Int = 0,
    byteCount: Int = size,
): Utf8 = Utf8.of(this, offset, byteCount)

/**
 * @return a [Utf8] containing a copy of the content of this [ByteBuffer].
 */
public fun ByteBuffer.toUtf8(): Utf8 = Utf8.of(this)

/**
 * Reads `count` bytes from this [InputStream] and returns the result as a [Utf8].
 * @throws jayo.JayoEOFException if `in` has fewer than `byteCount` bytes to read.
 */
public fun InputStream.readUtf8(byteCount: Int): Utf8 = Utf8.read(this, byteCount)

/** @return a new [Utf8] containing the UTF-8-encoded bytes of this [String]. */
public fun String.encodeToUtf8(): Utf8 = Utf8.encode(this)
