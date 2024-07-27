package jayo.benchmarks

import jayo.Buffer
import jayo.writer
import jayo.reader
import okio.sink
import okio.source
import org.openjdk.jmh.annotations.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@Timeout(time = 20)
@Warmup(iterations = 7, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.Throughput)
@Fork(value = 1)
@Threads(3)
open class TcpBenchmark {
    private val senderServer = ServerSocket(0)
    private val receiverServer = ServerSocket(0)

    companion object {
        const val BYTE_COUNT = 42 * 1024
    }

    @Setup
    fun setup() {
        // start sender server
        Thread.ofPlatform().start {
            try {
                while (true) {
                    val sock = senderServer.accept()
                    val output = sock.getOutputStream()
                    Thread.ofVirtual().start {
                        output.write(ByteArray(BYTE_COUNT) { 0x61 })
                    }
                }
            } catch (e: Exception) {
                senderServer.close()
                e.printStackTrace()
            }
        }

        // start receiver server
        Thread.ofPlatform().start {
            try {
                while (true) {
                    val sock = receiverServer.accept()
                    val input = sock.getInputStream()
                    Thread.ofVirtual().start {
                        check(input.readAllBytes().contentEquals(ByteArray(BYTE_COUNT) { 0x62 }))
                    }
                }
            } catch (e: Exception) {
                receiverServer.close()
                e.printStackTrace()
            }
        }
    }

    @Benchmark
    fun readerJayo() {
        Socket().use { socket ->
            socket.connect(senderServer.localSocketAddress)
            socket.reader().use { reader ->
                val buffer = Buffer()
                    .writeUtf8("b")
                var toRead = BYTE_COUNT
                while (toRead > 0) {
                    val read = reader.readAtMostTo(buffer, BYTE_COUNT.toLong()).toInt()
                    toRead -= read
                }
                buffer.clear()
            }
        }
    }

    @Benchmark
    fun sourceOkio() {
        Socket().use { socket ->
            socket.connect(senderServer.localSocketAddress)
            socket.source().use { source ->
                val buffer = okio.Buffer()
                    .writeUtf8("b")
                var toRead = BYTE_COUNT
                while (toRead > 0) {
                    val read = source.read(buffer, BYTE_COUNT.toLong()).toInt()
                    toRead -= read
                }
                buffer.clear()
            }
        }
    }

    @Benchmark
    fun senderJayo() {
        val socket = Socket()
        socket.connect(receiverServer.localSocketAddress)
        socket.writer().use { writer ->
            writer.write(Buffer().writeUtf8("b".repeat(BYTE_COUNT)), BYTE_COUNT.toLong())
        }
    }

    @Benchmark
    fun senderOkio() {
        val socket = Socket()
        socket.connect(receiverServer.localSocketAddress)
        socket.sink().use { sink ->
            sink.write(okio.Buffer().writeUtf8("b".repeat(BYTE_COUNT)), BYTE_COUNT.toLong())
        }
    }
}