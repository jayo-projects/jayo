/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network

import jayo.buffered
import jayo.internal.TestUtil.SEGMENT_SIZE
import jayo.network.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NetworkWithSocksProxyTest {
    companion object {
        @JvmStatic
        val TO_WRITE = "a".repeat(SEGMENT_SIZE * 4)
    }

    private lateinit var executor: ExecutorService

    @BeforeEach
    fun before() {
        executor = Executors.newCachedThreadPool()
    }

    @AfterEach
    fun after() {
        executor.shutdown()
    }

    @Test
    fun `Socks V5 Proxy test with NIO and IPv4`() {
        socks5(
            NetworkSocket.builder().protocol(NetworkProtocol.IPv4),
            NetworkServer.builder().protocol(NetworkProtocol.IPv4)
        )
    }

    @Test
    fun `Socks V5 Proxy test with NIO and IPv6`() {
        socks5(
            NetworkSocket.builder().protocol(NetworkProtocol.IPv6),
            NetworkServer.builder().protocol(NetworkProtocol.IPv6)
        )
    }

    @Test
    fun `Socks V5 Proxy test with IO`() {
        socks5(NetworkSocket.builder().useNio(false), NetworkServer.builder().useNio(false))
    }

    @Test
    fun `Socks V5 Proxy test with NIO and proxy credentials success`() {
        socks5(
            NetworkSocket.builder(),
            NetworkServer.builder(),
            PasswordAuthentication("Noël", "Pâques".toCharArray())
        )
    }

    @Test
    fun `Socks V5 Proxy test with IO and proxy credentials success`() {
        socks5(
            NetworkSocket.builder().useNio(false),
            NetworkServer.builder().useNio(false),
            PasswordAuthentication("Noël", "Pâques".toCharArray())
        )
    }

    @Test
    fun `Socks V5 Proxy test with NIO and proxy credentials failure`() {
        assertThatThrownBy {
            socks5(
                NetworkSocket.builder(),
                NetworkServer.builder(),
                PasswordAuthentication("Noël", "Pâques".toCharArray()),
                false
            )
        }.isInstanceOf(JayoSocketException::class.java)
            .hasMessage("SOCKS5 : Authentication failed")
    }

    @Test
    fun `Socks V5 Proxy test with IO and proxy credentials failure`() {
        assertThatThrownBy {
            socks5(
                NetworkSocket.builder().useNio(false),
                NetworkServer.builder().useNio(false),
                PasswordAuthentication("Noël", "Pâques".toCharArray()),
                false
            )
        }.isInstanceOf(JayoSocketException::class.java)
            .hasMessage("SOCKS5 : Authentication failed")
    }

    private fun socks5(
        clientBuilder: NetworkSocket.Builder,
        serverBuilder: NetworkServer.Builder,
        credentials: PasswordAuthentication? = null,
        authSuccess: Boolean = true
    ) {
        serverBuilder.bindTcp(InetSocketAddress(0 /* find free port */))
            .use { server ->
                Socks5ProxyServer(serverBuilder, executor, credentials).use { proxy ->
                    executor.execute {
                        val accepted = server.accept()
                        accepted.writer.buffered().use { writer ->
                            writer.write(TO_WRITE)
                                .flush()
                        }
                    }
                    val proxy = if (credentials != null) {
                        val username = if (authSuccess) credentials.userName else "error"
                        Proxy.socks5(proxy.address, username, String(credentials.password))
                    } else {
                        Proxy.socks5(proxy.address)
                    }
                    val client = clientBuilder.openTcp().connect(server.localAddress, proxy)

                    val stringRead = client.reader.buffered().readString()
                    assertThat(stringRead).isEqualTo(TO_WRITE)
                }
            }
    }

    @Test
    fun `Socks V4 Proxy test with NIO and IPv4`() {
        socks4(
            NetworkSocket.builder().protocol(NetworkProtocol.IPv4),
            NetworkServer.builder().protocol(NetworkProtocol.IPv4)
        )
    }

    @Test
    fun `Socks V4 Proxy test with NIO and IPv6`() {
        assertThatThrownBy {
            socks4(
                NetworkSocket.builder().useNio(false).protocol(NetworkProtocol.IPv6),
                NetworkServer.builder().useNio(false).protocol(NetworkProtocol.IPv6)
            )
        }.isInstanceOf(JayoSocketException::class.java)
            .hasMessage("SOCKS4 : SOCKS V4 only supports IPv4 socket address")
    }

    @Test
    fun `Socks V4 Proxy test with IO`() {
        socks4(NetworkSocket.builder().useNio(false), NetworkServer.builder().useNio(false))
    }

    @Test
    fun `Socks V4 Proxy test with NIO and proxy credentials success`() {
        socks4(
            NetworkSocket.builder().protocol(NetworkProtocol.IPv4),
            NetworkServer.builder().protocol(NetworkProtocol.IPv4),
            "Noël"
        )
    }

    @Test
    fun `Socks V4 Proxy test with IO and proxy credentials success`() {
        socks4(
            NetworkSocket.builder().useNio(false),
            NetworkServer.builder().useNio(false),
            "Noël"
        )
    }

    @Test
    fun `Socks V4 Proxy test with NIO and proxy credentials failure`() {
        assertThatThrownBy {
            socks4(
                NetworkSocket.builder().protocol(NetworkProtocol.IPv4),
                NetworkServer.builder().protocol(NetworkProtocol.IPv4),
                "Noël",
                false
            )
        }.isInstanceOf(JayoSocketException::class.java)
            .hasMessage("SOCKS4 : Authentication failed")
    }

    @Test
    fun `Socks V4 Proxy test with IO and proxy credentials failure`() {
        assertThatThrownBy {
            socks4(
                NetworkSocket.builder().useNio(false),
                NetworkServer.builder().useNio(false),
                "Noël",
                false
            )
        }.isInstanceOf(JayoSocketException::class.java)
            .hasMessage("SOCKS4 : Authentication failed")
    }

    private fun socks4(
        clientBuilder: NetworkSocket.Builder,
        serverBuilder: NetworkServer.Builder,
        username: String? = null,
        authSuccess: Boolean = true
    ) {
        serverBuilder.bindTcp(InetSocketAddress(0 /* find free port */))
            .use { server ->
                Socks4ProxyServer(serverBuilder, executor, username).use { proxy ->
                    executor.execute {
                        val accepted = server.accept()
                        accepted.writer.buffered().use { writer ->
                            writer.write(TO_WRITE)
                                .flush()
                        }
                    }
                    val proxy = if (username != null) {
                        val usern = if (authSuccess) username else "error"
                        Proxy.socks4(proxy.address, usern)
                    } else {
                        Proxy.socks4(proxy.address)
                    }
                    val client = clientBuilder.openTcp().connect(server.localAddress, proxy)

                    val stringRead = client.reader.buffered().readString()
                    assertThat(stringRead).isEqualTo(TO_WRITE)
                }
            }
    }
}
