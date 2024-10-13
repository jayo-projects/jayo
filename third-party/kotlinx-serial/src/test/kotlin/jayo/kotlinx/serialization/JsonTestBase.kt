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

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.assertj.core.api.Assertions.assertThat
import jayo.Buffer

@OptIn(ExperimentalSerializationApi::class)
abstract class JsonTestBase {
    protected val default = Json { encodeDefaults = true }

    internal fun <T> Json.encodeToStringWithJayo(
        serializer: SerializationStrategy<T>,
        value: T
    ): String {
        val buffer = Buffer()
        encodeToWriter(serializer, value, buffer)
        return buffer.readString()
    }

    internal inline fun <reified T : Any> Json.decodeFromStringWithJayo(reader: String): T {
        val deserializer = serializersModule.serializer<T>()
        return decodeFromStringWithJayo(deserializer, reader)
    }

    internal fun <T> Json.decodeFromStringWithJayo(
        deserializer: DeserializationStrategy<T>,
        reader: String,
    ): T {
        val buffer = Buffer()
        buffer.write(reader)
        return decodeFromReader(deserializer, buffer)
    }

    protected open fun parametrizedTest(test: () -> Unit) {
        processResults(buildList {
            add(runCatching { test() })
        })
    }

    private inner class SwitchableJson(
        val json: Json,
        override val serializersModule: SerializersModule = EmptySerializersModule()
    ) : StringFormat {
        override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
            return json.encodeToStringWithJayo(serializer, value)
        }

        override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
            return json.decodeFromStringWithJayo(deserializer, string)
        }
    }

    protected fun parametrizedTest(json: Json, test: StringFormat.() -> Unit) {
        val jayoResult = runCatching { SwitchableJson(json).test() }
        processResults(listOf(jayoResult))
    }

    private fun processResults(results: List<Result<*>>) {
        results.forEach { result ->
            result.onFailure {
                println("Failed test for Jayo")
                throw it
            }
        }
        for (i in results.indices) {
            for (j in results.indices) {
                if (i == j) continue
                assertThat(results[i].getOrNull()!!).isEqualTo(results[j].getOrNull())
            }
        }
    }

    /**
     * Tests both json converters (streaming and tree)
     * via [parametrizedTest]
     */
    internal fun <T> assertJsonFormAndRestored(
        serializer: KSerializer<T>,
        data: T,
        expected: String,
        json: Json = default
    ) {
        parametrizedTest {
            val serialized = json.encodeToStringWithJayo(serializer, data)
            assertThat(serialized).isEqualTo(expected)
            val deserialized: T = json.decodeFromStringWithJayo(serializer, serialized)
            assertThat(deserialized).isEqualTo(data)
        }
    }
}
