/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 * 
 * Forked from kotlinx.serialization : json-okio module
 * (https://github.com/Kotlin/kotlinx.serialization/tree/master/formats/json-okio), original copyright is below
 * 
 * Copyright 2017-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.kotlinx.serialization

import kotlinx.serialization.Serializable

@Serializable
data class IntData(val intV: Int)

@Serializable
data class StringData(val data: String)

enum class SampleEnum { OptionA, OptionB, OptionC }

@Serializable
sealed class SimpleSealed {
    @Serializable
    data class SubSealedA(val s: String) : SimpleSealed()

    @Serializable
    data class SubSealedB(val i: Int) : SimpleSealed()
}

@Serializable
data class DefaultPixelEvent(
    val version: Int,
    val dateTime2: String,
    val serverName: String,
    val domain: String,
    val method: String,
    val clientIp: String,
    val queryString: String,
    val userAgent: String,
    val contentType: String,
    val browserLanguage: String,
    val postData: String,
    val cookies: String
)

@Serializable
class SmallDataClass(val id: Int, val name: String)
