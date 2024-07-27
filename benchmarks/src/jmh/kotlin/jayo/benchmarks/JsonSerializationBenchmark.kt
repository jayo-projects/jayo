package jayo.benchmarks

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jayo.Buffer
import jayo.buffered
import jayo.discardingWriter
import jayo.kotlinx.serialization.decodeFromReader
import jayo.kotlinx.serialization.encodeToWriter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.blackholeSink
import okio.buffer
import org.openjdk.jmh.annotations.*
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalSerializationApi::class)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@Timeout(time = 20)
@Warmup(iterations = 7, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.Throughput)
@Fork(1)
open class JsonSerializationBenchmark {

    @Serializable
    data class DefaultPixelEvent(
        val version: Int,
        val dateTime2: String,
        val serverName: String,
        val domain: String,
        val method: String,
        val clientIp: String,
        val queryString: String,
        val userAgent: String,
        val contentType: String,
        val browserLanguage: String,
        val postData: String,
        val cookies: String
    )

    @Serializable
    private class SmallDataClass(val id: Int, val name: String)

    companion object {

        private val objectMapper: ObjectMapper = jacksonObjectMapper()
        private val kotlinxSerializationMapper = Json

        private val defaultPixelEvent = DefaultPixelEvent(
            version = 1,
            dateTime2 = System.currentTimeMillis().toString(),
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

        private val smallData = SmallDataClass(42, "Vincent")

        private val devNullWriterJayo = discardingWriter().buffered()
        private val devNullSinkOkio = blackholeSink().buffer()
        private val devNullStream = object : OutputStream() {
            override fun write(b: Int) {}
            override fun write(b: ByteArray) {}
            override fun write(b: ByteArray, off: Int, len: Int) {}
        }

        private val stringData = Json.encodeToString(DefaultPixelEvent.serializer(), defaultPixelEvent)
        private val utf8BytesData = stringData.toByteArray()
    }

    @Benchmark
    fun jacksonToStream() = objectMapper.writeValue(devNullStream, defaultPixelEvent)

    @Benchmark
    fun jacksonSmallToStream() = objectMapper.writeValue(devNullStream, smallData)

    @Benchmark
    fun jacksonFromStream() = objectMapper.readValue<DefaultPixelEvent>(ByteArrayInputStream(utf8BytesData))

    @Benchmark
    fun kotlinxToStream() =
        kotlinxSerializationMapper.encodeToStream(DefaultPixelEvent.serializer(), defaultPixelEvent, devNullStream)

    @Benchmark
    fun kotlinxSmallToStream() =
        kotlinxSerializationMapper.encodeToStream(SmallDataClass.serializer(), smallData, devNullStream)

    @Benchmark
    fun kotlinxFromStream() =
        kotlinxSerializationMapper.decodeFromStream(DefaultPixelEvent.serializer(), ByteArrayInputStream(utf8BytesData))

    @Benchmark
    fun kotlinxToOkio() = kotlinxSerializationMapper.encodeToBufferedSink(
        DefaultPixelEvent.serializer(),
        defaultPixelEvent,
        devNullSinkOkio
    )

    @Benchmark
    fun kotlinxSmallToOkio() =
        kotlinxSerializationMapper.encodeToBufferedSink(SmallDataClass.serializer(), smallData, devNullSinkOkio)

    @Benchmark
    fun kotlinxFromOkio() =
        kotlinxSerializationMapper.decodeFromBufferedSource(
            DefaultPixelEvent.serializer(),
            okio.Buffer().write(utf8BytesData)
        )

    @Benchmark
    fun kotlinxToJayo() =
        kotlinxSerializationMapper.encodeToWriter(DefaultPixelEvent.serializer(), defaultPixelEvent, devNullWriterJayo)

    @Benchmark
    fun kotlinxSmallToJayo() =
        kotlinxSerializationMapper.encodeToWriter(SmallDataClass.serializer(), smallData, devNullWriterJayo)

    @Benchmark
    fun kotlinxFromJayo() =
        kotlinxSerializationMapper.decodeFromReader(
            DefaultPixelEvent.serializer(),
            Buffer().write(utf8BytesData)
        )
}