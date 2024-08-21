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

import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import jayo.exceptions.JayoException;
import jayo.external.NonNegative;
import jayo.internal.RealBuffer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ByteChannel;
import java.nio.charset.Charset;

/**
 * A collection of bytes in memory.
 * <p>
 * The buffer can be viewed as an unbound queue whose size grows with the data being written and shrinks with data being
 * consumed.
 * <p>
 * Internally, the buffer consists of a queue of data segments, and the buffer's capacity grows and shrinks in units of
 * data segments instead of individual bytes. Each data segment store binary data in a fixed-sized {@code byte[]}.
 * <ul>
 * <li><b>Moving data from one buffer to another is fast.</b> The buffer was designed to reduce memory allocations when
 * possible. Instead of copying bytes from one place in memory to another, this class just changes ownership of the
 * underlying data segments.
 * <li><b>This buffer grows with your data.</b> Just like an {@code ArrayList}, each buffer starts small. It consumes
 * only the memory it needs to.
 * <li><b>This buffer pools its byte arrays.</b> When you allocate a byte array in Java, the runtime must zero-fill the
 * requested array before returning it to you. Even if you're going to write over that space anyway. This class avoids
 * zero-fill and GC churn by pooling byte arrays.
 * </ul>
 * This buffer write and read operations on numbers use the big-endian order. If you need little-endian order, use
 * <i>reverseBytes()</i>, for example {@code Short.reverseBytes(buffer.readShort())}. Jayo provides Kotlin extension
 * functions that support little-endian and unsigned numbers.
 * <p>
 * Please read {@link UnsafeCursor} javadoc for a detailed description of how a buffer works.
 *
 * @implNote {@link Buffer} implements both {@link Reader} and {@link Writer} and could be used as a reader or a
 * writer, but unlike regular writers and readers its {@link #close}, {@link #flush}, {@link #emit},
 * {@link #emitCompleteSegments()} does not affect buffer's state and {@link #exhausted} only indicates that a buffer is
 * empty.
 */
public sealed interface Buffer extends Reader, Writer, Cloneable permits RealBuffer {
    /**
     * @return a new {@link Buffer}
     */
    static @NonNull Buffer create() {
        return new RealBuffer();
    }

    /**
     * @return the number of bytes accessible for read from this buffer.
     */
    @NonNegative
    long byteSize();

    /**
     * This method does not affect this buffer's content as there is no upstream to write data to.
     */
    @Override
    @NonNull
    Buffer emitCompleteSegments();

    /**
     * This method does not affect this buffer's content as there is no upstream to write data to.
     */
    @Override
    @NonNull
    Buffer emit();

    /**
     * This method does not affect this buffer's content as there is no upstream to write data to.
     */
    @Override
    void flush();

    /**
     * Copy all bytes from this buffer to {@code out} stream. This method does not consume data from this buffer.
     *
     * @param out the stream to copy data into.
     * @return {@code this}
     */
    @NonNull
    Buffer copyTo(final @NonNull OutputStream out);

    /**
     * Copy {@code (getSize() - offset)} bytes from this buffer, starting at {@code offset}, to {@code out} stream. This
     * method does not consume data from this buffer.
     *
     * @param out    the stream to copy data into.
     * @param offset the start offset (inclusive) in this buffer of the first byte to copy.
     * @return {@code this}
     * @throws IndexOutOfBoundsException if {@code offset} is out of this buffer bounds
     *                                   ({@code [0..buffer.byteSize())}).
     */
    @NonNull
    Buffer copyTo(final @NonNull OutputStream out, final @NonNegative long offset);

    /**
     * Copy {@code byteCount} bytes from this buffer, starting at {@code offset}, to {@code out} stream. This method
     * does not consume data from this buffer.
     *
     * @param out       the stream to copy data into.
     * @param offset    the start offset (inclusive) in this buffer of the first byte to copy.
     * @param byteCount the number of bytes to copy.
     * @return {@code this}
     * @throws IndexOutOfBoundsException if {@code offset} or {@code byteCount} is out of this buffer bounds
     *                                   ({@code [0..buffer.byteSize())}).
     */
    @NonNull
    Buffer copyTo(final @NonNull OutputStream out,
                  final @NonNegative long offset, final @NonNegative long byteCount);

    /**
     * Copy all bytes from this buffer to {@code out} buffer. This method does not consume data from this buffer.
     *
     * @param out the destination buffer to copy data into.
     * @return {@code this}
     */
    @NonNull
    Buffer copyTo(final @NonNull Buffer out);

    /**
     * Copy {@code (getSize() - offset)} bytes from this buffer, starting at {@code offset}, to {@code out} buffer. This
     * method does not consume data from this buffer.
     *
     * @param out    the destination buffer to copy data into.
     * @param offset the start offset (inclusive) in this buffer of the first byte to copy.
     * @return {@code this}
     * @throws IndexOutOfBoundsException if {@code offset} is out of this buffer bounds
     *                                   ({@code [0..buffer.byteSize())}).
     */
    @NonNull
    Buffer copyTo(final @NonNull Buffer out, final @NonNegative long offset);

    /**
     * Copy {@code byteCount} bytes from this buffer, starting at {@code offset}, to {@code out} buffer. This method
     * does not consume data from this buffer.
     *
     * @param out       the destination buffer to copy data into.
     * @param offset    the start offset (inclusive) in this buffer of the first byte to copy.
     * @param byteCount the number of bytes to copy.
     * @return {@code this}
     * @throws IndexOutOfBoundsException if {@code offset} or {@code byteCount} is out of this buffer bounds
     *                                   ({@code [0..buffer.byteSize())}).
     */
    @NonNull
    Buffer copyTo(final @NonNull Buffer out,
                  final @NonNegative long offset, final @NonNegative long byteCount);

    /**
     * Consumes all bytes from this buffer and writes them to {@code out} stream.
     *
     * @param out the destination stream to write data into.
     * @return {@code this}
     */
    @NonNull
    Buffer readTo(final @NonNull OutputStream out);

    /**
     * Consumes {@code byteCount} bytes from this buffer and writes them to {@code out} stream.
     *
     * @param out       the destination stream to write data into.
     * @param byteCount the number of bytes to copy.
     * @return {@code this}
     * @throws IllegalArgumentException if {@code byteCount} is negative or exceeds this buffer size.
     */
    @NonNull
    Buffer readTo(final @NonNull OutputStream out, final @NonNegative long byteCount);

    /**
     * Read and exhaust bytes from {@code input} stream into this buffer. Stops reading data on {@code input}
     * exhaustion.
     *
     * @param input the stream to read data from.
     * @return {@code this}
     */
    @NonNull
    Buffer transferFrom(final @NonNull InputStream input);

    /**
     * Read {@code byteCount} bytes from {@code input} stream into this buffer. Throws an exception if {@code input} is
     * exhausted before reading {@code byteCount} bytes.
     *
     * @param input     the stream to read data from.
     * @param byteCount the number of bytes to read from {@code input}.
     * @return {@code this}
     * @throws JayoException            if {@code input} was exhausted before reading {@code byteCount} bytes from it.
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     */
    @NonNull
    Buffer write(final @NonNull InputStream input, final @NonNegative long byteCount);

    /**
     * @return the number of bytes in segments that are fully filled and are no longer writable.
     * This is the number of bytes that can be flushed immediately to an underlying writer without harming throughput.
     */
    @NonNegative
    long completeSegmentByteCount();

    /**
     * @return the byte at the {@code position} index.
     * <p>
     * Use of this method may expose significant performance penalties, and it's not recommended to use it for
     * sequential access to a range of bytes within the buffer.
     * @throws IndexOutOfBoundsException if {@code position} is negative or greater or equal to {@link #byteSize()}.
     */
    byte getByte(final @NonNegative long pos);

    /**
     * Discards all bytes in this buffer.
     * <p>
     * Call to this method is equivalent to {@link #skip(long)} with {@code byteCount = buffer.byteSize()}, call this
     * method when you're done with a buffer, its segments will return to the pool.
     */
    void clear();

    /**
     * Discards {@code byteCount} bytes, starting from the head of this buffer.
     *
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     */
    @Override
    void skip(final @NonNegative long byteCount);

    @Override
    @NonNull
    Buffer write(final @NonNull ByteString byteString);

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    @Override
    @NonNull
    Buffer write(final @NonNull ByteString byteString,
                 final @NonNegative int offset, final @NonNegative int byteCount);

    @Override
    @NonNull
    Buffer writeUtf8(final @NonNull Utf8 utf8);

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    @Override
    @NonNull
    Buffer writeUtf8(final @NonNull Utf8 utf8,
                     final @NonNegative int offset, final @NonNegative int byteCount);

    @Override
    @NonNull
    Buffer write(final byte @NonNull [] source);

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    @Override
    @NonNull
    Buffer write(final byte @NonNull [] source,
                 final @NonNegative int offset, final @NonNegative int byteCount);

    /**
     * @throws IllegalArgumentException {@inheritDoc}
     */
    @Override
    @NonNull
    Buffer write(final @NonNull RawReader reader, final @NonNegative long byteCount);

    @Override
    @NonNull
    Buffer writeUtf8(final @NonNull CharSequence charSequence);

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws IllegalArgumentException  {@inheritDoc}
     */
    @Override
    @NonNull
    Buffer writeUtf8(final @NonNull CharSequence charSequence,
                     final @NonNegative int startIndex,
                     final @NonNegative int endIndex);

    @Override
    @NonNull
    Buffer writeUtf8CodePoint(final @NonNegative int codePoint);

    @Override
    @NonNull
    Buffer writeString(final @NonNull String string, final @NonNull Charset charset);

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws IllegalArgumentException  {@inheritDoc}
     */
    @Override
    @NonNull
    Buffer writeString(final @NonNull String string,
                       @NonNegative int startIndex,
                       @NonNegative int endIndex,
                       @NonNull Charset charset);

    @Override
    @NonNull
    Buffer writeByte(final byte b);

    @Override
    @NonNull
    Buffer writeShort(final short s);

    @Override
    @NonNull
    Buffer writeInt(final int i);

    @Override
    @NonNull
    Buffer writeLong(final long l);

    @Override
    @NonNull
    Buffer writeDecimalLong(final long l);

    @Override
    @NonNull
    Buffer writeHexadecimalUnsignedLong(final long l);

    /**
     * @param digest the chosen message digest algorithm to use for hashing.
     * @return the hash of this buffer.
     */
    @NonNull
    ByteString hash(final @NonNull Digest digest);

    /**
     * @param hMac the chosen "Message Authentication Code" (MAC) algorithm to use.
     * @param key  the key to use for this MAC operation.
     * @return the MAC result of this buffer.
     * @throws IllegalArgumentException if the {@code key} is invalid
     */
    @NonNull
    ByteString hmac(final @NonNull Hmac hMac, final @NonNull ByteString key);

    /**
     * Returns a new byte channel that read from and writes to this buffer.
     */
    @NonNull
    ByteChannel asByteChannel();

    /**
     * @return a deep copy of this buffer.
     */
    @NonNull
    Buffer copy();

    /**
     * @return a deep copy of this buffer. This is the same as {@link #copy()}
     */
    @NonNull
    Buffer clone();

    /**
     * @return a human-readable string that describes the contents of this buffer. For buffers containing
     * few bytes, this is a string like {@code Buffer(size=4 hex=0000ffff)}. However, if the buffer is too large,
     * a string will contain its size and only a prefix of data, like {@code Buffer(size=1024 hex=01234…)}.
     * Thus, the returned string of this method cannot be used to compare buffers or verify buffer's content.
     */
    @Override
    @NonNull
    String toString();

    /**
     * @return a byte string containing a copy of all the bytes of this buffer. This method does not consume data from
     * this buffer.
     * @apiNote A {@link ByteString} is immutable
     */
    @NonNull
    ByteString snapshot();

    /**
     * @return a byte string containing a copy of the first {@code byteCount} bytes of this buffer. This method does not
     * consume data from this buffer.
     * @throws IndexOutOfBoundsException if {@code byteCount > buffer.byteSize()}.
     * @apiNote A {@link ByteString} is immutable
     */
    @NonNull
    ByteString snapshot(final @NonNegative int byteCount);

    /**
     * @return an unsafe cursor to only read this buffer's data. Always call {@link UnsafeCursor#close} when done with
     * the cursor. This is convenient with Java try-with-resource and Kotlin's {@code use} extension function.
     * @apiNote {@link UnsafeCursor} exposes privileged access to the internal memory segments of a buffer. This handle
     * is unsafe because it does not enforce its own invariants. Instead, it assumes a careful user who has studied
     * Jayo's implementation details and their consequences.
     */
    @NonNull
    UnsafeCursor readUnsafe();

    /**
     * @param unsafeCursor an existing unsafe cursor
     * @return an unsafe cursor to only read this buffer's data, reusing the provided {@code unsafeCursor}. Always call
     * {@link UnsafeCursor#close} when done with the cursor. This is convenient with Java try-with-resource and Kotlin's
     * {@code use} extension function.
     * @apiNote {@link UnsafeCursor} exposes privileged access to the internal memory segments of a buffer. This handle
     * is unsafe because it does not enforce its own invariants. Instead, it assumes a careful user who has studied
     * Jayo's implementation details and their consequences.
     */
    @NonNull
    UnsafeCursor readUnsafe(final @NonNull UnsafeCursor unsafeCursor);

    /**
     * @return an unsafe cursor to read and write this buffer's data. Always call {@link UnsafeCursor#close} when done
     * with the cursor. This is convenient with Java try-with-resource and Kotlin's {@code use} extension function.
     * @apiNote {@link UnsafeCursor} exposes privileged access to the internal memory segments of a buffer. This handle
     * is unsafe because it does not enforce its own invariants. Instead, it assumes a careful user who has studied
     * Jayo's implementation details and their consequences.
     */
    @NonNull
    UnsafeCursor readAndWriteUnsafe();

    /**
     * @param unsafeCursor an existing unsafe cursor
     * @return an unsafe cursor to read and write this buffer's data, reusing the provided {@code unsafeCursor}. Always
     * call {@link UnsafeCursor#close} when done with the cursor. This is convenient with Java try-with-resource and
     * Kotlin's {@code use} extension function.
     * @apiNote {@link UnsafeCursor} exposes privileged access to the internal memory segments of a buffer. This handle
     * is unsafe because it does not enforce its own invariants. Instead, it assumes a careful user who has studied
     * Jayo's implementation details and their consequences.
     */
    @NonNull
    UnsafeCursor readAndWriteUnsafe(final @NonNull UnsafeCursor unsafeCursor);

    /**
     * A handle to the underlying data in a buffer. This handle is unsafe because it does not enforce its own
     * invariants. Instead, it assumes a careful user who has studied Jayo's implementation details and their
     * consequences.
     * <h2>Buffer Internals</h2>
     * Most code should use {@link Buffer} as a black box: a class that holds 0 or more bytes of data with efficient
     * APIs to append data to the end and to consume data from the front. Usually this is also the most efficient way to
     * use buffers because it allows Jayo to employ several optimizations, including:
     * <ul>
     * <li><b>Fast Allocation:</b> Buffers use a shared pool of memory that is not zero-filled before use.
     * <li><b>Fast Resize:</b> A buffer's capacity can change without copying its contents.
     * <li><b>Fast Move:</b> Memory ownership can be reassigned from one buffer to another.
     * <li><b>Fast Copy:</b> Multiple buffers can share the same underlying memory.
     * <li><b>Fast Encoding and Decoding:</b> Common operations like UTF-8 encoding and decimal decoding do not require
     * intermediate objects to be allocated.
     * <li><b>Concurrency:</b> A buffer is a SPSC Single Producer Single Consumer lock-free data structure. In the Jayo
     * world this means that a thread can write data using all the {@link Writer} methods, and another thread can read
     * data using all the {@link Reader} methods concurrently.
     * </ul>
     * These optimizations all leverage the way Jayo stores data internally. Jayo buffers are implemented using a
     * singly-linked queue of segments. Each segment holds a {@code bye[]} of 16_709 bytes. Each segment has two
     * indexes: {@code pos}, the offset of the first byte of the first byte of the array containing application data,
     * and {@code limit}, the offset of the first byte beyond {@code pos} whose data is undefined.
     * <p>
     * New buffers are empty and have no segments:
     * <pre>
     * {@code
     * Buffer buffer = Buffer.create();
     * }
     * </pre>
     * We append 7 bytes of data to the end of our empty buffer. Internally, the buffer allocates a segment and writes
     * its new data there. This single segment has a byte array of 16_709 bytes but only 7 bytes of data in it:
     * <pre>
     * {@code
     * buffer.writeUtf8("unicorn");
     *
     * // [ 'u', 'n', 'i', 'c', 'o', 'r', 'n', '?', '?', '?', ...]
     * //    ^                                  ^
     * // pos = 0                          limit = 7
     * }
     * </pre>
     * When we read 4 bytes of data from the buffer, it finds its first segment and returns that data to us. As bytes
     * are read the data is consumed. The segment tracks this by adjusting its internal indices.
     * <pre>
     * {@code
     * buffer.readUtf8(4); // "seal"
     *
     * // [ 'u', 'n', 'i', 'c', 'o', 'r', 'n', '?', '?', '?', ...]
     * //                        ^              ^
     * //                     pos = 4      limit = 7
     * }
     * </pre>
     * As we write data into a buffer we fill up its internal segments. When a write operation doesn't fit into a
     * buffer's last segment, an additional segment is obtained from the internal segment pool and appended to the queue
     * so the write operation continues in this new segment. The segment pool may return a segment from the pool if one
     * is available, or allocate a brand-new segment with its fresh new byte array. Each segment has its own pos and
     * limit indexes tracking where the user's data begins and ends.
     * Let's illustrate that with a Kotlin sample
     * <pre>
     * {@code
     * val xoxo = Buffer()
     * xoxo.writeUtf8("xo".repeat(10_000))
     *
     * // [ 'x', 'o', 'x', 'o', 'x', 'o', 'x', 'o', ..., 'x', 'o', 'x']
     * //    ^                                                      ^
     * // pos = 0                                             limit = 16_709
     * //
     * // [ 'o', 'x', 'o', ..., 'x', 'o', 'x', 'o', '?', '?', '?', ...]
     * //    ^                                       ^
     * // pos = 0                               limit = 3_291
     * }
     * </pre>
     * The pos index is always <b>inclusive</b> and the limit index is always <b>exclusive</b>. The data preceding the
     * pos index is undefined, and the data at and following the limit index is undefined.
     * <p>
     * After the last byte of a segment has been read, that segment may be returned to the segment pool. In addition to
     * reducing the need to do garbage collection, segment pooling also saves the JVM from needing to zero-fill byte
     * arrays. Jayo doesn't need to zero-fill its arrays because it always writes memory before it reads it. But if you
     * look at a segment in a debugger you may see its effects. In the example below, let's assume that one of the
     * "xoxo" segments above is reused in an unrelated buffer:
     * <pre>
     * {@code
     * Buffer abc = Buffer.create();
     * abc.writeUtf8("abc");
     *
     * // [ 'a', 'b', 'c', 'o', 'x', 'o', 'x', 'o', ...]
     * //    ^              ^
     * // pos = 0     limit = 3
     * }
     * </pre>
     * There is an optimization in {@link Buffer#copy()} and other methods that allows two segments to share a slice of
     * the same underlying byte array. Clones can't write to the shared byte array; instead they allocate a new
     * (private) segment early.
     * <pre>
     * {@code
     * val nana = Buffer()
     * nana.writeUtf8("na".repeat(2_500))
     * nana.readUtf8(2) // reads "na"
     *
     * // [ 'n', 'a', 'n', 'a', ..., 'n', 'a', 'n', 'a', '?', '?', '?', ...]
     * //              ^                                  ^
     * //           pos = 2                         limit = 5000
     *
     * nana2 = nana.clone()
     * nana2.writeUtf8("batman")
     *
     * // this segment and its byte[] is shared between nana and nana2 buffers
     * //                                 ↓
     * // [ 'n', 'a', 'n', 'a', ..., 'n', 'a', 'n', 'a', '?', '?', '?', ...]
     * //              ^                                  ^
     * //           pos = 2                         limit = 5000
     * //
     * // [ 'b', 'a', 't', 'm', 'a', 'n', '?', '?', '?', ...]
     * //    ^                             ^
     * //  pos = 0                    limit = 6
     * }
     * </pre>
     * Segments are not shared when the shared region is small (i.e. less than 1 KiB). This is intended to prevent
     * fragmentation in sharing-heavy use cases.
     *
     * <h2>Unsafe Cursor API</h2>
     *
     * This class exposes privileged access to the internal byte arrays of a buffer. A cursor either references the
     * data of a single segment, it is before the first segment ({@code offset == -1}), or it is after the last segment
     * ({@code offset == buffer.byteSize()}).
     * <p>
     * Call {@link UnsafeCursor#seek(long)} to move the cursor to the segment that contains a specified offset.
     * After seeking, {@link UnsafeCursor#data} references the segment's internal byte array, {@link UnsafeCursor#pos}
     * is the segment's start and {@link UnsafeCursor#limit} is its end.
     * <p>
     * Call {@link UnsafeCursor#next()} to advance the cursor to the next segment. This returns -1 if there are no
     * further segments in the buffer.
     * <p>
     * Use {@link Buffer#readUnsafe()} to create a cursor to read buffer data and {@link Buffer#readAndWriteUnsafe()} to
     * create a cursor to read and write buffer data. In either case, always call {@link UnsafeCursor#close()} when done
     * with a cursor. This is convenient with Java try-with-resource and Kotlin's {@code use} extension function. In
     * this Kotlin example we read all the bytes in a buffer into a byte array:
     * <pre>
     * {@code
     * val bufferBytes = ByteArray(buffer.size.toInt())
     *
     * buffer.readUnsafe().use { cursor ->
     *   while (cursor.next() != -1) {
     *     System.arraycopy(cursor.data, cursor.pos,
     *     bufferBytes, cursor.offset.toInt(), cursor.limit - cursor.pos);
     *   }
     * }
     * }
     * </pre>
     * Change the capacity of a buffer with {@link UnsafeCursor#resizeBuffer(long)}. This is only permitted for
     * read+write cursors. The buffer's size always changes from the end: shrinking it removes bytes from the end;
     * growing it adds capacity to the end.
     * <h2>Warnings</h2>
     * Most application developers should avoid this API. Those that must use this API should respect these warnings.
     * <ul>
     * <li><b>Don't mutate a cursor.</b> This class has public, non-final fields because that is convenient for
     * low-level I/O frameworks. Never assign values to these fields; instead use the cursor API to adjust these.
     * <li><b>Never mutate {@code data} unless you have read+write access.</b> You are on the honor system to never
     * write the buffer in read-only mode. Read-only mode may be more efficient than read+write mode because it does not
     * need to make private copies of shared segments.
     * <li><b>Only access data in {@code [pos..limit)}.</b> Other data in the byte array is undefined! It may contain
     * private or sensitive data from other parts of your process.
     * <li><b>Always fill the new capacity when you grow a buffer.</b> New capacity is not zero-filled and may contain
     * data from other parts of your process. Avoid leaking this information by always writing something to the
     * newly-allocated capacity. Do not assume that new capacity will be filled with {@code 0}; it will not be.
     * <li><b>Do not access a buffer while it is being accessed by a cursor.</b> Even simple read-only operations like
     * {@link Buffer#clone()} are unsafe because they mark segments as shared.
     * <li><b>Do not hard-code the segment size in your application.</b> It is possible that segment sizes will change
     * with advances in hardware. Future versions of Jayo may even have heterogeneous segment sizes.
     * </ul>
     * These warnings are intended to help you to use this API safely. It's here for developers that need absolutely the
     * most throughput.
     * <p>
     * Since that's you, here's one final performance tip. You can reuse instances of this class if you like. Use the
     * overloads of {@link Buffer#readUnsafe(UnsafeCursor)} and {@link Buffer#readAndWriteUnsafe(UnsafeCursor)} that
     * take a cursor parameter and close it after use.
     */
    sealed abstract class UnsafeCursor implements AutoCloseable permits RealBuffer.RealUnsafeCursor {
        /**
         * @return a new {@link UnsafeCursor}
         */
        public static @NonNull UnsafeCursor create() {
            return new RealBuffer.RealUnsafeCursor();
        }

        public @Nullable Buffer buffer = null;
        public boolean readWrite = false;
        public @NonNegative long offset = -1L;
        public byte[] data = null;
        public @NonNegative int pos = -1;
        public @NonNegative int limit = -1;

        /**
         * Seeks to the next range of bytes, advancing the offset by {@code limit - pos}.
         *
         * @return the size of the readable range (at least 1), or -1 if we have reached the end of the buffer and there
         * are no more bytes to read.
         */
        public abstract int next();

        /**
         * Reposition the cursor so that the data at {@code offset} is readable at {@code data.get(pos)}.
         *
         * @return the number of bytes readable in {@link #data} (at least 1), or -1 if there are no data to read.
         */
        public abstract int seek(final @NonNegative long offset);

        /**
         * Change the size of the buffer so that it equals {@code newSize} by either adding new capacity at the end or
         * truncating the buffer at the end. Newly added capacity may span multiple segments.
         * <p>
         * As a side effect this cursor will {@link #seek(long)}. If the buffer is being enlarged it will move
         * {@link #offset} to the first byte of newly-added capacity. This is the size of the buffer prior to the
         * {@code resizeBuffer()} call. If the buffer is being shrunk it will move {@link #offset} to the end of the
         * buffer.
         * <p>
         * Warning: it is the caller’s responsibility to write new data to every byte of the newly-allocated capacity.
         * Failure to do so may cause serious security problems as the data in the returned buffers is not zero filled.
         * Buffers may contain dirty pooled segments that hold very sensitive data from other parts of the current
         * process.
         *
         * @return the previous size of the buffer.
         */
        public abstract @NonNegative long resizeBuffer(final long newSize);

        /**
         * Grow the buffer by adding a <b>contiguous range</b> of capacity in a single segment. This adds at least
         * {@code minByteCount} bytes but may add up to a full segment of additional capacity.
         * <p>
         * As a side effect this cursor will {@link #seek(long)}. It will move {@link #offset} to the first byte of
         * newly-added capacity. This is the size of the buffer prior to the {@code expandBuffer()} call.
         * <p>
         * If {@code minByteCount} bytes are available in the buffer's current tail segment that will be used; otherwise
         * another segment will be allocated and appended. In either case this returns the number of bytes of capacity
         * added to this buffer.
         * <p>
         * Warning: it is the caller’s responsibility to either write new data to every byte of the newly-allocated
         * capacity, or to {@linkplain UnsafeCursor#resizeBuffer shrink} the buffer to the data written. Failure to do
         * so may cause serious security problems as the data in the returned buffers is not zero filled. Buffers may
         * contain dirty pooled segments that hold very sensitive data from other parts of the current process.
         *
         * @param minByteCount the size of the contiguous capacity. Must be positive and not greater than the capacity
         *                     size of a single segment (8 KiB).
         * @return the number of bytes expanded by. Not less than {@code minByteCount}.
         */
        public abstract long expandBuffer(final int minByteCount);

        /**
         * Reset this UnsafeCursor
         */
        @Override
        public abstract void close();
    }
}
