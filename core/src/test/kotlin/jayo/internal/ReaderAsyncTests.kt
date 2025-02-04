/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal

import jayo.*
import jayo.scheduling.TaskRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import java.io.InputStream
import kotlin.random.Random

// these tests are a good race-condition test, do them several times !
class ReaderAsyncTests {
    companion object {
        private const val CHUNKS = 16
        const val CHUNKS_BYTE_SIZE = 4 * Segment.SIZE
        const val EXPECTED_SIZE = CHUNKS * CHUNKS_BYTE_SIZE
        val ARRAY = ByteArray(CHUNKS_BYTE_SIZE) { 0x61 }

        val TASK_RUNNER: TaskRunner = TaskRunner.create("ReaderAsyncTests-")
    }

    @RepeatedTest(10)
    fun readerSlowProducerFastConsumer() {
        val inputStream: InputStream = inputStream(true)

        inputStream.reader().buffered().use { reader ->
            assertThat(reader.readByteString()).isEqualTo('a'.repeat(EXPECTED_SIZE).encodeToUtf8())
        }
    }

    @RepeatedTest(10)
    fun asyncReaderSlowProducerFastConsumer() {
        val inputStream: InputStream = inputStream(true)

        inputStream.reader().buffered(TASK_RUNNER).use { reader ->
            assertThat(reader.readByteString()).isEqualTo('a'.repeat(EXPECTED_SIZE).encodeToUtf8())
        }
    }

    @RepeatedTest(10)
    fun readerFastProducerSlowConsumer() {
        val inputStream: InputStream = inputStream(false)

        val bytes = ByteArray(EXPECTED_SIZE)
        var offset = 0
        inputStream.reader().buffered().use { reader ->
            while (offset < EXPECTED_SIZE) {
                Thread.sleep(0, Random.nextInt(5) /*in nanos*/)
                reader.readTo(bytes, offset, CHUNKS_BYTE_SIZE * 2)
                offset += CHUNKS_BYTE_SIZE * 2
            }
            assertThat(bytes).hasSize(EXPECTED_SIZE)
            assertThat(bytes.decodeToString()).isEqualTo('a'.repeat(EXPECTED_SIZE))
        }
    }

    @RepeatedTest(10)
    fun asyncReaderFastProducerSlowConsumer() {
        val inputStream: InputStream = inputStream(false)

        val bytes = ByteArray(EXPECTED_SIZE)
        var offset = 0
        inputStream.reader().buffered(TASK_RUNNER).use { reader ->
            while (offset < EXPECTED_SIZE) {
                Thread.sleep(0, Random.nextInt(5) /*in nanos*/)
                reader.readTo(bytes, offset, CHUNKS_BYTE_SIZE * 2)
                offset += CHUNKS_BYTE_SIZE * 2
            }
            assertThat(bytes).hasSize(EXPECTED_SIZE)
            assertThat(bytes.decodeToString()).isEqualTo('a'.repeat(EXPECTED_SIZE))
        }
    }

    @RepeatedTest(10)
    fun readerSlowProducerSlowConsumer() {
        val inputStream: InputStream = inputStream(true)

        val bytes = ByteArray(EXPECTED_SIZE)
        var offset = 0
        inputStream.reader().buffered().use { reader ->
            while (offset < EXPECTED_SIZE) {
                Thread.sleep(0, Random.nextInt(5) /*in nanos*/)
                reader.readTo(bytes, offset, CHUNKS_BYTE_SIZE / 2)
                offset += CHUNKS_BYTE_SIZE / 2
            }
            assertThat(bytes).hasSize(EXPECTED_SIZE)
            assertThat(bytes.decodeToString()).isEqualTo('a'.repeat(EXPECTED_SIZE))
        }
    }

    @RepeatedTest(30)
    fun asyncReaderSlowProducerSlowConsumer() {
        val inputStream: InputStream = inputStream(true)

        val bytes = ByteArray(EXPECTED_SIZE)
        var offset = 0
        Jayo.bufferAsync(inputStream.reader(), TASK_RUNNER).use { reader ->
            while (offset < EXPECTED_SIZE) {
                Thread.sleep(0, Random.nextInt(5) /*in nanos*/)
                reader.readTo(bytes, offset, CHUNKS_BYTE_SIZE / 2)
                offset += CHUNKS_BYTE_SIZE / 2
            }
            assertThat(bytes).hasSize(EXPECTED_SIZE)
            assertThat(bytes.decodeToString()).isEqualTo('a'.repeat(EXPECTED_SIZE))
        }
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
                Thread.sleep(0, Random.nextInt(5) /*in nanos*/)
            }
            val toWrite = minOf(len, CHUNKS_BYTE_SIZE)
            ARRAY.copyInto(b, off, 0, toWrite)
            sent += toWrite
            return toWrite
        }
    }
}