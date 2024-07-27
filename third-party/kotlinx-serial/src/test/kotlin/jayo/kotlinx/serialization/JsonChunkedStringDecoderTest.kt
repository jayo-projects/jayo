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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.ChunkedDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


@Serializable(with = LargeStringSerializer::class)
data class LargeStringData(val largeString: String)

@Serializable
data class ClassWithLargeStringDataField(val largeStringField: LargeStringData)


object LargeStringSerializer : KSerializer<LargeStringData> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LargeStringContent", PrimitiveKind.STRING)

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): LargeStringData {
        require(decoder is ChunkedDecoder) { "Only chunked decoder supported" }

        val outStringBuilder = StringBuilder()

        decoder.decodeStringChunked { chunk ->
            outStringBuilder.append(chunk)
        }
        return LargeStringData(outStringBuilder.toString())
    }

    override fun serialize(encoder: Encoder, value: LargeStringData) {
        encoder.encodeString(value.largeString)
    }
}

open class JsonChunkedStringDecoderTest : JsonTestBase() {

    @Test
    fun decodePlainLenientString() {
        val longString = "abcd".repeat(8192) // Make string more than 16k
        val readerObject = ClassWithLargeStringDataField(LargeStringData(longString))
        val serializedObject = "{\"largeStringField\": $longString }"
        val jsonWithLenientMode = Json { isLenient = true }
        testDecodeInAllModes(jsonWithLenientMode, serializedObject, readerObject)
    }

    @Test
    fun decodePlainString() {
        val longStringWithEscape = "${"abcd".repeat(4096)}\"${"abcd".repeat(4096)}" // Make string more than 16k
        val readerObject = ClassWithLargeStringDataField(LargeStringData(longStringWithEscape))
        val serializedObject = Json.encodeToString(readerObject)
        testDecodeInAllModes(Json, serializedObject, readerObject)
    }

    private fun testDecodeInAllModes(
        seralizer: Json, serializedObject: String, readerObject: ClassWithLargeStringDataField
    ) {
        val deserializedObject =
            seralizer.decodeFromStringWithJayo<ClassWithLargeStringDataField>(serializedObject)
        assertThat(deserializedObject.largeStringField).isEqualTo(readerObject.largeStringField)
    }
}
