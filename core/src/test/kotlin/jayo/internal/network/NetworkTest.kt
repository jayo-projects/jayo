/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network

import jayo.*
import jayo.internal.TestUtil.SEGMENT_SIZE
import jayo.network.NetworkEndpoint
import jayo.network.NetworkServer
import org.assertj.core.api.AbstractThrowableAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
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
                Arguments.of(NetworkFactory.TCP_NIO_ASYNC, "NetworkFactoryTcpNioAsync"),
                Arguments.of(NetworkFactory.TCP_IO, "NetworkFactoryTcpIo"),
                Arguments.of(NetworkFactory.TCP_IO_ASYNC, "NetworkFactoryTcpIoAsync"),
            )
        }

        @JvmStatic
        val TO_WRITE = "a".repeat(SEGMENT_SIZE * 4)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `read ok case`(networkFactory: NetworkFactory) {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
            .use { server ->
                val serverThread = thread(start = true) {
                    val accepted = server.accept()
                    accepted.writer.use { writer ->
                        writer.write(TO_WRITE)
                            .flush()
                    }
                }
                val client = NetworkEndpoint.connectTcp(server.localAddress, networkFactory.networkEndpointConfig())

                val stringRead = client.reader.readString()
                assertThat(stringRead).isEqualTo(TO_WRITE)
                serverThread.join()
            }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `get option and addresses are present`(networkFactory: NetworkFactory) {
        val server =
            NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
        assertThat(server.getOption(StandardSocketOptions.SO_REUSEADDR)).isTrue()
        assertThat(server.localAddress).isInstanceOf(InetSocketAddress::class.java)

        val client = NetworkEndpoint.connectTcp(server.localAddress, networkFactory.networkEndpointConfig())
        assertThat(client.getOption(StandardSocketOptions.SO_REUSEADDR)).isTrue()
        assertThat(client.localAddress).isInstanceOf(InetSocketAddress::class.java)
        assertThat(client.peerAddress).isInstanceOf(InetSocketAddress::class.java)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `negative read throws IllegalArgumentException`(networkFactory: NetworkFactory) {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = NetworkEndpoint.connectTcp(server.localAddress, networkFactory.networkEndpointConfig())

                assertThatThrownBy {
                    client.reader.readAtMostTo(Buffer(), -1)
                }.isInstanceOf(IllegalArgumentException::class.java)
                    .hasMessage("byteCount < 0: -1")
                serverThread.join()
            }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `several invocations of getReader() always return the same instance`(
        networkFactory: NetworkFactory
    ) {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = NetworkEndpoint.connectTcp(server.localAddress, networkFactory.networkEndpointConfig())

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
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = NetworkEndpoint.connectTcp(server.localAddress, networkFactory.networkEndpointConfig())

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
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = NetworkEndpoint.connectTcp(server.localAddress, networkFactory.networkEndpointConfig())
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
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = NetworkEndpoint.connectTcp(server.localAddress, networkFactory.networkEndpointConfig())
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
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = NetworkEndpoint.connectTcp(server.localAddress, networkFactory.networkEndpointConfig())
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
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = NetworkEndpoint.connectTcp(server.localAddress, networkFactory.networkEndpointConfig())
                client.close()
                serverThread.join()
            }
    }

    @Disabled // inconsistent, sometimes does not throw
    @Tag("no-ci")
    @Test
    fun `default connect timeout`() {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */)).use { server ->
            val serverThread = thread(start = true, isDaemon = true) {
                server.accept()
            }
            assertThatThrownBy {
                NetworkEndpoint.connectTcp(
                    server.localAddress, NetworkEndpoint.configForNIO()
                        .connectTimeout(Duration.ofNanos(1))
                )
            }.isInstanceOf(JayoTimeoutException::class.java)
                .hasMessage("timeout")
            serverThread.join()
        }
    }

    @Tag("no-ci")
    @ParameterizedTest
    @MethodSource("parameters")
    fun `default read timeout`(networkFactory: NetworkFactory) {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
            .use { server ->
                val serverThread = thread(start = true) {
                    val accepted = server.accept()
                    accepted.writer.use { writer ->
                        writer.write(TO_WRITE)
                    }
                }
                val client = NetworkEndpoint.connectTcp(
                    server.localAddress, networkFactory.networkEndpointConfig()
                        .readTimeout(Duration.ofNanos(1))
                )

                assertThatThrownBy { client.reader.readString() }
                    .isInstanceOf(JayoTimeoutException::class.java)
                    .hasMessage("timeout")
                serverThread.join()
            }
    }

    @Tag("no-ci")
    @ParameterizedTest
    @MethodSource("parameters")
    fun `default write timeout`(networkFactory: NetworkFactory) {
        if (networkFactory.isIo) {
            return // inconsistent with IO
        }
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
            .use { server ->
                val serverThread = thread(start = true) {
                    server.accept()
                }
                val client = NetworkEndpoint.connectTcp(
                    server.localAddress, networkFactory.networkEndpointConfig()
                        .writeTimeout(Duration.ofNanos(1))
                )

                assertThatThrownBy {
                    client.writer.write(TO_WRITE)
                        .flush()
                }.isInstanceOf(JayoTimeoutException::class.java)
                    .hasMessage("timeout")

                serverThread.join()
            }
    }

    @Test
    fun `default connect timeout with declared timeout`() {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */)).use { server ->
            val serverThread = thread(start = true) {
                server.accept()
            }
            cancelScope(timeout = 10.seconds) {
                NetworkEndpoint.connectTcp(
                    server.localAddress, NetworkEndpoint.configForNIO()
                        .connectTimeout(Duration.ofNanos(1))
                )
            }
            serverThread.join()
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `default read timeout with declared timeout`(networkFactory: NetworkFactory) {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
            .use { server ->
                val serverThread = thread(start = true) {
                    val accepted = server.accept()
                    accepted.writer.use { writer ->
                        writer.write(TO_WRITE)
                    }
                }
                val client = NetworkEndpoint.connectTcp(
                    server.localAddress, networkFactory.networkEndpointConfig()
                        .readTimeout(Duration.ofNanos(1))
                )

                val stringRead = cancelScope(timeout = 10.seconds) {
                    client.reader.readString()
                }
                assertThat(stringRead).isEqualTo(TO_WRITE)
                serverThread.join()
            }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `default write timeout with declared timeout`(networkFactory: NetworkFactory) {
        NetworkServer.bindTcp(
            InetSocketAddress(0 /* find free port */),
            networkFactory.networkServerConfig()
                .writeTimeout(Duration.ofNanos(1))
        ).use { server ->
            val serverThread = thread(start = true) {
                val accepted = server.accept()
                accepted.writer.use { writer ->
                    cancelScope(timeout = 10.seconds) {
                        writer.write(TO_WRITE)
                            .flush()
                    }
                }
            }
            val client = NetworkEndpoint.connectTcp(server.localAddress, networkFactory.networkEndpointConfig())

            val stringRead = client.reader.readString()
            assertThat(stringRead).isEqualTo(TO_WRITE)
            serverThread.join()
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `get option and addresses on closed throws`(networkFactory: NetworkFactory) {
        val closedServer =
            NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
        closedServer.close()
        assertThatThrownBy { closedServer.getOption(StandardSocketOptions.SO_REUSEADDR) }
            .isInstanceOf(JayoClosedResourceException::class.java)
        assertThatThrownBy { closedServer.localAddress }
            .isInstanceOf(JayoClosedResourceException::class.java)

        val server =
            NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
        val closedClient = NetworkEndpoint.connectTcp(server.localAddress, networkFactory.networkEndpointConfig())
        closedClient.close()
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
        val closedServer =
            NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
        closedServer.close()
        closedServer.close()

        val server =
            NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), networkFactory.networkServerConfig())
        val closedClient = NetworkEndpoint.connectTcp(server.localAddress, networkFactory.networkEndpointConfig())
        closedClient.close()
        closedClient.close()
    }
}
