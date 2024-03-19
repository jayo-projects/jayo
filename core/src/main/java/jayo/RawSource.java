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

import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.io.InputStream;

/**
 * Supplies a stream of bytes. RawSource is a base interface for Jayo data suppliers.
 * <p>
 * This interface should be implemented to read data from wherever it's located: from the network, storage, or a buffer
 * in memory. Sources may be layered to transform supplied data, such as to decompress, decrypt, or remove protocol
 * framing.
 * <p>
 * <b>Most applications shouldn't operate on a raw source directly</b>, but rather on a {@link Source} which is both
 * more efficient and more convenient. Use {@link Jayo#buffer(RawSource)} to wrap any source with a buffer.
 * <p>
 * Sources are easy to test: just use a {@link Buffer} in your tests, and fill it with the data your application needs
 * to read.
 * <h3>Comparison with InputStream</h3>
 * This interface is functionally equivalent to {@link java.io.InputStream}.
 * <p>
 * {@code InputStream} requires multiple layers when consumed data is heterogeneous: a {@code java.io.DataInputStream}
 * for primitive values, a {@code java.io.BufferedInputStream} for buffering, and {@code java.io.InputStreamReader} for
 * strings. This library uses {@link Source} for all of the above.
 * <p>
 * Source is also easier to layer: it avoids the impossible-to-implement {@link InputStream#available()} method.
 * Instead, callers specify how many bytes they {@linkplain Source#require(long) require}.
 * <p>
 * Source omits the unsafe-to-compose {@link java.io.InputStream#mark(int)} state that is tracked by {@code InputStream}
 * and the associated {@link InputStream#reset()} method; instead, callers just buffer what they need.
 * <p>
 * When implementing a source, you don't need to worry about the {@link InputStream#read()} method that is awkward to
 * implement efficiently and returns one of 257 possible values.
 * <p>
 * Source has a stronger {@code skip} method: {@link Source#skip(long)} won't return prematurely.
 * <h3>Interop with InputStream</h3>
 * Use {@link Jayo#source(InputStream)} to adapt an {@code InputStream} to a source. Use {@link Source#asInputStream()}
 * to adapt a source to an {@code InputStream}.
 *
 * @implSpec Implementors should abstain from throwing exceptions other than those that are documented for RawSource
 * methods.
 */
public interface RawSource extends AutoCloseable {
    /**
     * Removes at least 1, and up to {@code byteCount} bytes from this and appends them to {@code sink}.
     *
     * @param sink      the destination to write the data from this source.
     * @param byteCount the number of bytes to read.
     * @return the number of bytes read, or {@code -1L} if this source is exhausted.
     * @throws IllegalArgumentException when {@code byteCount} is negative.
     * @throws IllegalStateException    when the source is closed.
     */
    long readAtMostTo(final @NonNull Buffer sink, final @NonNegative long byteCount);

    /**
     * Closes this source and releases the resources held by this source. It is an error to read a closed source. It is
     * safe to close a source more than once.
     */
    @Override
    void close();
}
