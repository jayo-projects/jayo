/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network

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
            NetworkEndpoint.configForNIO().protocol(NetworkProtocol.IPv4),
            NetworkServer.configForNIO().protocol(NetworkProtocol.IPv4)
        )
    }

    @Test
    fun `Socks V5 Proxy test with NIO and IPv6`() {
        socks5(
            NetworkEndpoint.configForNIO().protocol(NetworkProtocol.IPv6),
            NetworkServer.configForNIO().protocol(NetworkProtocol.IPv6)
        )
    }

    @Test
    fun `Socks V5 Proxy test with IO`() {
        socks5(NetworkEndpoint.configForIO(), NetworkServer.configForIO())
    }

    @Test
    fun `Socks V5 Proxy test with NIO and proxy credentials success`() {
        socks5(
            NetworkEndpoint.configForNIO(),
            NetworkServer.configForNIO(),
            PasswordAuthentication("Noël", "Pâques".toCharArray())
        )
    }

    @Test
    fun `Socks V5 Proxy test with IO and proxy credentials success`() {
        socks5(
            NetworkEndpoint.configForIO(),
            NetworkServer.configForIO(),
            PasswordAuthentication("Noël", "Pâques".toCharArray())
        )
    }

    @Test
    fun `Socks V5 Proxy test with NIO and proxy credentials failure`() {
        assertThatThrownBy {
            socks5(
                NetworkEndpoint.configForNIO(),
                NetworkServer.configForNIO(),
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
                NetworkEndpoint.configForIO(),
                NetworkServer.configForIO(),
                PasswordAuthentication("Noël", "Pâques".toCharArray()),
                false
            )
        }.isInstanceOf(JayoSocketException::class.java)
            .hasMessage("SOCKS5 : Authentication failed")
    }

    private fun socks5(
        clientConfig: NetworkEndpoint.Config<*>,
        serverConfig: NetworkServer.Config<*>,
        credentials: PasswordAuthentication? = null,
        authSuccess: Boolean = true
    ) {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), serverConfig)
            .use { server ->
                Socks5ProxyServer(serverConfig, executor, credentials).use { proxy ->
                    executor.execute {
                        val accepted = server.accept()
                        accepted.writer.use { writer ->
                            writer.write(TO_WRITE)
                                .flush()
                        }
                    }
                    val proxy = if (credentials != null) {
                        val username = if (authSuccess) credentials.userName else "error"
                        Proxy.socks5(proxy.address, username, credentials.password)
                    } else {
                        Proxy.socks5(proxy.address)
                    }
                    val client = NetworkEndpoint.connectTcp(server.localAddress, proxy, clientConfig)

                    val stringRead = client.reader.readString()
                    assertThat(stringRead).isEqualTo(TO_WRITE)
                }
            }
    }

    @Test
    fun `Socks V4 Proxy test with NIO and IPv4`() {
        socks4(
            NetworkEndpoint.configForNIO().protocol(NetworkProtocol.IPv4),
            NetworkServer.configForNIO().protocol(NetworkProtocol.IPv4)
        )
    }

    @Test
    fun `Socks V4 Proxy test with NIO and IPv6`() {
        assertThatThrownBy {
            socks4(
                NetworkEndpoint.configForNIO().protocol(NetworkProtocol.IPv6),
                NetworkServer.configForNIO().protocol(NetworkProtocol.IPv6)
            )
        }.isInstanceOf(JayoSocketException::class.java)
            .hasMessage("SOCKS4 : SOCKS V4 only supports IPv4 socket address")
    }

    @Test
    fun `Socks V4 Proxy test with IO`() {
        socks4(NetworkEndpoint.configForIO(), NetworkServer.configForIO())
    }

    @Test
    fun `Socks V4 Proxy test with NIO and proxy credentials success`() {
        socks4(
            NetworkEndpoint.configForNIO().protocol(NetworkProtocol.IPv4),
            NetworkServer.configForNIO().protocol(NetworkProtocol.IPv4),
            "Noël"
        )
    }

    @Test
    fun `Socks V4 Proxy test with IO and proxy credentials success`() {
        socks4(
            NetworkEndpoint.configForIO(),
            NetworkServer.configForIO(),
            "Noël"
        )
    }

    @Test
    fun `Socks V4 Proxy test with NIO and proxy credentials failure`() {
        assertThatThrownBy {
            socks4(
                NetworkEndpoint.configForNIO().protocol(NetworkProtocol.IPv4),
                NetworkServer.configForNIO().protocol(NetworkProtocol.IPv4),
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
                NetworkEndpoint.configForIO(),
                NetworkServer.configForIO(),
                "Noël",
                false
            )
        }.isInstanceOf(JayoSocketException::class.java)
            .hasMessage("SOCKS4 : Authentication failed")
    }

    private fun socks4(
        clientConfig: NetworkEndpoint.Config<*>,
        serverConfig: NetworkServer.Config<*>,
        username: String? = null,
        authSuccess: Boolean = true
    ) {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */), serverConfig)
            .use { server ->
                Socks4ProxyServer(serverConfig, executor, username).use { proxy ->
                    executor.execute {
                        val accepted = server.accept()
                        accepted.writer.use { writer ->
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
                    val client = NetworkEndpoint.connectTcp(server.localAddress, proxy, clientConfig)

                    val stringRead = client.reader.readString()
                    assertThat(stringRead).isEqualTo(TO_WRITE)
                }
            }
    }
}
