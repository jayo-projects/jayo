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

import jayo.JayoException
import jayo.tls.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.cert.Certificate
import kotlin.test.assertFailsWith

class HandshakeTest {
    private val serverRoot =
        HeldCertificate.builder()
            .certificateAuthority(1)
            .build()
    private val serverIntermediate =
        HeldCertificate.builder()
            .certificateAuthority(0)
            .signedBy(serverRoot)
            .build()
    private val serverCertificate =
        HeldCertificate.builder()
            .signedBy(serverIntermediate)
            .build()

    @Test
    fun createFromParts() {
        val handshake =
            Handshake.get(
                TlsVersion.TLS_1_3,
                CipherSuite.TLS_AES_128_GCM_SHA256,
                listOf(),
                listOf(serverCertificate.certificate, serverIntermediate.certificate),
            )

        assertThat(handshake.tlsVersion).isEqualTo(TlsVersion.TLS_1_3)
        assertThat(handshake.cipherSuite).isEqualTo(CipherSuite.TLS_AES_128_GCM_SHA256)
        assertThat(handshake.peerCertificates).containsExactly(
            serverCertificate.certificate,
            serverIntermediate.certificate,
        )
        assertThat(handshake.localPrincipal).isNull()
        assertThat(handshake.peerPrincipal)
            .isEqualTo(serverCertificate.certificate.subjectX500Principal)
        assertThat(handshake.localCertificates).isEmpty()
    }

    @Test
    fun createFromSslSession() {
        val sslSession =
            FakeSSLSession(
                "TLSv1.3",
                "TLS_AES_128_GCM_SHA256",
                arrayOf(serverCertificate.certificate, serverIntermediate.certificate),
                null,
            )

        val handshake = sslSession.handshake()

        assertThat(handshake.tlsVersion).isEqualTo(TlsVersion.TLS_1_3)
        assertThat(handshake.cipherSuite).isEqualTo(CipherSuite.TLS_AES_128_GCM_SHA256)
        assertThat(handshake.peerCertificates).containsExactly(
            serverCertificate.certificate,
            serverIntermediate.certificate,
        )
        assertThat(handshake.localPrincipal).isNull()
        assertThat(handshake.peerPrincipal)
            .isEqualTo(serverCertificate.certificate.subjectX500Principal)
        assertThat(handshake.localCertificates).isEmpty()
    }

    @Test
    fun sslWithNullNullNull() {
        val sslSession =
            FakeSSLSession(
                "TLSv1.3",
                "SSL_NULL_WITH_NULL_NULL",
                arrayOf(serverCertificate.certificate, serverIntermediate.certificate),
                null,
            )

        assertFailsWith<JayoException> {
            sslSession.handshake()
        }.also { expected ->
            assertThat(expected).hasMessage("Session's cipherSuite == SSL_NULL_WITH_NULL_NULL")
        }
    }

    @Test
    fun tlsWithNullNullNull() {
        val sslSession =
            FakeSSLSession(
                "TLSv1.3",
                "TLS_NULL_WITH_NULL_NULL",
                arrayOf(serverCertificate.certificate, serverIntermediate.certificate),
                null,
            )

        assertFailsWith<JayoException> {
            sslSession.handshake()
        }.also { expected ->
            assertThat(expected).hasMessage("Session's cipherSuite == TLS_NULL_WITH_NULL_NULL")
        }
    }

    class FakeSSLSession(
        private val protocol: String,
        private val cipherSuite: String,
        private val peerCertificates: Array<Certificate>?,
        private val localCertificates: Array<Certificate>?,
    ) : DelegatingSSLSession(null) {
        override fun getProtocol() = protocol

        override fun getCipherSuite() = cipherSuite

        override fun getPeerCertificates() = peerCertificates

        override fun getLocalCertificates() = localCertificates
    }
}
