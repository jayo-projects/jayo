package jayo.benchmarks

import jayo.Reader
import jayo.buffered
import jayo.endpoints.endpoint
import okio.BufferedSource
import okio.buffer
import okio.source
import org.openjdk.jmh.annotations.*
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.SocketChannel
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.SECONDS)
@Timeout(time = 20)
@Warmup(iterations = 7, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.Throughput)
@Fork(value = 1)
open class SocketReaderBenchmark {
    @Param("jayo-io", "jayo-nio", "okio")
    private lateinit var type: String

    private lateinit var serverSocket: ServerSocket
    private lateinit var clientSocket: Socket
    private lateinit var clientSocketChannel: SocketChannel
    private lateinit var clientOutputStream: OutputStream

    private lateinit var jayoReader: Reader
    private lateinit var okioSource: BufferedSource

    companion object {
        private const val BYTE_COUNT = 42 * 1024
        private val array = ByteArray(BYTE_COUNT) { 0x61 }
    }

    @Setup(Level.Trial)
    fun setup() {
        serverSocket = ServerSocket(0)
        // start sender server
        Thread.ofPlatform().start {
            try {
                while (true) {
                    if (Thread.interrupted()) {
                        break
                    }
                    val sock = serverSocket.accept()
                    val input = sock.getInputStream()
                    val output = sock.getOutputStream()
                    Thread.ofVirtual().start {
                        while (true) {
                            val read = input.read()
                            if (Thread.interrupted() || read == -1) {
                                break
                            }
                            if (read == 42) {
                                output.write(array)
                                output.flush()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                serverSocket.close()
            }
        }
        clientSocket = Socket("localhost", serverSocket.localPort)
        clientSocketChannel = SocketChannel.open(serverSocket.localSocketAddress)
        val clientSocketEndpoint = clientSocket.endpoint()
        val clientSocketChannelEndpoint = clientSocketChannel.endpoint()
            when (type) {
            "jayo-io" -> {
                clientOutputStream = clientSocket.getOutputStream()
                jayoReader = clientSocketEndpoint.reader.buffered()
            }

            "jayo-nio" -> {
                clientOutputStream = clientSocketChannelEndpoint.writer.buffered().asOutputStream()
                jayoReader = clientSocketChannelEndpoint.reader.buffered()
            }

            "okio" -> {
                clientOutputStream = clientSocket.getOutputStream()
                okioSource = clientSocket.source().buffer()
            }

            else -> throw IllegalStateException("Unknown type: $type")
        }
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        clientOutputStream.close()
        clientSocket.close()
        clientSocketChannel.close()
    }

    @Benchmark
    fun readerJayo() {
        clientOutputStream.write(42)
        clientOutputStream.flush()
        check(jayoReader.readByteArray(BYTE_COUNT.toLong()).contentEquals(array))
    }

    @Benchmark
    fun readerOkio() {
        clientOutputStream.write(42)
        clientOutputStream.flush()
        check(okioSource.readByteArray(BYTE_COUNT.toLong()).contentEquals(array))
    }
}