package jayo.benchmarks

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jayo.buffered
import jayo.endpoints.endpoint
import jayo.kotlinx.serialization.decodeFromReader
import jayo.kotlinx.serialization.encodeToWriter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.buffer
import okio.sink
import okio.source
import org.openjdk.jmh.annotations.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalSerializationApi::class)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@Timeout(time = 20)
@Warmup(iterations = 7, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.Throughput)
@Fork(value = 1)
open class TcpAndJsonSerializationBenchmark {
    private val senderServer = ServerSocket(0)
    private val receiverServer = ServerSocket(0)

    companion object {
        @JvmStatic
        private val objectMapper = jacksonObjectMapper()

        @JvmStatic
        private val kotlinxSerializationMapper = Json

        val defaultPixelEvent
            @JvmStatic get() = JsonSerializationBenchmark.DefaultPixelEvent(
                version = 1,
                dateTime2 = "01/01/2023",
                serverName = "some-endpoint-qwer",
                domain = "some.domain.com",
                method = "POST",
                clientIp = "127.0.0.1",
                queryString = "anxa=CASCative&anxv=13.901.16.34566&anxe=FoolbarActive&anxt=E7AFBF15-1761-4343-92C1-78167ED19B1C&anxtv=13.901.16.34566&anxp=%5ECQ6%5Expt292%5ES33656%5Eus&anxsi&anxd=2019-10-08T17%3A03%3A57.246Z&f=00400000&anxr=1571945992297&coid=66abafd0d49f42e58dc7536109395306&userSegment&cwsid=opgkcnbminncdgghighmimmphiooeohh",
                userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.14; rv:70.0) Gecko/20100101 Firefox/70.0",
                contentType = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                browserLanguage = "en-US,en;q=0.5",
                postData = "-",
                cookies = "_ga=GA1.2.971852807.1546968515"
            )
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
                        objectMapper.writeValue(output, defaultPixelEvent)
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
                        check(input.readAllBytes().size == 737)
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
            val socketEndpoint = socket.endpoint()
            socketEndpoint.reader.buffered().use { reader ->
                val decoded = kotlinxSerializationMapper.decodeFromReader(
                    JsonSerializationBenchmark.DefaultPixelEvent.serializer(),
                    reader
                )
                check(decoded == defaultPixelEvent)
            }
        }
    }

    @Benchmark
    fun readerOkio() {
        Socket().use { socket ->
            socket.connect(senderServer.localSocketAddress)
            socket.source().buffer().use { source ->
                val decoded = kotlinxSerializationMapper.decodeFromBufferedSource(
                    JsonSerializationBenchmark.DefaultPixelEvent.serializer(),
                    source
                )
                check(decoded == defaultPixelEvent)
            }
        }
    }

    @Benchmark
    fun senderJayo() {
        val socket = Socket()
        socket.connect(receiverServer.localSocketAddress)
        val socketEndpoint = socket.endpoint()
        socketEndpoint.writer.buffered().use { writer ->
            kotlinxSerializationMapper.encodeToWriter(
                JsonSerializationBenchmark.DefaultPixelEvent.serializer(),
                defaultPixelEvent,
                writer
            )
            writer.flush()
        }
    }

    @Benchmark
    fun senderJayoJackson() {
        val socket = Socket()
        socket.connect(receiverServer.localSocketAddress)
        val socketEndpoint = socket.endpoint()
        socketEndpoint.writer.buffered().use { writer ->
            val output = writer.asOutputStream()
            objectMapper.writeValue(output, defaultPixelEvent)
            output.flush()
        }
    }

    @Benchmark
    fun senderOkio() {
        val socket = Socket()
        socket.connect(receiverServer.localSocketAddress)
        socket.sink().buffer().use { sink ->
            kotlinxSerializationMapper.encodeToBufferedSink(
                JsonSerializationBenchmark.DefaultPixelEvent.serializer(),
                defaultPixelEvent,
                sink
            )
            sink.flush()
        }
    }
}