/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
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

import jayo.internal.RealPipe;
import org.jspecify.annotations.NonNull;

/**
 * A {@linkplain RawReader reader} and a {@linkplain RawWriter writer} that are attached. The writer's output is the
 * reader's input. Typically, each is accessed by its own thread: a producer thread writes data to the writer, and a
 * consumer thread reads data from the reader.
 * <p>
 * This class uses a buffer to decouple reader and writer. This buffer has a user-specified maximum size. When a
 * producer thread outruns its consumer, the buffer fills up and eventually writes to the writer will block until the
 * consumer has caught up. Symmetrically, if a consumer outruns its producer, reads block until there is data to be
 * read.
 * <p>
 * Limits on the amount of time spent waiting for the other party can be configured by using
 * {@linkplain Cancellable#call(java.time.Duration, java.util.function.Function) call with timeout} or
 * {@linkplain Cancellable#run(java.time.Duration, java.util.function.Consumer) run with timeout}.
 * <p>
 * When the writer is closed, reader reads will continue to complete normally until the buffer has been exhausted. At
 * that point reads will return -1, indicating the end of the stream. But if the reader is closed first, writes to the
 * writer will immediately fail with a {@link JayoException}.
 * <p>
 * A pipe may be canceled to immediately fail writes to the writer and reads from the reader.
 */
public sealed interface Pipe permits RealPipe {
    /**
     * @return a new {@link Pipe}. This pipe's buffer that decouples reader and writer has a maximum size of
     * {@code maxBufferSize}.
     */
    static @NonNull Pipe create(final long maxBufferSize) {
        return new RealPipe(maxBufferSize);
    }

    @NonNull
    RawReader getReader();

    @NonNull
    RawWriter getWriter();

    /**
     * Writes any buffered contents of this pipe to {@code writer}, then replace this pipe's reader with {@code writer}.
     * This pipe's reader is closed, and attempts to read it will throw a {@link JayoClosedResourceException}.
     * <p>
     * This method must not be called while concurrently accessing this pipe's reader. It is safe, however, to call this
     * while concurrently writing this pipe's writer.
     */
    void fold(final @NonNull RawWriter writer);

    /**
     * Fail any in-flight and future operations. After canceling:
     * <ul>
     * <li>Any attempt to write or flush {@linkplain #getWriter() writer} will fail immediately with a
     * {@link JayoException}.
     * <li>Any attempt to read {@linkplain #getReader() reader} will fail immediately with a {@link JayoException}.
     * <li>Any attempt to {@linkplain #fold(RawWriter) fold} will fail immediately with a {@link JayoException}.
     * </ul>
     * Closing the reader and the writer will complete normally even after a pipe has been canceled. If this writer
     * has been folded, closing it will also close the folded writer. This operation may block.
     * <p>
     * This operation may be called by any thread at any time. It is safe to call concurrently while operating on the
     * reader or the writer.
     */
    void cancel();

    /**
     * @return {@code true} if this pipe is open, ensuring that neither {@linkplain #getReader() reader} nor
     * {@linkplain #getWriter() writer} are closed and this pipe is not {@linkplain #cancel() canceled}.
     */
    boolean isOpen();
}
