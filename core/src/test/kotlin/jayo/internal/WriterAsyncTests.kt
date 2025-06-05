/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal

import jayo.buffered
import jayo.writer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import java.io.OutputStream
import kotlin.random.Random
import kotlin.test.fail

// these tests are a good race-condition test, do them several times!
class WriterAsyncTests {
    companion object {
        private const val CHUNKS = 32
        const val CHUNKS_BYTE_SIZE = 64 * 1024
        const val EXPECTED_SIZE = CHUNKS * CHUNKS_BYTE_SIZE
        val ARRAY = ByteArray(EXPECTED_SIZE) { 0x61 }
    }

    @RepeatedTest(10)
    fun writerFastProducerSlowEmitter() {
        val outputStream = outputStream(true)

        outputStream.writer().buffered().use { writer ->
            writer.write('a'.repeat(EXPECTED_SIZE))
        }
        assertThat(outputStream.bytes).hasSize(EXPECTED_SIZE)
        assertThat(outputStream.bytes).isEqualTo(ARRAY)
    }

    @RepeatedTest(10)
    fun writerSlowProducerFastEmitter() {
        val outputStream = outputStream(false)

        var written = 0
        outputStream.writer().buffered().use { writer ->
            val toWrite = CHUNKS_BYTE_SIZE
            val bytes = ByteArray(toWrite)
            while (written < EXPECTED_SIZE) {
                Thread.sleep(0, Random.nextInt(5) /*in nanos*/)
                ARRAY.copyInto(bytes, 0, 0, toWrite)
                writer.write(bytes)
                written += toWrite
            }
        }
        assertThat(outputStream.bytes).hasSize(EXPECTED_SIZE)
        assertThat(outputStream.bytes).isEqualTo(ARRAY)
    }

    @RepeatedTest(10)
    fun writerSlowProducerSlowEmitter() {
        val outputStream = outputStream(true)

        var written = 0
        outputStream.writer().buffered().use { writer ->
            val toWrite = CHUNKS_BYTE_SIZE
            val bytes = ByteArray(toWrite)
            while (written < EXPECTED_SIZE) {
                Thread.sleep(0, Random.nextInt(5) /*in nanos*/)
                ARRAY.copyInto(bytes, 0, 0, toWrite)
                writer.write(bytes)
                written += toWrite
            }
        }
        assertThat(outputStream.bytes).hasSize(EXPECTED_SIZE)
        assertThat(outputStream.bytes).isEqualTo(ARRAY)
    }

    private fun outputStream(delayed: Boolean) = object : OutputStream() {
        val bytes = ByteArray(EXPECTED_SIZE)
        var offset = 0
        override fun write(b: Int) {
            throw Exception("Purposely not implemented")
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (offset >= EXPECTED_SIZE) {
                fail()
            }
            if (delayed) {
                Thread.sleep(0, Random.nextInt(5) /*in nanos*/)
            }
            b.copyInto(bytes, offset, off, len)
            offset += len
        }
    }
}