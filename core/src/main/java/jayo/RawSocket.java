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

import org.jspecify.annotations.NonNull;

/**
 * A raw {@link Socket}.
 */
public interface RawSocket {
    @NonNull
    RawReader getReader();

    @NonNull
    RawWriter getWriter();

    /**
     * Fail any in-flight and future operations. After canceling:
     * <ul>
     * <li>Any attempt to write or flush {@linkplain #getWriter() writer} will fail immediately with a
     * {@linkplain JayoException JayoException}.
     * <li>Any attempt to read {@linkplain #getReader() reader} will fail immediately with a
     * {@linkplain JayoException JayoException}.
     * </ul>
     * Closing the reader and the writer will complete normally even after a socket has been canceled.
     * <p>
     * This operation may be called by any thread at any time. It is safe to call concurrently while operating on the
     * reader or the writer.
     */
    void cancel();
}
