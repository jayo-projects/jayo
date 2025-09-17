/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network

import jayo.*
import jayo.internal.TestUtil.SEGMENT_SIZE
import org.assertj.core.api.AbstractThrowableAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.time.Duration
import java.util.stream.Stream
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

class NetworkTest {
    companion object {
        @JvmStatic
        fun parameters(): Stream<Arguments>? {
            return Stream.of(
                Arguments.of(NetworkFactory.TCP_NIO, "NetworkFactoryTcpNio"),
                Arguments.of(NetworkFactory.TCP_IO, "NetworkFactoryTcpIo"),
            )
        }

        @JvmStatic
        val TO_WRITE = "a".repeat(SEGMENT_SIZE * 4)

        @JvmField
        val UNREACHABLE_ADDRESS_IPV4 = InetSocketAddress("198.51.100.1", 8080)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `read ok case`(networkFactory: NetworkFactory) {
        networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
            .use { server ->
                val serverThread = thread(start = true) {
                    val accepted = server.accept()
                    accepted.writer.use { writer ->
                        writer.write(TO_WRITE)
                            .flush()
                    }
                }
                val client = networkFactory.networkSocketBuilder().connectTcp(server.localAddress)

                val stringRead = client.reader.readString()
                assertThat(stringRead).isEqualTo(TO_WRITE)
                assertThat(client.isOpen).isTrue()
                serverThread.join()
            }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `get option and addresses are present`(networkFactory: NetworkFactory) {
        val server = networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
        assertThat(server.getOption(StandardSocketOptions.SO_REUSEADDR)).isTrue()
        assertThat(server.localAddress).isInstanceOf(InetSocketAddress::class.java)

        val client = networkFactory.networkSocketBuilder().connectTcp(server.localAddress)
        assertThat(client.getOption(StandardSocketOptions.SO_REUSEADDR)).isTrue()
        assertThat(client.localAddress).isInstanceOf(InetSocketAddress::class.java)
        assertThat(client.peerAddress).isInstanceOf(InetSocketAddress::class.java)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `negative read throws IllegalArgumentException`(networkFactory: NetworkFactory) {
        networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = networkFactory.networkSocketBuilder().connectTcp(server.localAddress)

                assertThatThrownBy {
                    client.reader.readAtMostTo(Buffer(), -1)
                }.isInstanceOf(IllegalArgumentException::class.java)
                    .hasMessage("byteCount < 0: -1")
                serverThread.join()
            }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `several invocations of getReader() always return the same instance`(networkFactory: NetworkFactory) {
        networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = networkFactory.networkSocketBuilder().connectTcp(server.localAddress)

                val reader1 = client.reader
                val reader2 = client.reader
                assertThat(reader1).isSameAs(reader2)
                serverThread.join()
            }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `several invocations of getWriter() always return the same instance`(
        networkFactory: NetworkFactory
    ) {
        networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = networkFactory.networkSocketBuilder().connectTcp(server.localAddress)

                val writer1 = client.writer
                val writer2 = client.writer
                assertThat(writer1).isSameAs(writer2)
                serverThread.join()
            }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `read while cancelled and interrupted platform thread`(networkFactory: NetworkFactory) {
        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = networkFactory.networkSocketBuilder().connectTcp(server.localAddress)
                cancelScope {
                    thread(start = true) {
                        cancel()
                        Thread.currentThread().interrupt()
                        val reader = client.reader
                        throwableAssert = assertThatThrownBy { reader.readByte() }
                    }.join()
                }
                serverThread.join()
            }
        throwableAssert!!.isInstanceOf(JayoInterruptedIOException::class.java)
            .hasMessage("current thread is interrupted")
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `read while cancelled and interrupted virtual thread`(networkFactory: NetworkFactory) {
        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = networkFactory.networkSocketBuilder().connectTcp(server.localAddress)
                cancelScope {
                    thread(start = true) {
                        cancel()
                        Thread.currentThread().interrupt()
                        val reader = client.reader
                        throwableAssert = assertThatThrownBy { reader.readByte() }
                    }.join()
                }
                serverThread.join()
            }
        throwableAssert!!.isInstanceOf(JayoInterruptedIOException::class.java)
            .hasMessage("current thread is interrupted")
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `write while cancelled and interrupted thread`(networkFactory: NetworkFactory) {
        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = networkFactory.networkSocketBuilder().connectTcp(server.localAddress)
                cancelScope {
                    thread(start = true) {
                        cancel()
                        Thread.currentThread().interrupt()
                        val writer = client.writer
                        throwableAssert = assertThatThrownBy {
                            writer.writeByte(0)
                                .flush()
                        }
                    }.join()
                }
                serverThread.join()
            }
        throwableAssert!!.isInstanceOf(JayoInterruptedIOException::class.java)
            .hasMessage("current thread is interrupted")
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `close ok case`(networkFactory: NetworkFactory) {
        networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = networkFactory.networkSocketBuilder().connectTcp(server.localAddress)
                assertThat(client.isOpen).isTrue()
                client.cancel()
                assertThat(client.isOpen).isFalse()
                serverThread.join()
            }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `default connect timeout`(networkFactory: NetworkFactory) {
        networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */)).use {
            assertThatThrownBy {
                networkFactory.networkSocketBuilder()
                    .connectTimeout(Duration.ofMillis(1))
                    .connectTcp(UNREACHABLE_ADDRESS_IPV4)
            }.isInstanceOf(JayoTimeoutException::class.java)
                .hasMessageEndingWith("timeout")
        }
    }

    @Disabled // inconsistent, sometimes does not throw
    @Tag("no-ci")
    @ParameterizedTest
    @MethodSource("parameters")
    fun `default write timeout`(networkFactory: NetworkFactory) {
        networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = networkFactory.networkSocketBuilder()
                    .connectTcp(server.localAddress)
                client.writeTimeout = Duration.ofNanos(1)

                assertThatThrownBy {
                    client.writer.write(TO_WRITE)
                        .flush()
                }.isInstanceOf(JayoTimeoutException::class.java)
                    .hasMessageEndingWith("timeout")

                serverThread.join()
            }
    }

    @Tag("no-ci")
    @ParameterizedTest
    @MethodSource("parameters")
    fun `default read timeout`(networkFactory: NetworkFactory) {
        networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
            .use { server ->
                val serverThread = thread(start = true) {
                    val accepted = server.accept()
                    accepted.writer.use { serverWriter ->
                        serverWriter.writeInt(1)
                            .flush()
                        Thread.sleep(400)
                        serverWriter.writeInt(2)
                    }
                }
                val client = networkFactory.networkSocketBuilder()
                    .connectTcp(server.localAddress)
                client.readTimeout = Duration.ofMillis(200)

                client.reader.use { clientReader ->
                    assertThat(clientReader.readInt()).isEqualTo(1)

                    assertThatThrownBy { clientReader.readInt() }
                        .isInstanceOf(JayoTimeoutException::class.java)
                        .hasMessageEndingWith("timeout")
                }
                serverThread.join()
            }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `default read timeout with declared timeout`(networkFactory: NetworkFactory) {
        networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
            .use { server ->
                val serverThread = thread(start = true) {
                    val accepted = server.accept()
                    accepted.writer.use { writer ->
                        writer.write(TO_WRITE)
                    }
                }
                val client = networkFactory.networkSocketBuilder()
                    .connectTcp(server.localAddress)
                client.readTimeout = Duration.ofNanos(1)

                val stringRead = cancelScope(timeout = 10.seconds) {
                    client.reader.readString()
                }
                assertThat(stringRead).isEqualTo(TO_WRITE)
                serverThread.join()
            }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `default server write timeout with declared timeout`(networkFactory: NetworkFactory) {
        networkFactory.networkServerBuilder()
            .bindTcp(InetSocketAddress(0 /* find free port */)).use { server ->
                val serverThread = thread(start = true) {
                    val accepted = server.accept()
                    accepted.writeTimeout = Duration.ofNanos(1)
                    accepted.writer.use { writer ->
                        cancelScope(timeout = 10.seconds) {
                            writer.write(TO_WRITE)
                                .flush()
                        }
                    }
                }
                val client = networkFactory.networkSocketBuilder().connectTcp(server.localAddress)

                val stringRead = client.reader.readString()
                assertThat(stringRead).isEqualTo(TO_WRITE)
                serverThread.join()
            }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `get option and addresses on closed throws`(networkFactory: NetworkFactory) {
        val closedServer = networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
        closedServer.close()
        assertThatThrownBy { closedServer.getOption(StandardSocketOptions.SO_REUSEADDR) }
            .isInstanceOf(JayoClosedResourceException::class.java)
        assertThatThrownBy { closedServer.localAddress }
            .isInstanceOf(JayoClosedResourceException::class.java)

        val server = networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
        val closedClient = networkFactory.networkSocketBuilder().connectTcp(server.localAddress)
        closedClient.cancel()
        assertThatThrownBy { closedClient.getOption(StandardSocketOptions.SO_REUSEADDR) }
            .isInstanceOf(JayoClosedResourceException::class.java)
        assertThatThrownBy { closedClient.localAddress }
            .isInstanceOf(JayoClosedResourceException::class.java)
        assertThatThrownBy { closedClient.peerAddress }
            .isInstanceOf(JayoClosedResourceException::class.java)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `double close is ok`(networkFactory: NetworkFactory) {
        val closedServer = networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
        closedServer.close()
        closedServer.close()

        val server = networkFactory.networkServerBuilder().bindTcp(InetSocketAddress(0 /* find free port */))
        val closedClient = networkFactory.networkSocketBuilder().connectTcp(server.localAddress)
        closedClient.cancel()
        closedClient.cancel()
    }
}
