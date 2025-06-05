/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package jayo;

import org.jspecify.annotations.NonNull;

/**
 * A reader that uses <a href="http://tools.ietf.org/html/rfc1951">DEFLATE</a> to decompress data read from another
 * reader.
 */
public interface InflaterRawReader extends RawReader {
    /**
     * Consume deflated bytes from the underlying reader, and write any inflated bytes to {@code destination}.
     * <p>
     * Use this instead of {@link #readAtMostTo(Buffer, long)} when it is useful to consume the deflated stream even
     * when doing so doesn't yield inflated bytes.
     *
     * @param destination the destination to write the data from this reader.
     * @param byteCount   the number of bytes to read.
     * @return the number of inflated bytes written to {@code destination}. This may return {@code 0L}, though it will always
     * consume 1 or more bytes from the underlying reader if it is not exhausted.
     * @throws IllegalArgumentException if {@code byteCount} is negative.
     * @throws IllegalStateException    if this reader is closed.
     */
    long readOrInflateAtMostTo(final @NonNull Buffer destination, final long byteCount);

    /**
     * Refills the inflater with compressed data if it needs input. (And only if it needs input).
     *
     * @return true if the inflater required input but the reader was exhausted.
     */
    boolean refill();
}
