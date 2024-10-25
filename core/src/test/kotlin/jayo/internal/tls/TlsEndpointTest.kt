/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.tls

import jayo.buffered
import jayo.endpoints.SocketEndpoint
import jayo.endpoints.endpoint
import jayo.tls.JayoTlsHandshakeCallbackException
import jayo.tls.TlsEndpoint
import jayo.tls.build
import jayo.tls.helpers.SslContextFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket

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
            val serverThread = Thread.ofVirtual().start {
                listener.accept().use { serverSocket ->
                    serverSocket.outputStream.write(42)
                }
            }
            val address = InetSocketAddress(localhost, listener.localPort)
            val rawClient = Socket()
            rawClient.connect(address)
            val encryptedEndpoint = SocketEndpoint.from(rawClient)
            var sslSession: SSLSession? = null
            TlsEndpoint.clientBuilder(encryptedEndpoint, sslContext).build {
                sessionInitCallback = { sslSession = it }
                waitForCloseConfirmation = true
            }.use { clientTlsEndpoint ->
                clientTlsEndpoint.handshake()
            }
            serverThread.join()
            assertThat(sslSession).isNotNull
        }
    }

    @Test
    fun tlsClientEndpointBuilder_ExceptionInSessionInitCallback() {
        sslServerSocketFactory.createServerSocket(0 /* find free port */).use { listener ->
            val serverThread = Thread.ofVirtual().start {
                listener.accept().use { serverSocket ->
                    serverSocket.outputStream.write(42)
                }
            }
            val address = InetSocketAddress(localhost, listener.localPort)
            val rawClient = Socket()
            rawClient.connect(address)
            val encryptedEndpoint = SocketEndpoint.from(rawClient)
            TlsEndpoint.clientBuilder(encryptedEndpoint, sslContext).build {
                sessionInitCallback = { throw Exception() }
            }.use { clientTlsEndpoint ->
                assertThatThrownBy { clientTlsEndpoint.handshake() }
                    .isInstanceOf(JayoTlsHandshakeCallbackException::class.java)
                    .hasMessage("Session initialization callback failed")
            }
            serverThread.join()
        }
    }

    @Test
    fun tlsClientEndpoint_EngineInServerMode() {
        sslServerSocketFactory.createServerSocket(0 /* find free port */).use { listener ->
            val address = InetSocketAddress(localhost, listener.localPort)
            val rawClient = Socket()
            rawClient.connect(address)
            val encryptedEndpoint = SocketEndpoint.from(rawClient)
            assertThatThrownBy { TlsEndpoint.clientBuilder(encryptedEndpoint, sslContext.createSSLEngine()).build() }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("The provided SSL engine must use client mode")
        }
    }

    @Test
    fun fullTlsServerEndpointBuilder() {
        ServerSocket(0 /* find free port */).use { listener ->
            var sslSession: SSLSession? = null
            val serverThread = Thread.ofVirtual().start {
                listener.accept().use { serverSocket ->
                    TlsEndpoint.serverBuilder(serverSocket.endpoint(), sslContext).build {
                        sessionInitCallback = { sslSession = it }
                        waitForCloseConfirmation = true
                        engineFactory = { it.createSSLEngine().apply { useClientMode = false } }
                    }.use { serverTlsEndpoint ->
                        serverTlsEndpoint.writer.buffered()
                            .writeInt(42)
                            .flush()
                    }
                }
            }
            val address = InetSocketAddress(localhost, listener.localPort)
            val client = sslSocketFactory.createSocket() as SSLSocket
            client.connect(address)
            client.startHandshake()
            client.shutdownOutput() // needed to respect the 'waitForCloseConfirmation' config of server
            serverThread.join()
            assertThat(sslSession).isNotNull
        }
    }

    @Test
    fun tlsServerEndpointBuilder_ExceptionInEngineFactory() {
        ServerSocket(0 /* find free port */).use { listener ->
            val serverThread = Thread.ofVirtual().start {
                listener.accept().use { serverSocket ->
                    TlsEndpoint.serverBuilder(serverSocket.endpoint(), sslContext).build {
                        engineFactory = { throw Exception() }
                    }.use { serverTlsEndpoint ->
                        assertThatThrownBy {
                            serverTlsEndpoint.writer.buffered()
                                .writeInt(42)
                                .flush()
                        }.isInstanceOf(JayoTlsHandshakeCallbackException::class.java)
                            .hasMessage("SSLEngine creation callback failed")
                    }
                }
            }
            val address = InetSocketAddress(localhost, listener.localPort)
            val client = sslSocketFactory.createSocket() as SSLSocket
            client.connect(address)
            assertThatThrownBy { client.startHandshake() }.isInstanceOf(IOException::class.java)
            serverThread.join()
        }
    }
}