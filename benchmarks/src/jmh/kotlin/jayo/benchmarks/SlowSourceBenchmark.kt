package jayo.benchmarks

import jayo.Source
import jayo.buffered
import jayo.source
import okio.BufferedSource
import okio.buffer
import okio.source as okioSource
import org.openjdk.jmh.annotations.*
import java.io.InputStream
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
open class SlowSourceBenchmark {
    @Param("jayo", "okio")
    private lateinit var type: String

    private lateinit var jayoSource: Source
    private lateinit var okioSource: BufferedSource

    companion object {
        private const val CHUNKS = 256
        private const val CHUNKS_BYTE_SIZE = 1024
        private val ARRAY = ByteArray(CHUNKS_BYTE_SIZE) { 0x61 }
    }

    @Setup(Level.Trial)
    fun setup() {
        val delayedInputStream = object : InputStream() {
            override fun read(): Int {
                throw Exception("Purposely not implemented")
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                randomSleep()
                val toRead = minOf(len, CHUNKS_BYTE_SIZE)
                ARRAY.copyInto(b, off, 0, toRead)
                return toRead
            }
        }

        when (type) {
            "jayo" -> {
                jayoSource = delayedInputStream.source().buffered(true)
            }

            "okio" -> {
                okioSource = delayedInputStream.okioSource().buffer()
            }

            else -> throw IllegalStateException("Unknown type: $type")
        }
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        when (type) {
            "jayo" -> jayoSource.close()
            "okio" -> okioSource.close()
            else -> throw IllegalStateException("Unknown type: $type")
        }
    }

    private fun randomSleep() {
        Thread.sleep(Duration.ofMillis(Random.nextLong(1L, 5L)))
    }

    @Benchmark
    fun sourceJayo() {
        IntRange(0, CHUNKS).forEach { _ ->
            randomSleep()
            check(jayoSource.readByteArray(CHUNKS_BYTE_SIZE.toLong()).contentEquals(ARRAY))
        }
    }

    @Benchmark
    fun sourceOkio() {
        IntRange(0, CHUNKS).forEach { _ ->
            randomSleep()
            check(okioSource.readByteArray(CHUNKS_BYTE_SIZE.toLong()).contentEquals(ARRAY))
        }
    }
}