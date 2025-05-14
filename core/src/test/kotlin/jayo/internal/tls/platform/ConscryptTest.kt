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

import org.assertj.core.api.Assertions.assertThat
import org.conscrypt.Conscrypt
import org.junit.jupiter.api.Test

class ConscryptTest {
    @Test
    fun testBuildIfSupported() {
        val actual = ConscryptJssePlatform.buildIfSupported()
        assertThat(actual).isNotNull()
    }

    @Test
    fun testVersion() {
        val version = Conscrypt.version()

        assertThat(ConscryptJssePlatform.atLeastVersion(1, 4, 9)).isTrue()
        assertThat(
            ConscryptJssePlatform.atLeastVersion(
                version.major(),
                version.minor(),
                version.patch()
            )
        ).isTrue()
        assertThat(
            ConscryptJssePlatform.atLeastVersion(
                version.major(),
                version.minor(),
                version.patch() + 1
            )
        ).isFalse()
    }
}