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

package jayo;

import jayo.bytestring.ByteString;
import jayo.internal.RealWriter;
import org.jspecify.annotations.NonNull;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;

/**
 * A writer that facilitates typed data writes and keeps a buffer internally so that a caller can write some data
 * without sending it directly to an upstream.
 * <p>
 * Writer is the main Jayo interface to write data in client's code, any {@link RawWriter} can be turned into
 * {@link Writer} using {@code Jayo.buffer(myRawWriter)}.
 * <p>
 * Depending on the kind of upstream and the number of bytes written, buffering may improve the performance by hiding
 * the latency of small writings.
 * <p>
 * Data stored inside the internal buffer could be sent to an upstream using {@link #flush()}, {@link #emit()}, or
 * {@link #emitCompleteSegments()}:
 * <ul>
 * <li>{@link #flush()} writes the whole buffer to an upstream and then flushes the upstream.
 * <li>{@link #emit()} writes all data from the buffer into the upstream without flushing it.
 * <li>{@link #emitCompleteSegments()} indicates that the current write operation is now finished and a part of data
 * from the buffer, complete segments, may be partially emitted into the upstream.
 * </ul>
 * The latter is aimed to reduce memory footprint by keeping the buffer as small as possible without excessive writings
 * to the upstream. On each write operation, the underlying buffer will automatically emit all the complete segment(s),
 * if any, by calling {@link #emitCompleteSegments()}.
 * <h3>Write methods' behavior and naming conventions</h3>
 * Methods writing a value of some type are usually named {@code write<Type>}, like {@link #writeByte(byte)} or
 * {@link #writeInt(int)}, except methods writing data from some collection of bytes, like
 * {@code write(byte[], int, int)} or {@code write(RawReader, long)}.
 * In the latter case, if a collection is consumable (i.e., once data was read from it will no longer be available for
 * reading again), the write method will consume as many bytes as it was requested to write.
 * <p>
 * Methods consuming data from a producer are named {@code write*From}, like {@link #writeFrom(RawReader, long)}.
 * <p>
 * Methods fully consuming a producer are named {@code writeAllFrom}, like {@link #writeAllFrom(RawReader)} or
 * {@link #writeAllFrom(ByteBuffer)}.
 * <p>
 * Kotlin notice: It is recommended to follow the same naming convention for Writer extensions.
 * <p>
 * Note: Write methods on numbers use the big-endian order. If you need little-endian order, use <i>reverseBytes()</i>,
 * for example {@code writer.writeShort(Short.reverseBytes(myShortValue))}. Jayo provides Kotlin extension functions
 * that support little-endian and unsigned numeric types.
 */
public sealed interface Writer extends RawWriter permits Buffer, RealWriter {
    /**
     * @return {@code true} if this writer is open.
     */
    boolean isOpen();

    /**
     * Writes all bytes from {@code source} to this writer.
     *
     * @param source the byte array source.
     * @return {@code this}
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @NonNull
    Writer write(final byte @NonNull [] source);

    /**
     * Writes {@code byteCount} bytes from {@code source}, starting at {@code offset} to this writer.
     *
     * @param source    the byte array source.
     * @param offset    the start offset (inclusive) in the byte array's data.
     * @param byteCount the number of bytes to write.
     * @return {@code this}
     * @throws IndexOutOfBoundsException   if {@code offset} or {@code byteCount} is out of range of {@code source}
     *                                     indices.
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @NonNull
    Writer write(final byte @NonNull [] source,
                 final int offset,
                 final int byteCount);

    /**
     * Writes all bytes from {@code byteString} to this writer.
     *
     * @param byteString the byte string source.
     * @return {@code this}
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @NonNull
    Writer write(final @NonNull ByteString byteString);

    /**
     * Writes {@code byteCount} bytes from {@code byteString}, starting at {@code offset} to this writer.
     *
     * @param byteString the byte string source.
     * @param offset     the start offset (inclusive) in the byte string's data.
     * @param byteCount  the number of bytes to write.
     * @return {@code this}
     * @throws IndexOutOfBoundsException   if {@code offset} or {@code byteCount} is out of range of
     *                                     {@code byteString} indices.
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @NonNull
    Writer write(final @NonNull ByteString byteString,
                 final int offset,
                 final int byteCount);

    /**
     * Encodes all the characters from {@code string} using UTF-8 and writes them to this writer.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.write("Uh uh uh!");
     * buffer.writeByte(' ');
     * buffer.write("You didn't say the magic word!");
     *
     * assertThat(buffer.readString()).isEqualTo("Uh uh uh! You didn't say the magic word!");
     * }
     * </pre>
     *
     * @param string the string to be encoded.
     * @return {@code this}
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @NonNull
    Writer write(final @NonNull String string);

    /**
     * Encodes all the characters from {@code string} using the provided {@code charset} and writes them to this writer.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.write("Uh uh uh¡", StandardCharsets.ISO_8859_1);
     * buffer.writeByte(' ');
     * buffer.write("You didn't say the magic word¡", StandardCharsets.ISO_8859_1);
     *
     * assertThat(buffer.readString(StandardCharsets.ISO_8859_1)).isEqualTo("Uh uh uh¡ You didn't say the magic word¡");
     * }
     * </pre>
     *
     * @param string  the string to be encoded.
     * @param charset the charset to use for encoding.
     * @return {@code this}
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @NonNull
    Writer write(final @NonNull String string, final @NonNull Charset charset);

    /**
     * Encodes {@code codePoint} in UTF-8 and writes it to this writer.
     *
     * @param codePoint the codePoint to be written.
     * @return {@code this}
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @NonNull
    Writer writeUtf8CodePoint(final int codePoint);

    /**
     * Removes {@code byteCount} bytes from {@code source} and appends them to this writer.
     * <p>
     * If {@code source} will be exhausted before reading {@code byteCount} from it, then an exception thrown on an
     * attempt to read remaining bytes will be propagated to the caller of this method.
     *
     * @param source    the source to consume data from.
     * @param byteCount the number of bytes to consume from {@code source} and to write into this writer.
     * @return {@code this}
     * @throws IllegalArgumentException    if {@code byteCount} is negative.
     * @throws JayoClosedResourceException if this writer or {@code source} is closed.
     */
    @NonNull
    Writer writeFrom(final @NonNull RawReader source, final long byteCount);

    /**
     * Writes a byte to this writer.
     *
     * @param b the byte to be written.
     * @return {@code this}
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @NonNull
    Writer writeByte(final byte b);

    /**
     * Writes two bytes containing a short, in the big-endian order, to this writer.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.writeShort(32767);
     * buffer.writeShort(15);
     *
     * assertThat(buffer.bytesAvailable()).isEqualTo(4);
     * assertThat(buffer.readByte()).isEqualTo(0x7f);
     * assertThat(buffer.readByte()).isEqualTo(0xff);
     * assertThat(buffer.readByte()).isEqualTo(0x00);
     * assertThat(buffer.readByte()).isEqualTo(0x0f);
     * assertThat(buffer.bytesAvailable()).isEqualTo(0);
     * }
     * </pre>
     *
     * @param s the short to be written.
     * @return {@code this}
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @NonNull
    Writer writeShort(final short s);

    /**
     * Writes four bytes containing an int, in the big-endian order, to this writer.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.writeInt(2147483647);
     * buffer.writeInt(15);
     *
     * assertThat(buffer.bytesAvailable()).isEqualTo(8);
     * assertThat(buffer.readByte()).isEqualTo(0x7f);
     * assertThat(buffer.readByte()).isEqualTo(0xff);
     * assertThat(buffer.readByte()).isEqualTo(0xff);
     * assertThat(buffer.readByte()).isEqualTo(0xff);
     * assertThat(buffer.readByte()).isEqualTo(0x00);
     * assertThat(buffer.readByte()).isEqualTo(0x00);
     * assertThat(buffer.readByte()).isEqualTo(0x00);
     * assertThat(buffer.readByte()).isEqualTo(0x0f);
     * assertThat(buffer.bytesAvailable()).isEqualTo(0);
     * }
     * </pre>
     *
     * @param i the int to be written.
     * @return {@code this}
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @NonNull
    Writer writeInt(final int i);

    /**
     * Writes eight bytes containing a long, in the big-endian order, to this writer.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.writeLong(9223372036854775807L);
     * buffer.writeLong(15);
     *
     * assertThat(buffer.bytesAvailable()).isEqualTo(16);
     * assertThat(buffer.readByte()).isEqualTo(0x7f);
     * assertThat(buffer.readByte()).isEqualTo(0xff);
     * assertThat(buffer.readByte()).isEqualTo(0xff);
     * assertThat(buffer.readByte()).isEqualTo(0xff);
     * assertThat(buffer.readByte()).isEqualTo(0xff);
     * assertThat(buffer.readByte()).isEqualTo(0xff);
     * assertThat(buffer.readByte()).isEqualTo(0xff);
     * assertThat(buffer.readByte()).isEqualTo(0xff);
     * assertThat(buffer.readByte()).isEqualTo(0x00);
     * assertThat(buffer.readByte()).isEqualTo(0x00);
     * assertThat(buffer.readByte()).isEqualTo(0x00);
     * assertThat(buffer.readByte()).isEqualTo(0x00);
     * assertThat(buffer.readByte()).isEqualTo(0x00);
     * assertThat(buffer.readByte()).isEqualTo(0x00);
     * assertThat(buffer.readByte()).isEqualTo(0x00);
     * assertThat(buffer.readByte()).isEqualTo(0x0f);
     * assertThat(buffer.bytesAvailable()).isEqualTo(0);
     * }
     * </pre>
     *
     * @param l the long to be written.
     * @return {@code this}
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @NonNull
    Writer writeLong(final long l);

    /**
     * Writes a long to this writer in signed decimal form (i.e., as a string in base 10). Resulting string will not
     * contain leading zeros, except the {@code 0} value itself.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.writeDecimalLong(8675309L);
     * buffer.writeByte(' ');
     * buffer.writeDecimalLong(-123L);
     * buffer.writeByte(' ');
     * buffer.writeDecimalLong(1L);
     *
     * assertThat(buffer.readString()).isEqualTo("8675309 -123 1");
     * }
     * </pre>
     *
     * @param l the long to be written.
     * @return {@code this}
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @NonNull
    Writer writeDecimalLong(final long l);

    /**
     * Writes a long to this writer in hexadecimal form (i.e., as a string in base 16). Resulting string will not
     * contain leading zeros, except the {@code 0} value itself.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.writeHexadecimalUnsignedLong(65535L);
     * buffer.writeByte(' ');
     * buffer.writeHexadecimalUnsignedLong(0xcafebabeL);
     * buffer.writeByte(' ');
     * buffer.writeHexadecimalUnsignedLong(0x10L);
     *
     * assertThat(buffer.readString()).isEqualTo("ffff cafebabe 10");
     * }
     * </pre>
     *
     * @param l the long to be written.
     * @return {@code this}
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @NonNull
    Writer writeHexadecimalUnsignedLong(final long l);

    /**
     * Reads all remaining bytes from the {@code source} byte buffer and writes them to this writer.
     *
     * @param source the byte buffer to read data from.
     * @return the number of bytes read, which will be {@code 0} if {@code source} has no
     * {@linkplain ByteBuffer#hasRemaining() remaining} bytes.
     * @throws JayoClosedResourceException if this writer is closed.
     */
    int writeAllFrom(final @NonNull ByteBuffer source);

    /**
     * Removes all bytes from {@code source} raw reader and writes them to this writer.
     *
     * @param source the source to consume data from.
     * @return the number of bytes read, which will be {@code 0L} if {@code source} is exhausted.
     * @throws JayoClosedResourceException if this writer or {@code source} is closed.
     */
    long writeAllFrom(final @NonNull RawReader source);

    /**
     * Ensures to write all the buffered data written until this call to the upstream. Then the upstream is explicitly
     * flushed.
     * <p>
     * Even though some {@link Writer} implementations are asynchronous, it is certain that all buffered data were
     * emitted to their final destination when calling this method.
     * <pre>
     * {@code
     * Writer b0 = Buffer.create();
     * Writer b1 = Jayo.buffer(b0);
     * Writer b2 = Jayo.buffer(b1);
     *
     * b2.write("hello");
     * assertThat(b2.buffer().byteSize()).isEqualTo(5);
     * assertThat(b1.buffer().byteSize()).isEqualTo(0);
     * assertThat(b0.buffer().byteSize()).isEqualTo(0);
     *
     * b2.flush();
     * assertThat(b2.buffer().byteSize()).isEqualTo(0);
     * assertThat(b1.buffer().byteSize()).isEqualTo(0);
     * assertThat(b0.buffer().byteSize()).isEqualTo(5);
     * }
     * </pre>
     *
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @Override
    void flush();

    /**
     * Ensures to write all the buffered data written until this call to the upstream. The upstream will not be
     * explicitly flushed.
     * <p>
     * This method behaves like {@link #flush()}, but has weaker guarantees.
     * <p>
     * Some {@link Writer} implementations are asynchronous; you cannot assert that all buffered data will be emitted to
     * their final destination when calling this method.
     * <p>
     * Call this method before a buffered writer goes out of scope so that its data can reach its destination.
     * <pre>
     * {@code
     * Writer b0 = Buffer.create();
     * Writer b1 = Jayo.buffer(b0);
     * Writer b2 = Jayo.buffer(b1);
     *
     * b2.write("hello");
     * assertThat(b2.buffer().byteSize()).isEqualTo(5);
     * assertThat(b1.buffer().byteSize()).isEqualTo(0);
     * assertThat(b0.buffer().byteSize()).isEqualTo(0);
     *
     * b2.emit();
     * assertThat(b2.buffer().byteSize()).isEqualTo(0);
     * assertThat(b1.buffer().byteSize()).isEqualTo(5);
     * assertThat(b0.buffer().byteSize()).isEqualTo(0);
     *
     * b1.emit();
     * assertThat(b2.buffer().byteSize()).isEqualTo(0);
     * assertThat(b1.buffer().byteSize()).isEqualTo(0);
     * assertThat(b0.buffer().byteSize()).isEqualTo(5);
     * }
     * </pre>
     *
     * @return {@code this}
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @NonNull
    Writer emit();

    /**
     * This writer's internal buffer consisting of a segment queue writes all data contained in its complete segments to
     * the upstream if at least one complete segment is present. The upstream will not be explicitly flushed. There are
     * no guarantees that this call will emit any buffered data, as well as there are no guarantees on how many bytes
     * will be emitted.
     * <p>
     * This method behaves like {@link #flush()}, but has weaker guarantees.
     * <p>
     * Typically, application code will not need to call this method: it is only necessary when application code writes
     * directly to this {@link Writer}.
     * Use this method to limit the memory held in the buffer to a single in-progress segment.
     * <pre>
     * {@code
     * Writer b0 = Buffer.create();
     * Writer b1 = Jayo.buffer(b0);
     * Writer b2 = Jayo.buffer(b1);
     *
     * b2.buffer().write(new byte[20_000]);
     * assertThat(b2.buffer().byteSize()).isEqualTo(20_000);
     * assertThat(b1.buffer().byteSize()).isEqualTo(0);
     * assertThat(b0.buffer().byteSize()).isEqualTo(0);
     *
     * b2.emitCompleteSegments();
     * assertThat(b2.buffer().byteSize()).isEqualTo(3_616);
     * assertThat(b1.buffer().byteSize()).isEqualTo(0);
     * assertThat(b0.buffer().byteSize()).isEqualTo(16_384); // This example assumes a segment has 16_384 bytes.
     * }
     * </pre>
     *
     * @return {@code this}
     * @throws JayoClosedResourceException if this writer is closed.
     */
    @NonNull
    Writer emitCompleteSegments();

    /**
     * @return an output stream that writes to this writer. Closing the stream will also close this writer.
     */
    @NonNull
    OutputStream asOutputStream();

    /**
     * @return a new writable byte channel that writes to this writer. Closing the byte channel will also close this
     * writer.
     */
    @NonNull
    WritableByteChannel asWritableByteChannel();
}
