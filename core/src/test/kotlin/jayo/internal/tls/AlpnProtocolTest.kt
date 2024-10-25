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

import jayo.JayoException
import jayo.tls.AlpnProtocol
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class AlpnProtocolTest {
    @Test
    fun testGetKnown() {
        assertThat(AlpnProtocol.get("http/1.0")).isEqualTo(AlpnProtocol.HTTP_1_0)
        assertThat(AlpnProtocol.get("http/1.1")).isEqualTo(AlpnProtocol.HTTP_1_1)
        assertThat(AlpnProtocol.get("h2")).isEqualTo(AlpnProtocol.HTTP_2)
        assertThat(AlpnProtocol.get("h2_prior_knowledge"))
            .isEqualTo(AlpnProtocol.H2_PRIOR_KNOWLEDGE)
        assertThat(AlpnProtocol.get("quic")).isEqualTo(AlpnProtocol.QUIC)
        assertThat(AlpnProtocol.get("h3")).isEqualTo(AlpnProtocol.HTTP_3)
        assertThat(AlpnProtocol.get("h3-29")).isEqualTo(AlpnProtocol.HTTP_3)
    }

    @Test
    fun testGetUnknown() {
        assertFailsWith<JayoException> { AlpnProtocol.get("tcp") }
    }

    @Test
    fun testToString() {
        assertThat(AlpnProtocol.HTTP_1_0.toString()).isEqualTo("http/1.0")
        assertThat(AlpnProtocol.HTTP_1_1.toString()).isEqualTo("http/1.1")
        assertThat(AlpnProtocol.HTTP_2.toString()).isEqualTo("h2")
        assertThat(AlpnProtocol.H2_PRIOR_KNOWLEDGE.toString())
            .isEqualTo("h2_prior_knowledge")
        assertThat(AlpnProtocol.QUIC.toString()).isEqualTo("quic")
        assertThat(AlpnProtocol.HTTP_3.toString()).isEqualTo("h3")
    }
}
