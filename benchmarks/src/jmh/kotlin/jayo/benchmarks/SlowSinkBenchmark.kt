package jayo.benchmarks

import jayo.Sink
import jayo.buffered
import jayo.sink
import okio.BufferedSink
import okio.buffer
import okio.sink as okioSink
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
open class SlowSinkBenchmark {
    @Param("jayo", "okio")
    private lateinit var type: String

    private lateinit var jayoSink: Sink
    private lateinit var okioSink: BufferedSink

    companion object {
        private const val CHUNKS = 256
        private const val CHUNKS_BYTE_SIZE = 8 * 1024
        const val EXPECTED_SIZE = CHUNKS * CHUNKS_BYTE_SIZE
        private val ARRAY = ByteArray(EXPECTED_SIZE) { 0x61 }
    }

    @Setup(Level.Trial)
    fun setup() {
        val delayedOutputStream = object : OutputStream() {
            val bytes = ByteArray(EXPECTED_SIZE)
            var offset = 0

            override fun write(b: Int) {
                throw Exception("Purposely not implemented")
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                if (offset >= EXPECTED_SIZE) {
                    offset = 0
                }
                randomSleep()
                b.copyInto(bytes, offset, off, len)
                offset += len
            }
        }

        when (type) {
            "jayo" -> {
                jayoSink = delayedOutputStream.sink().buffered(true)
            }

            "okio" -> {
                okioSink = delayedOutputStream.okioSink().buffer()
            }

            else -> throw IllegalStateException("Unknown type: $type")
        }
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        when (type) {
            "jayo" -> jayoSink.close()
            "okio" -> okioSink.close()
            else -> throw IllegalStateException("Unknown type: $type")
        }
    }

    private fun randomSleep() {
        Thread.sleep(Duration.ofMillis(Random.nextLong(1L, 5L)))
    }

    @Benchmark
    fun sinkJayo() {
        var written = 0
        val bytes = ByteArray(CHUNKS_BYTE_SIZE)
        while (written < EXPECTED_SIZE) {
            randomSleep()
            ARRAY.copyInto(bytes, 0, 0, CHUNKS_BYTE_SIZE)
            jayoSink.write(bytes)
            written += CHUNKS_BYTE_SIZE
        }
    }

    @Benchmark
    fun sinkOkio() {
        var written = 0
        val bytes = ByteArray(CHUNKS_BYTE_SIZE)
        while (written < EXPECTED_SIZE) {
            randomSleep()
            ARRAY.copyInto(bytes, 0, 0, CHUNKS_BYTE_SIZE)
            okioSink.write(bytes)
            written += CHUNKS_BYTE_SIZE
        }
    }
}