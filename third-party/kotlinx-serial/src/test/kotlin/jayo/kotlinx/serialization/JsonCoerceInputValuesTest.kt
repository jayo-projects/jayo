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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JsonCoerceInputValuesTest : JsonTestBase() {
    @Serializable
    data class WithBoolean(val b: Boolean = false)

    @Serializable
    data class WithEnum(val e: SampleEnum = SampleEnum.OptionC)

    @Serializable
    data class MultipleValues(
        val data: StringData,
        val data2: IntData = IntData(0),
        val i: Int = 42,
        val e: SampleEnum = SampleEnum.OptionA,
        val foo: String
    )

    @Serializable
    data class NullableEnumHolder(
        val enum: SampleEnum?
    )

    val json = Json {
        coerceInputValues = true
        isLenient = true
    }

    private fun <T> doTest(inputs: List<String>, expected: T, serializer: KSerializer<T>) {
        for (input in inputs) {
            parametrizedTest(json) {
                assertThat(decodeFromString(serializer, input)).isEqualTo(expected)
            }
        }
    }

    @Test
    fun testUseDefaultOnNonNullableBoolean() = doTest(
        listOf(
            """{"b":false}""",
            """{"b":null}""",
            """{}""",
        ),
        WithBoolean(),
        WithBoolean.serializer()
    )

    @Test
    fun testUseDefaultOnUnknownEnum() {
        doTest(
            listOf(
                """{"e":unknown_value}""",
                """{"e":"unknown_value"}""",
                """{"e":null}""",
                """{}""",
            ),
            WithEnum(),
            WithEnum.serializer()
        )
        assertFailsWithSerial("JsonDecodingException") {
            json.decodeFromStringWithJayo(WithEnum.serializer(), """{"e":{"x":"definitely not a valid enum value"}}""")
        }
        assertFailsWithSerial("JsonDecodingException") { // test user still sees exception on missing quotes
            Json(json) { isLenient = false }.decodeFromStringWithJayo(WithEnum.serializer(), """{"e":unknown_value}""")
        }
    }

    @Test
    fun testUseDefaultInMultipleCases() {
        val testData = mapOf(
            """{"data":{"data":"foo"},"data2":null,"i":null,"e":null,"foo":"bar"}""" to MultipleValues(
                StringData("foo"),
                foo = "bar"
            ),
            """{"data":{"data":"foo"},"data2":{"intV":42},"i":null,"e":null,"foo":"bar"}""" to MultipleValues(
                StringData(
                    "foo"
                ), IntData(42), foo = "bar"
            ),
            """{"data":{"data":"foo"},"data2":{"intV":42},"i":0,"e":"NoOption","foo":"bar"}""" to MultipleValues(
                StringData("foo"),
                IntData(42),
                i = 0,
                foo = "bar"
            ),
            """{"data":{"data":"foo"},"data2":{"intV":42},"i":0,"e":"OptionC","foo":"bar"}""" to MultipleValues(
                StringData("foo"),
                IntData(42),
                i = 0,
                e = SampleEnum.OptionC,
                foo = "bar"
            ),
        )
        for ((input, expected) in testData) {
            assertThat(json.decodeFromStringWithJayo(MultipleValues.serializer(), input)).isEqualTo(expected)
        }
    }

    @Test
    fun testNullSupportForEnums() = parametrizedTest(json) {
        var decoded = decodeFromString<NullableEnumHolder>("""{"enum": null}""")
        assertThat(decoded.enum).isNull()

        decoded = decodeFromString("""{"enum": OptionA}""")
        assertThat(decoded.enum).isEqualTo(SampleEnum.OptionA)
    }
}
