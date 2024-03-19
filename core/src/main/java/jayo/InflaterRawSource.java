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

import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

/**
 * A source that uses <a href="http://tools.ietf.org/html/rfc1951">DEFLATE</a> to decompress data read from another
 * source.
 */
public interface InflaterRawSource extends RawSource {
    /**
     * Consume deflated bytes from the underlying source, and write any inflated bytes to {@code sink}.
     * <p>
     * Use this instead of [read] when it is useful to consume the deflated stream even when doing so doesn't yield
     * inflated bytes.
     *
     * @param sink      the destination to write the data from this source.
     * @param byteCount the number of bytes to read.
     * @return the number of inflated bytes written to {@code sink}. This may return 0L, though it will always consume 1
     * or more bytes from the underlying source if it is not exhausted.
     * @throws IllegalArgumentException when {@code byteCount} is negative.
     * @throws IllegalStateException    when the source is closed.
     */
    @NonNegative
    long readOrInflateAtMostTo(final @NonNull Buffer sink, final @NonNegative long byteCount);

    /**
     * Refills the inflater with compressed data if it needs input. (And only if it needs input).
     *
     * @return true if the inflater required input but the source was exhausted.
     */
    boolean refill();
}
