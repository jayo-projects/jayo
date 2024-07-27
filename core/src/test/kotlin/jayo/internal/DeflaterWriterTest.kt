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

import jayo.Buffer
import jayo.exceptions.JayoException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

class DeflaterWriterTest {
    @Test
    fun deflateWithClose() {
        val data = Buffer()
        val original = "They're moving in herds. They do move in herds."
        data.writeUtf8(original)
        val writer = Buffer()
        val deflaterWriter = DeflaterRawWriter(writer, Deflater())
        deflaterWriter.write(data, data.byteSize())
        deflaterWriter.close()
        val inflated = inflate(writer)
        assertEquals(original, inflated.readUtf8String())
    }

    @Test
    fun deflateWithSyncFlush() {
        val original = "Yes, yes, yes. That's why we're taking extreme precautions."
        val data = Buffer()
        data.writeUtf8(original)
        val writer = Buffer()
        val deflaterWriter = DeflaterRawWriter(writer, Deflater())
        deflaterWriter.write(data, data.byteSize())
        deflaterWriter.flush()
        val inflated = inflate(writer)
        assertEquals(original, inflated.readUtf8String())
    }

    @Test
    fun deflateWellCompressed() {
        val original = "a".repeat(1024 * 1024)
        val data = Buffer()
        data.writeUtf8(original)
        val writer = Buffer()
        val deflaterWriter = DeflaterRawWriter(writer, Deflater())
        deflaterWriter.write(data, data.byteSize())
        deflaterWriter.close()
        val inflated = inflate(writer)
        assertEquals(original, inflated.readUtf8String())
    }

    @Test
    fun deflatePoorlyCompressed() {
        val original = randomBytes(1024 * 1024)
        val data = Buffer()
        data.write(original)
        val writer = Buffer()
        val deflaterWriter = DeflaterRawWriter(writer, Deflater())
        deflaterWriter.write(data, data.byteSize())
        deflaterWriter.close()
        val inflated = inflate(writer)
        assertEquals(original, inflated.readByteString())
    }

    @Test
    fun multipleSegmentsWithoutCompression() {
        val buffer = Buffer()
        val deflater = Deflater()
        deflater.setLevel(Deflater.NO_COMPRESSION)
        val deflaterWriter = DeflaterRawWriter(buffer, deflater)
        val byteCount = SEGMENT_SIZE * 4
        deflaterWriter.write(Buffer().writeUtf8("a".repeat(byteCount)), byteCount.toLong())
        deflaterWriter.close()
        assertEquals("a".repeat(byteCount), inflate(buffer).readUtf8String(byteCount.toLong()))
    }

    @Test
    fun deflateIntoNonemptyWriter() {
        val original = "They're moving in herds. They do move in herds."

        // Exercise all possible offsets for the outgoing segment.
        for (i in 0 until SEGMENT_SIZE) {
            val data = Buffer().writeUtf8(original)
            val writer = Buffer().writeUtf8("a".repeat(i))
            val deflaterWriter = DeflaterRawWriter(writer, Deflater())
            deflaterWriter.write(data, data.byteSize())
            deflaterWriter.close()
            writer.skip(i.toLong())
            val inflated = inflate(writer)
            assertEquals(original, inflated.readUtf8String())
        }
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

    /**
     * This test confirms that we swallow NullPointerException from Deflater and
     * rethrow as an IOException.
     */
    @Test
    fun rethrowNullPointerAsIOException() {
        val deflater = Deflater()
        // Close to cause a NullPointerException
        deflater.end()

        val data = Buffer().apply {
            writeUtf8("They're moving in herds. They do move in herds.")
        }
        val deflaterWriter = DeflaterRawWriter(Buffer(), deflater)

        val ioe = assertThrows(JayoException::class.java) {
            deflaterWriter.write(data, data.byteSize())
        }

        assertTrue(ioe.cause!!.cause is NullPointerException)
    }

    /**
     * Uses streaming decompression to inflate `deflated`. The input must
     * either be finished or have a trailing sync flush.
     */
    private fun inflate(deflated: Buffer): Buffer {
        val deflatedIn = deflated.asInputStream()
        val inflater = Inflater()
        val inflatedIn = InflaterInputStream(deflatedIn, inflater)
        val result = Buffer()
        val buffer = ByteArray(8192)
        while (!inflater.needsInput() || deflated.byteSize() > 0 || deflatedIn.available() > 0) {
            val count = inflatedIn.read(buffer, 0, buffer.size)
            if (count != -1) {
                result.write(buffer, 0, count)
            }
        }
        return result
    }
}
