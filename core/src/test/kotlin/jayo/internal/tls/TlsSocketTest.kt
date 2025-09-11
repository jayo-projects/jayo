/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.tls

import jayo.buffered
import jayo.network.NetworkServer
import jayo.network.NetworkSocket
import jayo.tls.*
import jayo.tls.helpers.CertificateFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

class TlsSocketTest {
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
            val encryptedEndpoint = NetworkSocket.connectTcp(address)
            assertThatThrownBy { ClientTlsSocket.create(encryptedEndpoint) }
                .isInstanceOf(JayoTlsException::class.java)
                .hasMessageStartingWith("PKIX path building failed")
            serverThread.join(1)
        }
    }

    @Test
    fun fullTlsClientSocketBuilder() {
        sslServerSocketFactory.createServerSocket(0 /* find free port */).use { listener ->
            val serverThread = thread(start = true) {
                listener.accept().use { serverSocket ->
                    serverSocket.outputStream.write(42)
                }
            }
            val address = InetSocketAddress(localhost, listener.localPort)
            val encryptedEndpoint = NetworkSocket.connectTcp(address)
            val parameterizer = ClientTlsSocket.builder(clientHandshakeCertificates).kotlin {
                waitForCloseConfirmation = true
            }.createParameterizer(encryptedEndpoint)

            assertThat(parameterizer.enabledProtocols).isEmpty()
            val protocol = Protocol.HTTP_1_1
            parameterizer.enabledProtocols = listOf(protocol)

            val tlsVersion = TlsVersion.TLS_1_3
            assertThat(parameterizer.enabledTlsVersions).contains(tlsVersion)
            parameterizer.enabledTlsVersions = listOf(tlsVersion)

            val cipher = CipherSuite.TLS_CHACHA20_POLY1305_SHA256
            assertThat(parameterizer.supportedCipherSuites).contains(cipher)
            assertThat(parameterizer.enabledCipherSuites).contains(cipher)
            parameterizer.enabledCipherSuites = listOf(cipher)

            val tlsClient = parameterizer.build()
            assertThat(tlsClient.session).isNotNull
            val handshake = tlsClient.handshake
            assertThat(handshake).isNotNull
            assertThat(handshake.localCertificates).isEmpty()
            assertThat(handshake.localPrincipal).isNull()
            assertThat(handshake.peerCertificates).isNotEmpty.hasSize(1)
            assertThat(handshake.peerPrincipal).isNotNull

            assertThat(handshake.protocol).isSameAs(protocol)
            assertThat(handshake.tlsVersion).isEqualTo(tlsVersion)
            assertThat(handshake.cipherSuite).isSameAs(cipher)
            serverThread.join()
        }
    }

    @Test
    fun handshakeCertificatesClientTlsSocket() {
        sslServerSocketFactory.createServerSocket(0 /* find free port */).use { listener ->
            val serverThread = thread(start = true) {
                listener.accept().use { serverSocket ->
                    serverSocket.outputStream.write(42)
                }
            }
            val address = InetSocketAddress(localhost, listener.localPort)
            val encryptedEndpoint = NetworkSocket.connectTcp(address)
            val tlsClient = ClientTlsSocket.builder(clientHandshakeCertificates)
                .build(encryptedEndpoint)
            assertThat(tlsClient.handshakeCertificates.keyManager)
                .isSameAs(clientHandshakeCertificates.keyManager)
            assertThat(tlsClient.handshakeCertificates.trustManager)
                .isSameAs(clientHandshakeCertificates.trustManager)
            serverThread.join()
        }
    }

    @Test
    fun handshakeCertificatesClientTlsSocketBuilder() {
        val builder = ClientTlsSocket.builder(clientHandshakeCertificates)
        assertThat(builder.handshakeCertificates.keyManager)
            .isSameAs(clientHandshakeCertificates.keyManager)
        assertThat(builder.handshakeCertificates.trustManager)
            .isSameAs(clientHandshakeCertificates.trustManager)
    }

    @Test
    fun fullTlsServerSocketBuilder() {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */)).use { listener ->
            var sslSession: SSLSession? = null
            var handshake: Handshake? = null
            val serverThread = thread(start = true) {
                val serverSocket = listener.accept()
                val tlsServer = ServerTlsSocket.builder(serverHandshakeCertificates).kotlin {
                    waitForCloseConfirmation = true
                }.build(serverSocket)
                tlsServer.writer.buffered()
                    .writeInt(42)
                    .flush()
                sslSession = tlsServer.session
                handshake = tlsServer.handshake
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
    fun handshakeCertificatesServerTlsSocket() {
        NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */)).use { listener ->
            var resolvedServerHandshakeCertificates: ServerHandshakeCertificates? = null
            val serverThread = thread(start = true) {
                val serverSocket = listener.accept()
                val tlsServer = ServerTlsSocket.builder(serverHandshakeCertificates)
                    .build(serverSocket)
                resolvedServerHandshakeCertificates = tlsServer.handshakeCertificates
            }
            val client = sslSocketFactory.createSocket() as SSLSocket
            client.connect(listener.localAddress)
            client.startHandshake()
            client.shutdownOutput() // needed to respect the 'waitForCloseConfirmation' config of server
            serverThread.join()
            assertThat(resolvedServerHandshakeCertificates!!.keyManager)
                .isSameAs(serverHandshakeCertificates.keyManager)
            assertThat(resolvedServerHandshakeCertificates.trustManager)
                .isSameAs(serverHandshakeCertificates.trustManager)
        }
    }
}