/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-Pipe") // Leading '-' hides this class from Java.

package jayo

/**
 * @return a new [Pipe]. This pipe's buffer that decouples reader and writer has a maximum size of [maxBufferSize].
 */
public fun Pipe(maxBufferSize: Long): Pipe = Pipe.create(maxBufferSize)