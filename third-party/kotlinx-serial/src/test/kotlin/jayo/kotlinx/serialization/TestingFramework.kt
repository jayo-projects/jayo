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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertEquals


inline fun assertFailsWithSerial(
    exceptionName: String,
    assertionMessage: String? = null,
    crossinline block: () -> Unit
) {
    val assertion = assertThatThrownBy { block() }
        .isInstanceOf(SerializationException::class.java)
        .matches { throwable -> throwable::class.simpleName == exceptionName }
    if (assertionMessage != null) {
        assertion.hasMessage(assertionMessage)
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun SerialDescriptor.assertDescriptorEqualsTo(other: SerialDescriptor) {
    assertEquals(serialName, other.serialName)
    assertEquals(elementsCount, other.elementsCount)
    assertEquals(isNullable, other.isNullable)
    assertEquals(annotations, other.annotations)
    assertEquals(kind, other.kind)
    for (i in 0 until elementsCount) {
        getElementDescriptor(i).assertDescriptorEqualsTo(other.getElementDescriptor(i))
        val name = getElementName(i)
        val otherName = other.getElementName(i)
        assertEquals(name, otherName)
        assertEquals(getElementAnnotations(i), other.getElementAnnotations(i))
        assertEquals(name, otherName)
        assertEquals(isElementOptional(i), other.isElementOptional(i))
    }
}
