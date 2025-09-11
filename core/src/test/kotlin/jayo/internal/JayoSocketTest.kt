/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal

import jayo.*
import org.assertj.core.api.AbstractThrowableAssert
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds

class JayoSocketTest {
    @Test
    fun `negative read throws IllegalArgumentException`() {
        val buffer = Buffer()
        val socket = object : Socket() {
            override fun isConnected() = true
            override fun getOutputStream() = buffer.asOutputStream()
            override fun getInputStream() = buffer.asInputStream()
        }

        assertThatThrownBy {
            socket.asJayoSocket().reader.readAtMostTo(Buffer(), -1)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("byteCount < 0: -1")
    }

    @Test
    fun `read while cancelled and interrupted platform thread`() {
        val buffer = Buffer()
        val socket = object : Socket() {
            override fun isConnected() = true
            override fun getOutputStream() = buffer.asOutputStream()
            override fun getInputStream() = buffer.asInputStream()
        }

        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        cancelScope {
            thread(start = true) {
                cancel()
                Thread.currentThread().interrupt()
                val reader = socket.asJayoSocket().reader
                throwableAssert = assertThatThrownBy { reader.readByte() }
            }.join()
        }
        throwableAssert!!.isInstanceOf(JayoInterruptedIOException::class.java)
            .hasMessage("current thread is interrupted")
    }

    @Test
    fun `read while cancelled and interrupted virtual thread`() {
        val buffer = Buffer()
        val socket = object : Socket() {
            override fun isConnected() = true
            override fun getOutputStream() = buffer.asOutputStream()
            override fun getInputStream() = buffer.asInputStream()
        }

        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        cancelScope {
            thread(start = true) {
                cancel()
                Thread.currentThread().interrupt()
                val reader = socket.asJayoSocket().reader
                throwableAssert = assertThatThrownBy { reader.readByte() }
            }.join()
        }
        throwableAssert!!.isInstanceOf(JayoInterruptedIOException::class.java)
            .hasMessage("current thread is interrupted")
    }

    @Test
    fun `write while cancelled and interrupted platform thread`() {
        val buffer = Buffer()
        val socket = object : Socket() {
            override fun isConnected() = true
            override fun getOutputStream() = buffer.asOutputStream()
            override fun getInputStream() = buffer.asInputStream()
        }

        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        cancelScope {
            thread(start = true) {
                cancel()
                Thread.currentThread().interrupt()
                val writer = socket.asJayoSocket().writer
                throwableAssert = assertThatThrownBy {
                    writer.writeByte(0)
                    writer.flush()
                }
            }.join()
        }
        throwableAssert!!.isInstanceOf(JayoInterruptedIOException::class.java)
            .hasMessage("current thread is interrupted")
    }

    @Test
    fun `write while cancelled and interrupted virtual thread`() {
        val buffer = Buffer()
        val socket = object : Socket() {
            override fun isConnected() = true
            override fun getOutputStream() = buffer.asOutputStream()
            override fun getInputStream() = buffer.asInputStream()
        }

        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        cancelScope {
            thread(start = true) {
                cancel()
                Thread.currentThread().interrupt()
                val writer = socket.asJayoSocket().writer
                throwableAssert = assertThatThrownBy {
                    writer.writeByte(0)
                    writer.flush()
                }
            }.join()
        }
        throwableAssert!!.isInstanceOf(JayoInterruptedIOException::class.java)
            .hasMessage("current thread is interrupted")
    }

    @Test
    @Tag("no-ci")
    fun `close with timeout`() {
        val buffer = Buffer()
        val socket = object : Socket() {
            override fun isConnected() = true
            override fun getOutputStream() = buffer.asOutputStream()
            override fun getInputStream() = buffer.asInputStream()

            override fun shutdownOutput() {
                Thread.sleep(3)
            }
        }
        cancelScope(1.milliseconds) {
            assertThatThrownBy {
                socket.asJayoSocket().writer.close()
            }.isInstanceOf(JayoTimeoutException::class.java)
                .hasMessage("timeout")
        }
    }

    @Test
    fun `close no timeout`() {
        val buffer = Buffer()
        val socket = object : Socket() {
            override fun isConnected() = true
            override fun getOutputStream() = buffer.asOutputStream()
            override fun getInputStream() = buffer.asInputStream()

            override fun shutdownOutput() {}
        }
        socket.asJayoSocket().writer.close()
    }
}
