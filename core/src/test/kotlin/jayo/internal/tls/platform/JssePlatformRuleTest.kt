/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
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

package jayo.internal.tls.platform

import jayo.tls.JssePlatform
import jayo.tls.JssePlatformRule
import org.assertj.core.api.Assertions.assertThat
import org.conscrypt.Conscrypt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Validates Conscrypt and BouncyCastle are working.
 */
class JssePlatformRuleTest {
    @JvmField
    @RegisterExtension
    val platform = JssePlatformRule()

    @Test
    fun testConscryptTrustManager() {
        platform.assumeConscrypt()
        assertThat(Conscrypt.isConscrypt(JssePlatform.get().defaultTrustManager)).isTrue()
    }

    @Test
    fun testBouncyCastle() {
        platform.assumeBouncyCastle()
        assertThat(Conscrypt.isConscrypt(JssePlatform.get().defaultTrustManager)).isFalse()
    }
}