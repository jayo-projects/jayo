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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo;

import org.jspecify.annotations.NonNull;
import jayo.external.NonNegative;
import jayo.internal.RealSink;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;

/**
 * A sink that facilitates typed data writes and keeps a buffer internally so that caller can write some data without
 * sending it directly to an upstream.
 * <p>
 * Sink is the main Jayo interface to write data in client's code, any {@link RawSink} could be turned into {@link Sink}
 * using {@link Jayo#buffer(RawSink)}.
 * <p>
 * Depending on the kind of upstream and the number of bytes written, buffering may improve the performance by hiding
 * the latency of small writes.
 * <p>
 * Data stored inside the internal buffer could be sent to an upstream using {@link #flush}, {@link #emit}, or
 * {@link #emitCompleteSegments}:
 * <ul>
 * <li>{@link #flush} writes the whole buffer to an upstream and then flushes the upstream.
 * <li>{@link #emit} writes all data from the buffer into the upstream without flushing it.
 * <li>{@link #emitCompleteSegments} hints the source that current write operation is now finished and a part of data
 * from the buffer, complete segments, may be partially emitted into the upstream.
 * </ul>
 * The latter is aimed to reduce memory footprint by keeping the buffer as small as possible without excessive writes
 * to the upstream. On each write operation, the underlying buffer will automatically emit all the complete segment(s),
 * if any, by calling {@link #emitCompleteSegments}.
 * <h3>Write methods' behavior and naming conventions</h3>
 * Methods writing a value of some type are usually named {@code write<Type>}, like {@link #writeByte} or
 * {@link #writeInt}, except methods writing data from some collection of bytes, like {@code write(byte[], int, int)}
 * or {@code write(RawSource, long)}.
 * In the latter case, if a collection is consumable (i.e., once data was read from it will no longer be available for
 * reading again), write method will consume as many bytes as it was requested to write.
 * <p>
 * Methods fully consuming its argument are named {@code transferFrom}, like {@link #transferFrom(RawSource)}.
 * <p>
 * Kotlin notice : It is recommended to follow the same naming convention for Sink extensions.
 * <p>
 * This buffered sink write operations use the big-endian order. If you need little-endian order, use
 * {@code reverseBytes()}. Jayo provides Kotlin extension functions that support little-endian and unsigned numeric
 * types.
 */
public sealed interface Sink extends RawSink permits Buffer, RealSink {
    /**
     * Writes all bytes from {@code byteString} to this sink.
     *
     * @param byteString the byte string source.
     * @return {@code this}
     * @throws IllegalStateException if this sink is closed.
     */
    @NonNull Sink write(final @NonNull ByteString byteString);

    /**
     * Writes {@code byteCount} bytes from {@code byteString}, starting at {@code offset} to this sink.
     *
     * @param byteString the byte string source.
     * @param offset     the start offset (inclusive) in the byte string's data.
     * @param byteCount  the number of bytes to write.
     * @return {@code this}
     * @throws IndexOutOfBoundsException if {@code offset} or {@code byteCount} is out of range of
     *                                   {@code byteString} indices.
     * @throws IllegalStateException     if this sink is closed.
     */
    @NonNull Sink write(final @NonNull ByteString byteString,
                        final @NonNegative int offset, final @NonNegative int byteCount);

    /**
     * Writes all bytes from {@code source} to this sink.
     *
     * @param source the byte array source.
     * @return {@code this}
     * @throws IllegalStateException if this sink is closed.
     */
    @NonNull Sink write(final byte @NonNull [] source);

    /**
     * Writes {@code byteCount} bytes from {@code source}, starting at {@code offset} to this sink.
     *
     * @param source    the byte array source.
     * @param offset    the start offset (inclusive) in the byte array's data.
     * @param byteCount the number of bytes to write.
     * @return {@code this}
     * @throws IndexOutOfBoundsException if {@code offset} or {@code byteCount} is out of range of
     *                                   {@code source} indices.
     * @throws IllegalStateException     if this sink is closed.
     */
    @NonNull Sink write(final byte @NonNull [] source, final @NonNegative int offset, final @NonNegative int byteCount);

    /**
     * Reads all remaining bytes from {@code source} byte buffer and writes them to this sink.
     *
     * @param source the byte buffer to read data from.
     * @return the number of bytes read, which will be 0 if {@code source} has no remaining bytes.
     * @throws IllegalStateException if this sink is closed.
     */
    @NonNegative
    int transferFrom(final @NonNull ByteBuffer source);

    /**
     * Removes all bytes from {@code source} and writes them to this sink.
     *
     * @param source the source to consume data from.
     * @return the number of bytes read, which will be 0L if {@code source} is exhausted.
     * @throws IllegalStateException if this sink or the {@code source} is closed.
     */
    @NonNegative
    long transferFrom(final @NonNull RawSource source);

    /**
     * Removes {@code byteCount} bytes from {@code source} and appends them to this sink.
     * <p>
     * If {@code source} will be exhausted before reading {@code byteCount} from it then an exception throws on
     * an attempt to read remaining bytes will be propagated to a caller of this method.
     *
     * @param source    the source to consume data from.
     * @param byteCount the number of bytes to read from {@code source} and to write into this sink.
     * @return {@code this}
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     * @throws IllegalStateException    if this sink or the source is closed.
     */
    @NonNull Sink write(final @NonNull RawSource source, final @NonNegative long byteCount);

    /**
     * Encodes all the characters from {@code charSequence} using UTF-8 and writes them to this sink.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.writeUtf8("Uh uh uh!");
     * buffer.writeByte(' ');
     * buffer.writeUtf8("You didn't say the magic word!");
     *
     * assertThat(buffer.readUtf8()).isEqualTo("Uh uh uh! You didn't say the magic word!");
     * }
     * </pre>
     *
     * @param charSequence the char sequence to be encoded.
     * @return {@code this}
     * @throws IllegalStateException if this sink is closed.
     */
    @NonNull Sink writeUtf8(final @NonNull CharSequence charSequence);

    /**
     * Encodes the characters at {@code startIndex} up to {@code endIndex} from {@code charSequence} using UTF-8 and
     * writes them to this sink.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.writeUtf8("I'm a hacker!\n", 6, 12);
     * buffer.writeByte(' ');
     * buffer.writeUtf8("That's what I said: you're a nerd.\n", 29, 33);
     * buffer.writeByte(' ');
     * buffer.writeUtf8("I prefer to be called a hacker!\n", 24, 31);
     *
     * assertThat(buffer.readUtf8()).isEqualTo("hacker nerd hacker!");
     * }
     * </pre>
     *
     * @param charSequence the char sequence to be encoded.
     * @param startIndex   the index (inclusive) of the first character to encode.
     * @param endIndex     the index (exclusive) of a character past to a last character to encode.
     * @return {@code this}
     * @throws IndexOutOfBoundsException if {@code startIndex} or {@code endIndex} is out of range of {@code string}
     *                                   indices.
     * @throws IllegalArgumentException  if {@code startIndex > endIndex}.
     * @throws IllegalStateException     if this sink is closed.
     */
    @NonNull Sink writeUtf8(final @NonNull CharSequence charSequence,
                            final @NonNegative int startIndex,
                            final @NonNegative int endIndex);

    /**
     * Encodes {@code codePoint} in UTF-8 and writes it to this sink.
     *
     * @param codePoint the codePoint to be written.
     * @return {@code this}
     * @throws IllegalStateException if this sink is closed.
     */
    @NonNull Sink writeUtf8CodePoint(final @NonNegative int codePoint);

    /**
     * Encodes {@code string} using the provided {@code charset} and writes it to this sink.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.writeString("Uh uh uh¡", StandardCharsets.ISO_8859_1);
     * buffer.writeByte(' ');
     * buffer.writeString("You didn't say the magic word¡", StandardCharsets.ISO_8859_1);
     *
     * assertThat(buffer.readString(StandardCharsets.ISO_8859_1)).isEqualTo("Uh uh uh¡ You didn't say the magic word¡");
     * }
     * </pre>
     *
     * @param string  the string to be encoded.
     * @param charset the charset to use for encoding.
     * @return {@code this}
     * @throws IllegalStateException if this sink is closed.
     */
    @NonNull Sink writeString(final @NonNull String string, final @NonNull Charset charset);

    /**
     * Encodes the characters at {@code startIndex} up to {@code endIndex} from {@code string} using the provided
     * {@code charset} and writes them to this sink.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.writeString("I'm a hacker!\n", 6, 12, StandardCharsets.ISO_8859_1);
     * buffer.writeByte(' ');
     * buffer.writeString("That's what I said: you're a nerd.\n", 29, 33, StandardCharsets.ISO_8859_1);
     * buffer.writeByte(' ');
     * buffer.writeString("I prefer to be called a hacker!\n", 24, 31, StandardCharsets.ISO_8859_1);
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
     * @throws IllegalStateException     if this sink is closed.
     */
    @NonNull Sink writeString(final @NonNull String string,
                              final @NonNegative int startIndex,
                              final @NonNegative int endIndex,
                              final @NonNull Charset charset);

    /**
     * Writes a byte to this sink.
     *
     * @param b the byte to be written.
     * @return {@code this}
     * @throws IllegalStateException if this sink is closed.
     */
    @NonNull Sink writeByte(final byte b);

    /**
     * Writes two bytes containing a short, in the big-endian order, to this sink.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.writeShort(32767);
     * buffer.writeShort(15);
     *
     * assertThat(buffer.getSize()).isEqualTo(4);
     * assertThat(buffer.readByte()).isEqualTo(0x7f);
     * assertThat(buffer.readByte()).isEqualTo(0xff);
     * assertThat(buffer.readByte()).isEqualTo(0x00);
     * assertThat(buffer.readByte()).isEqualTo(0x0f);
     * assertThat(buffer.getSize()).isEqualTo(0);
     * }
     * </pre>
     *
     * @param s the short to be written.
     * @return {@code this}
     * @throws IllegalStateException if this sink is closed.
     */
    @NonNull Sink writeShort(final short s);

    /**
     * Writes four bytes containing an int, in the big-endian order, to this sink.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.writeInt(2147483647);
     * buffer.writeInt(15);
     *
     * assertThat(buffer.getSize()).isEqualTo(8);
     * assertThat(buffer.readByte()).isEqualTo(0x7f);
     * assertThat(buffer.readByte()).isEqualTo(0xff);
     * assertThat(buffer.readByte()).isEqualTo(0xff);
     * assertThat(buffer.readByte()).isEqualTo(0xff);
     * assertThat(buffer.readByte()).isEqualTo(0x00);
     * assertThat(buffer.readByte()).isEqualTo(0x00);
     * assertThat(buffer.readByte()).isEqualTo(0x00);
     * assertThat(buffer.readByte()).isEqualTo(0x0f);
     * assertThat(buffer.getSize()).isEqualTo(0);
     * }
     * </pre>
     *
     * @param i the int to be written.
     * @return {@code this}
     * @throws IllegalStateException if this sink is closed.
     */
    @NonNull Sink writeInt(final int i);

    /**
     * Writes eight bytes containing a long, in the big-endian order, to this sink.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.writeLong(9223372036854775807L);
     * buffer.writeLong(15);
     *
     * assertThat(buffer.getSize()).isEqualTo(16);
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
     * assertThat(buffer.getSize()).isEqualTo(0);
     * }
     * </pre>
     *
     * @param l the long to be written.
     * @return {@code this}
     * @throws IllegalStateException if this sink is closed.
     */
    @NonNull Sink writeLong(final long l);

    /**
     * Writes a long to this sink in signed decimal form (i.e., as a string in base 10).
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
     * assertThat(buffer.readUtf8()).isEqualTo("8675309 -123 1");
     * }
     * </pre>
     *
     * @param l the long to be written.
     * @return {@code this}
     * @throws IllegalStateException if this sink is closed.
     */
    @NonNull Sink writeDecimalLong(final long l);

    /**
     * Writes a long to this sink in hexadecimal form (i.e., as a string in base 16).
     * Resulting string will not contain leading zeros, except the {@code 0} value itself.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * buffer.writeHexadecimalUnsignedLong(65535L);
     * buffer.writeByte(' ');
     * buffer.writeHexadecimalUnsignedLong(0xcafebabeL);
     * buffer.writeByte(' ');
     * buffer.writeHexadecimalUnsignedLong(0x10L);
     *
     * assertThat(buffer.readUtf8()).isEqualTo("ffff cafebabe 10");
     * }
     * </pre>
     *
     * @param l the long to be written.
     * @return {@code this}
     * @throws IllegalStateException if this sink is closed.
     */
    @NonNull Sink writeHexadecimalUnsignedLong(final long l);

    /**
     * Ensures to write all the buffered data that was written until this call to the underlying sink, if one exists.
     * Then the underlying sink is explicitly flushed.
     * <p>
     * Even though some {@link Sink} implementations are asynchronous, it is certain that all buffered data were emitted
     * to the underlying sink immediately after calling this method.
     * <pre>
     * {@code
     * Sink b0 = Buffer.create();
     * Sink b1 = Jayo.buffer(b0);
     * Sink b2 = Jayo.buffer(b1);
     *
     * b2.writeString("hello");
     * assertThat(b2.buffer().getSize()).isEqualTo(5);
     * assertThat(b1.buffer().getSize()).isEqualTo(0);
     * assertThat(b0.buffer().getSize()).isEqualTo(0);
     *
     * b2.flush();
     * assertThat(b2.buffer().getSize()).isEqualTo(0);
     * assertThat(b1.buffer().getSize()).isEqualTo(0);
     * assertThat(b0.buffer().getSize()).isEqualTo(5);
     * }
     * </pre>
     *
     * @throws IllegalStateException if this sink is closed.
     */
    @Override
    void flush();

    /**
     * Ensures to write all the buffered data that was written until this call to the underlying sink, if one exists.
     * The underlying sink will not be explicitly flushed.
     * <p>
     * This method behaves like {@link #flush}, but has weaker guarantees.
     * <p>
     * Some {@link Sink} implementations are asynchronous, you cannot assert that all buffered data will be emitted to
     * the underlying sink immediately after calling this method.
     * <p>
     * Call this method before a buffered sink goes out of scope so that its data can reach its destination.
     * <pre>
     * {@code
     * Sink b0 = Buffer.create();
     * Sink b1 = Jayo.buffer(b0);
     * Sink b2 = Jayo.buffer(b1);
     *
     * b2.writeString("hello");
     * assertThat(b2.buffer().getSize()).isEqualTo(5);
     * assertThat(b1.buffer().getSize()).isEqualTo(0);
     * assertThat(b0.buffer().getSize()).isEqualTo(0);
     *
     * b2.emit();
     * assertThat(b2.buffer().getSize()).isEqualTo(0);
     * assertThat(b1.buffer().getSize()).isEqualTo(5);
     * assertThat(b0.buffer().getSize()).isEqualTo(0);
     *
     * b1.emit();
     * assertThat(b2.buffer().getSize()).isEqualTo(0);
     * assertThat(b1.buffer().getSize()).isEqualTo(0);
     * assertThat(b0.buffer().getSize()).isEqualTo(5);
     * }
     * </pre>
     *
     * @return {@code this}
     * @throws IllegalStateException if this sink is closed.
     */
    @NonNull Sink emit();

    /**
     * This sink's internal buffer writes complete segments to the underlying sink, if at least one complete segment
     * exists. The underlying sink will not be explicitly flushed.
     * There are no guarantees that this call will cause emit of buffered data as well as there are no guarantees how
     * many bytes will be emitted.
     * <p>
     * This method behaves like {@link #flush}, but has weaker guarantees.
     * <p>
     * Typically, application code will not need to call this: it is only necessary when application code writes
     * directly to this {@link Sink}.
     * Use this to limit the memory held in the buffer to a single in-progress segment.
     * <pre>
     * {@code
     * Sink b0 = Buffer.create();
     * Sink b1 = Jayo.buffer(b0);
     * Sink b2 = Jayo.buffer(b1);
     *
     * b2.buffer().write(new byte[20_000]);
     * assertThat(b2.buffer().getSize()).isEqualTo(20_000);
     * assertThat(b1.buffer().getSize()).isEqualTo(0);
     * assertThat(b0.buffer().getSize()).isEqualTo(0);
     *
     * b2.emitCompleteSegments();
     * assertThat(b2.buffer().getSize()).isEqualTo(3_616);
     * assertThat(b1.buffer().getSize()).isEqualTo(0);
     * assertThat(b0.buffer().getSize()).isEqualTo(16_384); // This example assumes 16_384 byte segments.
     * }
     * </pre>
     *
     * @return {@code this}
     * @throws IllegalStateException if this sink is closed.
     */
    @NonNull Sink emitCompleteSegments();

    /**
     * Returns an output stream that writes to this sink. Closing the stream will also close this sink.
     */
    @NonNull OutputStream asOutputStream();

    /**
     * Returns a new writable byte channel that writes to this sink. Closing the byte channel will also close this sink.
     */
    @NonNull WritableByteChannel asWritableByteChannel();
}
