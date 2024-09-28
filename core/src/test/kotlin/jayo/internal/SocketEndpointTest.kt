/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal

import jayo.Buffer
import jayo.buffered
import jayo.cancelScope
import jayo.endpoints.JayoClosedEndpointException
import jayo.endpoints.endpoint
import jayo.exceptions.JayoInterruptedIOException
import jayo.exceptions.JayoTimeoutException
import org.assertj.core.api.AbstractThrowableAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds

class SocketEndpointTest {
    @Test
    fun `socket is not connected throws IllegalArgumentException`() {
        val socket = object : Socket() {
            override fun isConnected() = false
        }
        assertThatThrownBy {
            socket.endpoint()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Socket is not connected")
    }

    @Test
    fun `socket is closed throws IllegalArgumentException`() {
        val socket = object : Socket() {
            override fun isConnected() = true
            override fun isClosed() = true
        }
        assertThatThrownBy {
            socket.endpoint()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Socket is closed")
    }

    @Test
    fun `negative read throws IllegalArgumentException`() {
        val buffer = Buffer()
        val socketEndpoint = object : Socket() {
            override fun isConnected() = true
            override fun getInputStream() = buffer.asInputStream()
        }.endpoint()

        assertThatThrownBy {
            socketEndpoint.reader.readAtMostTo(Buffer(), -1)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("byteCount < 0 : -1")
    }

    @Test
    fun `underlying is the original socket`() {
        val socket = object : Socket() {
            override fun isConnected() = true
        }

        assertThat(socket.endpoint().underlying).isSameAs(socket)
    }

    @Test
    fun `several invocations of getReader() always return the same instance`() {
        val buffer = Buffer()
        val socketEndpoint = object : Socket() {
            override fun isConnected() = true
            override fun getInputStream() = buffer.asInputStream()
        }.endpoint()

        val reader1 = socketEndpoint.reader
        val reader2 = socketEndpoint.reader
        assertThat(reader1).isSameAs(reader2)
    }

    @Test
    fun `several invocations of getWriter() always return the same instance`() {
        val buffer = Buffer()
        val socketEndpoint = object : Socket() {
            override fun isConnected() = true
            override fun getOutputStream() = buffer.asOutputStream()
        }.endpoint()

        val writer1 = socketEndpoint.writer
        val writer2 = socketEndpoint.writer
        assertThat(writer1).isSameAs(writer2)
    }

    @Test
    fun `read while cancelled and interrupted platform thread`() {
        val buffer = Buffer()
        val socket = object : Socket() {
            override fun isConnected() = true
            override fun getInputStream() = buffer.asInputStream()
        }

        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        cancelScope {
            Thread.ofPlatform().start {
                cancel()
                Thread.currentThread().interrupt()
                val reader = socket.endpoint().reader.buffered()
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
            override fun getInputStream() = buffer.asInputStream()
        }

        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        cancelScope {
            Thread.ofVirtual().start {
                cancel()
                Thread.currentThread().interrupt()
                val reader = socket.endpoint().reader.buffered()
                throwableAssert = assertThatThrownBy { reader.readByte() }
            }.join()
        }
        throwableAssert!!.isInstanceOf(JayoClosedEndpointException::class.java)
    }

    @Test
    fun `write while cancelled and interrupted platform thread`() {
        val buffer = Buffer()
        val socket = object : Socket() {
            override fun isConnected() = true
            override fun getOutputStream() = buffer.asOutputStream()
        }

        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        cancelScope {
            Thread.ofPlatform().start {
                cancel()
                Thread.currentThread().interrupt()
                val writer = socket.endpoint().writer.buffered()
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
        }

        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        cancelScope {
            Thread.ofVirtual().start {
                cancel()
                Thread.currentThread().interrupt()
                val writer = socket.endpoint().writer.buffered()
                throwableAssert = assertThatThrownBy {
                    writer.writeByte(0)
                    writer.flush()
                }
            }.join()
        }
        throwableAssert!!.isInstanceOf(JayoClosedEndpointException::class.java)
    }

    @Test
    fun `close with timeout`() {
        val socket = object : Socket() {
            override fun isConnected() = true
            override fun close() {
                Thread.sleep(2)
            }
        }
        cancelScope(1.milliseconds) {
            assertThatThrownBy {
                socket.endpoint().close()
            }.isInstanceOf(JayoTimeoutException::class.java)
                .hasMessage("timeout")
        }
    }
    @Test
    fun `close no timeout`() {
        val socket = object : Socket() {
            override fun isConnected() = true
        }
        socket.endpoint().close()
    }
}
