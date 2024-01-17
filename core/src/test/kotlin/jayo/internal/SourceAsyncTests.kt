/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import jayo.buffered
import jayo.encodeToByteString
import jayo.source
import java.io.InputStream
import java.time.Duration
import kotlin.random.Random

class SourceAsyncTests {
    @Test
    fun sourceSlowProducerFastConsumer() {
        val inputStream: InputStream = inputStream(true)

        inputStream.source().buffered().use { source ->
            assertThat(source.readByteString()).isEqualTo('a'.repeat(EXPECTED_SIZE).encodeToByteString())
        }
    }

    @Test
    fun sourceFastProducerSlowConsumer() {
        val inputStream: InputStream = inputStream(false)

        val bytes = ByteArray(EXPECTED_SIZE)
        var offset = 0
        inputStream.source().buffered().use { source ->
            while (offset < EXPECTED_SIZE) {
                Thread.sleep(Duration.ofNanos(Random.nextLong(5L)))
                source.readTo(bytes, offset, CHUNKS_BYTE_SIZE * 2)
                offset += CHUNKS_BYTE_SIZE * 2
            }
            assertThat(bytes).hasSize(EXPECTED_SIZE)
            assertThat(bytes.decodeToString()).isEqualTo('a'.repeat(EXPECTED_SIZE))
        }
    }

    @Test
    fun sourceSlowProducerSlowConsumer() {
        val inputStream: InputStream = inputStream(true)

        val bytes = ByteArray(EXPECTED_SIZE)
        var offset = 0
        inputStream.source().buffered().use { source ->
            while (offset < EXPECTED_SIZE) {
                Thread.sleep(Duration.ofNanos(Random.nextLong(5L)))
                source.readTo(bytes, offset, CHUNKS_BYTE_SIZE / 2)
                offset += CHUNKS_BYTE_SIZE / 2
            }
            assertThat(bytes).hasSize(EXPECTED_SIZE)
            assertThat(bytes.decodeToString()).isEqualTo('a'.repeat(EXPECTED_SIZE))
        }
    }

    companion object {
        private const val CHUNKS = 512
        const val CHUNKS_BYTE_SIZE = 1024
        const val EXPECTED_SIZE = CHUNKS * CHUNKS_BYTE_SIZE
        val ARRAY = ByteArray(CHUNKS_BYTE_SIZE) { 0x61 }
    }

    private fun inputStream(delayed: Boolean) = object : InputStream() {
        var sent = 0

        override fun read(): Int {
            throw Exception("Purposely not implemented")
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (sent >= EXPECTED_SIZE) {
                return -1
            }
            if (delayed) {
                Thread.sleep(Duration.ofNanos(Random.nextLong(5L)))
            }
            val toWrite = minOf(len, CHUNKS_BYTE_SIZE)
            ARRAY.copyInto(b, off, 0, toWrite)
            sent += toWrite
            return toWrite
        }
    }
}