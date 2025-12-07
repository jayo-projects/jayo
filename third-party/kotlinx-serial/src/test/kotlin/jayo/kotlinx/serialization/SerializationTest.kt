/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.kotlinx.serialization

import jayo.Buffer
import jayo.buffered
import jayo.discardingWriter
import jayo.asJayoSocket
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.OutputStream
import java.net.Socket
import java.util.stream.IntStream

@OptIn(ExperimentalSerializationApi::class)
class SerializationTest {

    companion object {
        @JvmStatic
        private val kotlinxSerializationMapper = Json

        @JvmStatic
        private val date = System.currentTimeMillis().toString()

        @JvmStatic
        private val devNullWriterJayo = discardingWriter().buffered()

        private val defaultPixelEvent = DefaultPixelEvent(
            version = 1,
            dateTime2 = date,
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
    }

    @Test
    fun socketEncodeDecode() {
        val buffer = Buffer()
        kotlinxSerializationMapper.encodeToWriter(
            DefaultPixelEvent.serializer(),
            defaultPixelEvent,
            buffer
        )
        val socket = object : Socket() {
            override fun getInputStream() = buffer.asInputStream()
            override fun getOutputStream() = object : OutputStream() {
                override fun write(b: Int) = Unit
            }
            override fun isConnected() = true
        }
        val reader = socket.asJayoSocket().reader.buffered()
        val decoded = kotlinxSerializationMapper.decodeFromReader(
            DefaultPixelEvent.serializer(),
            reader
        )
        assertThat(decoded).isEqualTo(defaultPixelEvent)
    }

    @Test
    fun kotlinxSmallToJayo() {
        IntStream.range(0, 1000).forEach {
            kotlinxSerializationMapper.encodeToWriter(SmallDataClass.serializer(), smallData, devNullWriterJayo)
        }
    }
}