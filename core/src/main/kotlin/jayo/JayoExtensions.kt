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

@file:JvmName("-Jayo") // A leading '-' hides this class from Java.

package jayo

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.file.OpenOption
import java.nio.file.Path

/** @return a sink that writes to this [OutputStream]. */
public fun OutputStream.sink(): RawSink = Jayo.sink(this)

/** @return a sink that reads from this [InputStream]. */
public fun InputStream.source(): RawSource = Jayo.source(this)

/**
 * @return a sink that writes to this [Socket]. Prefer this over [sink] because this method honors timeouts.
 * When the socket write times out, it is asynchronously closed by a watchdog thread.
 */
public fun Socket.sink(): RawSink = Jayo.sink(this)

/**
 * @return a source that reads from this [Socket]. Prefer this over [source] because this method honors timeouts.
 * When the socket read times out, it is asynchronously closed by a watchdog thread.
 */
public fun Socket.source(): RawSource = Jayo.source(this)

/**
 * @return a sink that writes to this [Path]. options allow to specify how the file is opened.
 *
 * Note : we always add the `StandardOpenOption.WRITE` option to the options Set, so we ensure we can write in this path
 */
public fun Path.sink(vararg options: OpenOption): RawSink = Jayo.sink(this, *options)

/**
 * @return a sink that writes to this [Path]. options allow to specify how the file is opened.
 *
 * Note : we always add the `StandardOpenOption.READ` option to the options Set, so we ensure we can read from this path
 */
public fun Path.source(vararg options: OpenOption): RawSource = Jayo.source(this, *options)

/** @return a sink that writes to this [File]. If you need specific options, please use [Path.sink] instead. */
public fun File.sink(): RawSink = Jayo.sink(this)

/** @return a source that reads from this [File]. If you need specific options, please use [Path.source] instead. */
public fun File.source(): RawSource = Jayo.source(this)

/** @return a sink that discards all data written to it. */
public fun discardingSink(): RawSink = Jayo.discardingSink()
