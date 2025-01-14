/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.tls

import jayo.buffered
import jayo.network.NetworkEndpoint
import jayo.network.NetworkServer
import jayo.tls.Handshake
import jayo.tls.JayoTlsHandshakeCallbackException
import jayo.tls.TlsEndpoint
import jayo.tls.helpers.SslContextFactory
import jayo.tls.kotlin
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

class TlsEndpointTest {
    companion object {
        @JvmStatic
        private val sslContext = SslContextFactory("TLSv1.3").defaultContext

        @JvmStatic
        private val sslSocketFactory = sslContext.socketFactory

        @JvmStatic
        private val sslServerSocketFactory = sslContext.serverSocketFactory

        @JvmStatic
        private val localhost = InetAddress.getByName(null)
    }

    @Test
    fun fullTlsClientEndpointBuilder() {
        sslServerSocketFactory.createServerSocket(0 /* find free port */).use { listener ->
            val serverThread = thread(start = true) {
                listener.accept().use { serverSocket ->
                    serverSocket.outputStream.write(42)
                }
            }
            val address = InetSocketAddress(localhost, listener.localPort)
            val encryptedEndpoint = NetworkEndpoint.connectTcp(address)
            var sslSession: SSLSession? = null
            val konfig = TlsEndpoint.configForClient().kotlin {
                sessionInitCallback = { sslSession = it }
                waitForCloseConfirmation = true
            }
            TlsEndpoint.createClient(encryptedEndpoint, sslContext, konfig).use { tlsClient ->
                assertThat(sslSession).isNotNull
                val handshake = tlsClient.handshake
                assertThat(handshake).isNotNull
                assertThat(handshake.localCertificates).isEmpty()
                assertThat(handshake.localPrincipal).isNull()
                assertThat(handshake.peerCertificates).isNotEmpty.hasSize(1)
                assertThat(handshake.peerPrincipal).isNotNull
            }
            serverThread.join()
        }
    }

    @Test
    fun tlsClientEndpointBuilder_ExceptionInSessionInitCallback() {
        sslServerSocketFactory.createServerSocket(0 /* find free port */).use { listener ->
            val serverThread = thread(start = true) {
                listener.accept().use { serverSocket ->
                    serverSocket.outputStream.write(42)
                }
            }
            val address = InetSocketAddress(localhost, listener.localPort)
            val encryptedEndpoint = NetworkEndpoint.connectTcp(address)
            val konfig = TlsEndpoint.configForClient().kotlin {
                sessionInitCallback = { throw Exception() }
            }
            assertThatThrownBy { TlsEndpoint.createClient(encryptedEndpoint, sslContext, konfig) }
                .isInstanceOf(JayoTlsHandshakeCallbackException::class.java)
                .hasMessage("Session initialization callback failed")
            serverThread.join()
        }
    }

    @Test
    fun tlsClientEndpoint_EngineInServerMode() {
        sslServerSocketFactory.createServerSocket(0 /* find free port */).use { listener ->
            val address = InetSocketAddress(localhost, listener.localPort)
            val encryptedEndpoint = NetworkEndpoint.connectTcp(address)
            assertThatThrownBy { TlsEndpoint.createClient(encryptedEndpoint, sslContext.createSSLEngine()) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("The provided SSL engine must use client mode")
        }
    }

    @Test
    fun fullTlsServerEndpointBuilder() {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */)).use { listener ->
            var sslSession: SSLSession? = null
            var handshake: Handshake? = null
            val serverThread = thread(start = true) {
                listener.accept().use { serverEndpoint ->
                    val konfig = TlsEndpoint.configForServer().kotlin {
                        sessionInitCallback = { sslSession = it }
                        waitForCloseConfirmation = true
                        engineFactory = { it.createSSLEngine().apply { useClientMode = false } }
                    }
                    TlsEndpoint.createServer(serverEndpoint, sslContext, konfig).use { tlsServer ->
                        tlsServer.writer.buffered()
                            .writeInt(42)
                            .flush()
                        handshake = tlsServer.handshake
                    }
                }
            }
            val client = sslSocketFactory.createSocket() as SSLSocket
            client.connect(listener.localAddress)
            client.startHandshake()
            client.shutdownOutput() // needed to respect the 'waitForCloseConfirmation' config of server
            serverThread.join()
            assertThat(sslSession).isNotNull
            assertThat(handshake).isNotNull
            assertThat(handshake!!.localCertificates).isNotEmpty.hasSize(1)
            assertThat(handshake!!.localPrincipal).isNotNull
            assertThat(handshake!!.peerCertificates).isEmpty()
            assertThat(handshake!!.peerPrincipal).isNull()
        }
    }

    @Test
    fun tlsServerEndpointBuilder_ExceptionInEngineFactory() {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */)).use { listener ->
            val serverThread = thread(start = true) {
                listener.accept().use { serverEndpoint ->
                    val konfig = TlsEndpoint.configForServer().kotlin {
                        engineFactory = { throw Exception() }
                    }
                    TlsEndpoint.createServer(serverEndpoint, sslContext, konfig).use { serverTlsEndpoint ->
                        assertThatThrownBy {
                            serverTlsEndpoint.writer.buffered()
                                .writeInt(42)
                                .flush()
                        }.isInstanceOf(JayoTlsHandshakeCallbackException::class.java)
                            .hasMessage("SSLEngine creation callback failed")
                    }
                }
            }
            val client = sslSocketFactory.createSocket() as SSLSocket
            client.connect(listener.localAddress)
            assertThatThrownBy { client.startHandshake() }.isInstanceOf(IOException::class.java)
            serverThread.join()
        }
    }
}