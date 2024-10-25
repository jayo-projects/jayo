/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from OkHttp (https://github.com/square/okhttp), original copyright is below
 *
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jayo.internal.tls

import jayo.tls.CipherSuite
import jayo.tls.CipherSuite.fromJavaName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CipherSuiteTest {
    @Test
    fun hashCode_usesIdentityHashCode_legacyCase() {
        val cs = CipherSuite.TLS_RSA_EXPORT_WITH_RC4_40_MD5 // This one's javaName starts with "SSL_".
        assertThat(cs.hashCode()).isEqualTo(System.identityHashCode(cs))
    }

    @Test
    fun hashCode_usesIdentityHashCode_regularCase() {
        // This one's javaName matches the identifier.
        val cs = CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256
        assertThat(cs.hashCode()).isEqualTo(System.identityHashCode(cs))
    }

    @Test
    fun instancesAreInterned() {
        assertThat(fromJavaName("TestCipherSuite"))
            .isSameAs(fromJavaName("TestCipherSuite"))
        assertThat(fromJavaName(CipherSuite.TLS_KRB5_WITH_DES_CBC_MD5.javaName))
            .isSameAs(CipherSuite.TLS_KRB5_WITH_DES_CBC_MD5)
    }

    /**
     * Tests that interned CipherSuite instances remain the case across garbage collections, even if
     * the String used to construct them is no longer strongly referenced outside of the CipherSuite.
     */
    @Test
    fun instancesAreInterned_survivesGarbageCollection() {
        // We're not holding onto a reference to this String instance outside of the CipherSuite...
        val cs = fromJavaName("FakeCipherSuite_instancesAreInterned")
        System.gc() // Unless cs references the String instance, it may now be garbage collected.
        assertThat(fromJavaName(java.lang.String(cs.javaName) as String))
            .isSameAs(cs)
    }

    @Test
    fun equals() {
        assertThat(fromJavaName("cipher"))
            .isEqualTo(fromJavaName("cipher"))
        assertThat(fromJavaName("cipherB"))
            .isNotEqualTo(fromJavaName("cipherA"))
        assertThat(CipherSuite.TLS_RSA_EXPORT_WITH_RC4_40_MD5)
            .isEqualTo(fromJavaName("SSL_RSA_EXPORT_WITH_RC4_40_MD5"))
        assertThat(CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256)
            .isNotEqualTo(CipherSuite.TLS_RSA_EXPORT_WITH_RC4_40_MD5)
    }

    @Test
    fun fromJavaName_acceptsArbitraryStrings() {
        // Shouldn't throw.
        fromJavaName("example CipherSuite name that is not in the whitelist")
    }

    @Test
    fun javaName_examples() {
        assertThat(CipherSuite.TLS_RSA_EXPORT_WITH_RC4_40_MD5.javaName)
            .isEqualTo("SSL_RSA_EXPORT_WITH_RC4_40_MD5")
        assertThat(CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256.javaName)
            .isEqualTo("TLS_RSA_WITH_AES_128_CBC_SHA256")
        assertThat(fromJavaName("TestCipherSuite").javaName)
            .isEqualTo("TestCipherSuite")
    }

    @Test
    fun javaName_equalsToString() {
        assertThat(CipherSuite.TLS_RSA_EXPORT_WITH_RC4_40_MD5.toString())
            .isEqualTo(CipherSuite.TLS_RSA_EXPORT_WITH_RC4_40_MD5.javaName)
        assertThat(CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256.toString())
            .isEqualTo(CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256.javaName)
    }

    /**
     * On the Oracle JVM some older cipher suites have the "SSL_" prefix and others have the "TLS_"
     * prefix. On the IBM JVM all cipher suites have the "SSL_" prefix.
     */
    @Test
    fun fromJavaName_fromLegacyEnumName() {
        // These would have been considered equal in OkHttp 3.3.1, but now aren't.
        assertThat(fromJavaName("SSL_RSA_EXPORT_WITH_RC4_40_MD5"))
            .isEqualTo(fromJavaName("TLS_RSA_EXPORT_WITH_RC4_40_MD5"))
        assertThat(fromJavaName("SSL_DH_RSA_EXPORT_WITH_DES40_CBC_SHA"))
            .isEqualTo(fromJavaName("TLS_DH_RSA_EXPORT_WITH_DES40_CBC_SHA"))
        assertThat(fromJavaName("SSL_FAKE_NEW_CIPHER"))
            .isEqualTo(fromJavaName("TLS_FAKE_NEW_CIPHER"))
    }
}
