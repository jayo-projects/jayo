/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network

import jayo.*
import org.assertj.core.api.AbstractThrowableAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.time.Duration
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

class NetworkTest {
    companion object {
        @JvmStatic
        fun parameters(): Stream<Arguments>? {
            return Stream.of(
                Arguments.of(NetworkFactory.TCP_IO, "NetworkFactoryTcpIo"),
                Arguments.of(NetworkFactory.TCP_NIO, "NetworkFactoryTcpNio"),
            )
        }

        @JvmStatic
        val TO_WRITE = "42".repeat(10_000)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `read ok case`(networkFactory: NetworkFactory) {
        networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */)).use { server ->
            val serverThread = Thread.ofVirtual().start {
                val accepted = server.accept()
                accepted.writer.buffered().use { writer ->
                    writer.write(TO_WRITE)
                        .flush()
                }
            }
            val client = networkFactory.networkEndpointBuilder().connect(server.localAddress)

            val stringRead = client.reader.buffered().readString()
            assertThat(stringRead).isEqualTo(TO_WRITE)
            serverThread.join()
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `get option and addresses are present`(networkFactory: NetworkFactory) {
        val server = networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */))
        assertThat(server.getOption(StandardSocketOptions.SO_REUSEADDR)).isTrue()
        assertThat(server.localAddress).isInstanceOf(InetSocketAddress::class.java)

        val client = networkFactory.networkEndpointBuilder().connect(server.localAddress)
        assertThat(client.getOption(StandardSocketOptions.SO_REUSEADDR)).isTrue()
        assertThat(client.localAddress).isInstanceOf(InetSocketAddress::class.java)
        assertThat(client.peerAddress).isInstanceOf(InetSocketAddress::class.java)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `negative read throws IllegalArgumentException`(networkFactory: NetworkFactory) {
        networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */)).use { server ->
            val serverThread = Thread.ofVirtual().start {
                server.accept()
            }
            val client = networkFactory.networkEndpointBuilder().connect(server.localAddress)

            assertThatThrownBy {
                client.reader.readAtMostTo(Buffer(), -1)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("byteCount < 0 : -1")
            serverThread.join()
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `several invocations of getReader() always return the same instance`(
        networkFactory: NetworkFactory
    ) {
        networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */)).use { server ->
            val serverThread = Thread.ofVirtual().start {
                server.accept()
            }
            val client = networkFactory.networkEndpointBuilder().connect(server.localAddress)

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
        networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */)).use { server ->
            val serverThread = Thread.ofVirtual().start {
                server.accept()
            }
            val client = networkFactory.networkEndpointBuilder().connect(server.localAddress)

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
        networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */)).use { server ->
            val serverThread = Thread.ofVirtual().start {
                server.accept()
            }
            val client = networkFactory.networkEndpointBuilder().connect(server.localAddress)
            cancelScope {
                Thread.ofPlatform().start {
                    cancel()
                    Thread.currentThread().interrupt()
                    val reader = client.reader.buffered()
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
        networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */)).use { server ->
            val serverThread = Thread.ofVirtual().start {
                server.accept()
            }
            val client = networkFactory.networkEndpointBuilder().connect(server.localAddress)
            cancelScope {
                Thread.ofVirtual().start {
                    cancel()
                    Thread.currentThread().interrupt()
                    val reader = client.reader.buffered()
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
    fun `write while cancelled and interrupted platform thread`(networkFactory: NetworkFactory) {
        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */)).use { server ->
            val serverThread = Thread.ofVirtual().start {
                server.accept()
            }
            val client = networkFactory.networkEndpointBuilder().connect(server.localAddress)
            cancelScope {
                Thread.ofPlatform().start {
                    cancel()
                    Thread.currentThread().interrupt()
                    val writer = client.writer.buffered()
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
    fun `write while cancelled and interrupted virtual thread`(networkFactory: NetworkFactory) {
        var throwableAssert: AbstractThrowableAssert<*, *>? = null
        networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */)).use { server ->
            val serverThread = Thread.ofVirtual().start {
                server.accept()
            }
            val client = networkFactory.networkEndpointBuilder().connect(server.localAddress)
            cancelScope {
                Thread.ofVirtual().start {
                    cancel()
                    Thread.currentThread().interrupt()
                    val writer = client.writer.buffered()
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
        networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */)).use { server ->
            val serverThread = Thread.ofVirtual().start {
                server.accept()
            }
            val client = networkFactory.networkEndpointBuilder().connect(server.localAddress)
            client.close()
            serverThread.join()
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `connect timeout`(networkFactory: NetworkFactory) {
        assertThatThrownBy {
            networkFactory.networkEndpointBuilder()
                .connectTimeout(Duration.ofNanos(1))
                .connect(InetSocketAddress(0 /* find free port */))
        }.isInstanceOf(JayoTimeoutException::class.java)
            .hasMessage("timeout")
    }

    @Tag("no-ci")
    @ParameterizedTest
    @MethodSource("parameters")
    fun `default read timeout`(networkFactory: NetworkFactory) {
        networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */)).use { server ->
            val serverThread = Thread.ofVirtual().start {
                val accepted = server.accept()
                accepted.writer.buffered().use { writer ->
                    writer.write(TO_WRITE)
                }
            }
            val client = networkFactory.networkEndpointBuilder()
                .readTimeout(Duration.ofNanos(1))
                .connect(server.localAddress)

            assertThatThrownBy { client.reader.buffered().readString() }
                .isInstanceOf(JayoTimeoutException::class.java)
                .hasMessage("timeout")
            serverThread.join()
        }
    }

    @Tag("no-ci")
    @ParameterizedTest
    @MethodSource("parameters")
    fun `default write timeout`(networkFactory: NetworkFactory) {
        networkFactory.networkServerBuilder()
            .bind(InetSocketAddress(0 /* find free port */)).use { server ->
                val serverThread = Thread.ofVirtual().start {
                    server.accept()
                }
                val client = networkFactory.networkEndpointBuilder()
                    .writeTimeout(Duration.ofNanos(1))
                    .connect(server.localAddress)

                assertThatThrownBy {
                    client.writer.buffered().write(TO_WRITE)
                }.isInstanceOf(JayoTimeoutException::class.java)
                    .hasMessage("timeout")

                serverThread.join()
            }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `default read timeout with declared timeout`(networkFactory: NetworkFactory) {
        networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */)).use { server ->
            val serverThread = Thread.ofVirtual().start {
                val accepted = server.accept()
                accepted.writer.buffered().use { writer ->
                    writer.write(TO_WRITE)
                }
            }
            val client = networkFactory.networkEndpointBuilder()
                .readTimeout(Duration.ofNanos(1))
                .connect(server.localAddress)

            val stringRead = cancelScope(timeout = 10.seconds) {
                client.reader.buffered().readString()
            }
            assertThat(stringRead).isEqualTo(TO_WRITE)
            serverThread.join()
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `default write timeout with declared timeout`(networkFactory: NetworkFactory) {
        networkFactory.networkServerBuilder()
            .writeTimeout(Duration.ofNanos(1))
            .bind(InetSocketAddress(0 /* find free port */)).use { server ->
                val serverThread = Thread.ofVirtual().start {
                    val accepted = server.accept()
                    accepted.writer.buffered().use { writer ->
                        cancelScope(timeout = 10.seconds) {
                            writer.write(TO_WRITE)
                                .flush()
                        }
                    }
                }
                val client = networkFactory.networkEndpointBuilder().connect(server.localAddress)

                val stringRead = client.reader.buffered().readString()
                assertThat(stringRead).isEqualTo(TO_WRITE)
                serverThread.join()
            }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `get option and addresses on closed throws`(networkFactory: NetworkFactory) {
        val closedServer = networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */))
        closedServer.close()
        assertThatThrownBy { closedServer.getOption(StandardSocketOptions.SO_REUSEADDR) }
            .isInstanceOf(JayoClosedEndpointException::class.java)
        assertThatThrownBy { closedServer.localAddress }
            .isInstanceOf(JayoClosedEndpointException::class.java)

        val server = networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */))
        val closedClient = networkFactory.networkEndpointBuilder().connect(server.localAddress)
        closedClient.close()
        assertThatThrownBy { closedClient.getOption(StandardSocketOptions.SO_REUSEADDR) }
            .isInstanceOf(JayoClosedEndpointException::class.java)
        assertThatThrownBy { closedClient.localAddress }
            .isInstanceOf(JayoClosedEndpointException::class.java)
        assertThatThrownBy { closedClient.peerAddress }
            .isInstanceOf(JayoClosedEndpointException::class.java)
    }

    @ParameterizedTest
    @MethodSource("parameters")
    fun `double close is ok`(networkFactory: NetworkFactory) {
        val closedServer = networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */))
        closedServer.close()
        closedServer.close()

        val server = networkFactory.networkServerBuilder().bind(InetSocketAddress(0 /* find free port */))
        val closedClient = networkFactory.networkEndpointBuilder().connect(server.localAddress)
        closedClient.close()
        closedClient.close()
    }
}
