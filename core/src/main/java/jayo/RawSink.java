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

import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.io.Flushable;
import java.io.OutputStream;

/**
 * Receives a stream of bytes. RawSink is a base interface for Jayo data receivers.
 * <p>
 * This interface should be implemented to write data wherever it's needed: to the network, storage, or a buffer in
 * memory. Sinks may be layered to transform received data, such as to compress, encrypt, throttle, or add protocol
 * framing.
 * <p>
 * <b>Most application code shouldn't operate on a raw sink directly</b>, but rather on a {@link Sink} which is both
 * more efficient and more convenient. Use {@link Jayo#buffer(RawSink)} to wrap any sink with a buffer.
 * <p>
 * Sinks are easy to test: just use a {@link Buffer} in your tests, and read from it to confirm it received the data
 * that was expected.
 * <h3>Comparison with OutputStream</h3>
 * This interface is functionally equivalent to {@link java.io.OutputStream}.
 * <p>
 * {@code OutputStream} requires multiple layers when emitted data is heterogeneous: a {@code java.io.DataOutputStream}
 * for primitive values, a {@code java.io.BufferedOutputStream} for buffering, and {@code java.io.OutputStreamWriter}
 * for charset encoding. This library uses {@link Sink} for all of the above.
 * <p>
 * Sink is also easier to layer: there is no {@link OutputStream#write(int)} method that is awkward to implement
 * efficiently.
 * <h3>Interop with OutputStream</h3>
 * Use {@link Jayo#sink(OutputStream)} to adapt an {@code OutputStream} to a sink. Use {@link Sink#asOutputStream()} to
 * adapt a sink to an {@code OutputStream}.
 *
 * @implSpec Implementors should abstain from throwing exceptions other than those that are documented for RawSink
 * methods.
 */
public interface RawSink extends AutoCloseable, Flushable {
    /**
     * Removes {@code byteCount} bytes from {@code source} and appends them to this sink.
     *
     * @param source    the source to read data from.
     * @param byteCount the number of bytes to write.
     * @throws IndexOutOfBoundsException when the {@code source}'s size is below {@code byteCount} or {@code byteCount}
     *                                   is negative.
     * @throws IllegalStateException     when this sink is closed.
     */
    void write(final @NonNull Buffer source, final @NonNegative long byteCount);

    /**
     * Pushes all buffered bytes to their final destination.
     *
     * @throws IllegalStateException when this sink is closed.
     */
    @Override
    void flush();

    /**
     * Pushes all buffered bytes to their final destination and releases the resources held by this sink. It is an error
     * to write a closed sink. It is safe to close a sink more than once.
     */
    @Override
    void close();
}
