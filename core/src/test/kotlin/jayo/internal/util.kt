/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio) and kotlinx-io (https://github.com/Kotlin/kotlinx-io), original
 * copyrights are below
 *
 * Copyright 2017-2023 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENCE file.
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

package jayo.internal

import jayo.Buffer
import jayo.bytestring.ByteString
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun fromHexChar(char: Char): Int {
    return when (val code = char.code) {
        in '0'.code..'9'.code -> code - '0'.code
        in 'a'.code..'f'.code -> code - 'a'.code + 10
        in 'A'.code..'F'.code -> code - 'A'.code + 10
        else -> throw NumberFormatException("Not a hexadecimal digit: $char")
    }
}

fun String.decodeHex(): ByteArray {
    if (length % 2 != 0) throw IllegalArgumentException("Even number of bytes is expected.")

    val result = ByteArray(length / 2)

    for (idx in result.indices) {
        val byte = fromHexChar(this[idx * 2]).shl(4).or(fromHexChar(this[idx * 2 + 1]))
        result[idx] = byte.toByte()
    }

    return result
}

fun randomBytes(length: Int, seed: Int = 0): ByteString {
    val random = Random(seed)
    val randomBytes = ByteArray(length)
    random.nextBytes(randomBytes)
    return ByteString.of(*randomBytes)
}

fun assertArrayEquals(a: ByteArray, b: ByteArray) {
    assertEquals(a.contentToString(), b.contentToString())
}

// Syntactic sugar.
internal infix fun Byte.and(other: Int): Int = toInt() and other

fun assertNoEmptySegments(buffer: Buffer) {
    assertTrue(segmentSizes(buffer).all { it != 0 }, "Expected all segments to be non-empty")
}

const val LATIN1 = "Je vais vous dire le problème avec le pouvoir scientifique que vous utilisez ici, il n'a " +
        "pas fallu de discipline pour l'atteindre. Vous avez lu ce que les autres avaient fait " +
        "et vous avez pris la prochaine étape. Vous n'avez pas gagné les connaissances pour " +
        "vous-mêmes, donc vous n'en assumez aucune responsabilité. Tu étais sur les épaules de " +
        "génies pour accomplir quelque chose aussi vite que tu pouvais, et avant même " +
        "de savoir ce que tu avais, tu l'as breveté, et l'as emballé, et tu l'as mis sur une boîte " +
        "à lunch en plastique, et maintenant tu le vends, tu veux le vendre"
