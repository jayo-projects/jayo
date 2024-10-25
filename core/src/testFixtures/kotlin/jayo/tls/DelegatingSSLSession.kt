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

@file:Suppress("removal", "deprecation")

package jayo.tls

import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSessionContext
import javax.security.cert.X509Certificate

/** A [SSLSession] that delegates all calls.  */
abstract class DelegatingSSLSession(protected val delegate: SSLSession?) : SSLSession {
    override fun getId(): ByteArray {
        return delegate!!.id
    }

    override fun getSessionContext(): SSLSessionContext {
        return delegate!!.sessionContext
    }

    override fun getCreationTime(): Long {
        return delegate!!.creationTime
    }

    override fun getLastAccessedTime(): Long {
        return delegate!!.lastAccessedTime
    }

    override fun invalidate() {
        delegate!!.invalidate()
    }

    override fun isValid(): Boolean {
        return delegate!!.isValid
    }

    override fun putValue(
        s: String,
        o: Any,
    ) {
        delegate!!.putValue(s, o)
    }

    override fun getValue(s: String): Any {
        return delegate!!.getValue(s)
    }

    override fun removeValue(s: String) {
        delegate!!.removeValue(s)
    }

    override fun getValueNames(): Array<String> {
        return delegate!!.valueNames
    }

    @Throws(SSLPeerUnverifiedException::class)
    override fun getPeerCertificates(): Array<Certificate>? {
        return delegate!!.peerCertificates
    }

    override fun getLocalCertificates(): Array<Certificate>? {
        return delegate!!.localCertificates
    }

    @Throws(SSLPeerUnverifiedException::class)
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getPeerCertificateChain(): Array<X509Certificate> {
        return delegate!!.peerCertificateChain
    }

    @Throws(SSLPeerUnverifiedException::class)
    override fun getPeerPrincipal(): Principal {
        return delegate!!.peerPrincipal
    }

    override fun getLocalPrincipal(): Principal {
        return delegate!!.localPrincipal
    }

    override fun getCipherSuite(): String {
        return delegate!!.cipherSuite
    }

    override fun getProtocol(): String {
        return delegate!!.protocol
    }

    override fun getPeerHost(): String {
        return delegate!!.peerHost
    }

    override fun getPeerPort(): Int {
        return delegate!!.peerPort
    }

    override fun getPacketBufferSize(): Int {
        return delegate!!.packetBufferSize
    }

    override fun getApplicationBufferSize(): Int {
        return delegate!!.applicationBufferSize
    }
}
