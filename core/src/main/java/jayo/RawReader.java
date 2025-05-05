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

import org.jspecify.annotations.NonNull;

import java.io.Closeable;
import java.io.InputStream;

/**
 * Supplies a stream of bytes. RawReader is a base interface for Jayo data suppliers.
 * <p>
 * This interface should be implemented to read data from wherever it's located: from the network, storage, or a buffer
 * in memory. Readers may be layered to transform supplied data, such as to decompress, decrypt, or remove protocol
 * framing.
 * <p>
 * <b>Most applications shouldn't operate on a {@code RawReader} directly</b>, but rather on a
 * {@linkplain Reader jayo.Reader} which is both more efficient and more convenient. Use {@link Jayo#buffer(RawReader)}
 * to wrap any reader with a buffer.
 * <p>
 * Note: Readers are straightforward to test: use a {@link Buffer} in your tests, and fill it with the data your
 * application needs to read.
 * <h3>Comparison with InputStream</h3>
 * This interface is functionally equivalent to {@link java.io.InputStream}.
 * <p>
 * {@code InputStream} requires multiple layers when consumed data is heterogeneous: a {@code java.io.DataInputStream}
 * for primitive values, a {@code java.io.BufferedInputStream} for buffering, and {@code java.io.InputStreamReader} for
 * strings. This library uses {@linkplain Reader jayo.Reader} for all of the above.
 * <p>
 * Reader is also easier to layer: it avoids the impossible-to-implement {@link InputStream#available()} method.
 * Instead, callers specify how many bytes they {@linkplain Reader#require(long) require}.
 * <p>
 * Reader omits the unsafe-to-compose {@link java.io.InputStream#mark(int)} state that is tracked by {@code InputStream}
 * and the associated {@link InputStream#reset()} method; instead, callers just buffer what they need.
 * <p>
 * When implementing a {@code RawReader}, you don't need to worry about the {@link InputStream#read()} method that is
 * awkward to implement efficiently and returns one of 257 possible values.
 * <p>
 * Reader has a stronger {@code skip} method: {@link Reader#skip(long)} won't return prematurely.
 * <h3>Interop with InputStream</h3>
 * Use {@link Jayo#reader(InputStream)} to adapt an {@code InputStream} to a {@code RawReader}. Use
 * {@link Reader#asInputStream()} to adapt a {@code RawReader} to an {@code InputStream}.
 *
 * @implSpec Implementors should abstain from throwing exceptions other than those that are documented below.
 */
public interface RawReader extends Closeable {
    /**
     * Removes at least 1, and up to {@code byteCount} bytes from this and appends them to {@code writer}.
     *
     * @param writer    the destination to write the data from this reader.
     * @param byteCount the number of bytes to read.
     * @return the number of bytes read, or {@code -1L} if this reader is exhausted.
     * @throws IllegalArgumentException    if {@code byteCount} is negative.
     * @throws JayoClosedResourceException if this reader is closed.
     * @throws JayoException               if an I/O error occurs.
     */
    long readAtMostTo(final @NonNull Buffer writer, final long byteCount);

    /**
     * Closes this reader and releases the resources held by this reader. Trying to read in a closed reader will throw a
     * {@link JayoClosedResourceException}.
     * <p>
     * It is safe to close a reader more than once, but only the first call has an effect.
     *
     * @throws JayoException if an I/O error occurs during the closing phase.
     */
    @Override
    void close();
}
