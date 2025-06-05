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

import jayo.bytestring.Ascii;
import jayo.bytestring.ByteString;
import jayo.bytestring.Utf8;
import jayo.internal.RealReader;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

/**
 * A reader that facilitates typed data reads and keeps a buffer internally so that callers can read chunks of data
 * without requesting it from a downstream on every call.
 * <p>
 * {@link Reader} is the main Jayo interface to read data in client's code, any {@link RawReader} can be converted
 * into {@link Reader} using {@link Jayo#buffer(RawReader)}.
 * <p>
 * Depending on the kind of downstream and the number of bytes read, buffering may improve the performance by hiding
 * the latency of small reads.
 * <p>
 * The buffer is refilled on reads as necessary, but it is also possible to ensure it contains enough data using
 * {@link #require(long)} or {@link #request(long)}.
 * {@link Reader} also allows skipping unneeded prefix of data using {@link #skip(long)} and provides look ahead into
 * incoming data, buffering as much as necessary, using {@link #peek()}.
 * <p>
 * Reader's read* methods have different guarantees of how much data will be consumed from the reader and what to expect
 * in case of error.
 * <h3>Read methods' behavior and naming conventions</h3>
 * Unless stated otherwise, all read methods consume the exact number of bytes requested (or the number of bytes
 * required to represent a value of a requested type). If a reader contains fewer bytes than requested, these methods
 * will throw an exception.
 * <p>
 * Methods reading up to requested number of bytes are named as {@code readAtMost} in contrast to methods reading exact
 * number of bytes, which don't have <b>AtMost</b> suffix in their names.
 * If a reader contains fewer bytes than requested, these methods will not treat it as en error and will return
 * gracefully.
 * <p>
 * Methods returning a value as a result are named {@code read<Type>}, like {@link #readInt()} or {@link #readByte()}.
 * These methods don't consume the reader's content in case of an error.
 * <p>
 * Methods reading data into a consumer supplied as one of its arguments are named {@code read*To}, like
 * {@link #readTo(RawWriter, long)} or {@link #readAtMostTo(ByteBuffer)}. These methods consume a reader even when an
 * error occurs.
 * <p>
 * Methods moving all data from a reader to some other writer are named {@code transferTo}, like
 * {@link #transferTo(RawWriter)}.
 * <p>
 * Kotlin notice: it is recommended to follow the same naming convention for Reader extensions.
 * <p>
 * Note: Read methods on numbers use the big-endian order. If you need little-endian order, use <i>reverseBytes()</i>,
 * for example {@code Short.reverseBytes(reader.readShort())}. Jayo provides Kotlin extension functions that support
 * little-endian and unsigned numeric types.
 */
public sealed interface Reader extends RawReader permits Buffer, RealReader {
    /**
     * @return the current number of bytes that can be read (or skipped over) immediately from the buffered data without
     * requesting it from the downstream, which may be 0, or 0 when this reader is {@linkplain #exhausted() exhausted}.
     * Ongoing or future responses received after requests sent to the downstream may increase the number of available
     * bytes.
     * <p>
     * It is never correct to use the return value of this method to allocate a buffer intended to hold all data in this
     * reader.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    long bytesAvailable();

    /**
     * @return {@code false} if the buffered data already contains at least one byte, else new data will be requested
     * from the downstream. If this reader is definitely exhausted, it returns {@code true}.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    boolean exhausted();

    /**
     * Attempts to fill the buffer with at least {@code byteCount} bytes of data from the downstream.
     * <p>
     * If the buffered data already contains the required number of bytes, this method returns {@code true} immediately.
     * Else new data will be requested from the downstream and buffered until the expected number of bytes is reached,
     * or it is definitely exhausted before reaching this goal.
     *
     * @param byteCount the number of bytes that the buffer should contain.
     * @return a boolean value indicating if the requirement was successfully fulfilled. {@code false} indicates that
     * this reader was exhausted before containing {@code byteCount} bytes of data.
     * @throws IllegalArgumentException    if {@code byteCount} is negative.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    boolean request(final long byteCount);

    /**
     * Attempts to fill the buffer with at least {@code byteCount} bytes of data from the downstream and throw
     * {@link JayoEOFException} when the reader is exhausted before fulfilling the requirement.
     * <p>
     * If the buffered data already contains the required number of bytes, this method returns immediately. Else new
     * data will be requested from the downstream and buffered until the expected number of bytes is reached, or it is
     * definitely exhausted before reaching this goal.
     *
     * @param byteCount the number of bytes that the buffer should contain.
     * @throws JayoEOFException            if this reader is exhausted before containing {@code byteCount} bytes of
     *                                     data.
     * @throws IllegalArgumentException    if {@code byteCount} is negative.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    void require(final long byteCount);

    /**
     * Reads and discards {@code byteCount} bytes from this reader.
     *
     * @param byteCount the number of bytes to be skipped.
     * @throws JayoEOFException            if this reader is exhausted before skipping the requested number of bytes.
     * @throws IllegalArgumentException    if {@code byteCount} is negative.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    void skip(final long byteCount);

    /**
     * Removes a byte from this reader and returns it.
     *
     * @return the read byte value.
     * @throws JayoEOFException            if there are no more bytes to read.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    byte readByte();

    /**
     * Removes two bytes from this reader and returns a short composed of them according to the big-endian order.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .writeByte(0x7f)
     * .writeByte(0xff)
     * .writeByte(0x00)
     * .writeByte(0x0f);
     * assertThat(buffer.bytesAvailable()).isEqualTo(4);
     *
     * assertThat(buffer.readShort()).isEqualTo(32767);
     * assertThat(buffer.bytesAvailable()).isEqualTo(2);
     *
     * assertThat(buffer.readShort()).isEqualTo(15);
     * assertThat(buffer.bytesAvailable()).isEqualTo(0);
     * }
     * </pre>
     *
     * @return the read short value.
     * @throws JayoEOFException            if there are not enough data to read a short value.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    short readShort();

    /**
     * Removes four bytes from this reader and returns an int composed of them according to the big-endian order.
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
     * assertThat(buffer.bytesAvailable()).isEqualTo(8);
     *
     * assertThat(buffer.readInt()).isEqualTo(2147483647);
     * assertThat(buffer.bytesAvailable()).isEqualTo(4);
     *
     * assertThat(buffer.readInt()).isEqualTo(15);
     * assertThat(buffer.bytesAvailable()).isEqualTo(0);
     * }
     * </pre>
     *
     * @return the read int value.
     * @throws JayoEOFException            if there are not enough data to read an int value.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    int readInt();

    /**
     * Removes eight bytes from this reader and returns a long composed of them according to the big-endian order.
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
     * assertThat(buffer.bytesAvailable()).isEqualTo(16);
     *
     * assertThat(buffer.readLong()).isEqualTo(9223372036854775807L);
     * assertThat(buffer.bytesAvailable()).isEqualTo(8);
     *
     * assertThat(buffer.readLong()).isEqualTo(15L);
     * assertThat(buffer.bytesAvailable()).isEqualTo(0);
     * }
     * </pre>
     *
     * @return the read long value.
     * @throws JayoEOFException            if there are not enough data to read a long value.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    long readLong();

    /**
     * Reads a long from this reader in signed decimal form (i.e., as a string in base 10 with optional leading '-').
     * <p>
     * Reader data will be consumed until the reader is exhausted, the first occurrence of non-digit byte, or overflow
     * happened during resulting value construction.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .write("8675309 -123 00001");
     *
     * assertThat(buffer.readDecimalLong()).isEqualTo(8675309L);
     * assertThat(buffer.readByte()).isEqualTo(' ');
     * assertThat(buffer.readDecimalLong()).isEqualTo(-123L);
     * assertThat(buffer.readByte()).isEqualTo(' ');
     * assertThat(buffer.readDecimalLong()).isEqualTo(1L);
     * }
     * </pre>
     *
     * @return the read decimal long value.
     * @throws NumberFormatException       if the found digits do not fit into a long, or a decimal number was not
     *                                     present.
     * @throws JayoEOFException            if this reader is exhausted before a call of this method.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    long readDecimalLong();

    /**
     * Reads a long from this reader in hexadecimal form (i.e., as a string in base 16).
     * <p>
     * Reader data will be consumed until the reader is exhausted, the first occurrence of non-digit byte, or overflow
     * happened during resulting value construction.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .write("ffff CAFEBABE 10");
     *
     * assertThat(buffer.readHexadecimalUnsignedLong()).isEqualTo(65535L);
     * assertThat(buffer.readByte()).isEqualTo(' ');
     * assertThat(buffer.readHexadecimalUnsignedLong()).isEqualTo(0xcafebabeL);
     * assertThat(buffer.readByte()).isEqualTo(' ');
     * assertThat(buffer.readHexadecimalUnsignedLong()).isEqualTo(0x10L);
     * }
     * </pre>
     *
     * @return the read hexadecimal-long value.
     * @throws NumberFormatException       if the found hexadecimal does not fit into a long, or a hexadecimal number
     *                                     was not present.
     * @throws JayoEOFException            if this reader is exhausted before a call of this method.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    long readHexadecimalUnsignedLong();

    /**
     * Finds the first string in {@code options} that is a prefix of this reader, consumes it from this buffer, and
     * returns its index. If no byte string in {@code options} is a prefix of this reader, this returns {@code -1} and
     * no bytes are consumed.
     * <p>
     * This can be used as an alternative to {@link #readByteString} or even {@link #readString} if the set of expected
     * values is known in advance.
     * <pre>
     * {@code
     * private static Options FIELDS = Options.of(
     * Utf8.encode("depth="),  // index = 0
     * Utf8.encode("height="), // index = 1
     * Utf8.encode("width=")); // index = 2
     * // ...
     * Buffer buffer = Buffer.create()
     * .write("width=640\n")
     * .write("height=480\n");
     *
     * assertThat(buffer.select(FIELDS)).isEqualTo(2); // found the third option of FIELDS = "width="
     * assertThat(buffer.readDecimalLong()).isEqualTo(640);
     * assertThat(buffer.readByte()).isEqualTo('\n');
     * assertThat(buffer.select(FIELDS)).isEqualTo(1); // found the second option of FIELDS = "height="
     * assertThat(buffer.readDecimalLong()).isEqualTo(480);
     * assertThat(buffer.readByte()).isEqualTo('\n');
     * }
     * </pre>
     *
     * @param options a list of several potential prefixes.
     * @return the index of the matching prefix in the {@code options} list, or {@code -1} if none matched.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    int select(final @NonNull Options options);

    /**
     * Removes all bytes from this reader and returns them as a byte array.
     *
     * @throws JayoClosedResourceException if this reader is closed.
     */
    byte @NonNull [] readByteArray();

    /**
     * Removes {@code byteCount} bytes from this reader and returns them as a byte array.
     *
     * @param byteCount the number of bytes to read from this reader.
     * @throws JayoEOFException            if this reader is exhausted before containing {@code byteCount} bytes of
     *                                     data.
     * @throws IllegalArgumentException    if {@code byteCount} is negative.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    byte @NonNull [] readByteArray(final long byteCount);

    /**
     * Removes all bytes from this reader and returns them as a byte string.
     *
     * @return the byte string containing all bytes from this reader.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    @NonNull
    ByteString readByteString();

    /**
     * Removes {@code byteCount} bytes from this and returns them as a byte string.
     *
     * @param byteCount the number of bytes to read from this reader.
     * @return the byte string containing {@code byteCount} bytes from this reader.
     * @throws JayoEOFException            if this reader is exhausted before containing {@code byteCount} bytes of
     *                                     data.
     * @throws IllegalArgumentException    if {@code byteCount} is negative.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    @NonNull
    ByteString readByteString(final long byteCount);

    /**
     * Removes all UTF-8 bytes from this reader and returns them as a UTF-8 byte string.
     *
     * @return the UTF-8 byte string containing all UTF-8 bytes from this reader.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    @NonNull
    Utf8 readUtf8();

    /**
     * Removes {@code byteCount} UTF-8 bytes from this and returns them as a UTF-8 byte string.
     *
     * @param byteCount the number of bytes to read from this reader.
     * @return the UTF-8 byte string containing {@code byteCount} UTF-8 bytes from this reader.
     * @throws JayoEOFException            if this reader is exhausted before containing {@code byteCount} bytes of
     *                                     data.
     * @throws IllegalArgumentException    if {@code byteCount} is negative.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    @NonNull
    Utf8 readUtf8(final long byteCount);

    /**
     * Removes all ASCII bytes from this reader and returns them as an ASCII byte string.
     *
     * @return the UTF-8 byte string containing all ASCII bytes from this reader.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    @NonNull
    Ascii readAscii();

    /**
     * Removes {@code byteCount} ASCII bytes from this and returns them as an ASCII byte string.
     *
     * @param byteCount the number of bytes to read from this reader.
     * @return the UTF-8 byte string containing {@code byteCount} ASCII bytes from this reader.
     * @throws JayoEOFException            if this reader is exhausted before containing {@code byteCount} bytes of
     *                                     data.
     * @throws IllegalArgumentException    if {@code byteCount} is negative.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    @NonNull
    Ascii readAscii(final long byteCount);

    /**
     * Removes and returns UTF-8 encoded characters up to but not including the next line break. A line break is either
     * {@code "\n"} or {@code "\r\n"}; these characters are not included in the result.
     * <p>
     * On the end of the stream this method returns null. If the reader doesn't end with a line break, then an implicit
     * line break is assumed. {@code null} is returned once the reader is exhausted.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .write("I'm a hacker!\n")
     * .write("That's what I said: you're a nerd.\n")
     * .write("I prefer to be called a hacker!\n");
     * assertThat(buffer.bytesAvailable()).isEqualTo(81);
     *
     * assertThat(buffer.readLine()).isEqualTo("I'm a hacker!");
     * assertThat(buffer.bytesAvailable()).isEqualTo(67);
     *
     * assertThat(buffer.readLine()).isEqualTo("That's what I said: you're a nerd.");
     * assertThat(buffer.bytesAvailable()).isEqualTo(32);
     *
     * assertThat(buffer.readLine()).isEqualTo("I prefer to be called a hacker!");
     * assertThat(buffer.bytesAvailable()).isEqualTo(0);
     *
     * assertThat(buffer.readLine()).isNull();
     * assertThat(buffer.bytesAvailable()).isEqualTo(0);
     * }
     * </pre>
     *
     * @throws JayoClosedResourceException if this reader is closed.
     */
    @Nullable
    String readLine();

    /**
     * Removes and returns UTF-8 encoded characters up to but not including the next line break, throwing
     * {@link JayoEOFException} if a line break was not encountered. A line break is either {@code "\n"} or
     * {@code "\r\n"}; these characters are not included in the result.
     * <p>
     * This method is safe. No bytes are discarded if the match fails, and the caller is free to try another match.
     *
     * @throws JayoEOFException            if a line break was not encountered.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    @NonNull
    String readLineStrict();

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
     * .write("12345\r\n");
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
     * @throws JayoEOFException            when this reader does not contain a string consisting with at most
     *                                     {@code limit} bytes followed by line break characters.
     * @throws IllegalArgumentException    when {@code limit} is negative.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    @NonNull
    String readLineStrict(final long limit);

    /**
     * Removes and returns a single UTF-8 code point, reading between 1 and 4 bytes as necessary.
     * <p>
     * If this reader is exhausted before a complete code point can be read, this throws a {@link JayoEOFException} and
     * consumes no input.
     * <p>
     * If this reader doesn't start with a properly encoded UTF-8 code point, this method will remove 1 or more
     * non-UTF-8 bytes and return the replacement character ({@code U+FFFD}). This covers encoding problems (the input
     * is not properly-encoded UTF-8), characters out of range (beyond the 0x10ffff limit of Unicode), code points for
     * UTF-16 surrogates (U+d800..U+dfff) and overlong encodings (such as {@code 0xc080} for the NUL character in
     * modified UTF-8).
     *
     * @throws JayoEOFException            when this reader is exhausted before a complete code point can be read.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    int readUtf8CodePoint();

    /**
     * Removes all bytes from this reader, decodes them as UTF-8, and returns the string. Returns the empty string if
     * this reader is empty.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .write("Uh uh uh!")
     * .writeByte(' ')
     * .write("You didn't say the magic word!");
     * assertThat(buffer.bytesAvailable()).isEqualTo(40);
     *
     * assertThat(buffer.readString()).isEqualTo("Uh uh uh! You didn't say the magic word!");
     * assertThat(buffer.bytesAvailable()).isEqualTo(0);
     *
     * assertThat(buffer.readString()).isEqualTo("");
     * }
     * </pre>
     *
     * @throws JayoClosedResourceException if this reader is closed.
     */
    @NonNull
    String readString();

    /**
     * Removes {@code byteCount} bytes from this reader, decodes them as UTF-8, and returns the string.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .write("Uh uh uh!")
     * .writeByte(' ')
     * .write("You didn't say the magic word!");
     * assertThat(buffer.bytesAvailable()).isEqualTo(40);
     *
     * assertThat(buffer.readString(14)).isEqualTo("Uh uh uh! You ");
     * assertThat(buffer.bytesAvailable()).isEqualTo(26);
     *
     * assertThat(buffer.readString(14)).isEqualTo("didn't say the");
     * assertThat(buffer.bytesAvailable()).isEqualTo(12);
     *
     * assertThat(buffer.readString(12)).isEqualTo(" magic word!");
     * assertThat(buffer.bytesAvailable()).isEqualTo(0);
     * }
     * </pre>
     *
     * @param byteCount the number of bytes to read from this reader for string decoding.
     * @throws IllegalArgumentException    if {@code byteCount} is negative.
     * @throws JayoEOFException            if this reader is exhausted before containing {@code byteCount} bytes of
     *                                     data.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    @NonNull
    String readString(final long byteCount);

    /**
     * Removes all bytes from this reader, decodes them using the {@code charset} encoding, and returns the string.
     * Returns the empty string if this reader is empty.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .write("Uh uh uh¡", StandardCharsets.ISO_8859_1)
     * .writeByte(' ')
     * .write("You didn't say the magic word¡", StandardCharsets.ISO_8859_1);
     * assertThat(buffer.bytesAvailable()).isEqualTo(40);
     *
     * assertThat(buffer.readString(StandardCharsets.ISO_8859_1)).isEqualTo("Uh uh uh¡ You didn't say the magic word¡");
     * assertThat(buffer.bytesAvailable()).isEqualTo(0);
     *
     * assertThat(buffer.readString(StandardCharsets.ISO_8859_1)).isEqualTo("");
     * }
     * </pre>
     *
     * @throws JayoClosedResourceException if this reader is closed.
     */
    @NonNull
    String readString(final @NonNull Charset charset);

    /**
     * Removes {@code byteCount} bytes from this reader, decodes them using the {@code charset} encoding, and returns
     * the string.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .write("Uh uh uh¡", StandardCharsets.ISO_8859_1)
     * .writeByte(' ')
     * .write("You didn't say the magic word¡", StandardCharsets.ISO_8859_1);
     * assertThat(buffer.bytesAvailable()).isEqualTo(40);
     *
     * assertThat(buffer.readString(14, StandardCharsets.ISO_8859_1)).isEqualTo("Uh uh uh¡ You ");
     * assertThat(buffer.bytesAvailable()).isEqualTo(26);
     *
     * assertThat(buffer.readString(14, StandardCharsets.ISO_8859_1)).isEqualTo("didn't say the");
     * assertThat(buffer.bytesAvailable()).isEqualTo(12);
     *
     * assertThat(buffer.readString(12, StandardCharsets.ISO_8859_1)).isEqualTo(" magic word¡");
     * assertThat(buffer.bytesAvailable()).isEqualTo(0);
     * }
     * </pre>
     *
     * @param byteCount the number of bytes to read from this reader for string decoding.
     * @throws IllegalArgumentException    if {@code byteCount} is negative.
     * @throws JayoEOFException            if this reader is exhausted before containing {@code byteCount} bytes of
     *                                     data.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    @NonNull
    String readString(final long byteCount, final @NonNull Charset charset);

    /**
     * Removes up to {@code destination.length} bytes from this reader and copies them into {@code destination}.
     *
     * @param destination the array to which data will be written from this reader.
     * @return the number of bytes read, or {@code -1} if this reader is exhausted.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    int readAtMostTo(final byte @NonNull [] destination);

    /**
     * Removes up to {@code byteCount} bytes from this reader and copies them into {@code destination} at
     * {@code offset}.
     *
     * @param destination the byte array to which data will be written from this reader.
     * @param offset      the start offset (inclusive) in the {@code destination} of the first byte to copy.
     * @param byteCount   the number of bytes to copy.
     * @return the number of bytes read, or {@code -1} if this reader is exhausted.
     * @throws IndexOutOfBoundsException if {@code offset} or {@code byteCount} is out of range of {@code destination}
     *                                   indices.
     * @throws IllegalStateException     if this reader is closed.
     */
    int readAtMostTo(final byte @NonNull [] destination, final int offset, final int byteCount);

    /**
     * Removes exactly {@code destination.length} bytes from this reader and copies them into {@code destination}.
     *
     * @param destination the byte array to which data will be written from this reader.
     * @throws JayoEOFException            if this reader is exhausted before containing {@code destination.length}
     *                                     bytes of data.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    void readTo(final byte @NonNull [] destination);

    /**
     * Removes exactly {@code byteCount} bytes from this reader and copies them into {@code destination} at
     * {@code offset}.
     *
     * @param destination the byte array to which data will be written from this reader.
     * @param offset      the start offset (inclusive) in the {@code destination} of the first byte to copy.
     * @param byteCount   the number of bytes to copy.
     * @throws IndexOutOfBoundsException   if {@code offset} or {@code byteCount} is out of range of {@code destination}
     *                                     indices.
     * @throws JayoEOFException            if this reader is exhausted before containing {@code byteCount} bytes of
     *                                     data.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    void readTo(final byte @NonNull [] destination, final int offset, final int byteCount);

    /**
     * Removes exactly {@code byteCount} bytes from this and appends them to {@code destination}.
     *
     * @param destination the destination to which data will be written from this reader.
     * @param byteCount   the number of bytes to copy.
     * @throws JayoEOFException            if this reader is exhausted before containing {@code byteCount} bytes of
     *                                     data.
     * @throws JayoClosedResourceException if this reader or {@code destination} is closed.
     */
    void readTo(final @NonNull RawWriter destination, final long byteCount);

    /**
     * Removes all bytes from this reader and appends them to {@code destination}.
     *
     * @param destination the destination to which data will be written from this reader.
     * @return the total number of bytes written to {@code destination} which will be {@code 0L} if this reader is exhausted.
     * @throws JayoClosedResourceException if this reader or {@code destination} is closed.
     */
    long transferTo(final @NonNull RawWriter destination);

    /**
     * Returns the index of {@code b} first occurrence in this reader, or {@code -1} if it doesn't contain {@code b}.
     * <p>
     * If {@code b} is not found in the buffered data and the downstream is not yet exhausted, then new data will be
     * requested from the downstream. The scan terminates at the reader's exhaustion.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .write("Don't move! He can't see us if we don't move.");
     *
     * assertThat(buffer.indexOf('m')).isEqualTo(6);
     * }
     * </pre>
     *
     * @param b the byte value to find.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    long indexOf(final byte b);

    /**
     * Returns the index of {@code b} first occurrence in this reader at or after {@code startIndex}, or {@code -1} if
     * it doesn't contain {@code b}.
     * <p>
     * If {@code b} is not found in the buffered data and the downstream is not yet exhausted, then new data will be
     * requested from the downstream. The scan terminates at the reader's exhaustion.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .write("Don't move! He can't see us if we don't move.");
     *
     * assertThat(buffer.indexOf('m', 12)).isEqualTo(40);
     * }
     * </pre>
     *
     * @param b          the byte value to find.
     * @param startIndex the start of the range (inclusive) to find {@code b}.
     * @throws IllegalArgumentException    if {@code startIndex} is negative.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    long indexOf(final byte b, final long startIndex);

    /**
     * Returns the index of {@code b} first occurrence in this reader in the range of {@code startIndex} to
     * {@code endIndex}, or {@code -1} if the range doesn't contain {@code b}.
     * <p>
     * If {@code b} is not found in the buffered data, {@code endIndex} is yet to be reached, and the downstream is not
     * yet exhausted, then new data will be requested from the downstream. The scan terminates at either
     * {@code endIndex} or reader's exhaustion, whichever comes first. The maximum number of bytes scanned is
     * {@code endIndex - startIndex}.
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .write("Don't move! He can't see us if we don't move.");
     *
     * assertThat(buffer.indexOf('m', 12, 40)).isEqualTo(-1);
     * assertThat(buffer.indexOf('m', 12, 41)).isEqualTo(40);
     * }
     * </pre>
     *
     * @param b          the byte value to find.
     * @param startIndex the start of the range (inclusive) to find {@code b}.
     * @param endIndex   the end of the range (exclusive) to find {@code b}.
     * @throws IllegalArgumentException    when {@code startIndex > endIndex} or either of indexes is negative.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    long indexOf(final byte b, final long startIndex, final long endIndex);

    /**
     * Returns the index of the first match for {@code byteString} in this reader, or {@code -1} if it doesn't contain
     * {@code byteString}.
     * <p>
     * If {@code byteString} is not found in the buffered data and the downstream is not yet exhausted, then new data
     * will be requested from the downstream. The scan terminates at the reader's exhaustion.
     *
     * @param byteString the sequence of bytes to find within the reader.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    long indexOf(final @NonNull ByteString byteString);

    /**
     * Returns the index of the first match for {@code byteString} in this reader at or after {@code startIndex}, or
     * {@code -1} if it doesn't contain {@code byteString}.
     * <p>
     * If {@code byteString} is not found in the buffered data and the downstream is not yet exhausted, then new data
     * will be requested from the downstream. The scan terminates at the reader's exhaustion.
     *
     * @param byteString the sequence of bytes to find within this reader.
     * @param startIndex the start of the range (inclusive) to find {@code byteString}.
     * @throws IllegalArgumentException    if {@code startIndex} is negative.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    long indexOf(final @NonNull ByteString byteString, final long startIndex);

    /**
     * Returns the index of the first match for {@code byteString} in this reader in the range of {@code startIndex} to
     * {@code endIndex}, or {@code -1} if it doesn't contain {@code byteString}.
     * <p>
     * If {@code byteString} is not found in the buffered data, {@code endIndex} is yet to be reached, and the
     * downstream is not yet exhausted, then new data will be requested from the downstream. The scan terminates at
     * either {@code endIndex} or reader's exhaustion, whichever comes first. The maximum number of bytes scanned is
     * {@code endIndex - startIndex}.
     *
     * @param byteString the sequence of bytes to find within this reader.
     * @param startIndex the start of the range (inclusive) to find {@code byteString}.
     * @param endIndex   the end of the range (exclusive) to find {@code byteString}.
     * @throws IllegalArgumentException    if {@code startIndex} is negative.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    long indexOf(final @NonNull ByteString byteString, final long startIndex, final long endIndex);

    /**
     * Returns the first index in this reader that contains any of the bytes in {@code targetBytes}, or {@code -1} if
     * it doesn't contain any.
     * <p>
     * If none of the bytes in {@code targetBytes} was found in the buffered data and the downstream is not yet
     * exhausted, then new data will be requested from the downstream. The scan terminates at the reader's exhaustion.
     * <pre>
     * {@code
     * ByteString ANY_VOWEL = Utf8.encode("AEOIUaeoiu");
     *
     * Buffer buffer = Buffer.create()
     * .write("Dr. Alan Grant");
     *
     * assertEquals(4,  buffer.indexOfElement(ANY_VOWEL));    // 'A' in 'Alan'.
     * }
     * </pre>
     *
     * @param targetBytes the byte sequence we try to find within the reader.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    long indexOfElement(final @NonNull ByteString targetBytes);

    /**
     * Returns the first index in this reader at or after {@code startIndex} that contains any of the bytes in
     * {@code targetBytes}, or {@code -1} if it doesn't contain any.
     * <p>
     * If none of the bytes in {@code targetBytes} was found in the buffered data and the downstream is not yet
     * exhausted, then new data will be requested from the downstream. The scan terminates at the reader's exhaustion.
     * <pre>
     * {@code
     * ByteString ANY_VOWEL = Utf8.encode("AEOIUaeoiu");
     *
     * Buffer buffer = Buffer.create()
     * .write("Dr. Alan Grant");
     *
     * assertEquals(11, buffer.indexOfElement(ANY_VOWEL, 9)); // 'a' in 'Grant'.
     * }
     * </pre>
     *
     * @param targetBytes the byte sequence we try to find within the reader.
     * @param startIndex  the start of the range (inclusive) to find any of the bytes in {@code targetBytes}.
     * @throws IllegalArgumentException    if {@code startIndex} is negative.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    long indexOfElement(final @NonNull ByteString targetBytes, final long startIndex);

    /**
     * Returns true if the bytes starting at {@code offset} in this reader equal the bytes of {@code byteString}.
     * <p>
     * If {@code byteString} is not entirely found in the buffered data and the downstream is not yet exhausted, then
     * new data will be requested from the downstream until a byte does not match, all bytes are matched, or if this
     * reader is exhausted before enough bytes could determine a match.
     * <pre>
     * {@code
     * ByteString simonSays = Utf8.encode("Simon says:");
     *
     * Buffer standOnOneLeg = Buffer.create().write("Simon says: Stand on one leg.");
     * assertEquals(standOnOneLeg.rangeEquals(0, simonSays)).isTrue();
     *
     * Buffer payMeMoney = Buffer.create().write("Pay me $1,000,000.");
     * assertEquals(payMeMoney.rangeEquals(0, simonSays)).isFalse();
     * }
     * </pre>
     *
     * @param offset     the start offset (inclusive) in this reader to compare with {@code byteString}.
     * @param byteString the sequence of bytes we are comparing to.
     * @throws IndexOutOfBoundsException   if {@code offset} is out of range of this reader's indices.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    boolean rangeEquals(final long offset, final @NonNull ByteString byteString);

    /**
     * Returns true if {@code byteCount} bytes starting at {@code offset} in this reader equal {@code byteCount} bytes
     * of {@code byteString} starting at {@code byteStringOffset}.
     * <p>
     * If {@code byteCount} bytes of {@code byteString} are not entirely found in the buffered data and the downstream
     * is not yet exhausted, then new data will be requested from the downstream until a byte does not match, all bytes
     * are matched, or if this reader is exhausted before enough bytes could determine a match.
     * <pre>
     * {@code
     * ByteString simonSays = Utf8.encode("Simon says:");
     *
     * Buffer standOnOneLeg = Buffer.create().write("Garfunkel says: Stand on one leg.");
     * assertEquals(standOnOneLeg.rangeEquals(10, simonSays, 6, 5)).isTrue();
     *
     * Buffer payMeMoney = Buffer.create().write("Pay me $1,000,000.");
     * assertEquals(payMeMoney.rangeEquals(0, simonSays, 3, 5)).isFalse();
     * }
     * </pre>
     *
     * @param offset           the start offset (inclusive) in this reader to compare with {@code byteString}.
     * @param byteString       the sequence of bytes we are comparing to.
     * @param byteStringOffset the start offset (inclusive) in the byte string's data to compare with this reader.
     * @param byteCount        the number of bytes to compare.
     * @throws IndexOutOfBoundsException   if {@code offset} or {@code byteCount} is out of range of this reader's
     *                                     indices, or if {@code byteStringOffset} or {@code byteCount} is out of range
     *                                     of {@code byteString} indices.
     * @throws JayoClosedResourceException if this reader is closed.
     */
    boolean rangeEquals(final long offset,
                        final @NonNull ByteString byteString,
                        final int byteStringOffset,
                        final int byteCount);

    /**
     * Returns a new {@link Reader} that can read data from this reader without consuming it. The returned reader
     * becomes invalid once this reader is next read or closed.
     * <p>
     * Peek could be used to lookahead and read the same data multiple times.
     * <p>
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create()
     * .write("abcdefghi");
     *
     * buffer.readString(3); // returns "abc", buffer now contains "defghi"
     *
     * Reader peek = buffer.peek();
     * peek.readString(3); // returns "def", buffer still contains "defghi"
     * peek.readString(3); // returns "ghi", buffer still contains "defghi"
     *
     * buffer.readString(3) // returns "def", buffer now contains "ghi"
     * }
     * </pre>
     *
     * @throws JayoClosedResourceException if this reader is closed.
     */
    @NonNull
    Reader peek();

    /**
     * Returns a new input stream that reads from this reader. Closing the stream will also close this reader.
     */
    @NonNull
    InputStream asInputStream();

    /**
     * Consumes up to {@link ByteBuffer#remaining} bytes from this reader and writes them to the {@code destination}
     * byte buffer.
     *
     * @param destination the byte buffer to write data into.
     * @return the number of bytes written.
     */
    int readAtMostTo(final @NonNull ByteBuffer destination);

    /**
     * Returns a new readable byte channel that reads from this reader. Closing the byte channel will also close this
     * reader.
     */
    @NonNull
    ReadableByteChannel asReadableByteChannel();
}
