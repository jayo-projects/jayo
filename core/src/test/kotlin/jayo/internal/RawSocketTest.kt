/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
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
import jayo.network.NetworkSocket
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.channels.SocketChannel
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class NetworkIoRawSocketTest : RawSocketTest(RawSocketPairFactory.NETWORK_IO)
class NetworkNioRawSocketTest : RawSocketTest(RawSocketPairFactory.NETWORK_NIO)
class InMemoryRawSocketTest : RawSocketTest(RawSocketPairFactory.IN_MEMORY)

abstract class RawSocketTest internal constructor(private val factory: RawSocketPairFactory) {
    private lateinit var socket: RawSocket
    private lateinit var peerSocket: RawSocket
    private lateinit var peer: AsyncSocket

    @BeforeEach
    fun setUp() {
        val socketPair = factory.createSocketPair()
        this.socket = socketPair[0]
        this.peerSocket = socketPair[1]
        this.peer = AsyncSocket(peerSocket)
    }

    @AfterEach
    fun tearDown() {
        peer.close()
        socket.reader.close()
        try {
            socket.writer.close()
        } catch (_: JayoException) {
            // Ignore exception if data was left in 'sink'.
        }
    }

    @Test
    fun happyPath() {
        val bufferedSource = socket.reader.buffered()
        val bufferedSink = socket.writer.buffered()

        peer.write("one")
        assertThat(bufferedSource.readLineStrict()).isEqualTo("one")

        bufferedSink.write("two\n")
        bufferedSink.flush()
        assertThat(peer.read()).isEqualTo("two")

        peer.write("three")
        assertThat(bufferedSource.readLineStrict()).isEqualTo("three")

        bufferedSink.write("four\n")
        bufferedSink.flush()
        assertThat(peer.read()).isEqualTo("four")
    }

    @Test
    fun sourceIsReadableAfterSinkIsClosed() {
        peer.closeSource()
        socket.writer.close()

        peer.write("Hello")
        assertThat(socket.reader.buffered().readLine()).isEqualTo("Hello")

        socket.reader.close()
        peer.closeSink()
    }

    @Test
    fun sinkIsWritableAfterSourceIsClosed() {
        peer.closeSink()
        socket.reader.close()

        val bufferedSink = socket.writer.buffered()
        bufferedSink.write("Hello\n")
        bufferedSink.flush()
        assertThat(peer.read()).isEqualTo("Hello")

        socket.writer.close()
        peer.closeSource()
    }

    @Test
    fun localCancelCausesSubsequentReadToFail() {
        peer.write("Hello")

        socket.cancel()

        assertFailsWith<JayoException> {
            socket.reader.buffered().readLine()
        }
    }

    @Test
    fun localCancelCausesSubsequentWriteToFail() {
        socket.cancel()

        val bufferedSink = socket.writer.buffered()
        bufferedSink.write("Hello\n")
        assertFailsWith<JayoException> {
            bufferedSink.flush()
        }
    }

    @Test
    fun peerCloseCausesSubsequentLocalReadToFail() {
        peer.closeSink()

        val bufferedSource = socket.reader.buffered()
        assertFailsWith<JayoException> {
            bufferedSource.readLineStrict()
        }
    }

    @Test
    fun peerCancelCausesSubsequentLocalReadToFail() {
        peerSocket.cancel()

        val bufferedSource = socket.reader.buffered()
        assertFailsWith<JayoException> {
            bufferedSource.readLineStrict()
        }
    }

    @Test
    fun readTimeout() {
        val bufferedSource = socket.reader.buffered()
        val duration = measureTime {
            cancelScope(500.milliseconds) {
                assertFailsWith<JayoTimeoutException> {
                    bufferedSource.readLine()
                }
            }
        }

        assertThat(duration).isBetween(250.milliseconds, 750.milliseconds)
    }

    /** Make a large-enough write to saturate the outgoing write buffer. */
    @Test
    fun writeTimeout() {
        val bufferedSink = socket.writer.buffered()

        val duration = measureTime {
            cancelScope(500.milliseconds) {
                assertFailsWith<JayoTimeoutException> {
                    bufferedSink.write(ByteArray(1024 * 1024 * 16))
                }
            }
        }

        assertThat(duration).isBetween(250.milliseconds, 750.milliseconds)
    }

    @Test
    fun closeSourceDoesNotCloseJavaNetSocket() {
        val javaNetSocket = (this.socket as? NetworkSocket)?.underlying as? java.net.Socket ?: return

        socket.reader.close()
        assertThat(javaNetSocket.isInputShutdown).isTrue()
        assertThat(javaNetSocket.isOutputShutdown).isFalse()
        assertThat(javaNetSocket.isClosed).isFalse()
    }

    @Test
    fun closeSinkDoesNotCloseJavaNetSocket() {
        val javaNetSocket = (this.socket as? NetworkSocket)?.underlying as? java.net.Socket ?: return

        socket.writer.close()
        assertThat(javaNetSocket.isInputShutdown).isFalse()
        assertThat(javaNetSocket.isOutputShutdown).isTrue()
        assertThat(javaNetSocket.isClosed).isFalse()
    }

    @Test
    fun closeSourceThenSinkClosesJavaNetSocket() {
        val javaNetSocket = (this.socket as? NetworkSocket)?.underlying as? java.net.Socket ?: return

        socket.reader.close()
        socket.writer.close()
        assertThat(javaNetSocket.isClosed).isTrue()
    }

    @Test
    fun closeSinkThenSourceClosesJavaNetSocket() {
        val javaNetSocket = (this.socket as? NetworkSocket)?.underlying as? java.net.Socket ?: return

        socket.writer.close()
        socket.reader.close()
        assertThat(javaNetSocket.isClosed).isTrue()
    }

    @Test
    fun closeSinkThenSourceClosesJavaNetSocketEvenIfStreamsAlreadyClosed() {
        val javaNetSocket = (this.socket as? NetworkSocket)?.underlying as? java.net.Socket ?: return
        javaNetSocket.shutdownInput()
        javaNetSocket.shutdownOutput()
        assertThat(javaNetSocket.isClosed).isFalse()

        socket.writer.close()
        socket.reader.close()
        assertThat(javaNetSocket.isClosed).isTrue()
    }

    @Test
    fun closeSourceJavaNetSocketIsIdempotent() {
        val javaNetSocket = (this.socket as? NetworkSocket)?.underlying as? java.net.Socket ?: return

        socket.reader.close()
        assertThat(javaNetSocket.isInputShutdown).isTrue()
        assertThat(javaNetSocket.isClosed).isFalse()
        socket.reader.close()
        assertThat(javaNetSocket.isInputShutdown).isTrue()
        assertThat(javaNetSocket.isClosed).isFalse()
    }

    @Test
    fun closeSinkJavaNetSocketIsIdempotent() {
        val javaNetSocket = (this.socket as? NetworkSocket)?.underlying as? java.net.Socket ?: return

        socket.writer.close()
        assertThat(javaNetSocket.isOutputShutdown).isTrue()
        assertThat(javaNetSocket.isClosed).isFalse()
        socket.writer.close()
        assertThat(javaNetSocket.isOutputShutdown).isTrue()
        assertThat(javaNetSocket.isClosed).isFalse()
    }

    @Test
    fun closeSourceDoesNotCloseJavaNioSocketChannel() {
        val javaNioSocket = (this.socket as? NetworkSocket)?.underlying as? SocketChannel ?: return

        socket.reader.close()
        assertThat(javaNioSocket.isOpen).isTrue()
    }

    @Test
    fun closeSinkDoesNotCloseJavaNioSocketChannel() {
        val javaNioSocket = (this.socket as? NetworkSocket)?.underlying as? SocketChannel ?: return

        socket.writer.close()
        assertThat(javaNioSocket.isOpen).isTrue()
    }

    @Test
    fun closeSourceThenSinkClosesJavaNioSocketChannel() {
        val javaNioSocket = (this.socket as? NetworkSocket)?.underlying as? SocketChannel ?: return

        socket.reader.close()
        socket.writer.close()
        assertThat(javaNioSocket.isOpen).isFalse()
    }

    @Test
    fun closeSinkThenSourceClosesJavaNioSocketChannel() {
        val javaNioSocket = (this.socket as? NetworkSocket)?.underlying as? SocketChannel ?: return

        socket.writer.close()
        socket.reader.close()
        assertThat(javaNioSocket.isOpen).isFalse()
    }

    @Test
    fun closeSinkThenSourceClosesJavaNioSocketChannelEvenIfStreamsAlreadyClosed() {
        val javaNioSocket = (this.socket as? NetworkSocket)?.underlying as? SocketChannel ?: return
        javaNioSocket.shutdownInput()
        javaNioSocket.shutdownOutput()
        assertThat(javaNioSocket.isOpen).isTrue()

        socket.writer.close()
        socket.reader.close()
        assertThat(javaNioSocket.isOpen).isFalse()
    }

    @Test
    fun closeSourceJavaNioSocketChannelIsIdempotent() {
        val javaNioSocket = (this.socket as? NetworkSocket)?.underlying as? SocketChannel ?: return

        socket.reader.close()
        assertThat(javaNioSocket.isOpen).isTrue()
        socket.reader.close()
        assertThat(javaNioSocket.isOpen).isTrue()
    }

    @Test
    fun closeSinkJavaNioSocketChannelIsIdempotent() {
        val javaNioSocket = (this.socket as? NetworkSocket)?.underlying as? SocketChannel ?: return

        socket.writer.close()
        assertThat(javaNioSocket.isOpen).isTrue()
        socket.writer.close()
        assertThat(javaNioSocket.isOpen).isTrue()
    }
}