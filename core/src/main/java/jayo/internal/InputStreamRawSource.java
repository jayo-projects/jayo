/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
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

package jayo.internal;

import org.jspecify.annotations.NonNull;
import jayo.Buffer;
import jayo.RawSource;
import jayo.exceptions.JayoException;
import jayo.external.CancelToken;
import jayo.external.NonNegative;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class InputStreamRawSource implements RawSource {
    private final @NonNull InputStream in;

    public InputStreamRawSource(final @NonNull InputStream in) {
        this.in = Objects.requireNonNull(in);
    }

    /**
     * execute a single read from the InputStream, that reads up to byteCount bytes of data from the input stream.
     * A smaller number may be read.
     * Returns the number of bytes actually read as a long.
     */
    @Override
    public long readAtMostTo(final @NonNull Buffer sink, final @NonNegative long byteCount) {
        Objects.requireNonNull(sink);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0 : " + byteCount);
        }
        if (!(sink instanceof RealBuffer _sink)) {
            throw new IllegalArgumentException("sink must be an instance of RealBuffer");
        }

        final var cancelToken = CancellableUtils.getCancelToken();
        CancelToken.throwIfReached(cancelToken);

        if (byteCount == 0L) {
            return 0L;
        }

        final var tail = _sink.segmentQueue.writableSegmentWithState();
        var bytesRead = 0;
        try {
            final var currentLimit = tail.limit;
            final var toRead = (int) Math.min(byteCount, Segment.SIZE - currentLimit);

            bytesRead = in.read(tail.data, currentLimit, toRead);
            final var isNewTail = currentLimit == 0;
            if (bytesRead == -1) {
                if (isNewTail) {
                    // We allocated a tail segment, but didn't end up needing it. Recycle!
                    SegmentPool.recycle(tail);
                }
                return -1L;
            }
            tail.limit = currentLimit + bytesRead;
            if (isNewTail) {
                _sink.segmentQueue.addTail(tail);
            }
            return bytesRead;
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        } finally {
            _sink.segmentQueue.finishWrite(tail);
            if (bytesRead > 0) {
                _sink.segmentQueue.incrementSize(bytesRead);
            }
        }
    }

    @Override
    public void close() {
        try {
            in.close();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public String toString() {
        return "source(" + in + ")";
    }
}
