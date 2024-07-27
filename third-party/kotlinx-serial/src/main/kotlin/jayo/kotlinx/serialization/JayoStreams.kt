/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 * 
 * Forked from kotlinx.serialization : json-okio module
 * (https://github.com/Kotlin/kotlinx.serialization/tree/master/formats/json-okio), original copyright is below
 * 
 * Copyright 2017-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package jayo.kotlinx.serialization

import kotlinx.serialization.*
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.internal.decodeByReader
import kotlinx.serialization.json.internal.decodeToSequenceByReader
import kotlinx.serialization.json.internal.encodeByWriter
import jayo.Writer
import jayo.Reader
import jayo.kotlinx.serialization.internal.JayoSerialReader
import jayo.kotlinx.serialization.internal.JsonToJayoStreamWriter

/**
 * Serializes the [value] with [serializer] into the [writer] writer using JSON format and UTF-8 encoding.
 *
 * @throws [SerializationException] if the given value cannot be serialized to JSON.
 * @throws [jayo.exceptions.JayoException] If an I/O error occurs and writer can't be written to.
 */
@ExperimentalSerializationApi
public fun <T> Json.encodeToWriter(
    serializer: SerializationStrategy<T>,
    value: T,
    writer: Writer
) {
    val jsonWriter = JsonToJayoStreamWriter(writer)
    try {
        encodeByWriter(this, jsonWriter, serializer, value)
    } finally {
        jsonWriter.release()
    }
}

/**
 * Serializes given [value] into the [writer] writer using JSON format and UTF-8 encoding, and serializer retrieved from
 * the reified type parameter.
 *
 * @throws [SerializationException] if the given value cannot be serialized to JSON.
 * @throws [jayo.exceptions.JayoException] If an I/O error occurs and writer can't be written to.
 */
@ExperimentalSerializationApi
public inline fun <reified T> Json.encodeToWriter(
    value: T,
    writer: Writer
): Unit = encodeToWriter(serializersModule.serializer(), value, writer)


/**
 * Deserializes JSON from [reader] using UTF-8 encoding to a value of type [T] using [deserializer].
 *
 * Note that this functions expects that exactly one object would be present in the reader and throws an exception if
 * there are any dangling bytes after an object.
 *
 * @throws [SerializationException] if the given JSON input cannot be deserialized to the value of type [T].
 * @throws [jayo.exceptions.JayoException] If an I/O error occurs and reader can't be read from.
 */
@ExperimentalSerializationApi
public fun <T> Json.decodeFromReader(
    deserializer: DeserializationStrategy<T>,
    reader: Reader
): T {
    return decodeByReader(this, deserializer, JayoSerialReader(reader))
}

/**
 * Deserializes the contents of given [reader] to the value of type [T] using UTF-8 encoding and deserializer retrieved
 * from the reified type parameter.
 *
 * Note that this functions expects that exactly one object would be present in the stream and throws an exception if
 * there are any dangling bytes after an object.
 *
 * @throws [SerializationException] if the given JSON input cannot be deserialized to the value of type [T].
 * @throws [jayo.exceptions.JayoException] If an I/O error occurs and reader can't be read from.
 */
@ExperimentalSerializationApi
public inline fun <reified T> Json.decodeFromReader(reader: Reader): T =
    decodeFromReader(serializersModule.serializer(), reader)


/**
 * Transforms the given [reader] into lazily deserialized sequence of elements of type [T] using JSON format and UTF-8
 * encoding and [deserializer].
 * 
 * Unlike [decodeFromReader], [reader] is allowed to have more than one element, separated as [format] declares.
 *
 * Elements must all be of type [T].
 * Elements are parsed lazily when resulting [Sequence] is evaluated.
 * Resulting sequence is tied to the stream and can be evaluated only once.
 *
 * **Resource caution:** this method neither closes the [reader] when the parsing is finished nor provides a method to
 * close it manually. It is a caller responsibility to hold a reference to a reader and close it. Moreover, because
 * reader is parsed lazily, closing it before returned sequence is evaluated completely will result in [Exception] from
 * decoder.
 *
 * @throws [SerializationException] if the given JSON input cannot be deserialized to the value of type [T].
 * @throws [jayo.exceptions.JayoException] If an I/O error occurs and reader can't be read from.
 */
@ExperimentalSerializationApi
public fun <T> Json.decodeReaderToSequence(
    reader: Reader,
    deserializer: DeserializationStrategy<T>,
    format: DecodeSequenceMode = DecodeSequenceMode.AUTO_DETECT
): Sequence<T> {
    return decodeToSequenceByReader(this, JayoSerialReader(reader), deserializer, format)
}

/**
 * Transforms the given [reader] into lazily deserialized sequence of elements of type [T] using JSON format and UTF-8
 * encoding and deserializer retrieved from the reified type parameter.
 * 
 * Unlike [decodeFromReader], [reader] is allowed to have more than one element, separated as [format] declares.
 *
 * Elements must all be of type [T].
 * Elements are parsed lazily when resulting [Sequence] is evaluated.
 * Resulting sequence is tied to the stream and constrained to be evaluated only once.
 *
 * **Resource caution:** this method neither closes the [reader] when the parsing is finished nor provides a method to
 * close it manually. It is a caller responsibility to hold a reference to a reader and close it. Moreover, because
 * reader is parsed lazily, closing it before returned sequence is evaluated completely will result in [Exception] from
 * decoder.
 *
 * @throws [SerializationException] if the given JSON input cannot be deserialized to the value of type [T].
 * @throws [jayo.exceptions.JayoException] If an I/O error occurs and reader can't be read from.
 */
@ExperimentalSerializationApi
public inline fun <reified T> Json.decodeReaderToSequence(
    reader: Reader,
    format: DecodeSequenceMode = DecodeSequenceMode.AUTO_DETECT
): Sequence<T> = decodeReaderToSequence(reader, serializersModule.serializer(), format)
