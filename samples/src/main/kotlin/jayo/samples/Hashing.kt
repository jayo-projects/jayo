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

package jayo.samples

import jayo.*
import jayo.crypto.Digests
import jayo.crypto.Hmacs
import java.io.IOException
import java.nio.file.Path

class KotlinHashing {
    fun run() {
        val path = Path.of("README.md")

        println("ByteString")
        val byteString = readByteString(path)
        println("       md5: " + byteString.hash(Digests.MD5).hex())
        println("      sha1: " + byteString.hash(Digests.SHA_1).hex())
        println("    sha256: " + byteString.hash(Digests.SHA_256).hex())
        println("    sha512: " + byteString.hash(Digests.SHA_512).hex())
        println("  sha3_512: " + byteString.hash(Digests.SHA3_512).hex())
        println()

        println("Buffer")
        val buffer = readBuffer(path)
        println("       md5: " + buffer.hash(Digests.MD5).hex())
        println("      sha1: " + buffer.hash(Digests.SHA_1).hex())
        println("    sha256: " + buffer.hash(Digests.SHA_256).hex())
        println("    sha512: " + buffer.hash(Digests.SHA_512).hex())
        println("  sha3_512: " + buffer.hash(Digests.SHA3_512).hex())
        println()

//    println("HashingReader")
//    sha256(FileSystem.SYSTEM.reader(path)).use { hashingReader ->
//      hashingReader.buffer().use { reader ->
//        reader.readAll(blackholeWriter())
//        println("    sha256: " + hashingReader.hash.hex())
//      }
//    }
//    println()
//
//    println("HashingWriter")
//    sha256(blackholeWriter()).use { hashingWriter ->
//      hashingWriter.buffer().use { writer ->
//        FileSystem.SYSTEM.reader(path).use { reader ->
//          writer.writeAll(reader)
//          writer.close() // Emit anything buffered.
//          println("    sha256: " + hashingWriter.hash.hex())
//        }
//      }
//    }
//    println()

        println("HMAC")
        val secret = "7065616e7574627574746572".decodeHex()
        println("hmacSha256: " + byteString.hmac(Hmacs.HMAC_SHA_256, secret).hex())
        println()
    }

    private fun readByteString(path: Path): ByteString {
        return path.reader().buffered().use { it.readByteString() }
    }

    @Throws(IOException::class)
    fun readBuffer(path: Path): Buffer {
        path.reader().use { rawReader ->
            return Buffer().apply { transferFrom(rawReader) }
        }
    }
}

fun main() {
    KotlinHashing().run()
}
