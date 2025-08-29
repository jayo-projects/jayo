/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
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

package jayo.internal

import jayo.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class GzipWriterTest {
    @Test
    fun writer() {
        val data = Buffer()
        (data as RawWriter).gzip().buffered().use { gzip ->
            gzip.write("Hi!")
        }
        assertEquals("1f8b0800000000000000f3c8540400dac59e7903000000", data.readByteString().hex())
    }

    @Test
    fun gzipGunzip() {
        val data = Buffer()
        val original = "It's a UNIX system! I know this!"
        data.write(original)
        val sink = Buffer()
        val gzipSink = GzipRawWriter(sink)
        gzipSink.writeFrom(data, data.bytesAvailable())
        gzipSink.close()
        val inflated = gunzip(sink)
        assertEquals(original, inflated.readString())
    }

    @Test
    fun closeWithExceptionWhenWritingAndClosing() {
        val mockSink = MockWriter()
        mockSink.scheduleThrow(0, JayoException("first"))
        mockSink.scheduleThrow(1, JayoException("second"))
        val gzipSink = GzipRawWriter(mockSink)
        gzipSink.writeFrom(Buffer().write("a".repeat(Segment.SIZE)), Segment.SIZE.toLong())
        try {
            gzipSink.close()
            fail()
        } catch (expected: JayoException) {
            assertEquals("first", expected.message)
        }
        mockSink.assertLogContains("close()")
    }

    private fun gunzip(gzipped: Buffer): Buffer {
        val result = Buffer()
        val source = GzipRawReader(gzipped)
        while (source.readAtMostTo(result, Int.MAX_VALUE.toLong()) != -1L) {
        }
        return result
    }
}
