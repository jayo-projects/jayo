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

import jayo.Buffer
import jayo.bytestring.ByteString
import jayo.bytestring.decodeBase64
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class KotlinGoldenValue {
    fun run() {
        val point = Point(8.0, 15.0)
        val pointBytes = serialize(point)
        println(pointBytes.base64())
        val goldenBytes = (
                "rO0ABXNyABJqYXlvLnNhbXBsZXMuUG9pbnT1wai9iK0Z/QIAAkQAAXhEAAF5eHBAIAAAAAAAAEAuAAAAAAAA"
                ).decodeBase64()!!
        val decoded = deserialize(goldenBytes) as Point
        assertEquals(point, decoded)
    }

    @Throws(IOException::class)
    private fun serialize(o: Any?): ByteString {
        val buffer = Buffer()
        ObjectOutputStream(buffer.asOutputStream()).use { objectOut ->
            objectOut.writeObject(o)
        }
        return buffer.readByteString()
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun deserialize(byteString: ByteString): Any? {
        val buffer = Buffer()
        buffer.write(byteString)
        ObjectInputStream(buffer.asInputStream()).use { objectIn ->
            val result = objectIn.readObject()
            if (objectIn.read() != -1) throw IOException("Unconsumed bytes in stream")
            return result
        }
    }

    private fun assertEquals(
        a: Point,
        b: Point,
    ) {
        if (a.x != b.x || a.y != b.y) throw AssertionError()
    }
}

class Point(var x: Double, var y: Double) : Serializable

fun main() {
    KotlinGoldenValue().run()
}
