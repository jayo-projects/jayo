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
import java.io.OutputStream
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds

class JayoSocketTest {
    @Test
    fun `negative read throws IllegalArgumentException`() {
        val buffer = Buffer()
        val socket = object : Socket() {
            override fun isConnected() = true
            override fun getInputStream() = buffer.asInputStream()
        }

        assertThatThrownBy {
            Jayo.reader(socket).readAtMostTo(Buffer(), -1)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("byteCount < 0 : -1")
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
                val reader = Jayo.reader(socket).buffered()
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
                val reader = Jayo.reader(socket).buffered()
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
        }

        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        cancelScope {
            Thread.ofPlatform().start {
                cancel()
                Thread.currentThread().interrupt()
                val writer = Jayo.writer(socket).buffered()
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
                val writer = Jayo.writer(socket).buffered()
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
        val socket = object : Socket() {
            override fun isConnected() = true
            override fun getOutputStream() = object : OutputStream() {
                override fun write(b: Int) = TODO("Not yet implemented")

                override fun close() {
                    Thread.sleep(2)
                }
            }
        }
        cancelScope(1.milliseconds) {
            assertThatThrownBy {
                Jayo.writer(socket).close()
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
        }
        Jayo.writer(socket).close()
    }
}
