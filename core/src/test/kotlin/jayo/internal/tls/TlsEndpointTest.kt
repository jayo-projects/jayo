/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.tls

import jayo.buffered
import jayo.network.NetworkEndpoint
import jayo.network.NetworkServer
import jayo.tls.*
import jayo.tls.helpers.CertificateFactory
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
        private val certificateFactory = CertificateFactory(TlsVersion.TLS_1_3)

        @JvmStatic
        private val sslContext = certificateFactory.defaultContext

        @JvmStatic
        private val sslSocketFactory = sslContext.socketFactory

        @JvmStatic
        private val sslServerSocketFactory = sslContext.serverSocketFactory

        @JvmStatic
        private val clientHandshakeCertificates = certificateFactory.clientHandshakeCertificates

        @JvmStatic
        private val serverHandshakeCertificates = certificateFactory.serverHandshakeCertificates

        @JvmStatic
        private val localhost = InetAddress.getByName(null)
    }

    @Test
    fun defaultTlsClientConfig() {
        sslServerSocketFactory.createServerSocket(0 /* find free port */).use { listener ->
            val serverThread = thread(start = true) {
                listener.accept().use { serverSocket ->
                    serverSocket.outputStream.write(42)
                }
            }
            val address = InetSocketAddress(localhost, listener.localPort)
            val encryptedEndpoint = NetworkEndpoint.connectTcp(address)
            assertThatThrownBy { ClientTlsEndpoint.create(encryptedEndpoint) }
                .isInstanceOf(JayoTlsException::class.java)
                .hasMessageStartingWith("PKIX path building failed")
            serverThread.join(1)
        }
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
            ClientTlsEndpoint.builder(clientHandshakeCertificates).kotlin {
                sessionInitCallback = { sslSession = it }
                waitForCloseConfirmation = true
            }.build(encryptedEndpoint).use { tlsClient ->
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
    fun handshakeCertificatesClientTlsEndpoint() {
        sslServerSocketFactory.createServerSocket(0 /* find free port */).use { listener ->
            val serverThread = thread(start = true) {
                listener.accept().use { serverSocket ->
                    serverSocket.outputStream.write(42)
                }
            }
            val address = InetSocketAddress(localhost, listener.localPort)
            val encryptedEndpoint = NetworkEndpoint.connectTcp(address)
            ClientTlsEndpoint.builder(clientHandshakeCertificates).build(encryptedEndpoint).use { tlsClient ->
                assertThat(tlsClient.handshakeCertificates.keyManager)
                    .isSameAs(clientHandshakeCertificates.keyManager)
                assertThat(tlsClient.handshakeCertificates.trustManager)
                    .isSameAs(clientHandshakeCertificates.trustManager)
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
            assertThatThrownBy {
                ClientTlsEndpoint.builder(clientHandshakeCertificates).kotlin {
                    sessionInitCallback = { throw Exception() }
                }.build(encryptedEndpoint)
            }
                .isInstanceOf(JayoTlsHandshakeCallbackException::class.java)
                .hasMessage("Session initialization callback failed")
            serverThread.join()
        }
    }

    @Test
    fun fullTlsServerEndpointBuilder() {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */)).use { listener ->
            var sslSession: SSLSession? = null
            var handshake: Handshake? = null
            val serverThread = thread(start = true) {
                listener.accept().use { serverEndpoint ->
                    ServerTlsEndpoint.builder(serverHandshakeCertificates).kotlin {
                        sessionInitCallback = { sslSession = it }
                        waitForCloseConfirmation = true
                        engineFactory = { it.createSSLEngine().apply { useClientMode = false } }
                    }.build(serverEndpoint).use { tlsServer ->
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
            assertThat(handshake.localPrincipal).isNotNull
            assertThat(handshake.peerCertificates).isEmpty()
            assertThat(handshake.peerPrincipal).isNull()
        }
    }

    @Test
    fun handshakeCertificatesServerTlsEndpoint() {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */)).use { listener ->
            var resolvedServerHandshakeCertificates: ServerHandshakeCertificates? = null
            val serverThread = thread(start = true) {
                listener.accept().use { serverEndpoint ->
                    ServerTlsEndpoint.builder(serverHandshakeCertificates).build(serverEndpoint).use { tlsServer ->
                        resolvedServerHandshakeCertificates = tlsServer.handshakeCertificates
                    }
                }
            }
            val client = sslSocketFactory.createSocket() as SSLSocket
            client.connect(listener.localAddress)
            client.startHandshake()
            client.shutdownOutput() // needed to respect the 'waitForCloseConfirmation' config of server
            serverThread.join()
            assertThat(resolvedServerHandshakeCertificates!!.trustManager)
                .isSameAs(serverHandshakeCertificates.trustManager)
            assertThat(resolvedServerHandshakeCertificates.trustManager)
                .isSameAs(serverHandshakeCertificates.trustManager)
        }
    }

    @Test
    fun tlsServerEndpointBuilder_ExceptionInEngineFactory() {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */)).use { listener ->
            val serverThread = thread(start = true) {
                listener.accept().use { serverEndpoint ->
                    ServerTlsEndpoint.builder(serverHandshakeCertificates).kotlin {
                        engineFactory = { throw Exception() }
                    }.build(serverEndpoint).use { serverTlsEndpoint ->
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