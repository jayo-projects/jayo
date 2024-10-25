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

import jayo.tls.JssePlatform
import jayo.tls.PlatformRule
import jayo.tls.PlatformVersion
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Validates which environment is used by the IDE.
 */
class PlatformRuleTest {
    @RegisterExtension
    @JvmField
    val platform = PlatformRule()

    @Test
    fun testMode() {
        println(PlatformRule.getPlatformSystemProperty())
        println(JssePlatform.get().javaClass.simpleName)
    }

    @Test
    fun testGreenCase() {
    }

    @Test
    fun testGreenCaseFailingOnLater() {
        platform.expectFailureFromJdkVersion(PlatformVersion.majorVersion + 1)
    }

    @Test
    fun failureCase() {
        platform.expectFailureFromJdkVersion(PlatformVersion.majorVersion)

        check(false)
    }
}
