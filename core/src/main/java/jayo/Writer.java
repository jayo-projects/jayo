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
 * A writer that facilitates typed data writes and keeps a buffer internally so that caller can write some data without
 * sending it directly to an upstream.
 * <p>
 * Writer is the main Jayo interface to write data in client's code, any {@link RawWriter} could be turned into
 * {@link Writer} using {@code Jayo.buffer(myRawWriter)}.
 * <p>
 * Depending on the kind of upstream and the number of bytes written, buffering may improve the performance by hiding
 * the latency of small writes.
 * <p>
 * Data stored inside the internal buffer could be sent to an upstream using {@link #flush}, {@link #emit}, or
 * {@link #emitCompleteSegments}:
 * <ul>
 * <li>{@link #flush} writes the whole buffer to an upstream and then flushes the upstream.
 * <li>{@link #emit} writes all data from the buffer into the upstream without flushing it.
 * <li>{@link #emitCompleteSegments} hints the reader that current write operation is now finished and a part of data
 * from the buffer, complete segments, may be partially emitted into the upstream.
 * </ul>
 * The latter is aimed to reduce memory footprint by keeping the buffer as small as possible without excessive writes
 * to the upstream. On each write operation, the underlying buffer will automatically emit all the complete segment(s),
 * if any, by calling {@link #emitCompleteSegments}.
 * <h3>Write methods' behavior and naming conventions</h3>
 * Methods writing a value of some type are usually named {@code write<Type>}, like {@link #writeByte} or
 * {@link #writeInt}, except methods writing data from some collection of bytes, like {@code write(byte[], int, int)}
 * or {@code write(RawReader, long)}.
 * In the latter case, if a collection is consumable (i.e., once data was read from it will no longer be available for
 * reading again), write method will consume as many bytes as it was requested to write.
 * <p>
 * Methods fully consuming its argument are named {@code transferFrom}, like {@link #transferFrom(RawReader)}.
 * <p>
 * Kotlin notice : It is recommended to follow the same naming convention for Writer extensions.
 * <p>
 * Write methods on numbers use the big-endian order. If you need little-endian order, use <i>reverseBytes()</i>, for
 * example {@code writer.writeShort(Short.reverseBytes(myShortValue))}. Jayo provides Kotlin extension functions that
 * support little-endian and unsigned numeric types.
 */
public sealed interface Writer extends RawWriter permits Buffer, RealWriter {
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
     * @throws IndexOutOfBoundsException if {@code offset} or {@code byteCount} is out of range of
     *                                   {@code source} indices.
     * @throws IllegalStateException     if this writer is closed.
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
     * @throws IndexOutOfBoundsException if {@code offset} or {@code byteCount} is out of range of
     *                                   {@code byteString} indices.
     * @throws IllegalStateException     if this writer is closed.
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
     * Encodes the characters at {@code startIndex} up to {@code endIndex} from {@code string} using UTF-8 and writes
     * them to this writer.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.write("I'm a hacker!\n", 6, 12);
     * buffer.writeByte(' ');
     * buffer.write("That's what I said: you're a nerd.\n", 29, 33);
     * buffer.writeByte(' ');
     * buffer.write("I prefer to be called a hacker!\n", 24, 31);
     *
     * assertThat(buffer.readString()).isEqualTo("hacker nerd hacker!");
     * }
     * </pre>
     *
     * @param string the string to be encoded.
     * @param startIndex   the index (inclusive) of the first character to encode.
     * @param endIndex     the index (exclusive) of a character past to a last character to encode.
     * @return {@code this}
     * @throws IndexOutOfBoundsException if {@code startIndex} or {@code endIndex} is out of range of {@code string}
     *                                   indices.
     * @throws IllegalArgumentException  if {@code startIndex > endIndex}.
     * @throws IllegalStateException     if this writer is closed.
     */
    @NonNull
    Writer write(final @NonNull String string,
                 final int startIndex,
                 final int endIndex);

    /**
     * Encodes all the characters from {@code string} using the provided {@code charset} and writes it to this writer.
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
     * Encodes the characters at {@code startIndex} up to {@code endIndex} from {@code string} using the provided
     * {@code charset} and writes them to this writer.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.write("I'm a hacker!\n", 6, 12, StandardCharsets.ISO_8859_1);
     * buffer.writeByte(' ');
     * buffer.write("That's what I said: you're a nerd.\n", 29, 33, StandardCharsets.ISO_8859_1);
     * buffer.writeByte(' ');
     * buffer.write("I prefer to be called a hacker!\n", 24, 31, StandardCharsets.ISO_8859_1);
     *
     * assertThat(buffer.readString(StandardCharsets.ISO_8859_1)).isEqualTo("hacker nerd hacker!");
     * }
     * </pre>
     *
     * @param string     the string to be encoded.
     * @param startIndex the index (inclusive) of the first character to encode.
     * @param endIndex   the index (exclusive) of a character past to a last character to encode.
     * @param charset    the charset to use for encoding.
     * @return {@code this}
     * @throws IndexOutOfBoundsException if {@code startIndex} or {@code endIndex} is out of range of {@code string}
     *                                   indices.
     * @throws IllegalArgumentException  if {@code startIndex > endIndex}.
     * @throws IllegalStateException     if this writer is closed.
     */
    @NonNull
    Writer write(final @NonNull String string,
                 final int startIndex,
                 final int endIndex,
                 final @NonNull Charset charset);

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
     * Removes {@code byteCount} bytes from {@code reader} and appends them to this writer.
     * <p>
     * If {@code reader} will be exhausted before reading {@code byteCount} from it then an exception throws on
     * an attempt to read remaining bytes will be propagated to a caller of this method.
     *
     * @param reader    the reader to consume data from.
     * @param byteCount the number of bytes to read from {@code reader} and to write into this writer.
     * @return {@code this}
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     * @throws IllegalStateException    if this writer or the reader is closed.
     */
    @NonNull
    Writer write(final @NonNull RawReader reader, final long byteCount);

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
     * Writes a long to this writer in signed decimal form (i.e., as a string in base 10).
     * Resulting string will not contain leading zeros, except the {@code 0} value itself.
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
     * Reads all remaining bytes from {@code source} byte buffer and writes them to this writer.
     *
     * @param source the byte buffer to read data from.
     * @return the number of bytes read, which will be 0 if {@code source} has no remaining bytes.
     * @throws JayoClosedResourceException if this writer is closed.
     */
    int transferFrom(final @NonNull ByteBuffer source);

    /**
     * Removes all bytes from {@code reader} and writes them to this writer.
     *
     * @param reader the reader to consume data from.
     * @return the number of bytes read, which will be 0L if {@code reader} is exhausted.
     * @throws JayoClosedResourceException if this writer or the {@code reader} is closed.
     */
    long transferFrom(final @NonNull RawReader reader);

    /**
     * Ensures to write all the buffered data that was written until this call to the underlying writer, if one exists.
     * Then the underlying writer is explicitly flushed.
     * <p>
     * Even though some {@link Writer} implementations are asynchronous, it is certain that all buffered data were emitted
     * to the underlying writer immediately after calling this method.
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
     * Ensures to write all the buffered data that was written until this call to the underlying writer, if one exists.
     * The underlying writer will not be explicitly flushed.
     * <p>
     * This method behaves like {@link #flush}, but has weaker guarantees.
     * <p>
     * Some {@link Writer} implementations are asynchronous, you cannot assert that all buffered data will be emitted to
     * the underlying writer immediately after calling this method.
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
     * This writer's internal buffer writes complete segments to the underlying writer, if at least one complete segment
     * exists. The underlying writer will not be explicitly flushed.
     * There are no guarantees that this call will cause emit of buffered data as well as there are no guarantees how
     * many bytes will be emitted.
     * <p>
     * This method behaves like {@link #flush}, but has weaker guarantees.
     * <p>
     * Typically, application code will not need to call this: it is only necessary when application code writes
     * directly to this {@link Writer}.
     * Use this to limit the memory held in the buffer to a single in-progress segment.
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
     * assertThat(b0.buffer().byteSize()).isEqualTo(16_384); // This example assumes 16_384 byte segments.
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
