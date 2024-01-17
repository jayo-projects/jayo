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
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import jayo.sink
import jayo.source
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Socket
import java.nio.file.StandardOpenOption

class JayoTest {

    @TempDir
    private lateinit var tempDir: File

    @Test
    fun fileRawSink() {
        val file = tempDir.resolve("fileSink.txt")
        file.createNewFile()
        val sink = file.sink()
        sink.write(RealBuffer().writeUtf8("a"), 1L)
        assertThat(file.readText()).isEqualTo("a")
    }

    @Test
    fun fileRawSource() {
        val file = tempDir.resolve("fileSource.txt")
        file.writeText("a")
        val source = file.source()
        val buffer = RealBuffer()
        source.readAtMostTo(buffer, 1L)
        assertThat(buffer.readUtf8()).isEqualTo("a")
    }

    @Test
    fun pathRawSink() {
        val file = tempDir.resolve("pathSink.txt")
        file.createNewFile()
        val sink = file.toPath().sink()
        sink.write(RealBuffer().writeUtf8("a"), 1L)
        assertThat(file.readText()).isEqualTo("a")
    }

    @Test
    fun pathRawSinkWithAppend() {
        val file = tempDir.resolve("pathSinkWithOptions.txt")
        file.writeText("a")
        val sink = file.toPath().sink(StandardOpenOption.APPEND)
        sink.write(RealBuffer().writeUtf8("b"), 1L)
        assertThat(file.readText()).isEqualTo("ab")
    }

    @Test
    fun pathRawSinkWithIgnoredOptions() {
        val file = tempDir.resolve("pathSinkWithIgnoredOptions.txt")
        file.createNewFile()
        val sink = file.toPath().sink(StandardOpenOption.READ)
        sink.write(RealBuffer().writeUtf8("a"), 1L)
        assertThat(file.readText()).isEqualTo("a")
    }

    @Test
    fun pathRawSource() {
        val file = tempDir.resolve("pathSource.txt")
        file.writeText("a")
        val source = file.toPath().source()
        val buffer = RealBuffer()
        source.readAtMostTo(buffer, 1L)
        assertThat(buffer.readUtf8()).isEqualTo("a")
    }

    @Test
    fun pathRawSourceWithIgnoredOptions() {
        val file = tempDir.resolve("pathSourceWithIgnoredOptions.txt")
        file.writeText("a")
        val source = file.toPath().source(StandardOpenOption.DSYNC)
        val buffer = RealBuffer()
        source.readAtMostTo(buffer, 1L)
        assertThat(buffer.readUtf8()).isEqualTo("a")
    }

    @Test
    fun outputStreamRawSink() {
        val baos = ByteArrayOutputStream()
        val sink = baos.sink()
        sink.write(RealBuffer().writeUtf8("a"), 1L)
        assertThat(baos.toByteArray()).isEqualTo(byteArrayOf(0x61))
    }

    @Test
    fun inputStreamRawSource() {
        val bais = ByteArrayInputStream(byteArrayOf(0x61))
        val source = bais.source()
        val buffer = RealBuffer()
        source.readAtMostTo(buffer, 1)
        assertThat(buffer.readUtf8()).isEqualTo("a")
    }

    @Test
    fun socketRawSink() {
        val baos = ByteArrayOutputStream()
        val socket = object : Socket() {
            override fun getOutputStream() = baos
        }
        val sink = socket.sink()
        sink.write(RealBuffer().writeUtf8("a"), 1L)
        assertThat(baos.toByteArray()).isEqualTo(byteArrayOf(0x61))
    }

    @Test
    fun socketRawSource() {
        val bais = ByteArrayInputStream(byteArrayOf(0x61))
        val socket = object : Socket() {
            override fun getInputStream() = bais
        }
        val source = socket.source()
        val buffer = RealBuffer()
        source.readAtMostTo(buffer, 1L)
        assertThat(buffer.readUtf8()).isEqualTo("a")
    }
}