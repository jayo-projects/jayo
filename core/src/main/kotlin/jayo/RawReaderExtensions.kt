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

@file:JvmName("-RawReader") // Leading '-' hides this class from Java.

package jayo

import jayo.bytestring.ByteString
import jayo.crypto.Digest
import jayo.crypto.Hmac
import jayo.internal.RealReader
import java.util.zip.Inflater

/**
 * @return a new reader that buffers reads from the raw `reader`. The returned reader will perform bulk reads into its
 * underlying buffer.
 *
 * Use this wherever you read from a raw reader to get ergonomic and efficient access to data.
 */
public fun RawReader.buffered(): Reader = RealReader(this)

/**
 * Consumes all bytes from this reader and return its hash.
 *
 * @param digest the chosen message digest algorithm to use for hashing.
 * @return the hash of this reader.
 */
public fun RawReader.hash(digest: Digest): ByteString = Jayo.hash(this, digest)

/**
 * Consumes all bytes from this reader and return its MAC result.
 *
 * @param hMac the chosen "Message Authentication Code" (MAC) algorithm to use.
 * @param key the key to use for this MAC operation.
 * @return the MAC result of this reader.
 */
public fun RawReader.hmac(hMac: Hmac, key: ByteString): ByteString = Jayo.hmac(this, hMac, key)

/**
 * @return an [InflaterRawReader] that DEFLATE-decompresses data of this [RawReader] while reading.
 */
public fun RawReader.inflate(inflater: Inflater = Inflater()): InflaterRawReader = Jayo.inflate(this, inflater)

/**
 * Returns a [RawReader] that gzip-decompresses data of this [RawReader] while reading.
 */
public fun RawReader.gzip(): RawReader = Jayo.gzip(this)
