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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.internal

import org.junit.jupiter.api.Test
import jayo.Buffer
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnsafeCursorTest {
    @Test
    fun acquireForRead() {
        val buffer = RealBuffer()
        buffer.writeUtf8("xo".repeat(5000))

        val cursor = buffer.readAndWriteUnsafe()
        try {
            val copy = RealBuffer()
            while (cursor.next() != -1) {
                copy.write(cursor.data, cursor.start, cursor.end - cursor.start)
            }
        } finally {
            cursor.close()
        }

        assertEquals("xo".repeat(5000), buffer.readUtf8())
    }

    @Test
    fun acquireForWriteSmall() {
        val buffer = RealBuffer()
        buffer.writeUtf8("xo".repeat(5000))

        buffer.readAndWriteUnsafe().use { cursor ->
            while (cursor.next() != -1) {
                cursor.data.fill('z'.code.toByte(), cursor.start, cursor.end)
            }
        }

        assertEquals("zz".repeat(5000), buffer.readUtf8())
    }

    @Test
    fun acquireForWriteBig() {
        val buffer = RealBuffer()
        buffer.writeUtf8("xo".repeat(10000))

        buffer.readAndWriteUnsafe().use { cursor ->
            while (cursor.next() != -1) {
                cursor.data.fill('z'.code.toByte(), cursor.start, cursor.end)
            }
        }

        assertEquals("zz".repeat(10000), buffer.readUtf8())
    }

    @Test
    fun expand() {
        val buffer = RealBuffer()

        buffer.readAndWriteUnsafe().use { cursor ->
            cursor.expandBuffer(100)
            cursor.data.fill(
                'z'.code.toByte(),
                cursor.start,
                cursor.start + 100
            )
            cursor.resizeBuffer(100L)
        }

        val expected = "z".repeat(100)
        val actual = buffer.readUtf8()
        assertEquals(expected, actual)
    }

    @Test
    fun resizeBuffer() {
        val buffer = RealBuffer()

        buffer.readAndWriteUnsafe().use { cursor ->
            cursor.resizeBuffer(100L)
            cursor.data.fill('z'.code.toByte(), cursor.start, cursor.end)
        }

        assertEquals("z".repeat(100), buffer.readUtf8())
    }

    @Test
    fun testUnsafeCursorIsClosable() {
        assertTrue(AutoCloseable::class.isInstance(Buffer.UnsafeCursor.create()))
    }
}
