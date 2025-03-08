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
import jayo.bytestring.ByteString.of
import jayo.bytestring.decodeHex
import jayo.JayoException
import jayo.bytestring.ByteString
import jayo.bytestring.encodeToUtf8
import org.junit.jupiter.api.assertThrows
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class GzipReaderTest {
    @Test
    fun gunzip() {
        val gzipped = Buffer()
        gzipped.write(gzipHeader)
        gzipped.write(deflated)
        gzipped.write(gzipTrailer)
        assertGzipped(gzipped)
    }

    @Test
    fun gunzip_withHCRC() {
        val hcrc = CRC32()
        val gzipHeader = gzipHeaderWithFlags(0x02.toByte())
        hcrc.update(gzipHeader.toByteArray())
        val gzipped = Buffer()
        gzipped.write(gzipHeader)
        gzipped.writeShort(java.lang.Short.reverseBytes(hcrc.value.toShort())) // little endian
        gzipped.write(deflated)
        gzipped.write(gzipTrailer)
        assertGzipped(gzipped)
    }

    @Test
    fun gunzip_withExtra() {
        val gzipped = Buffer()
        gzipped.write(gzipHeaderWithFlags(0x04.toByte()))
        gzipped.writeShort(java.lang.Short.reverseBytes(7.toShort())) // little endian extra length
        gzipped.write("blubber".encodeToUtf8().toByteArray(), 0, 7)
        gzipped.write(deflated)
        gzipped.write(gzipTrailer)
        assertGzipped(gzipped)
    }

    @Test
    fun gunzip_withName() {
        val gzipped = Buffer()
        gzipped.write(gzipHeaderWithFlags(0x08.toByte()))
        gzipped.write("foo.txt".encodeToUtf8().toByteArray(), 0, 7)
        gzipped.writeByte(0) // zero-terminated
        gzipped.write(deflated)
        gzipped.write(gzipTrailer)
        assertGzipped(gzipped)
    }

    @Test
    fun gunzip_withComment() {
        val gzipped = Buffer()
        gzipped.write(gzipHeaderWithFlags(0x10.toByte()))
        gzipped.write("rubbish".encodeToUtf8().toByteArray(), 0, 7)
        gzipped.writeByte(0) // zero-terminated
        gzipped.write(deflated)
        gzipped.write(gzipTrailer)
        assertGzipped(gzipped)
    }

    /**
     * For portability, it is a good idea to export the gzipped bytes and try running gzip.  Ex.
     * `echo gzipped | base64 --decode | gzip -l -v`
     */
    @Test
    fun gunzip_withAll() {
        val gzipped = Buffer()
        gzipped.write(gzipHeaderWithFlags(0x1c.toByte()))
        gzipped.writeShort(java.lang.Short.reverseBytes(7.toShort())) // little endian extra length
        gzipped.write("blubber".encodeToUtf8().toByteArray(), 0, 7)
        gzipped.write("foo.txt".encodeToUtf8().toByteArray(), 0, 7)
        gzipped.writeByte(0) // zero-terminated
        gzipped.write("rubbish".encodeToUtf8().toByteArray(), 0, 7)
        gzipped.writeByte(0) // zero-terminated
        gzipped.write(deflated)
        gzipped.write(gzipTrailer)
        assertGzipped(gzipped)
    }

    /**
     * Note that you cannot test this with old versions of gzip, as they interpret flag bit 1 as
     * CONTINUATION, not HCRC. For example, this is the case with the default gzip on osx.
     */
    @Test
    fun gunzipWhenHeaderCRCIncorrect() {
        val gzipped = Buffer()
        gzipped.write(gzipHeaderWithFlags(0x02.toByte()))
        gzipped.writeShort(0.toShort()) // wrong HCRC!
        gzipped.write(deflated)
        gzipped.write(gzipTrailer)
        try {
            gunzip(gzipped)
            fail()
        } catch (e: JayoException) {
            assertEquals("FHCRC: actual 0x261d != expected 0x0", e.message)
        }
    }

    @Test
    fun gunzipWhenCRCIncorrect() {
        val gzipped = Buffer()
        gzipped.write(gzipHeader)
        gzipped.write(deflated)
        gzipped.writeInt(Integer.reverseBytes(0x1234567)) // wrong CRC
        gzipped.write(gzipTrailer.toByteArray(), 3, 4)
        try {
            gunzip(gzipped)
            fail()
        } catch (e: JayoException) {
            assertEquals("CRC: actual 0x37ad8f8d != expected 0x1234567", e.message)
        }
    }

    @Test
    fun gunzipWhenLengthIncorrect() {
        val gzipped = Buffer()
        gzipped.write(gzipHeader)
        gzipped.write(deflated)
        gzipped.write(gzipTrailer.toByteArray(), 0, 4)
        gzipped.writeInt(Integer.reverseBytes(0x123456)) // wrong length
        try {
            gunzip(gzipped)
            fail()
        } catch (e: JayoException) {
            assertEquals("ISIZE: actual 0x20 != expected 0x123456", e.message)
        }
    }

    @Test
    fun gunzipExhaustsReader() {
        val gzippedReader = Buffer()
            .write("1f8b08000000000000004b4c4a0600c241243503000000".decodeHex()) // 'abc'
        val exhaustableReader = ExhaustableReader(gzippedReader)
        val gunzippedReader = exhaustableReader.buffered().gzip().buffered()
        assertEquals('a'.code.toLong(), gunzippedReader.readByte().toLong())
        assertEquals('b'.code.toLong(), gunzippedReader.readByte().toLong())
        assertEquals('c'.code.toLong(), gunzippedReader.readByte().toLong())
        //assertFalse(exhaustableReader.exhausted)
        assertEquals(-1, gunzippedReader.readAtMostTo(Buffer(), 1))
        assertTrue(exhaustableReader.exhausted)
    }

    @Test
    fun gunzipThrowsIfReaderIsNotExhausted() {
        val gzippedReader = Buffer()
            .write("1f8b08000000000000004b4c4a0600c241243503000000".decodeHex()) // 'abc'
        gzippedReader.writeByte('d'.code.toByte()) // This byte shouldn't be here!
        val gunzippedReader = gzippedReader.gzip().buffered()
        assertEquals('a'.code.toLong(), gunzippedReader.readByte().toLong())
        assertEquals('b'.code.toLong(), gunzippedReader.readByte().toLong())
        assertEquals('c'.code.toLong(), gunzippedReader.readByte().toLong())
        assertThrows<JayoException> { gunzippedReader.readByte() }
    }

    private fun assertGzipped(gzipped: Buffer) {
        val gunzipped = gunzip(gzipped)
        assertEquals("It's a UNIX system! I know this!", gunzipped.readString())
    }

    private fun gzipHeaderWithFlags(flags: Byte): ByteString {
        val result = gzipHeader.toByteArray()
        result[3] = flags
        return of(*result)
    }

    private val gzipHeader = "1f8b0800000000000000".decodeHex()

    // Deflated "It's a UNIX system! I know this!"
    private val deflated = "f32c512f56485408f5f38c5028ae2c2e49cd5554f054c8cecb2f5728c9c82c560400".decodeHex()
    private val gzipTrailer = (
            "" +
                    "8d8fad37" + // Checksum of deflated.
                    "20000000"
            ) // 32 in little endian.
        .decodeHex()

    private fun gunzip(gzipped: Buffer): Buffer {
        val result = Buffer()
        val reader = gzipped.gzip()
        while (reader.readAtMostTo(result, Int.MAX_VALUE.toLong()) != -1L) {
        }
        return result
    }

    /** This reader keeps track of whether its read has returned -1.  */
    internal class ExhaustableReader(private val reader: RawReader) : RawReader {
        var exhausted = false

        override fun readAtMostTo(writer: Buffer, byteCount: Long): Long {
            val result = reader.readAtMostTo(writer, byteCount)
            if (result == -1L) exhausted = true
            return result
        }

        override fun close() {
            reader.close()
        }
    }
}
