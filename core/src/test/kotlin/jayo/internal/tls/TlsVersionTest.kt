/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from OkHttp (https://github.com/square/okhttp), original copyright is below
 *
 * Copyright (C) 2013 Square, Inc.
 * Copyright (C) 2011 The Guava Authors
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

import jayo.tls.TlsVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class TlsVersionTest {
    @Test
    fun testGetKnown() {
        assertThat(TlsVersion.fromJavaName("TLSv1.3")).isEqualTo(TlsVersion.TLS_1_3)
        assertThat(TlsVersion.fromJavaName("TLSv1.2")).isEqualTo(TlsVersion.TLS_1_2)
        assertThat(TlsVersion.fromJavaName("TLSv1.1")).isEqualTo(TlsVersion.TLS_1_1)
        assertThat(TlsVersion.fromJavaName("TLSv1")).isEqualTo(TlsVersion.TLS_1_0)
        assertThat(TlsVersion.fromJavaName("SSLv3")).isEqualTo(TlsVersion.SSL_3_0)
    }

    @Test
    fun testGetUnknown() {
        assertFailsWith<IllegalArgumentException> { TlsVersion.fromJavaName("TLSv2") }
    }

    @Test
    fun testJavaName() {
        assertThat(TlsVersion.TLS_1_3.javaName).isEqualTo("TLSv1.3")
        assertThat(TlsVersion.TLS_1_2.javaName).isEqualTo("TLSv1.2")
        assertThat(TlsVersion.TLS_1_1.javaName).isEqualTo("TLSv1.1")
        assertThat(TlsVersion.TLS_1_0.javaName).isEqualTo("TLSv1")
        assertThat(TlsVersion.SSL_3_0.javaName).isEqualTo("SSLv3")
    }

    @Test
    fun testToString() {
        assertThat(TlsVersion.TLS_1_3.toString()).isEqualTo("TLSv1.3")
        assertThat(TlsVersion.TLS_1_2.toString()).isEqualTo("TLSv1.2")
        assertThat(TlsVersion.TLS_1_1.toString()).isEqualTo("TLSv1.1")
        assertThat(TlsVersion.TLS_1_0.toString()).isEqualTo("TLSv1")
        assertThat(TlsVersion.SSL_3_0.toString()).isEqualTo("SSLv3")
    }
}
