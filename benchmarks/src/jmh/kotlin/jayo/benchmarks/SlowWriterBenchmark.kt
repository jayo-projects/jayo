package jayo.benchmarks

import jayo.Writer
import jayo.buffered
import jayo.writer
import okio.BufferedSink
import okio.buffer
import okio.sink
import org.openjdk.jmh.annotations.*
import java.io.OutputStream
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.SECONDS)
@Timeout(time = 20)
@Warmup(iterations = 7, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.Throughput)
@Fork(value = 1)
open class SlowWriterBenchmark {
    @Param("jayo", "okio")
    private lateinit var type: String

    private lateinit var jayoWriter: Writer
    private lateinit var okioSink: BufferedSink

    companion object {
        private const val JAYO_CHUNKS = 127
        private const val JAYO_CHUNKS_BYTE_SIZE = 16_709
        const val JAYO_EXPECTED_SIZE = JAYO_CHUNKS * JAYO_CHUNKS_BYTE_SIZE
        private val JAYO_ARRAY = ByteArray(JAYO_EXPECTED_SIZE) { 0x61 }

        private const val OKIO_CHUNKS = 128
        private const val OKIO_CHUNKS_BYTE_SIZE = 16 * 1024
        const val OKIO_EXPECTED_SIZE = OKIO_CHUNKS * OKIO_CHUNKS_BYTE_SIZE
        private val OKIO_ARRAY = ByteArray(OKIO_EXPECTED_SIZE) { 0x61 }
    }

    @Setup
    fun setup() {
        val delayedOutputStreamJayo = object : OutputStream() {
            val bytes = ByteArray(JAYO_EXPECTED_SIZE)
            var offset = 0

            override fun write(b: Int) {
                throw Exception("Purposely not implemented")
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                if (offset >= JAYO_EXPECTED_SIZE) {
                    offset = 0
                }
                randomSleep()
                b.copyInto(bytes, offset, off, len)
                offset += len
            }
        }

        val delayedOutputStreamOkio = object : OutputStream() {
            val bytes = ByteArray(OKIO_EXPECTED_SIZE)
            var offset = 0

            override fun write(b: Int) {
                throw Exception("Purposely not implemented")
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                if (offset >= OKIO_EXPECTED_SIZE) {
                    offset = 0
                }
                randomSleep()
                b.copyInto(bytes, offset, off, len)
                offset += len
            }
        }

        when (type) {
            "jayo" -> {
                jayoWriter = delayedOutputStreamJayo.writer().buffered(true)
            }

            "okio" -> {
                okioSink = delayedOutputStreamOkio.sink().buffer()
            }

            else -> throw IllegalStateException("Unknown type: $type")
        }
    }

    @TearDown
    fun tearDown() {
        when (type) {
            "jayo" -> jayoWriter.close()
            "okio" -> okioSink.close()
            else -> throw IllegalStateException("Unknown type: $type")
        }
    }

    private fun randomSleep() {
        Thread.sleep(Duration.ofMillis(Random.nextLong(1L, 5L)))
    }

    @Benchmark
    fun writerJayo() {
        var written = 0
        val bytes = ByteArray(JAYO_CHUNKS_BYTE_SIZE)
        while (written < JAYO_EXPECTED_SIZE) {
            randomSleep()
            JAYO_ARRAY.copyInto(bytes, 0, 0, JAYO_CHUNKS_BYTE_SIZE)
            jayoWriter.write(bytes)
            written += JAYO_CHUNKS_BYTE_SIZE
        }
    }

    @Benchmark
    fun sinkOkio() {
        var written = 0
        val bytes = ByteArray(OKIO_CHUNKS_BYTE_SIZE)
        while (written < OKIO_EXPECTED_SIZE) {
            randomSleep()
            OKIO_ARRAY.copyInto(bytes, 0, 0, OKIO_CHUNKS_BYTE_SIZE)
            okioSink.write(bytes)
            written += OKIO_CHUNKS_BYTE_SIZE
        }
    }
}