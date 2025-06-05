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
import jayo.RawWriter
import jayo.Writer
import jayo.buffered
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.test.assertEquals

class BufferDeflaterWriterTest : AbstractDeflaterWriterTest(WriterFactory.BUFFER)

class RealDeflaterWriterTest : AbstractDeflaterWriterTest(WriterFactory.REAL_BUFFERED_SINK)

abstract class AbstractDeflaterWriterTest internal constructor(private val factory: WriterFactory) {

    private val data: Buffer = RealBuffer()
    private lateinit var writer: Writer

    @BeforeEach
    fun before() {
        val writerOrBuffer = factory.create(data)
        writer = if (writerOrBuffer is Buffer) {
            (writerOrBuffer as RawWriter).buffered()
        } else {
            writerOrBuffer
        }
    }

    @AfterEach
    fun after() {
        writer.close()
    }

    @Test
    fun deflateWithClose() {
        val original = "They're moving in herds. They do move in herds."
        val clearText = Buffer().write(original)
        val deflaterWriter = DeflaterRawWriter(writer, Deflater())
        deflaterWriter.write(clearText, clearText.bytesAvailable())
        deflaterWriter.close()
        val inflated = inflate(data)
        assertEquals(original, inflated.readString())
    }

    @Test
    fun deflateWithSyncFlush() {
        val original = "Yes, yes, yes. That's why we're taking extreme precautions."
        val clearText = Buffer().write(original)
        val deflaterWriter = DeflaterRawWriter(writer, Deflater())
        deflaterWriter.write(clearText, clearText.bytesAvailable())
        deflaterWriter.flush()
        val inflated = inflate(data)
        assertEquals(original, inflated.readString())
    }

    @Test
    fun deflateWellCompressed() {
        val original = "a".repeat(1024 * 1024)
        val clearText = Buffer().write(original)
        val deflaterWriter = DeflaterRawWriter(writer, Deflater())
        deflaterWriter.write(clearText, clearText.bytesAvailable())
        deflaterWriter.close()
        val inflated = inflate(data)
        assertEquals(original, inflated.readString())
    }

    @Test
    fun deflatePoorlyCompressed() {
        val original = randomBytes(1024 * 1024)
        val clearText = Buffer().write(original)
        val deflaterWriter = DeflaterRawWriter(writer, Deflater())
        deflaterWriter.write(clearText, clearText.bytesAvailable())
        deflaterWriter.close()
        val inflated = inflate(data)
        assertEquals(original, inflated.readByteString())
    }

    @Test
    fun deflateIntoNonemptyWriter() {
        val original = "They're moving in herds. They do move in herds."

        // Exercise all possible offsets for the outgoing segment.
        for (i in 0 until Segment.SIZE) {
            before()
            val clearText = Buffer().write(original)
            writer.write("a".repeat(i))
            val deflaterWriter = DeflaterRawWriter(writer, Deflater())
            deflaterWriter.write(clearText, clearText.bytesAvailable())
            deflaterWriter.close()
            data.skip(i.toLong())
            val inflated = inflate(data)
            assertEquals(original, inflated.readString())
            after()
        }
    }

    @Test
    fun multipleSegmentsWithoutCompression() {
        val deflater = Deflater()
        deflater.setLevel(Deflater.NO_COMPRESSION)
        val deflaterWriter = DeflaterRawWriter(writer, deflater)
        val byteCount = Segment.SIZE * 4
        deflaterWriter.write(Buffer().write("a".repeat(byteCount)), byteCount.toLong())
        deflaterWriter.close()
        assertEquals("a".repeat(byteCount), inflate(data).readString(byteCount.toLong()))
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
        while (!inflater.needsInput() || deflated.bytesAvailable() > 0 || deflatedIn.available() > 0) {
            val count = inflatedIn.read(buffer, 0, buffer.size)
            if (count != -1) {
                result.write(buffer, 0, count)
            }
        }
        return result
    }
}
