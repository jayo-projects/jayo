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

package jayo.internal

import jayo.*
import jayo.exceptions.JayoException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Non factory based tests for deflate and inflate
 */
class DeflateKotlinTest {
    @Test
    fun deflate() {
        val data = Buffer()
        val deflater = Jayo.deflate(data as RawWriter)
        deflater.buffered().writeUtf8("Hi!").close()
        assertEquals("789cf3c854040001ce00d3", data.readByteString().hex())
    }

    @Test
    fun deflateWithDeflater() {
        val data = Buffer()
        val deflater = (data as RawWriter).deflate(Deflater(0, true))
        deflater.buffered().writeUtf8("Hi!").close()
        assertEquals("010300fcff486921", data.readByteString().hex())
    }

    /**
     * This test confirms that we swallow NullPointerException from Deflater and
     * rethrow as an IOException.
     */
    @Test
    fun rethrowNullPointerAsIOException() {
        val deflater = Deflater()
        // Close to cause a NullPointerException
        deflater.end()

        val data = Buffer().writeUtf8("They're moving in herds. They do move in herds.")
        val deflaterWriter = DeflaterRawWriter(Buffer(), deflater)

        val ioe = assertThrows(JayoException::class.java) {
            deflaterWriter.write(data, data.byteSize())
        }

        assertTrue(ioe.cause!!.cause is NullPointerException)
    }

    /**
     * This test deflates a single segment of without compression because that's
     * the easiest way to force close() to emit a large amount of data to the
     * underlying writer.
     */
    @Test
    fun closeWithExceptionWhenWritingAndClosing() {
        val mockWriter = MockWriter()
        mockWriter.scheduleThrow(0, JayoException("first"))
        mockWriter.scheduleThrow(1, JayoException("second"))
        val deflater = Deflater()
        deflater.setLevel(Deflater.NO_COMPRESSION)
        val deflaterWriter = DeflaterRawWriter(mockWriter, deflater)
        deflaterWriter.write(Buffer().writeUtf8("a".repeat(SEGMENT_SIZE)), SEGMENT_SIZE.toLong())
        try {
            deflaterWriter.close()
            fail()
        } catch (expected: JayoException) {
            assertEquals("first", expected.message)
        }
        mockWriter.assertLogContains("close()")
    }

    @Test
    fun inflate() {
        val buffer = Buffer().write("789cf3c854040001ce00d3".decodeHex())
        val inflated = (buffer as RawReader).inflate()
        assertEquals("Hi!", inflated.buffered().readUtf8String())
    }

    @Test
    fun inflateWithInflater() {
        val buffer = Buffer().write("010300fcff486921".decodeHex())
        val inflated = (buffer as Reader).inflate(Inflater(true))
        assertEquals("Hi!", inflated.buffered().readUtf8String())
    }
}
