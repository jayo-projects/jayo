/*
 * Copyright (C) 2014 Square, Inc.
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
import jayo.exceptions.JayoException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

class DeflaterSinkTest {
  @Test
  fun deflateWithClose() {
    val data = Buffer()
    val original = "They're moving in herds. They do move in herds."
    data.writeUtf8(original)
    val sink = Buffer()
    val deflaterSink = DeflaterRawSink(sink, Deflater())
    deflaterSink.write(data, data.size)
    deflaterSink.close()
    val inflated = inflate(sink)
    assertEquals(original, inflated.readUtf8())
  }

  @Test
  fun deflateWithSyncFlush() {
    val original = "Yes, yes, yes. That's why we're taking extreme precautions."
    val data = Buffer()
    data.writeUtf8(original)
    val sink = Buffer()
    val deflaterSink = DeflaterRawSink(sink, Deflater())
    deflaterSink.write(data, data.size)
    deflaterSink.flush()
    val inflated = inflate(sink)
    assertEquals(original, inflated.readUtf8())
  }

  @Test
  fun deflateWellCompressed() {
    val original = "a".repeat(1024 * 1024)
    val data = Buffer()
    data.writeUtf8(original)
    val sink = Buffer()
    val deflaterSink = DeflaterRawSink(sink, Deflater())
    deflaterSink.write(data, data.size)
    deflaterSink.close()
    val inflated = inflate(sink)
    assertEquals(original, inflated.readUtf8())
  }

  @Test
  fun deflatePoorlyCompressed() {
    val original = randomBytes(1024 * 1024)
    val data = Buffer()
    data.write(original)
    val sink = Buffer()
    val deflaterSink = DeflaterRawSink(sink, Deflater())
    deflaterSink.write(data, data.size)
    deflaterSink.close()
    val inflated = inflate(sink)
    assertEquals(original, inflated.readByteString())
  }

  @Test
  fun multipleSegmentsWithoutCompression() {
    val buffer = Buffer()
    val deflater = Deflater()
    deflater.setLevel(Deflater.NO_COMPRESSION)
    val deflaterSink = DeflaterRawSink(buffer, deflater)
    val byteCount = SEGMENT_SIZE * 4
    deflaterSink.write(Buffer().writeUtf8("a".repeat(byteCount)), byteCount.toLong())
    deflaterSink.close()
    assertEquals("a".repeat(byteCount), inflate(buffer).readUtf8(byteCount.toLong()))
  }

  @Test
  fun deflateIntoNonemptySink() {
    val original = "They're moving in herds. They do move in herds."

    // Exercise all possible offsets for the outgoing segment.
    for (i in 0 until SEGMENT_SIZE) {
      val data = Buffer().writeUtf8(original)
      val sink = Buffer().writeUtf8("a".repeat(i))
      val deflaterSink = DeflaterRawSink(sink, Deflater())
      deflaterSink.write(data, data.size)
      deflaterSink.close()
      sink.skip(i.toLong())
      val inflated = inflate(sink)
      assertEquals(original, inflated.readUtf8())
    }
  }

  /**
   * This test deflates a single segment of without compression because that's
   * the easiest way to force close() to emit a large amount of data to the
   * underlying sink.
   */
  @Test
  fun closeWithExceptionWhenWritingAndClosing() {
    val mockSink = MockSink()
    mockSink.scheduleThrow(0, JayoException("first"))
    mockSink.scheduleThrow(1, JayoException("second"))
    val deflater = Deflater()
    deflater.setLevel(Deflater.NO_COMPRESSION)
    val deflaterSink = DeflaterRawSink(mockSink, deflater)
    deflaterSink.write(Buffer().writeUtf8("a".repeat(SEGMENT_SIZE)), SEGMENT_SIZE.toLong())
    try {
      deflaterSink.close()
      fail()
    } catch (expected: JayoException) {
      assertEquals("second", expected.message)
    }
    mockSink.assertLogContains("close()")
  }

  /**
   * This test confirms that we swallow NullPointerException from Deflater and
   * rethrow as an IOException.
   */
  @Test
  fun rethrowNullPointerAsIOException() {
    val deflater = Deflater()
    // Close to cause a NullPointerException
    deflater.end()

    val data = Buffer().apply {
      writeUtf8("They're moving in herds. They do move in herds.")
    }
    val deflaterSink = DeflaterRawSink(Buffer(), deflater)

    val ioe = assertThrows(JayoException::class.java) {
      deflaterSink.write(data, data.size)
    }

    assertTrue(ioe.cause!!.cause is NullPointerException)
  }

  /**
   * Uses streaming decompression to inflate `deflated`. The input must
   * either be finished or have a trailing sync flush.
   */
  private fun inflate(deflated: Buffer): Buffer {
    val deflatedIn = deflated.asInputStream()
    val inflater = Inflater()
    val inflatedIn = InflaterInputStream(deflatedIn, inflater)
    val result = Buffer()
    val buffer = ByteArray(8192)
    while (!inflater.needsInput() || deflated.size > 0 || deflatedIn.available() > 0) {
      val count = inflatedIn.read(buffer, 0, buffer.size)
      if (count != -1) {
        result.write(buffer, 0, count)
      }
    }
    return result
  }
}
