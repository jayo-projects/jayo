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

@file:JvmName("-Jayo") // Leading '-' hides this class from Java.

package jayo

import jayo.tools.Utf8Utils
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SocketChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.OpenOption
import java.nio.file.Path

/**
 * @return a writer that writes to this [Socket]. Prefer this over [writer] because this method honors timeouts.
 * When the socket write times out, it is asynchronously closed by a watchdog thread.
 */
public fun Socket.writer(): RawWriter = Jayo.writer(this)

/**
 * @return a reader that reads from this [Socket]. Prefer this over [reader] because this method honors timeouts.
 * When the socket read times out, it is asynchronously closed by a watchdog thread.
 */
public fun Socket.reader(): RawReader = Jayo.reader(this)

/** @return a writer that writes to this [OutputStream]. */
public fun OutputStream.writer(): RawWriter = Jayo.writer(this)

/** @return a reader that reads from this [InputStream]. */
public fun InputStream.reader(): RawReader = Jayo.reader(this)

/**
 * @return a writer that writes to this [SocketChannel]. Prefer this over [writer] because this method honors timeouts.
 * When the socket channel write times out, it is asynchronously closed by a watchdog thread.
 */
public fun SocketChannel.writer(): RawWriter = Jayo.writer(this)

/**
 * @return a reader that reads from this [SocketChannel]. Prefer this over [reader] because this method honors timeouts.
 * When the socket channel read times out, it is asynchronously closed by a watchdog thread.
 */
public fun SocketChannel.reader(): RawReader = Jayo.reader(this)

/** @return a writer that writes to this [GatheringByteChannel]. */
public fun GatheringByteChannel.writer(): RawWriter = Jayo.writer(this)

/** @return a writer that writes to this [WritableByteChannel]. */
public fun WritableByteChannel.writer(): RawWriter = Jayo.writer(this)

/** @return a reader that reads from this [ReadableByteChannel]. */
public fun ReadableByteChannel.reader(): RawReader = Jayo.reader(this)

/**
 * @return a writer that writes to this [Path]. options allow to specify how the file is opened.
 *
 * Note : we always add the `StandardOpenOption.WRITE` option to the options Set, so we ensure we can write in this path
 */
public fun Path.writer(vararg options: OpenOption): RawWriter = Jayo.writer(this, *options)

/**
 * @return a reader that reads from this [Path]. options allow to specify how the file is opened.
 *
 * Note : we always add the `StandardOpenOption.READ` option to the options Set, so we ensure we can read from this path
 */
public fun Path.reader(vararg options: OpenOption): RawReader = Jayo.reader(this, *options)

/**
 * @return a writer that writes to this [File]. If you need specific options, please use `path.writer(myOptions)`
 * instead.
 */
public fun File.writer(): RawWriter = Jayo.writer(this)

/**
 * @return a reader that reads from this [File]. If you need specific options, please use `path.reader(myOptions)`
 * instead.
 */
public fun File.reader(): RawReader = Jayo.reader(this)

/** @return a writer that discards all data written to it. */
public fun discardingWriter(): RawWriter = Jayo.discardingWriter()

/**
 * @return the number of bytes that would be used to encode the slice of `string` as UTF-8 when using
 * `writer.write("myCharSequence)`.
 */
public fun CharSequence.utf8ByteSize(): Long = Utf8Utils.utf8ByteSize(this)
