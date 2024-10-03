/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal

import jayo.Buffer
import jayo.buffered
import jayo.cancelScope
import jayo.endpoints.endpoint
import jayo.exceptions.JayoInterruptedIOException
import org.assertj.core.api.AbstractThrowableAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.nio.channels.SocketChannel

class SocketChannelEndpointTest {
    @Test
    fun `socket channel is not connected throws IllegalArgumentException`() {
        val socketChannel = SocketChannel.open()
        assertThatThrownBy {
            socketChannel.endpoint()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Socket channel is not connected")
    }

    @Test
    fun `socket channel is closed throws IllegalArgumentException`() {
        val socketChannel = SocketChannel.open()
        socketChannel.close()
        assertThatThrownBy {
            socketChannel.endpoint()
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Socket channel is closed")
    }

    @Test
    fun `negative read throws IllegalArgumentException`() {
        ServerSocket(0 /* find free port */).use { serverSocket ->
            val serverThread = Thread.ofVirtual().start {
                serverSocket.accept()
            }
            val socketChannelEndpoint = SocketChannel.open(serverSocket.localSocketAddress).endpoint()

            assertThatThrownBy {
                socketChannelEndpoint.reader.readAtMostTo(Buffer(), -1)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("byteCount < 0 : -1")
            serverThread.join()
        }
    }

    @Test
    fun `underlying is the original socket channel`() {
        ServerSocket(0 /* find free port */).use { serverSocket ->
            val serverThread = Thread.ofVirtual().start {
                serverSocket.accept()
            }
            val socketChannel = SocketChannel.open(serverSocket.localSocketAddress)

            assertThat(socketChannel.endpoint().underlying).isSameAs(socketChannel)
            serverThread.join()
        }
    }

    @Test
    fun `several invocations of getReader() always return the same instance`() {
        ServerSocket(0 /* find free port */).use { serverSocket ->
            val serverThread = Thread.ofVirtual().start {
                serverSocket.accept()
            }
            val socketChannelEndpoint = SocketChannel.open(serverSocket.localSocketAddress).endpoint()

            val reader1 = socketChannelEndpoint.reader
            val reader2 = socketChannelEndpoint.reader
            assertThat(reader1).isSameAs(reader2)
            serverThread.join()
        }
    }

    @Test
    fun `several invocations of getWriter() always return the same instance`() {
        ServerSocket(0 /* find free port */).use { serverSocket ->
            val serverThread = Thread.ofVirtual().start {
                serverSocket.accept()
            }
            val socketChannelEndpoint = SocketChannel.open(serverSocket.localSocketAddress).endpoint()

            val writer1 = socketChannelEndpoint.writer
            val writer2 = socketChannelEndpoint.writer
            assertThat(writer1).isSameAs(writer2)
            serverThread.join()
        }
    }

    @Test
    fun `read while cancelled and interrupted platform thread`() {
        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        ServerSocket(0 /* find free port */).use { serverSocket ->
            val serverThread = Thread.ofVirtual().start {
                serverSocket.accept()
            }
            val socketChannelEndpoint = SocketChannel.open(serverSocket.localSocketAddress).endpoint()
            cancelScope {
                Thread.ofPlatform().start {
                    cancel()
                    Thread.currentThread().interrupt()
                    val reader = socketChannelEndpoint.reader.buffered()
                    throwableAssert = assertThatThrownBy { reader.readByte() }
                }.join()
            }
            serverThread.join()
        }
        throwableAssert!!.isInstanceOf(JayoInterruptedIOException::class.java)
            .hasMessage("current thread is interrupted")
    }

    @Test
    fun `read while cancelled and interrupted virtual thread`() {
        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        ServerSocket(0 /* find free port */).use { serverSocket ->
            val serverThread = Thread.ofVirtual().start {
                serverSocket.accept()
            }
            val socketChannelEndpoint = SocketChannel.open(serverSocket.localSocketAddress).endpoint()
            cancelScope {
                Thread.ofVirtual().start {
                    cancel()
                    Thread.currentThread().interrupt()
                    val reader = socketChannelEndpoint.reader.buffered()
                    throwableAssert = assertThatThrownBy { reader.readByte() }
                }.join()
            }
            serverThread.join()
        }
        throwableAssert!!.isInstanceOf(JayoInterruptedIOException::class.java)
            .hasMessage("current thread is interrupted")
    }

    @Test
    fun `write while cancelled and interrupted platform thread`() {
        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        ServerSocket(0 /* find free port */).use { serverSocket ->
            val serverThread = Thread.ofVirtual().start {
                serverSocket.accept()
            }
            val socketChannelEndpoint = SocketChannel.open(serverSocket.localSocketAddress).endpoint()
            cancelScope {
                Thread.ofPlatform().start {
                    cancel()
                    Thread.currentThread().interrupt()
                    val writer = socketChannelEndpoint.writer.buffered()
                    throwableAssert = assertThatThrownBy {
                        writer.writeByte(0)
                        writer.flush()
                    }
                }.join()
            }
            serverThread.join()
        }
        throwableAssert!!.isInstanceOf(JayoInterruptedIOException::class.java)
            .hasMessage("current thread is interrupted")
    }

    @Test
    fun `write while cancelled and interrupted virtual thread`() {
        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        ServerSocket(0 /* find free port */).use { serverSocket ->
            val serverThread = Thread.ofVirtual().start {
                serverSocket.accept()
            }
            val socketChannelEndpoint = SocketChannel.open(serverSocket.localSocketAddress).endpoint()
            cancelScope {
                Thread.ofVirtual().start {
                    cancel()
                    Thread.currentThread().interrupt()
                    val writer = socketChannelEndpoint.writer.buffered()
                    throwableAssert = assertThatThrownBy {
                        writer.writeByte(0)
                        writer.flush()
                    }
                }.join()
            }
            serverThread.join()
        }
        throwableAssert!!.isInstanceOf(JayoInterruptedIOException::class.java)
            .hasMessage("current thread is interrupted")
    }

    @Test
    fun `close no timeout`() {
        ServerSocket(0 /* find free port */).use { serverSocket ->
            val serverThread = Thread.ofVirtual().start {
                serverSocket.accept()
            }
            val socketChannelEndpoint = SocketChannel.open(serverSocket.localSocketAddress).endpoint()
            socketChannelEndpoint.close()
            serverThread.join()
        }
    }
}
