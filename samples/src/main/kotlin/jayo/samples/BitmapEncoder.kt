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

import jayo.*
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.math.hypot

class KotlinBitmapEncoder {
    class Bitmap(
        private val pixels: Array<IntArray>,
    ) {
        val width: Int = pixels[0].size
        val height: Int = pixels.size

        fun red(x: Int, y: Int): Byte = (pixels[y][x] and 0xff0000 shr 16).toByte()

        fun green(x: Int, y: Int): Byte = (pixels[y][x] and 0xff00 shr 8).toByte()

        fun blue(x: Int, y: Int): Byte = (pixels[y][x] and 0xff).toByte()
    }

    /**
     * Returns a bitmap that lights up red subpixels at the bottom, green subpixels on the right, and
     * blue subpixels in bottom-right.
     */
    fun generateGradient(): Bitmap {
        val pixels = Array(1080) { IntArray(1920) }
        for (y in 0 until 1080) {
            for (x in 0 until 1920) {
                val r = (y / 1080f * 255).toInt()
                val g = (x / 1920f * 255).toInt()
                val b = (hypot(x.toDouble(), y.toDouble()) / hypot(1080.0, 1920.0) * 255).toInt()
                pixels[y][x] = r shl 16 or (g shl 8) or b
            }
        }
        return Bitmap(pixels)
    }

    fun encode(bitmap: Bitmap, path: Path) {
        path.sink(StandardOpenOption.CREATE).buffered().use { sink ->
            encode(bitmap, sink)
        }
    }

    /** https://en.wikipedia.org/wiki/BMP_file_format  */
    private fun encode(bitmap: Bitmap, sink: Sink) {
        val height = bitmap.height
        val width = bitmap.width
        val bytesPerPixel = 3
        val rowByteCountWithoutPadding = bytesPerPixel * width
        val rowByteCount = (rowByteCountWithoutPadding + 3) / 4 * 4
        val pixelDataSize = rowByteCount * height
        val bmpHeaderSize = 14
        val dibHeaderSize = 40

        // BMP Header
        sink.writeUtf8("BM") // ID.
        sink.writeIntLe(bmpHeaderSize + dibHeaderSize + pixelDataSize) // File size.
        sink.writeShortLe(0) // Unused.
        sink.writeShortLe(0) // Unused.
        sink.writeIntLe(bmpHeaderSize + dibHeaderSize) // Offset of pixel data.

        // DIB Header
        sink.writeIntLe(dibHeaderSize)
        sink.writeIntLe(width)
        sink.writeIntLe(height)
        sink.writeShortLe(1) // Color plane count.
        sink.writeShortLe((bytesPerPixel * Byte.SIZE_BITS).toShort())
        sink.writeIntLe(0) // No compression.
        sink.writeIntLe(16) // Size of bitmap data including padding.
        sink.writeIntLe(2835) // Horizontal print resolution in pixels/meter. (72 dpi).
        sink.writeIntLe(2835) // Vertical print resolution in pixels/meter. (72 dpi).
        sink.writeIntLe(0) // Palette color count.
        sink.writeIntLe(0) // 0 important colors.

        // Pixel data.
        for (y in height - 1 downTo 0) {
            for (x in 0 until width) {
                sink.writeByte(bitmap.blue(x, y))
                sink.writeByte(bitmap.green(x, y))
                sink.writeByte(bitmap.red(x, y))
            }

            // Padding for 4-byte alignment.
            for (p in rowByteCountWithoutPadding until rowByteCount) {
                sink.writeByte(0)
            }
        }
    }
}

fun main() {
    val encoder = KotlinBitmapEncoder()
    val bitmap = encoder.generateGradient()
    encoder.encode(bitmap, Path.of("gradient.bmp"))
}
