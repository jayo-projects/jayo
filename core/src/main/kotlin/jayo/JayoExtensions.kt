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

import jayo.network.NetworkSocket
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

/** @return a writer that writes to this [OutputStream]. */
public fun OutputStream.writer(): RawWriter = Jayo.writer(this)

/** @return a reader that reads from this [InputStream]. */
public fun InputStream.reader(): RawReader = Jayo.reader(this)

/** @return a writer that writes to this [GatheringByteChannel]. */
public fun GatheringByteChannel.writer(): RawWriter = Jayo.writer(this)

/** @return a writer that writes to this [WritableByteChannel]. */
public fun WritableByteChannel.writer(): RawWriter = Jayo.writer(this)

/** @return a reader that reads from this [ReadableByteChannel]. */
public fun ReadableByteChannel.reader(): RawReader = Jayo.reader(this)

/**
 * @return a writer that writes to this [Path]. [options] allow to specify how the file is opened.
 *
 * Note: we always add the `StandardOpenOption.WRITE` option to the options Set, so we ensure we can write in this path
 */
public fun Path.writer(vararg options: OpenOption): RawWriter = Jayo.writer(this, *options)

/**
 * @return a reader that reads from this [Path]. [options] allow to specify how the file is opened.
 *
 * Note: we always add the `StandardOpenOption.READ` option to the options Set, so we ensure we can read from this path
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

/**
 * @return a [Jayo network socket][NetworkSocket] based on [Socket]. Prefer this over
 * [Jayo.writer(ioSocket.getOutputStream())][writer] and [Jayo.reader(ioSocket.getInputStream())][reader] because this
 * socket honors timeouts. When a socket operation times out, this socket is asynchronously closed by a watchdog thread.
 */
public fun Socket.asJayoSocket(): NetworkSocket = Jayo.socket(this)

/**
 * @return a [Jayo network socket][NetworkSocket] based on [SocketChannel]. Prefer this over
 * [Jayo.writer(nioSocketChannel)][writer] and [Jayo.reader(nioSocketChannel)][reader] because this socket honors
 * timeouts. When a socket operation times out, this socket is asynchronously closed by a watchdog thread.
 */
public fun SocketChannel.asJayoSocket(): NetworkSocket = Jayo.socket(this)

/**
 * Closes this [RawSocket], ignoring any [JayoException].
 */
public fun RawSocket.closeQuietly(): Unit = Jayo.closeQuietly(this)

/** @return a writer that discards all data written to it. */
public fun discardingWriter(): RawWriter = Jayo.discardingWriter()

/**
 * @return the number of bytes that would be used to encode the slice of `string` as UTF-8 when using
 * `writer.write("myCharSequence)`.
 */
public fun CharSequence.utf8ByteSize(): Long = Utf8Utils.utf8ByteSize(this)
