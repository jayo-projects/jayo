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

import jayo.exceptions.JayoEOFException;
import jayo.external.NonNegative;
import jayo.internal.RealSource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

/**
 * A source that facilitates typed data reads and keeps a buffer internally so that callers can read chunks of data
 * without requesting it from a downstream on every call.
 * <p>
 * {@link Source} is the main Jayo interface to read data in client's code, any {@link RawSource} could be converted
 * into {@link Source} using {@link Jayo#buffer(RawSource)}.
 * <p>
 * Depending on the kind of downstream and the number of bytes read, buffering may improve the performance by hiding
 * the latency of small reads.
 * <p>
 * The buffer is refilled on reads as necessary, but it is also possible to ensure it contains enough data using
 * {@link #require} or {@link #request}.
 * {@link Source} also allows skipping unneeded prefix of data using {@link #skip} and provides look ahead into
 * incoming data, buffering as much as necessary, using {@link #peek}.
 * <p>
 * Source's read* methods have different guarantees of how much data will be consumed from the source and what to expect
 * in case of error.
 * <h3>Read methods' behavior and naming conventions</h3>
 * Unless stated otherwise, all read methods consume the exact number of bytes requested (or the number of bytes
 * required to represent a value of a requested type). If a source contains fewer bytes than requested, these methods
 * will throw an exception.
 * <p>
 * Methods reading up to requested number of bytes are named as {@code readAtMost} in contrast to methods reading exact
 * number of bytes, which don't have <b>AtMost</b> suffix in their names.
 * If a source contains fewer bytes than requested, these methods will not treat it as en error and will return
 * gracefully.
 * <p>
 * Methods returning a value as a result are named {@code read<Type>}, like {@link #readInt} or {@link #readByte}.
 * These methods don't consume source's content in case of an error.
 * <p>
 * Methods reading data into a consumer supplied as one of its arguments are named {@code read*To},
 * like {@link #readTo(RawSink, long)} or {@link #readAtMostTo(byte[], int, int)}. These methods consume a source even when
 * an error occurs.
 * <p>
 * Methods moving all data from a source to some other sink are named {@code transferTo}, like
 * {@link #transferTo(RawSink)}.
 * <p>
 * Kotlin notice : it is recommended to follow the same naming convention for Source extensions.
 * <p>
 * This buffered source read operations use the big-endian order. If you need little-endian order, use
 * {@code reverseBytes()}. Jayo provides Kotlin extension functions that support little-endian and unsigned numeric
 * types.
 */
public sealed interface Source extends RawSource permits Buffer, RealSource {
    /**
     * The call of this method will block until there are bytes to read or the source is definitely exhausted.
     *
     * @return true if there are no more bytes in this source.
     * @throws IllegalStateException if this source is closed.
     */
    boolean exhausted();

    /**
     * Attempts to fill the buffer with at least {@code byteCount} bytes of data from the underlying source and throw
     * {@link JayoEOFException} when the source is exhausted before fulfilling the requirement.
     * <p>
     * If the buffer already contains required number of bytes then there will be no requests to the underlying source.
     *
     * @param byteCount the number of bytes that the buffer should contain.
     * @throws JayoEOFException         if this source is exhausted before the required bytes count could be read.
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     * @throws IllegalStateException    if this source is closed.
     */
    void require(final @NonNegative long byteCount);

    /**
     * Attempts to fill the buffer with at least {@code byteCount} bytes of data from the underlying source.
     *
     * @param byteCount the number of bytes that the buffer should contain.
     * @return a boolean value indicating if the requirement was successfully fulfilled. {@code false} indicates that
     * the underlying source was exhausted before filling the buffer with {@code byteCount} bytes of data.
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     * @throws IllegalStateException    if this source is closed.
     */
    boolean request(final @NonNegative long byteCount);

    /**
     * Reads and discards {@code byteCount} bytes from this source.
     *
     * @param byteCount the number of bytes to be skipped.
     * @throws JayoEOFException         if this source is exhausted before the requested number of bytes can be
     *                                  skipped.
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     * @throws IllegalStateException    if this source is closed.
     */
    void skip(final @NonNegative long byteCount);

    /**
     * Removes a byte from this source and returns it.
     *
     * @return the read byte value
     * @throws JayoEOFException      if there are no more bytes to read.
     * @throws IllegalStateException if this source is closed.
     */
    byte readByte();

    /**
     * Removes two bytes from this source and returns a short composed of them according to the big-endian order.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeByte(0x7f)
     * .writeByte(0xff)
     * .writeByte(0x00)
     * .writeByte(0x0f);
     * assertThat(buffer.byteSize()).isEqualTo(4);
     *
     * assertThat(buffer.readShort()).isEqualTo(32767);
     * assertThat(buffer.byteSize()).isEqualTo(2);
     *
     * assertThat(buffer.readShort()).isEqualTo(15);
     * assertThat(buffer.byteSize()).isEqualTo(0);
     * }
     * </pre>
     *
     * @return the read short value
     * @throws JayoEOFException      if there are not enough data to read a short value.
     * @throws IllegalStateException if this source is closed.
     */
    short readShort();

    /**
     * Removes four bytes from this source and returns an int composed of them according to the big-endian order.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeByte(0x7f)
     * .writeByte(0xff)
     * .writeByte(0xff)
     * .writeByte(0xff)
     * .writeByte(0x00)
     * .writeByte(0x00)
     * .writeByte(0x00)
     * .writeByte(0x0f);
     * assertThat(buffer.byteSize()).isEqualTo(8);
     *
     * assertThat(buffer.readInt()).isEqualTo(2147483647);
     * assertThat(buffer.byteSize()).isEqualTo(4);
     *
     * assertThat(buffer.readInt()).isEqualTo(15);
     * assertThat(buffer.byteSize()).isEqualTo(0);
     * }
     * </pre>
     *
     * @return the read int value
     * @throws JayoEOFException      if there are not enough data to read an int value.
     * @throws IllegalStateException if this source is closed.
     */
    int readInt();

    /**
     * Removes eight bytes from this source and returns a long composed of them according to the big-endian order.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeByte(0x7f)
     * .writeByte(0xff)
     * .writeByte(0xff)
     * .writeByte(0xff)
     * .writeByte(0xff)
     * .writeByte(0xff)
     * .writeByte(0xff)
     * .writeByte(0xff)
     * .writeByte(0x00)
     * .writeByte(0x00)
     * .writeByte(0x00)
     * .writeByte(0x00)
     * .writeByte(0x00)
     * .writeByte(0x00)
     * .writeByte(0x00)
     * .writeByte(0x0f);
     * assertThat(buffer.byteSize()).isEqualTo(16);
     *
     * assertThat(buffer.readLong()).isEqualTo(9223372036854775807L);
     * assertThat(buffer.byteSize()).isEqualTo(8);
     *
     * assertThat(buffer.readLong()).isEqualTo(15);
     * assertThat(buffer.byteSize()).isEqualTo(0);
     * }
     * </pre>
     *
     * @return the read long value
     * @throws JayoEOFException      if there are not enough data to read a long value.
     * @throws IllegalStateException if this source is closed.
     */
    long readLong();

    /**
     * Reads a long from this source in signed decimal form (i.e., as a string in base 10 with optional leading '-').
     * <p>
     * Source data will be consumed until the source is exhausted, the first occurrence of non-digit byte, or overflow
     * happened during resulting value construction.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeString("8675309 -123 00001");
     *
     * assertThat(buffer.readDecimalLong()).isEqualTo(8675309L);
     * assertThat(buffer.readByte()).isEqualTo(' ');
     * assertThat(buffer.readDecimalLong()).isEqualTo(-123L);
     * assertThat(buffer.readByte()).isEqualTo(' ');
     * assertThat(buffer.readDecimalLong()).isEqualTo(1L);
     * }
     * </pre>
     *
     * @return the read decimal long value
     * @throws NumberFormatException if the found digits do not fit into a long or a decimal number was not present.
     * @throws JayoEOFException      if this source is exhausted before a call of this method.
     * @throws IllegalStateException if this source is closed.
     */
    long readDecimalLong();

    /**
     * Reads a long form this source in hexadecimal form (i.e., as a string in base 16).
     * <p>
     * Source data will be consumed until the source is exhausted, the first occurrence of non-digit byte, or overflow
     * happened during resulting value construction.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeString("ffff CAFEBABE 10");
     *
     * assertThat(buffer.readHexadecimalUnsignedLong()).isEqualTo(65535L);
     * assertThat(buffer.readByte()).isEqualTo(' ');
     * assertThat(buffer.readHexadecimalUnsignedLong()).isEqualTo(0xcafebabeL);
     * assertThat(buffer.readByte()).isEqualTo(' ');
     * assertThat(buffer.readHexadecimalUnsignedLong()).isEqualTo(0x10L);
     * }
     * </pre>
     *
     * @return the read hexadecimal long value
     * @throws NumberFormatException if the found hexadecimal does not fit into a long or a hexadecimal number was not
     *                               present.
     * @throws JayoEOFException      if the source is exhausted before a call of this method.
     * @throws IllegalStateException if this source is closed.
     */
    long readHexadecimalUnsignedLong();

    /**
     * Removes all bytes from this source and returns them as a byte string.
     *
     * @return the byte string containing all bytes from this source.
     * @throws IllegalStateException if this source is closed.
     */
    @NonNull ByteString readByteString();

    /**
     * Removes {@code byteCount} bytes from this and returns them as a byte string.
     *
     * @param byteCount the number of bytes to read from the source.
     * @return the byte string containing {@code byteCount} bytes from this source.
     * @throws JayoEOFException         if the source is exhausted before reading {@code byteCount} bytes from it.
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     * @throws IllegalStateException    if this source is closed.
     */
    @NonNull ByteString readByteString(final @NonNegative long byteCount);

    /**
     * Removes all bytes from this source and returns them as a UTF-8 byte string.
     *
     * @return the UTF-8 byte string containing all bytes from this source.
     * @throws IllegalStateException if this source is closed.
     */
    @NonNull Utf8ByteString readUtf8ByteString();

    /**
     * Removes {@code byteCount} bytes from this and returns them as a UTF-8 byte string.
     *
     * @param byteCount the number of bytes to read from the source.
     * @return the UTF-8 byte string containing {@code byteCount} bytes from this source.
     * @throws JayoEOFException         if the source is exhausted before reading {@code byteCount} bytes from it.
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     * @throws IllegalStateException    if this source is closed.
     */
    @NonNull Utf8ByteString readUtf8ByteString(final @NonNegative long byteCount);

    /**
     * Finds the first string in {@code options} that is a prefix of this source, consumes it from this
     * buffer, and returns its index. If no byte string in {@code options} is a prefix of this source this
     * returns -1 and no bytes are consumed.
     * <p>
     * This can be used as an alternative to {@link #readByteString} or even {@link #readUtf8} if the set of expected
     * values is known in advance.
     * <pre>
     * {@code
     * Options FIELDS = Options.of(
     * ByteString.encodeUtf8("depth="),
     * ByteString.encodeUtf8("height="),
     * ByteString.encodeUtf8("width="));
     *
     * Buffer buffer = Buffer.create()
     * .writeString("width=640\n")
     * .writeString("height=480\n");
     *
     * assertThat(buffer.select(FIELDS)).isEqualTo(2); // found third option of FIELDS = "width="
     * assertThat(buffer.readDecimalLong()).isEqualTo(640);
     * assertThat(buffer.readByte()).isEqualTo('\n');
     * assertThat(buffer.select(FIELDS)).isEqualTo(1); // found second option of FIELDS = "height="
     * assertThat(buffer.readDecimalLong()).isEqualTo(480);
     * assertThat(buffer.readByte()).isEqualTo('\n');
     * }
     * </pre>
     *
     * @param options a list of several potential prefixes.
     * @return the index of the matching prefix in the {@code options} list, or {@code -1} if none matched.
     * @throws IllegalStateException if this source is closed.
     */
    int select(final @NonNull Options options);

    /**
     * Removes all bytes from this source and returns them as a byte array.
     *
     * @throws IllegalStateException if this source is closed.
     */
    byte @NonNull [] readByteArray();

    /**
     * Removes {@code byteCount} bytes from this source and returns them as a byte array.
     *
     * @param byteCount the number of bytes that should be read from the source.
     * @throws JayoEOFException         if the underlying source is exhausted before {@code byteCount} bytes of data
     *                                  could be read.
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     * @throws IllegalStateException    if this source is closed.
     */
    byte @NonNull [] readByteArray(final @NonNegative long byteCount);

    /**
     * Removes up to {@code sink.length} bytes from this source and copies them into {@code sink}.
     *
     * @param sink the array to which data will be written from this source.
     * @return the number of bytes read, or -1 if this source is exhausted.
     * @throws IllegalStateException if this source is closed.
     */
    int readAtMostTo(final byte @NonNull [] sink);

    /**
     * Removes up to {@code byteCount} bytes from this source and copies them into {@code sink} at {@code offset}.
     *
     * @param sink      the byte array to which data will be written from this source.
     * @param offset    the start offset (inclusive) in the {@code sink} of the first byte to copy.
     * @param byteCount the number of bytes to copy.
     * @return the number of bytes read, or -1 if this source is exhausted.
     * @throws IndexOutOfBoundsException if {@code offset} or {@code byteCount} is out of range of {@code sink} indices.
     * @throws IllegalStateException     if this source is closed.
     */
    int readAtMostTo(final byte @NonNull [] sink, final @NonNegative int offset, final @NonNegative int byteCount);

    /**
     * Removes exactly {@code sink.length} bytes from this source and copies them into {@code sink}.
     *
     * @param sink the byte array to which data will be written from this source.
     * @throws JayoEOFException      if {@code sink.length} bytes cannot be read.
     * @throws IllegalStateException if this source is closed.
     */
    void readTo(final byte @NonNull [] sink);

    /**
     * Removes exactly {@code byteCount} bytes from this source and copies them into {@code sink} at {@code offset}.
     *
     * @param sink      the byte array to which data will be written from this source.
     * @param offset    the start offset (inclusive) in the {@code sink} of the first byte to copy.
     * @param byteCount the number of bytes to copy.
     * @throws IndexOutOfBoundsException if {@code offset} or {@code byteCount} is out of range of {@code sink} indices.
     * @throws JayoEOFException          if {@code byteCount} bytes cannot be read.
     * @throws IllegalStateException     if this source is closed.
     */
    void readTo(final byte @NonNull [] sink, final @NonNegative int offset, final @NonNegative int byteCount);

    /**
     * Removes exactly {@code byteCount} bytes from this and appends them to {@code sink}.
     *
     * @param sink      the sink to which data will be written from this source.
     * @param byteCount the number of bytes to copy.
     * @throws JayoEOFException      if {@code byteCount} bytes cannot be read.
     * @throws IllegalStateException if this source or {@code sink} is closed.
     */
    void readTo(final @NonNull RawSink sink, final @NonNegative long byteCount);

    /**
     * Removes all bytes from this source and appends them to {@code sink}.
     *
     * @param sink the sink to which data will be written from this source.
     * @return the total number of bytes written to {@code sink} which will be {@code 0L} if this source is exhausted.
     * @throws IllegalStateException when this source or {@code sink} is closed.
     */
    @NonNegative
    long transferTo(final @NonNull RawSink sink);

    /**
     * Removes all bytes from this source, decodes them as UTF-8, and returns the string. Returns the empty string if
     * this source is empty.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeUtf8("Uh uh uh!")
     * .writeByte(' ')
     * .writeUtf8("You didn't say the magic word!");
     * assertThat(buffer.byteSize()).isEqualTo(40);
     *
     * assertThat(buffer.readUtf8()).isEqualTo("Uh uh uh! You didn't say the magic word!");
     * assertThat(buffer.byteSize()).isEqualTo(0);
     *
     * assertThat(buffer.readUtf8()).isEqualTo("");
     * }
     * </pre>
     *
     * @throws IllegalStateException if this source is closed.
     */
    @NonNull String readUtf8();

    /**
     * Removes {@code byteCount} bytes from this source, decodes them as UTF-8, and returns the string.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeUtf8("Uh uh uh!")
     * .writeByte(' ')
     * .writeUtf8("You didn't say the magic word!");
     * assertThat(buffer.byteSize()).isEqualTo(40);
     *
     * assertThat(buffer.readUtf8(14)).isEqualTo("Uh uh uh! You ");
     * assertThat(buffer.byteSize()).isEqualTo(26);
     *
     * assertThat(buffer.readUtf8(14)).isEqualTo("didn't say the");
     * assertThat(buffer.byteSize()).isEqualTo(12);
     *
     * assertThat(buffer.readUtf8(12)).isEqualTo(" magic word!");
     * assertThat(buffer.byteSize()).isEqualTo(0);
     * }
     * </pre>
     *
     * @param byteCount the number of bytes to read from this source for string decoding.
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     * @throws JayoEOFException         when this source is exhausted before reading {@code byteCount} bytes from it.
     * @throws IllegalStateException    if this source is closed.
     */
    @NonNull String readUtf8(final @NonNegative long byteCount);

    /**
     * Removes and returns UTF-8 encoded characters up to but not including the next line break. A line break is
     * either {@code "\n"} or {@code "\r\n"}; these characters are not included in the result.
     * <p>
     * On the end of the stream this method returns null. If the source doesn't end with a line break, then an implicit
     * line break is assumed. Null is returned once the source is exhausted.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeUtf8("I'm a hacker!\n")
     * .writeUtf8("That's what I said: you're a nerd.\n")
     * .writeUtf8("I prefer to be called a hacker!\n");
     * assertThat(buffer.byteSize()).isEqualTo(81);
     *
     * assertThat(buffer.readLine()).isEqualTo("I'm a hacker!");
     * assertThat(buffer.byteSize()).isEqualTo(67);
     *
     * assertThat(buffer.readLine()).isEqualTo("That's what I said: you're a nerd.");
     * assertThat(buffer.byteSize()).isEqualTo(32);
     *
     * assertThat(buffer.readLine()).isEqualTo("I prefer to be called a hacker!");
     * assertThat(buffer.byteSize()).isEqualTo(0);
     *
     * assertThat(buffer.readLine()).isNull();
     * assertThat(buffer.byteSize()).isEqualTo(0);
     * }
     * </pre>
     *
     * @throws IllegalStateException if this source is closed.
     */
    @Nullable String readUtf8Line();

    /**
     * Removes and returns UTF-8 encoded characters up to but not including the next line break, throwing
     * {@link JayoEOFException} if a line break was not encountered. A line break is either {@code "\n"} or
     * {@code "\r\n"}; these characters are not included in the result.
     * <p>
     * This method is safe. No bytes are discarded if the match fails, and the caller is free to try another match
     *
     * @throws IllegalStateException if this source is closed.
     */
    @NonNull String readUtf8LineStrict();

    /**
     * Removes and returns UTF-8 encoded characters up to but not including the next line break, throwing
     * {@link JayoEOFException} if a line break was not encountered. A line break is either {@code "\n"} or
     * {@code "\r\n"}; these characters are not included in the result.
     * <p>
     * The returned string will have at most {@code limit} UTF-8 bytes, and the maximum number of bytes scanned is
     * {@code limit + 2}. If {@code limit == 0} this will always throw a {@link JayoEOFException} because no bytes will
     * be scanned.
     * <p>
     * This method is safe. No bytes are discarded if the match fails, and the caller is free to try another match:
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeUtf8("12345\r\n");
     *
     * // This will throw! There must be \r\n or \n at the limit or before it.
     * buffer.readLineStrict(4);
     *
     * // No bytes have been consumed so the caller can retry.
     * assertThat(buffer.readLineStrict(5)).isEqualTo("12345");
     * }
     * </pre>
     *
     * @param limit the maximum UTF-8 bytes constituting a returned string.
     * @throws JayoEOFException         when the source does not contain a string consisting with at most {@code limit}
     *                                  bytes followed by line break characters.
     * @throws IllegalArgumentException when {@code limit} is negative.
     * @throws IllegalStateException    if this source is closed.
     */
    @NonNull String readUtf8LineStrict(final @NonNegative long limit);

    /**
     * Removes and returns a single UTF-8 code point, reading between 1 and 4 bytes as necessary.
     * <p>
     * If this source is exhausted before a complete code point can be read, this throws a {@link JayoEOFException} and
     * consumes no input.
     * <p>
     * If this source doesn't start with a properly-encoded UTF-8 code point, this method will remove 1 or more
     * non-UTF-8 bytes and return the replacement character ({@code U+FFFD}). This covers encoding problems (the input
     * is not properly-encoded UTF-8), characters out of range (beyond the 0x10ffff limit of Unicode), code points for
     * UTF-16 surrogates (U+d800..U+dfff) and overlong encodings (such as {@code 0xc080} for the NUL character in
     * modified UTF-8).
     *
     * @throws JayoEOFException      when the source is exhausted before a complete code point can be read.
     * @throws IllegalStateException if this source is closed.
     */
    @NonNegative
    int readUtf8CodePoint();

    /**
     * Removes all bytes from this source, decodes them as {@code charset}, and returns the string. Returns the empty
     * string if this source is empty.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeString("Uh uh uh¡", StandardCharsets.ISO_8859_1)
     * .writeByte(' ')
     * .writeString("You didn't say the magic word¡", StandardCharsets.ISO_8859_1);
     * assertThat(buffer.byteSize()).isEqualTo(40);
     *
     * assertThat(buffer.readString(StandardCharsets.ISO_8859_1)).isEqualTo("Uh uh uh¡ You didn't say the magic word¡");
     * assertThat(buffer.byteSize()).isEqualTo(0);
     *
     * assertThat(buffer.readString(StandardCharsets.ISO_8859_1)).isEqualTo("");
     * }
     * </pre>
     *
     * @throws IllegalStateException if this source is closed.
     */
    @NonNull String readString(final @NonNull Charset charset);

    /**
     * Removes {@code byteCount} bytes from this source, decodes them as {@code charset}, and returns the string.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeString("Uh uh uh¡", StandardCharsets.ISO_8859_1)
     * .writeByte(' ')
     * .writeString("You didn't say the magic word¡", StandardCharsets.ISO_8859_1);
     * assertThat(buffer.byteSize()).isEqualTo(40);
     *
     * assertThat(buffer.readString(14, StandardCharsets.ISO_8859_1)).isEqualTo("Uh uh uh¡ You ");
     * assertThat(buffer.byteSize()).isEqualTo(26);
     *
     * assertThat(buffer.readString(14, StandardCharsets.ISO_8859_1)).isEqualTo("didn't say the");
     * assertThat(buffer.byteSize()).isEqualTo(12);
     *
     * assertThat(buffer.readString(12, StandardCharsets.ISO_8859_1)).isEqualTo(" magic word¡");
     * assertThat(buffer.byteSize()).isEqualTo(0);
     * }
     * </pre>
     *
     * @param byteCount the number of bytes to read from this source for string decoding.
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     * @throws JayoEOFException         when this source is exhausted before reading {@code byteCount} bytes from it.
     * @throws IllegalStateException    if this source is closed.
     */
    @NonNull String readString(final @NonNegative long byteCount, final @NonNull Charset charset);

    /**
     * Returns the index of {@code b} first occurrence in this source, or {@code -1} if it doesn't contain {@code b}.
     * <p>
     * The scan terminates at source's exhaustion.
     * If {@code b} is not found in buffered data and the underlying source is not yet exhausted, then new data will be
     * read from the underlying source into the buffer.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeUtf8("Don't move! He can't see us if we don't move.");
     *
     * assertThat(buffer.indexOf('m')).isEqualTo(6);
     * }
     * </pre>
     *
     * @param b the value to find.
     * @throws IllegalStateException if this source is closed.
     */
    long indexOf(final byte b);

    /**
     * Returns the index of {@code b} first occurrence in this source at or after {@code startIndex}, or {@code -1} if
     * it doesn't contain {@code b}.
     * <p>
     * The scan terminates at source's exhaustion.
     * If {@code b} is not found in buffered data and the underlying source is not yet exhausted, then new data will be
     * read from the underlying source into the buffer.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeUtf8("Don't move! He can't see us if we don't move.");
     *
     * assertThat(buffer.indexOf('m', 12)).isEqualTo(40);
     * }
     * </pre>
     *
     * @param b          the value to find.
     * @param startIndex the start of the range (inclusive) to find {@code b}.
     * @throws IllegalArgumentException if {@code startIndex} is negative.
     * @throws IllegalStateException    if this source is closed.
     */
    long indexOf(final byte b, final @NonNegative long startIndex);

    /**
     * Returns the index of {@code b} first occurrence in this source in the range of {@code startIndex} to
     * {@code endIndex}, or {@code -1} if the range doesn't contain {@code b}.
     * <p>
     * The scan terminates at either {@code endIndex} or source's exhaustion, whichever comes first. The
     * maximum number of bytes scanned is {@code endIndex - startIndex}.
     * If {@code b} is not found in buffered data, {@code endIndex} is yet to be reached and the underlying source is
     * not yet exhausted, then new data will be read from the underlying source into the buffer.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeUtf8("Don't move! He can't see us if we don't move.");
     *
     * assertThat(buffer.indexOf('m', 12, 40)).isEqualTo(-1);
     * assertThat(buffer.indexOf('m', 12, 41)).isEqualTo(40);
     * }
     * </pre>
     *
     * @param b          the value to find.
     * @param startIndex the start of the range (inclusive) to find {@code b}.
     * @param endIndex   the end of the range (exclusive) to find {@code b}.
     * @throws IllegalArgumentException when {@code startIndex > endIndex} or either of indexes is negative.
     * @throws IllegalStateException    if this source is closed.
     */
    long indexOf(final byte b, final @NonNegative long startIndex, final @NonNegative long endIndex);

    /**
     * Returns the index of the first match for {@code byteString} in this source, or {@code -1} if it doesn't contain
     * {@code byteString}.
     * <p>
     * The scan terminates at source's exhaustion.
     * If {@code byteString} is not found in buffered data and the underlying source is not yet exhausted, then new data
     * will be read from the underlying source into the buffer.
     *
     * @param byteString the sequence of bytes to find within the source.
     * @throws IllegalStateException if this source is closed.
     */
    long indexOf(final @NonNull ByteString byteString);

    /**
     * Returns the index of the first match for {@code byteString} in this source at or after {@code startIndex}, or
     * {@code -1} if it doesn't contain {@code byteString}.
     * <p>
     * The scan terminates at source's exhaustion.
     * If {@code byteString} is not found in buffered data and the underlying source is not yet exhausted, then new data
     * will be read from the underlying source into the buffer.
     *
     * @param byteString the sequence of bytes to find within this source.
     * @param startIndex the start of the range (inclusive) to find {@code byteString}.
     * @throws IllegalArgumentException if {@code startIndex} is negative.
     * @throws IllegalStateException    if this source is closed.
     */
    long indexOf(final @NonNull ByteString byteString, final @NonNegative long startIndex);

    /**
     * Returns the first index in this source that contains any of the bytes in {@code targetBytes}, or -1 if the stream
     * is exhausted before any of the requested bytes is found.
     * <p>
     * The scan terminates at source's exhaustion.
     * If none of the bytes in {@code targetBytes} was found in buffered data and the underlying source is not yet
     * exhausted, then new data will be read from the underlying source into the buffer.
     * <pre>
     * {@code
     * ByteString ANY_VOWEL = ByteString.encodeUtf8("AEOIUaeoiu");
     *
     * Buffer buffer = Buffer.create()
     * .writeString("Dr. Alan Grant");
     *
     * assertEquals(4,  buffer.indexOfElement(ANY_VOWEL));    // 'A' in 'Alan'.
     * }
     * </pre>
     *
     * @param targetBytes the byte sequence we try to find within the source.
     * @throws IllegalStateException if this source is closed.
     */
    long indexOfElement(final @NonNull ByteString targetBytes);

    /**
     * Returns the first index in this source at or after {@code startIndex} that contains any of the bytes in
     * {@code targetBytes}, or -1 if the stream is exhausted before any of the requested bytes is found.
     * <p>
     * The scan terminates at source's exhaustion.
     * If none of the bytes in {@code targetBytes} was found in buffered data and the underlying source is not yet
     * exhausted, then new data will be read from the underlying source into the buffer.
     * <pre>
     * {@code
     * ByteString ANY_VOWEL = ByteString.encodeUtf8("AEOIUaeoiu");
     *
     * Buffer buffer = Buffer.create()
     * .writeUtf8("Dr. Alan Grant");
     *
     * assertEquals(11, buffer.indexOfElement(ANY_VOWEL, 9)); // 'a' in 'Grant'.
     * }
     * </pre>
     *
     * @param targetBytes the byte sequence we try to find within the source.
     * @param startIndex  the start of the range (inclusive) to find any of the bytes in {@code targetBytes}.
     * @throws IllegalArgumentException if {@code startIndex} is negative.
     * @throws IllegalStateException    if this source is closed.
     */
    long indexOfElement(final @NonNull ByteString targetBytes, final @NonNegative long startIndex);

    /**
     * Returns true if the bytes starting at {@code offset} in this source equal the bytes of {@code byteString}.
     * <p>
     * If {@code byteString} is not entirely found in buffered data and the underlying source is not yet exhausted, then
     * new data will be read from the underlying source into the buffer until a byte does not match, all bytes are
     * matched, or if this source is exhausted before enough bytes could determine a match.
     * <pre>
     * {@code
     * ByteString simonSays = ByteString.encodeUtf8("Simon says:");
     *
     * Buffer standOnOneLeg = Buffer.create().writeUtf8("Simon says: Stand on one leg.");
     * assertEquals(standOnOneLeg.rangeEquals(0, simonSays)).isTrue();
     *
     * Buffer payMeMoney = Buffer.create().writeUtf8("Pay me $1,000,000.");
     * assertEquals(payMeMoney.rangeEquals(0, simonSays)).isFalse();
     * }
     * </pre>
     *
     * @param offset     the start offset (inclusive) in this source to compare with {@code byteString}.
     * @param byteString the sequence of bytes we are comparing to.
     * @throws IndexOutOfBoundsException if {@code offset} is out of range of this source's indices.
     * @throws IllegalStateException     if this source is closed.
     */
    boolean rangeEquals(final @NonNegative long offset, final @NonNull ByteString byteString);

    /**
     * Returns true if {@code byteCount} bytes starting at {@code offset} in this source equal {@code byteCount} bytes
     * of {@code byteString} starting at {@code byteStringOffset}.
     * <p>
     * If {@code byteCount} bytes of {@code byteString} are not entirely found in buffered data and the underlying
     * source is not yet exhausted, then new data will be read from the underlying source into the buffer until a byte
     * does not match, all bytes are matched, or if this source is exhausted before enough bytes could determine a
     * match.
     * <pre>
     * {@code
     * ByteString simonSays = ByteString.encodeUtf8("Simon says:");
     *
     * Buffer standOnOneLeg = Buffer.create().writeUtf8("Garfunkel says: Stand on one leg.");
     * assertEquals(standOnOneLeg.rangeEquals(10, simonSays, 6, 5)).isTrue();
     *
     * Buffer payMeMoney = Buffer.create().writeUtf8("Pay me $1,000,000.");
     * assertEquals(payMeMoney.rangeEquals(0, simonSays, 3, 5)).isFalse();
     * }
     * </pre>
     *
     * @param offset           the start offset (inclusive) in this source to compare with {@code byteString}.
     * @param byteString       the sequence of bytes we are comparing to.
     * @param byteStringOffset the start offset (inclusive) in the byte string's data to compare with this source.
     * @param byteCount        the number of bytes to compare.
     * @throws IndexOutOfBoundsException if {@code offset} or {@code byteCount} is out of range of this source's
     *                                   indices, or if {@code byteStringOffset} or {@code byteCount} is out of range of
     *                                   {@code byteString} indices.
     * @throws IllegalStateException     if this source is closed.
     */
    boolean rangeEquals(final @NonNegative long offset,
                        final @NonNull ByteString byteString,
                        final @NonNegative int byteStringOffset,
                        final @NonNegative int byteCount);

    /**
     * Returns a new {@link Source} that can read data from this source without consuming it.
     * The returned source becomes invalid once this source is next read or closed.
     * <p>
     * Peek could be used to lookahead and read the same data multiple times.
     * <p>
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeUtf8("abcdefghi");
     *
     * buffer.readUtf8(3); // returns "abc", buffer contains "defghi"
     *
     * Source peek = buffer.peek();
     * peek.readUtf8(3); // returns "def", buffer contains "defghi"
     * peek.readUtf8(3); // returns "ghi", buffer contains "defghi"
     *
     * buffer.readUtf8(3) // returns "def", buffer contains "ghi"
     * }
     * </pre>
     *
     * @throws IllegalStateException if this source is closed.
     */
    @NonNull Source peek();

    /**
     * Returns a new input stream that reads from this source. Closing the stream will also close this source.
     */
    @NonNull InputStream asInputStream();

    /**
     * Consumes up to {@link ByteBuffer#remaining} bytes from this source and writes them to {@code sink} byte buffer.
     *
     * @param sink the byte buffer to write data into.
     * @return the number of bytes written.
     */
    int readAtMostTo(final @NonNull ByteBuffer sink);

    /**
     * Returns a new readable byte channel that reads from this source. Closing the byte channel will also close this
     * source.
     */
    @NonNull ReadableByteChannel asReadableByteChannel();
}
