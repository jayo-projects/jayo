/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-Utf8String") // A leading '-' hides this class from Java.

package jayo

import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Returns a new [ByteString] containing a copy of `byteCount` bytes of this `ByteArray` starting at `offset`.
 */
public fun ByteArray.toUtf8String(
    offset: Int = 0,
    byteCount: Int = size
): Utf8String {
    return Utf8String.ofUtf8(this, offset, byteCount)
}

/** Returns a [ByteString] containing a copy of the content of this [ByteBuffer]. */
public fun ByteBuffer.toUtf8String(): Utf8String = Utf8String.ofUtf8(this)

/**
 * Reads `count` bytes from this [InputStream] and returns the result as a [ByteString].
 *
 * @throws jayo.exceptions.JayoEOFException if `in` has fewer than `byteCount` bytes to read.
 */
public fun InputStream.readUtf8String(byteCount: Int): Utf8String = Utf8String.readUtf8(this, byteCount)

/** Returns a new [Utf8String] containing the UTF-8-encoded bytes of this [String]. */
public fun String.encodeToUtf8String(): Utf8String = Utf8String.encodeUtf8(this)
