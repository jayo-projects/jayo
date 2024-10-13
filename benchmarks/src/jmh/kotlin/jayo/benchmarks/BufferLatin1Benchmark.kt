package jayo.benchmarks

import jayo.Buffer
import jayo.ByteString
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@Timeout(time = 20)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.Throughput)
@Fork(value = 1)
open class BufferLatin1Benchmark {
    companion object {
        private val strings = java.util.Map.of(
            "ascii",
            "Um, I'll tell you the problem with the scientific power that you're using here, "
                    + "it didn't require any discipline to attain it. You read what others had done and you "
                    + "took the next step. You didn't earn the knowledge for yourselves, so you don't take any "
                    + "responsibility for it. You stood on the shoulders of geniuses to accomplish something "
                    + "as fast as you could, and before you even knew what you had, you patented it, and "
                    + "packaged it, and slapped it on a plastic lunchbox, and now you're selling it, you wanna "
                    + "sell it.",
            "latin1",
            "Je vais vous dire le problème avec le pouvoir scientifique que vous utilisez ici, il n'a " +
                    "pas fallu de discipline pour l'atteindre. Vous avez lu ce que les autres avaient fait " +
                    "et vous avez pris la prochaine étape. Vous n'avez pas gagné les connaissances pour " +
                    "vous-mêmes, donc vous n'en assumez aucune responsabilité. Tu étais sur les épaules de " +
                    "génies pour accomplir quelque chose aussi vite que tu pouvais, et avant même " +
                    "de savoir ce que tu avais, tu l'as breveté, et l'as emballé, et tu l'as mis sur une boîte " +
                    "à lunch en plastique, et maintenant tu le vends, tu veux le vendre",
        )
    }

    @Param("20", "2000", "200000")
    private var length = 0

    @Param("ascii", "latin1")
    private lateinit var encoding: String

    private lateinit var jayoBuffer: Buffer
    private lateinit var jayoDecode: ByteString
    private lateinit var okioBuffer: okio.Buffer
    private lateinit var okioDecode: okio.ByteString
    private lateinit var encode: String

    @Setup
    fun setup() {
        val part = strings[encoding]

        // Make all the strings the same length for comparison
        val builder = StringBuilder(length + 1000)
        while (builder.length < length) {
            builder.append(part)
        }
        builder.setLength(length)
        encode = builder.toString()

        // Prepare a string and ByteString for encoding and decoding with Okio and Jayo
        jayoBuffer = Buffer()
        val tempJayoIo = Buffer()
        tempJayoIo.write(encode, Charsets.ISO_8859_1)
        jayoDecode = tempJayoIo.snapshot()

        okioBuffer = okio.Buffer()
        val tempOkio = okio.Buffer()
        tempOkio.writeString(encode, Charsets.ISO_8859_1)
        okioDecode = tempOkio.snapshot()
    }

    @Benchmark
    fun writeLatin1Okio() {
        okioBuffer.writeString(encode, Charsets.ISO_8859_1)
        okioBuffer.clear()
    }

    @Benchmark
    fun readLatin1Okio(): String {
        okioBuffer.writeString(encode, Charsets.ISO_8859_1)
        return okioBuffer.readString(Charsets.ISO_8859_1)
    }

    @Benchmark
    fun writeLatin1Jayo() {
        jayoBuffer.write(encode, Charsets.ISO_8859_1)
        jayoBuffer.clear()
    }

    @Benchmark
    fun readLatin1Jayo(): String {
        jayoBuffer.write(encode, Charsets.ISO_8859_1)
        return jayoBuffer.readString(Charsets.ISO_8859_1)
    }
}