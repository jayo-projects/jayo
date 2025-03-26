/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from OkHttp (https://github.com/square/okhttp), original copyright is below
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

package jayo.internal.tls

import jayo.bytestring.toByteString
import jayo.internal.JavaVersionUtils.threadFactory
import jayo.tls.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.PrivateKey
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.net.ServerSocketFactory
import javax.net.SocketFactory
import javax.net.ssl.SSLSocket

class HandshakeCertificatesTest {
    @RegisterExtension
    var platform = PlatformRule()

    private lateinit var executorService: ExecutorService

    private var serverSocket: ServerSocket? = null

    @BeforeEach
    fun setUp() {
        executorService = Executors.newCachedThreadPool(threadFactory("HandshakeCertificatesTest"))
    }

    @AfterEach
    fun tearDown() {
        executorService.shutdown()
        serverSocket?.close()
    }

    @Test
    fun keyManager() {
        val root =
            HeldCertificate.builder()
                .certificateAuthority(1)
                .build()
        val intermediate =
            HeldCertificate.builder()
                .certificateAuthority(0)
                .signedBy(root)
                .build()
        val certificate =
            HeldCertificate.builder()
                .signedBy(intermediate)
                .build()
        val handshakeCertificates =
            ClientHandshakeCertificates.builder()
                .addTrustedCertificate(root.certificate) // BouncyCastle requires at least one
                .heldCertificate(certificate, intermediate.certificate)
                .build()
        assertPrivateKeysEquals(
            certificate.keyPair.private,
            handshakeCertificates.keyManager!!.getPrivateKey("private"),
        )
        assertThat(handshakeCertificates.keyManager!!.getCertificateChain("private").toList())
            .isEqualTo(listOf(certificate.certificate, intermediate.certificate))
    }

    @Test
    fun platformTrustedCertificates() {
        // default = all platform
        var handshakeCertificates =
            ClientHandshakeCertificates.builder()
                .build()
        var acceptedIssuers = handshakeCertificates.trustManager.acceptedIssuers
        assertThat(acceptedIssuers).isNotEmpty()
        val names =
            acceptedIssuers
                .map { it.subjectX500Principal.name }
                .toSet()

        // It's safe to assume all platforms will have a major Internet certificate issuer.
        assertThat(names).matches { strings ->
            strings.any { it.matches(Regex("[A-Z]+=Entrust.*")) }
        }

        handshakeCertificates =
            ClientHandshakeCertificates.builder()
                .addPlatformTrustedCertificates(false)
                .build()
        acceptedIssuers = handshakeCertificates.trustManager.acceptedIssuers
        assertThat(acceptedIssuers).isEmpty()
    }

    @Test
    fun clientAndServer() {
        platform.assumeNotConscrypt()
        platform.assumeNotBouncyCastle()

        val clientRoot =
            HeldCertificate.builder()
                .certificateAuthority(1)
                .build()
        val clientIntermediate =
            HeldCertificate.builder()
                .certificateAuthority(0)
                .signedBy(clientRoot)
                .build()
        val clientCertificate =
            HeldCertificate.builder()
                .signedBy(clientIntermediate)
                .build()
        val serverRoot =
            HeldCertificate.builder()
                .certificateAuthority(1)
                .build()
        val serverIntermediate =
            HeldCertificate.builder()
                .certificateAuthority(0)
                .signedBy(serverRoot)
                .build()
        val serverCertificate =
            HeldCertificate.builder()
                .signedBy(serverIntermediate)
                .build()
        val server =
            ServerHandshakeCertificates.builder(serverCertificate, serverIntermediate.certificate)
                .addTrustedCertificate(clientRoot.certificate)
                .build()
        val client =
            ClientHandshakeCertificates.builder()
                .addTrustedCertificate(serverRoot.certificate)
                .heldCertificate(clientCertificate, clientIntermediate.certificate)
                .build()
        val serverAddress = startTlsServer()
        val serverHandshakeFuture = doServerHandshake(server)
        val clientHandshakeFuture = doClientHandshake(client, serverAddress)
        val serverHandshake = serverHandshakeFuture.get()
        assertThat(listOf(clientCertificate.certificate, clientIntermediate.certificate))
            .isEqualTo(serverHandshake.peerCertificates)
        assertThat(listOf(serverCertificate.certificate, serverIntermediate.certificate))
            .isEqualTo(serverHandshake.localCertificates)
        val clientHandshake = clientHandshakeFuture.get()
        assertThat(listOf(serverCertificate.certificate, serverIntermediate.certificate))
            .isEqualTo(clientHandshake.peerCertificates)
        assertThat(listOf(clientCertificate.certificate, clientIntermediate.certificate))
            .isEqualTo(clientHandshake.localCertificates)
    }

    private fun startTlsServer(): InetSocketAddress {
        val serverSocketFactory = ServerSocketFactory.getDefault()
        serverSocket = serverSocketFactory.createServerSocket()
        val serverAddress = InetAddress.getByName("localhost")
        serverSocket!!.bind(InetSocketAddress(serverAddress, 0), 50)
        return InetSocketAddress(serverAddress, serverSocket!!.localPort)
    }

    private fun doServerHandshake(server: ServerHandshakeCertificates): Future<Handshake> {
        return executorService.submit<Handshake> {
            serverSocket!!.accept().use { rawSocket ->
                val sslSocket =
                    (server as RealHandshakeCertificates).sslContext().socketFactory.createSocket(
                        rawSocket,
                        rawSocket.inetAddress.hostAddress,
                        rawSocket.port,
                        true,
                    ) as SSLSocket
                sslSocket.use {
                    sslSocket.useClientMode = false
                    sslSocket.wantClientAuth = true
                    sslSocket.startHandshake()
                    return@submit sslSocket.session.handshake()
                }
            }
        }
    }

    private fun doClientHandshake(
        client: ClientHandshakeCertificates,
        serverAddress: InetSocketAddress,
    ): Future<Handshake> {
        return executorService.submit<Handshake> {
            SocketFactory.getDefault().createSocket().use { rawSocket ->
                rawSocket.connect(serverAddress)
                val sslSocket =
                    (client as RealHandshakeCertificates).sslContext().socketFactory.createSocket(
                        rawSocket,
                        rawSocket.inetAddress.hostAddress,
                        rawSocket.port,
                        true,
                    ) as SSLSocket
                sslSocket.use {
                    sslSocket.startHandshake()
                    return@submit sslSocket.session.handshake()
                }
            }
        }
    }

    private fun assertPrivateKeysEquals(
        expected: PrivateKey,
        actual: PrivateKey,
    ) {
        assertThat(actual.encoded.toByteString()).isEqualTo(expected.encoded.toByteString())
    }
}
