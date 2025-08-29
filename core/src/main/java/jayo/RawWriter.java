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
import java.io.Flushable;
import java.io.OutputStream;

/**
 * Receives a stream of bytes. RawWriter is a base interface for Jayo data receivers.
 * <p>
 * This interface should be implemented to write data wherever it's necessary: to the network, storage, or a buffer in
 * memory. Writers may be layered to transform received data, such as to compress, encrypt, throttle, or add protocol
 * framing.
 * <p>
 * <b>Most application code shouldn't operate on a {@code RawWriter} directly</b>, but rather on a
 * {@linkplain Writer jayo.Writer} which is both more efficient and more convenient. Use {@link Jayo#buffer(RawWriter)}
 * to wrap any writer with a buffer.
 * <p>
 * Writers are easy to test: use a {@link Buffer} in your tests, and read from it to confirm it received the data that
 * was expected.
 * <h3>Comparison with OutputStream</h3>
 * This interface is functionally equivalent to {@link java.io.OutputStream}.
 * <p>
 * {@code OutputStream} requires multiple layers when emitted data is heterogeneous: a {@code java.io.DataOutputStream}
 * for primitive values, a {@code java.io.BufferedOutputStream} for buffering, and {@code java.io.OutputStreamWriter}
 * for charset encoding. This library uses {@linkplain Writer jayo.Writer} for all of the above.
 * <p>
 * Writer is also easier to layer: there is no {@link OutputStream#write(int)} method that is awkward to implement
 * efficiently.
 * <h3>Interop with OutputStream</h3>
 * Use {@link Jayo#writer(OutputStream)} to adapt an {@code OutputStream} to a {@code RawWriter}. Use
 * {@link Writer#asOutputStream()} to adapt a {@code RawWriter} to an {@code OutputStream}.
 *
 * @implSpec Implementors should abstain from throwing exceptions other than those that are documented below.
 */
public interface RawWriter extends Closeable, Flushable {
    /**
     * Removes {@code byteCount} bytes from {@code source} and appends them to this writer.
     *
     * @param source    the source to read data from.
     * @param byteCount the number of bytes to write.
     * @throws IndexOutOfBoundsException   if the {@code source}'s byte size is below {@code byteCount} or
     *                                     {@code byteCount} is negative.
     * @throws JayoClosedResourceException if this writer is closed.
     * @throws JayoException               if an I/O error occurs.
     */
    void writeFrom(final @NonNull Buffer source, final long byteCount);

    /**
     * Pushes all buffered bytes to their final destination.
     *
     * @throws JayoClosedResourceException if this writer is closed.
     * @throws JayoException               if an I/O error occurs.
     */
    @Override
    void flush();

    /**
     * Pushes all buffered bytes to their final destination and releases the resources held by this writer. Trying to
     * write to a closed writer will throw a {@link JayoClosedResourceException}.
     * <p>
     * It is safe to close a writer more than once, but only the first call has an effect.
     *
     * @throws JayoException if an I/O error occurs during the closing phase.
     */
    @Override
    void close();
}
