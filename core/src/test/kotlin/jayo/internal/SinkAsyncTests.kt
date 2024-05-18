/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal

import jayo.buffered
import jayo.sink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.OutputStream
import java.time.Duration
import kotlin.random.Random
import kotlin.test.fail

// these tests are a good race-condition test, do them several times !
class SinkAsyncTests {
    @Test
    fun sinkFastProducerSlowEmitter() {
        repeat(10) {
            val outputStream = outputStream(true)

            outputStream.sink().buffered().use { sink ->
                sink.writeUtf8('a'.repeat(EXPECTED_SIZE))
            }
            assertThat(outputStream.bytes).hasSize(EXPECTED_SIZE)
            assertThat(outputStream.bytes).isEqualTo(ARRAY)
        }
    }

    @Test
    fun asyncSinkFastProducerSlowEmitter() {
        repeat(10) {
            val outputStream = outputStream(true)

            outputStream.sink().buffered(true).use { sink ->
                sink.writeUtf8('a'.repeat(EXPECTED_SIZE))
            }
            assertThat(outputStream.bytes).hasSize(EXPECTED_SIZE)
            assertThat(outputStream.bytes).isEqualTo(ARRAY)
        }
    }

    @Test
    fun sinkSlowProducerFastEmitter() {
        repeat(10) {
            val outputStream = outputStream(false)

            var written = 0
            outputStream.sink().buffered().use { sink ->
                val toWrite = CHUNKS_BYTE_SIZE
                val bytes = ByteArray(toWrite)
                while (written < EXPECTED_SIZE) {
                    Thread.sleep(Duration.ofNanos(Random.nextLong(5L)))
                    ARRAY.copyInto(bytes, 0, 0, toWrite)
                    sink.write(bytes)
                    written += toWrite
                }
            }
            assertThat(outputStream.bytes).hasSize(EXPECTED_SIZE)
            assertThat(outputStream.bytes).isEqualTo(ARRAY)
        }
    }

    @Test
    fun asyncSinkSlowProducerFastEmitter() {
        repeat(10) {
            val outputStream = outputStream(false)

            var written = 0
            outputStream.sink().buffered().use { sink ->
                val toWrite = CHUNKS_BYTE_SIZE
                val bytes = ByteArray(toWrite)
                while (written < EXPECTED_SIZE) {
                    Thread.sleep(Duration.ofNanos(Random.nextLong(5L)))
                    ARRAY.copyInto(bytes, 0, 0, toWrite)
                    sink.write(bytes)
                    written += toWrite
                }
            }
            assertThat(outputStream.bytes).hasSize(EXPECTED_SIZE)
            assertThat(outputStream.bytes).isEqualTo(ARRAY)
        }
    }

    @Test
    fun sinkSlowProducerSlowEmitter() {
        repeat(10) {
            val outputStream = outputStream(true)

            var written = 0
            outputStream.sink().buffered().use { sink ->
                val toWrite = CHUNKS_BYTE_SIZE
                val bytes = ByteArray(toWrite)
                while (written < EXPECTED_SIZE) {
                    Thread.sleep(Duration.ofNanos(Random.nextLong(5L)))
                    ARRAY.copyInto(bytes, 0, 0, toWrite)
                    sink.write(bytes)
                    written += toWrite
                }
            }
            assertThat(outputStream.bytes).hasSize(EXPECTED_SIZE)
            assertThat(outputStream.bytes).isEqualTo(ARRAY)
        }
    }

    @Test
    fun asyncSinkSlowProducerSlowEmitter() {
        repeat(10) {
            val outputStream = outputStream(true)

            var written = 0
            outputStream.sink().buffered().use { sink ->
                val toWrite = CHUNKS_BYTE_SIZE
                val bytes = ByteArray(toWrite)
                while (written < EXPECTED_SIZE) {
                    Thread.sleep(Duration.ofNanos(Random.nextLong(5L)))
                    ARRAY.copyInto(bytes, 0, 0, toWrite)
                    sink.write(bytes)
                    written += toWrite
                }
            }
            assertThat(outputStream.bytes).hasSize(EXPECTED_SIZE)
            assertThat(outputStream.bytes).isEqualTo(ARRAY)
        }
    }

    companion object {
        private const val CHUNKS = 32
        const val CHUNKS_BYTE_SIZE = 64 * 1024
        const val EXPECTED_SIZE = CHUNKS * CHUNKS_BYTE_SIZE
        val ARRAY = ByteArray(EXPECTED_SIZE) { 0x61 }
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
                Thread.sleep(Duration.ofNanos(Random.nextLong(5L)))
            }
            b.copyInto(bytes, offset, off, len)
            offset += len
        }
    }
}