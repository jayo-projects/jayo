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
import jayo.exceptions.JayoEOFException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.assertThrows
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import kotlin.test.Test
import kotlin.test.assertEquals

class BufferInflaterReaderTest : AbstractInflaterReaderTest(ReaderFactory.BUFFER)

class RealInflaterReaderTest : AbstractInflaterReaderTest(ReaderFactory.REAL_SOURCE)

class PeekInflaterBufferTest : AbstractInflaterReaderTest(ReaderFactory.PEEK_BUFFER)

class PeekInflaterReaderTest : AbstractInflaterReaderTest(ReaderFactory.PEEK_SOURCE)

class BufferedInflaterReaderTest : AbstractInflaterReaderTest(ReaderFactory.BUFFERED_SOURCE)

abstract class AbstractInflaterReaderTest internal constructor(private val bufferFactory: ReaderFactory) {

    private lateinit var deflatedWriter: Writer
    private lateinit var deflatedReader: Reader

    init {
        resetDeflatedReaderAndWriter()
    }

    private fun resetDeflatedReaderAndWriter() {
        val pipe = bufferFactory.pipe()
        deflatedWriter = pipe.writer
        deflatedReader = pipe.reader
    }

    @Test
    fun inflate() {
        decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=")
        val inflated = inflate(deflatedReader)
        assertEquals("God help us, we're in the hands of engineers.", inflated.readUtf8String())
    }

    @Test
    fun inflateTruncated() {
        decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CDw==")
        assertThrows<JayoEOFException> { inflate(deflatedReader) }
    }

    @Test
    fun inflateWellCompressed() {
        decodeBase64(
            "eJztwTEBAAAAwqCs61/CEL5AAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB8BtFeWvE=",
        )
        val original = "a".repeat(1024 * 1024)
        deflate(original.encodeToByteString())
        val inflated = inflate(deflatedReader)
        assertEquals(original, inflated.readUtf8String())
    }

    @Test
    fun inflatePoorlyCompressed() {
        val original = randomBytes(1024 * 1024)
        deflate(original)
        val inflated = inflate(deflatedReader)
        assertEquals(original, inflated.readByteString())
    }

    @Test
    fun inflateIntoNonemptyWriter() {
        // fixme inflater reader does not like async Readers !
        if (bufferFactory == ReaderFactory.BUFFER || bufferFactory == ReaderFactory.PEEK_BUFFER) {
            for (i in 0 until SEGMENT_SIZE) {
                resetDeflatedReaderAndWriter()
                val inflated = Buffer().writeUtf8("a".repeat(i))
                deflate("God help us, we're in the hands of engineers.".encodeToByteString())
                val reader = RealInflaterRawReader(deflatedReader, Inflater())
                while (reader.readAtMostTo(inflated, Int.MAX_VALUE.toLong()) != -1L) {
                }
                inflated.skip(i.toLong())
                assertEquals("God help us, we're in the hands of engineers.", inflated.readUtf8String())
            }
        }
    }

    @Test
    fun inflateSingleByte() {
        val inflated = Buffer()
        decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=")
        val reader = RealInflaterRawReader(deflatedReader, Inflater())
        reader.readOrInflateAtMostTo(inflated, 1)
        reader.close()
        assertEquals("G", inflated.readUtf8String())
        assertEquals(0, inflated.byteSize())
    }

    @Test
    fun inflateByteCount() {
        val inflated = Buffer()
        decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=")
        val reader = RealInflaterRawReader(deflatedReader, Inflater())
        reader.readAtMostTo(inflated, 11)
        reader.close()
        assertEquals("God help us", inflated.readUtf8String())
        assertEquals(0, inflated.byteSize())
    }

    @Test
    fun readerExhaustedPrematurelyOnRead() {
        // Deflate 0 bytes of data that lacks the in-stream terminator.
        decodeBase64("eJwAAAD//w==")
        val inflated = Buffer()
        val inflater = Inflater()
        val reader = RealInflaterRawReader(deflatedReader, inflater)
        assertThat(deflatedReader.exhausted()).isFalse
        try {
            reader.readAtMostTo(inflated, Long.MAX_VALUE)
            fail()
        } catch (expected: JayoEOFException) {
            assertThat(expected).hasMessage("reader exhausted prematurely")
        }

        // Despite the exception, the read() call made forward progress on the underlying stream!
        assertThat(deflatedReader.exhausted()).isTrue
    }

    /**
     * Confirm that [InflaterRawReader.readOrInflateAtMostTo] consumes a byte on each call even if it
     * doesn't produce a byte on every call.
     */
    @Test
    fun readOrInflateMakesByteByByteProgress() {
        // Deflate 0 bytes of data that lacks the in-stream terminator.
        decodeBase64("eJwAAAD//w==")
        val deflatedByteCount = 7
        val inflated = Buffer()
        val inflater = Inflater()
        val reader = RealInflaterRawReader(deflatedReader, inflater)
        //assertThat(deflatedReader.exhausted()).isFalse
        assertThat(reader.readOrInflateAtMostTo(inflated, Long.MAX_VALUE)).isEqualTo(0L)
        assertThat(inflater.bytesRead).isEqualTo(deflatedByteCount.toLong())
        assertThat(deflatedReader.exhausted()).isTrue()
    }

    private fun decodeBase64(s: String) {
        deflatedWriter.write(s.decodeBase64()!!)
        deflatedWriter.flush()
    }

    /** Use DeflaterOutputStream to deflate reader.  */
    private fun deflate(reader: ByteString) {
        val writer = DeflaterOutputStream(deflatedWriter.asOutputStream()).writer()
        writer.write(Buffer().write(reader), reader.byteSize().toLong())
        writer.close()
    }

    /** Returns a new buffer containing the inflated contents of `deflated`.  */
    private fun inflate(deflated: Reader?): Buffer {
        val result = Buffer()
        val reader = Jayo.inflate(deflated!!)
        while (reader.readAtMostTo(result, Int.MAX_VALUE.toLong()) != -1L) {
        }
        return result
    }
}
