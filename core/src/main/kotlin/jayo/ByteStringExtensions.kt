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

@file:JvmName("-ByteString") // A leading '-' hides this class from Java.

package jayo

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

/** @return a new [ByteString] containing a copy of `byteCount` bytes of this `ByteArray` starting at `offset`. */
public fun ByteArray.toByteString(
    offset: Int = 0,
    byteCount: Int = size
): ByteString = ByteString.of(this, offset, byteCount)

/** @return a [ByteString] containing a copy of the content of this [ByteBuffer]. */
public fun ByteBuffer.toByteString(): ByteString = ByteString.of(this)

/**
 * Reads `count` bytes from this [InputStream] and returns the result as a [ByteString].
 *
 * @throws jayo.exceptions.JayoEOFException if `in` has fewer than `byteCount` bytes to read.
 */
public fun InputStream.readByteString(byteCount: Int): ByteString = ByteString.read(this, byteCount)

/** Returns a new [ByteString] containing the `charset`-encoded bytes of this [String]. */
public fun String.encodeToByteString(charset: Charset = Charsets.UTF_8): ByteString =
    ByteString.encode(this, charset)

/**
 * Decodes the Base64-encoded bytes and returns the result as a [ByteString].
 * Returns null if this is not a Base64-encoded sequence of bytes.
 */
public fun CharSequence.decodeBase64(): ByteString? = ByteString.decodeBase64(this)

/** Decodes the hex-encoded bytes and returns the result as a [ByteString]. */
public fun CharSequence.decodeHex(): ByteString = ByteString.decodeHex(this)
    