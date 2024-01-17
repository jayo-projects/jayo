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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.internal

import jayo.Buffer
import jayo.ByteString

fun segmentSizes(buffer: Buffer): List<Int> {
    check(buffer is RealBuffer)
    var segment = buffer.segmentQueue.head() ?: return emptyList()

    val sizes = mutableListOf(segment.limit - segment.pos)
    segment = segment.next
    while (segment !== buffer.segmentQueue) {
        sizes.add(segment.limit - segment.pos)
        segment = segment.next
    }
    return sizes
}

fun bufferWithSegments(vararg segments: String): Buffer {
    val result = RealBuffer()
    for (s in segments) {
        val offsetInSegment =
            if (s.length < Segment.SIZE) (Segment.SIZE - s.length) / 2 else 0
        val buffer = RealBuffer()
        buffer.writeUtf8('_'.repeat(offsetInSegment))
        buffer.writeUtf8(s)
        buffer.skip(offsetInSegment.toLong())
        result.write(buffer.copyTo(RealBuffer()), buffer.size)
    }
    return result
}

fun makeSegments(source: ByteString): ByteString {
    val buffer = RealBuffer()
    for (i in 0 until source.size) {
        val tail = buffer.segmentQueue.writableSegment(Segment.SIZE)
        tail.data[tail.pos] = source[i]
        val limit = tail.limit
        tail.limit = limit + 1
        buffer.segmentQueue.addTail(tail)
        buffer.segmentQueue.incrementSize(1)
    }
    return buffer.snapshot()
}

fun Char.repeat(count: Int): String {
    return toString().repeat(count)
}
