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

class BufferInflaterSourceTest : AbstractInflaterSourceTest(SourceFactory.BUFFER)

class RealInflaterSourceTest : AbstractInflaterSourceTest(SourceFactory.REAL_SOURCE)

class PeekInflaterBufferTest : AbstractInflaterSourceTest(SourceFactory.PEEK_BUFFER)

class PeekInflaterSourceTest : AbstractInflaterSourceTest(SourceFactory.PEEK_SOURCE)

class BufferedInflaterSourceTest : AbstractInflaterSourceTest(SourceFactory.BUFFERED_SOURCE)

abstract class AbstractInflaterSourceTest internal constructor(private val bufferFactory: SourceFactory) {

    private lateinit var deflatedSink: Sink
    private lateinit var deflatedSource: Source

    init {
        resetDeflatedSourceAndSink()
    }

    private fun resetDeflatedSourceAndSink() {
        val pipe = bufferFactory.pipe()
        deflatedSink = pipe.sink
        deflatedSource = pipe.source
    }

    @Test
    fun inflate() {
        decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=")
        val inflated = inflate(deflatedSource)
        assertEquals("God help us, we're in the hands of engineers.", inflated.readUtf8())
    }

    @Test
    fun inflateTruncated() {
        decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CDw==")
        assertThrows<JayoEOFException> { inflate(deflatedSource) }
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
        val inflated = inflate(deflatedSource)
        assertEquals(original, inflated.readUtf8())
    }

    @Test
    fun inflatePoorlyCompressed() {
        val original = randomBytes(1024 * 1024)
        deflate(original)
        val inflated = inflate(deflatedSource)
        assertEquals(original, inflated.readByteString())
    }

    @Test
    fun inflateIntoNonemptySink() {
        // fixme inflater source does not like async Sources !
        if (bufferFactory == SourceFactory.BUFFER || bufferFactory == SourceFactory.PEEK_BUFFER) {
            for (i in 0 until SEGMENT_SIZE) {
                resetDeflatedSourceAndSink()
                val inflated = Buffer().writeUtf8("a".repeat(i))
                deflate("God help us, we're in the hands of engineers.".encodeToByteString())
                val source = RealInflaterRawSource(deflatedSource, Inflater())
                while (source.readAtMostTo(inflated, Int.MAX_VALUE.toLong()) != -1L) {
                }
                inflated.skip(i.toLong())
                assertEquals("God help us, we're in the hands of engineers.", inflated.readUtf8())
            }
        }
    }

    @Test
    fun inflateSingleByte() {
        val inflated = Buffer()
        decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=")
        val source = RealInflaterRawSource(deflatedSource, Inflater())
        source.readOrInflateAtMostTo(inflated, 1)
        source.close()
        assertEquals("G", inflated.readUtf8())
        assertEquals(0, inflated.byteSize())
    }

    @Test
    fun inflateByteCount() {
        val inflated = Buffer()
        decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=")
        val source = RealInflaterRawSource(deflatedSource, Inflater())
        source.readAtMostTo(inflated, 11)
        source.close()
        assertEquals("God help us", inflated.readUtf8())
        assertEquals(0, inflated.byteSize())
    }

    @Test
    fun sourceExhaustedPrematurelyOnRead() {
        // Deflate 0 bytes of data that lacks the in-stream terminator.
        decodeBase64("eJwAAAD//w==")
        val inflated = Buffer()
        val inflater = Inflater()
        val source = RealInflaterRawSource(deflatedSource, inflater)
        assertThat(deflatedSource.exhausted()).isFalse
        try {
            source.readAtMostTo(inflated, Long.MAX_VALUE)
            fail()
        } catch (expected: JayoEOFException) {
            assertThat(expected).hasMessage("source exhausted prematurely")
        }

        // Despite the exception, the read() call made forward progress on the underlying stream!
        assertThat(deflatedSource.exhausted()).isTrue
    }

    /**
     * Confirm that [InflaterRawSource.readOrInflateAtMostTo] consumes a byte on each call even if it
     * doesn't produce a byte on every call.
     */
    @Test
    fun readOrInflateMakesByteByByteProgress() {
        // Deflate 0 bytes of data that lacks the in-stream terminator.
        decodeBase64("eJwAAAD//w==")
        val deflatedByteCount = 7
        val inflated = Buffer()
        val inflater = Inflater()
        val source = RealInflaterRawSource(deflatedSource, inflater)
        //assertThat(deflatedSource.exhausted()).isFalse
        assertThat(source.readOrInflateAtMostTo(inflated, Long.MAX_VALUE)).isEqualTo(0L)
        assertThat(inflater.bytesRead).isEqualTo(deflatedByteCount.toLong())
        assertThat(deflatedSource.exhausted()).isTrue()
    }

    private fun decodeBase64(s: String) {
        deflatedSink.write(s.decodeBase64()!!)
        deflatedSink.flush()
    }

    /** Use DeflaterOutputStream to deflate source.  */
    private fun deflate(source: ByteString) {
        val sink = DeflaterOutputStream(deflatedSink.asOutputStream()).sink()
        sink.write(Buffer().write(source), source.byteSize().toLong())
        sink.close()
    }

    /** Returns a new buffer containing the inflated contents of `deflated`.  */
    private fun inflate(deflated: Source?): Buffer {
        val result = Buffer()
        val source = Jayo.inflate(deflated!!)
        while (source.readAtMostTo(result, Int.MAX_VALUE.toLong()) != -1L) {
        }
        return result
    }
}
