/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
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

package jayo.samples

import jayo.bytestring.encodeToUtf8
import jayo.utf8ByteSize

fun dumpStringData(s: String) {
    println("                       " + s)
    println("        String.length: " + s.length)
    println("String.codePointCount: " + s.codePointCount(0, s.length))
    println("            Utf8.size: " + s.utf8ByteSize())
    println("          UTF-8 bytes: " + s.encodeToUtf8().hex())
    println()
}

fun main() {
    dumpStringData("Café \uD83C\uDF69") // NFC: é is one code point.
    dumpStringData("Café \uD83C\uDF69") // NFD: e is one code point, its accent is another.
}
