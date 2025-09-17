/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 * 
 * Forked from Okio (https://github.com/square/okio), original copyright is below
 * 
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.internal

import jayo.*
import jayo.network.NetworkServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

class JayoTest {

    @TempDir
    private lateinit var tempDir: File

    @Test
    fun fileRawWriter() {
        val file = tempDir.resolve("fileWriter.txt")
        file.createNewFile()
        val writer = file.writer()
        writer.writeFrom(RealBuffer().write("a"), 1L)
        writer.flush()
        assertThat(file.readText()).isEqualTo("a")
    }

    @Test
    fun fileRawReader() {
        val file = tempDir.resolve("fileReader.txt")
        file.writeText("abc")
        val reader = file.reader()
        val buffer = RealBuffer()
        reader.readAtMostTo(buffer, 1L)
        assertThat(buffer.readString()).isEqualTo("a")
        assertThrows<IllegalArgumentException> { reader.readAtMostTo(buffer, -42) }
    }

    @Test
    fun pathRawWriter() {
        val file = tempDir.resolve("pathWriter.txt")
        file.createNewFile()
        val writer = file.toPath().writer()
        writer.writeFrom(RealBuffer().write("a"), 1L)
        writer.flush()
        assertThat(file.readText()).isEqualTo("a")
    }

    @Test
    fun pathRawWriterWithAppend() {
        val file = tempDir.resolve("pathWriterWithOptions.txt")
        file.writeText("a")
        val writer = file.toPath().writer(StandardOpenOption.APPEND)
        writer.writeFrom(RealBuffer().write("b"), 1L)
        assertThat(file.readText()).isEqualTo("ab")
    }

    @Test
    fun pathRawWriterWithIgnoredOptions() {
        val file = tempDir.resolve("pathWriterWithIgnoredOptions.txt")
        file.createNewFile()
        val writer = file.toPath().writer(StandardOpenOption.READ)
        writer.writeFrom(RealBuffer().write("a"), 1L)
        assertThat(file.readText()).isEqualTo("a")
    }

    @Test
    fun pathRawReader() {
        val file = tempDir.resolve("pathReader.txt")
        file.writeText("abc")
        val reader = file.toPath().reader(StandardOpenOption.DELETE_ON_CLOSE)
        val buffer = RealBuffer()
        reader.readAtMostTo(buffer, 1L)
        assertThat(buffer.readString()).isEqualTo("a")
        assertThrows<IllegalArgumentException> { reader.readAtMostTo(buffer, -42) }
    }

    @Test
    fun pathRawReaderWithIgnoredOptions() {
        val file = tempDir.resolve("pathReaderWithIgnoredOptions.txt")
        file.writeText("a")
        val reader = file.toPath().reader(StandardOpenOption.DSYNC)
        val buffer = RealBuffer()
        reader.readAtMostTo(buffer, 1L)
        assertThat(buffer.readString()).isEqualTo("a")
    }

    @Test
    fun outputStreamRawWriter() {
        val baos = ByteArrayOutputStream()
        val writer = baos.writer()
        writer.writeFrom(RealBuffer().write("a"), 1L)
        assertThat(baos.toByteArray()).isEqualTo(byteArrayOf(0x61))
    }

    @Test
    fun inputStreamRawReader() {
        val bais = ByteArrayInputStream(byteArrayOf(0x61))
        val reader = bais.reader()
        val buffer = RealBuffer()
        reader.readAtMostTo(buffer, 1)
        assertThat(buffer.readString()).isEqualTo("a")
        assertThrows<IllegalArgumentException> { reader.readAtMostTo(buffer, -42) }
    }

    @Test
    fun socketWriter() {
        val baos = ByteArrayOutputStream()
        val socket = object : Socket() {
            override fun getInputStream() = object : InputStream() {
                override fun read() = -1
            }

            override fun getOutputStream() = baos
            override fun isConnected() = true
        }
        val writer = socket.asJayoSocket().writer
        writer.writeFrom(RealBuffer().write("a"), 1L)
        writer.flush()
        assertThat(baos.toByteArray()).isEqualTo(byteArrayOf(0x61))
    }

    @Test
    fun socketReader() {
        val bais = ByteArrayInputStream(byteArrayOf(0x61))
        val socket = object : Socket() {
            override fun getInputStream() = bais
            override fun getOutputStream() = object : OutputStream() {
                override fun write(b: Int) = Unit
            }

            override fun isConnected() = true
        }
        val reader = socket.asJayoSocket().reader
        val buffer = RealBuffer()
        reader.readAtMostTo(buffer, 1L)
        assertThat(buffer.readString()).isEqualTo("a")
    }

    @Test
    fun writableByteChannelRawWriter() {
        val file = tempDir.resolve("writableByteChannelRawWriter.txt")
        file.createNewFile()
        val writer = Files.newByteChannel(file.toPath(), StandardOpenOption.WRITE).writer()
        writer.writeFrom(RealBuffer().write("a"), 1L)
        writer.flush()
        assertThat(file.readText()).isEqualTo("a")
    }

    @Test
    fun gatheringByteChannelRawWriterOneSegment() {
        val file = tempDir.resolve("gatheringByteChannelRawWriter1.txt")
        file.createNewFile()
        val writer = FileChannel.open(file.toPath(), StandardOpenOption.WRITE).writer()
        writer.writeFrom(RealBuffer().write("a"), 1L)
        writer.flush()
        assertThat(file.readText()).isEqualTo("a")
    }

    @Test
    fun gatheringByteChannelRawWriterTwoSegment() {
        val file = tempDir.resolve("gatheringByteChannelRawWriter2.txt")
        file.createNewFile()
        val writer = FileChannel.open(file.toPath(), StandardOpenOption.WRITE).writer()
        writer.writeFrom(RealBuffer().write("a".repeat(Segment.SIZE + 2)), Segment.SIZE + 1L)
        writer.flush()
        assertThat(file.readText()).isEqualTo("a".repeat(Segment.SIZE + 1))
    }

    @Test
    fun readableByteChannelRawReader() {
        val file = tempDir.resolve("readableByteChannelRawReader.txt")
        file.writeText("abc")
        val reader = Files.newByteChannel(file.toPath()).reader()
        val buffer = RealBuffer()
        reader.readAtMostTo(buffer, 1L)
        assertThat(buffer.readString()).isEqualTo("a")
        assertThrows<IllegalArgumentException> { reader.readAtMostTo(buffer, -42) }
    }

    @Test
    fun socketChannelWriter() {
        // Let the system pick up a local free port
        val buffer = Buffer()
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */)).use { listener ->
            val serverThread = thread(start = true) {
                val serverSocket = listener.accept()
                serverSocket.reader.readAtMostTo(buffer, 1L)
            }

            SocketChannel.open(listener.localAddress).use { socketChannel ->
                socketChannel.writer().buffered().use { clientWriter ->
                    clientWriter.write("a")
                }
            }
            serverThread.join()
        }
        assertThat(buffer.readString()).isEqualTo("a")
    }

    @Test
    fun socketChannelReader() {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */)).use { listener ->
            val serverThread = thread(start = true) {
                val serverSocket = listener.accept()
                serverSocket.writer.buffered().use { serverWriter ->
                    serverWriter.write("a")
                }
            }

            SocketChannel.open(listener.localAddress).use { socketChannel ->
                socketChannel.reader().buffered().use { clientReader ->
                    assertThat(clientReader.readString()).isEqualTo("a")
                }
            }
            serverThread.join()
        }
    }
}