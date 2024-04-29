/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-Utf8ByteString") // A leading '-' hides this class from Java.

package jayo

import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Returns a new [ByteString] containing a copy of `byteCount` bytes of this `ByteArray` starting at `offset`.
 */
public fun ByteArray.toUtf8ByteString(
    offset: Int = 0,
    byteCount: Int = size
): Utf8ByteString {
    return Utf8ByteString.ofUtf8(this, offset, byteCount)
}

/** Returns a [ByteString] containing a copy of the content of this [ByteBuffer]. */
public fun ByteBuffer.toUtf8ByteString(): Utf8ByteString = Utf8ByteString.ofUtf8(this)

/**
 * Reads `count` bytes from this [InputStream] and returns the result as a [ByteString].
 *
 * @throws jayo.exceptions.JayoEOFException if `in` has fewer than `byteCount` bytes to read.
 */
public fun InputStream.readUtf8ByteString(byteCount: Int): Utf8ByteString = Utf8ByteString.readUtf8(this, byteCount)

/** Returns a new [Utf8ByteString] containing the UTF-8-encoded bytes of this [String]. */
public fun String.encodeToUtf8ByteString(): Utf8ByteString = Utf8ByteString.encodeUtf8(this)
