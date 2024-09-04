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

@file:JvmName("-RawWriter") // A leading '-' hides this class from Java.

package jayo

import jayo.internal.RealWriter
import java.util.zip.Deflater

/**
 * @return a new writer that buffers writes to the raw `writer`. The returned writer will batch writes to `writer`.
 *
 * If you choose the [async] option, actual write operations to the raw `writer` are seamlessly processed
 * **asynchronously** by a virtual thread.
 *
 * Use this wherever you write to a writer to get an ergonomic and efficient access to data.
 */
public fun RawWriter.buffered(async: Boolean = false): Writer = RealWriter(this, async)

/**
 * Returns a [RawWriter] that DEFLATE-compresses data to this [RawWriter] while writing.
 */
public fun RawWriter.deflate(deflater: Deflater = Deflater()): RawWriter = Jayo.deflate(this, deflater)
