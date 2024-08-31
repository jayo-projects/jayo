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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import jayo.writer
import jayo.reader
import jayo.endpoints.endpoint
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Socket
import java.nio.file.StandardOpenOption

class JayoTest {

    @TempDir
    private lateinit var tempDir: File

    @Test
    fun fileRawWriter() {
        val file = tempDir.resolve("fileWriter.txt")
        file.createNewFile()
        val writer = file.writer()
        writer.write(RealBuffer().writeUtf8("a"), 1L)
        writer.flush()
        assertThat(file.readText()).isEqualTo("a")
    }

    @Test
    fun fileRawReader() {
        val file = tempDir.resolve("fileReader.txt")
        file.writeText("a")
        val reader = file.reader()
        val buffer = RealBuffer()
        reader.readAtMostTo(buffer, 1L)
        assertThat(buffer.readUtf8String()).isEqualTo("a")
    }

    @Test
    fun pathRawWriter() {
        val file = tempDir.resolve("pathWriter.txt")
        file.createNewFile()
        val writer = file.toPath().writer()
        writer.write(RealBuffer().writeUtf8("a"), 1L)
        writer.flush()
        assertThat(file.readText()).isEqualTo("a")
    }

    @Test
    fun pathRawWriterWithAppend() {
        val file = tempDir.resolve("pathWriterWithOptions.txt")
        file.writeText("a")
        val writer = file.toPath().writer(StandardOpenOption.APPEND)
        writer.write(RealBuffer().writeUtf8("b"), 1L)
        assertThat(file.readText()).isEqualTo("ab")
    }

    @Test
    fun pathRawWriterWithIgnoredOptions() {
        val file = tempDir.resolve("pathWriterWithIgnoredOptions.txt")
        file.createNewFile()
        val writer = file.toPath().writer(StandardOpenOption.READ)
        writer.write(RealBuffer().writeUtf8("a"), 1L)
        assertThat(file.readText()).isEqualTo("a")
    }

    @Test
    fun pathRawReader() {
        val file = tempDir.resolve("pathReader.txt")
        file.writeText("a")
        val reader = file.toPath().reader(StandardOpenOption.DELETE_ON_CLOSE)
        val buffer = RealBuffer()
        reader.readAtMostTo(buffer, 1L)
        assertThat(buffer.readUtf8String()).isEqualTo("a")
    }

    @Test
    fun pathRawReaderWithIgnoredOptions() {
        val file = tempDir.resolve("pathReaderWithIgnoredOptions.txt")
        file.writeText("a")
        val reader = file.toPath().reader(StandardOpenOption.DSYNC)
        val buffer = RealBuffer()
        reader.readAtMostTo(buffer, 1L)
        assertThat(buffer.readUtf8String()).isEqualTo("a")
    }

    @Test
    fun outputStreamRawWriter() {
        val baos = ByteArrayOutputStream()
        val writer = baos.writer()
        writer.write(RealBuffer().writeUtf8("a"), 1L)
        assertThat(baos.toByteArray()).isEqualTo(byteArrayOf(0x61))
    }

    @Test
    fun inputStreamRawReader() {
        val bais = ByteArrayInputStream(byteArrayOf(0x61))
        val reader = bais.reader()
        val buffer = RealBuffer()
        reader.readAtMostTo(buffer, 1)
        assertThat(buffer.readUtf8String()).isEqualTo("a")

        assertThrows<IllegalArgumentException> { reader.readAtMostTo(buffer, -42)}
    }

    @Test
    fun socketRawWriter() {
        val baos = ByteArrayOutputStream()
        val socket = object : Socket() {
            override fun getOutputStream() = baos
            override fun isConnected() = true
        }
        val socketEndpoint = socket.endpoint()
        val writer = socketEndpoint.writer
        writer.write(RealBuffer().writeUtf8("a"), 1L)
        assertThat(baos.toByteArray()).isEqualTo(byteArrayOf(0x61))
    }

    @Test
    fun socketRawReader() {
        val bais = ByteArrayInputStream(byteArrayOf(0x61))
        val socket = object : Socket() {
            override fun getInputStream() = bais
            override fun isConnected() = true
        }
        val socketEndpoint = socket.endpoint()
        val reader = socketEndpoint.reader
        val buffer = RealBuffer()
        reader.readAtMostTo(buffer, 1L)
        assertThat(buffer.readUtf8String()).isEqualTo("a")
    }
}