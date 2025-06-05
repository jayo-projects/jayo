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

import jayo.Buffer;
import jayo.JayoException;
import jayo.RawReader;
import jayo.tools.CancelToken;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import static java.lang.System.Logger.Level.TRACE;

public final class InputStreamRawReader implements RawReader {
    private static final System.Logger LOGGER = System.getLogger("jayo.InputStreamRawReader");

    private final @NonNull InputStream in;

    public InputStreamRawReader(final @NonNull InputStream in) {
        assert in != null;
        this.in = in;
    }

    /**
     * Execute a single read from the InputStream, which reads up to byteCount bytes of data from the input stream.
     * A smaller number may be read.
     *
     * @return the number of bytes actually read.
     */
    @Override
    public long readAtMostTo(final @NonNull Buffer destination, final long byteCount) {
        Objects.requireNonNull(destination);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0 : " + byteCount);
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "InputStreamRawReader: Start reading up to {0} bytes from the InputStream to " +
                            "Buffer#{1} (size={2}){3}",
                    byteCount, destination.hashCode(), destination.bytesAvailable(), System.lineSeparator());
        }

        if (byteCount == 0L) {
            return 0L;
        }

        final var cancelToken = CancellableUtils.getCancelToken();
        CancelToken.throwIfReached(cancelToken);

        final var dst = (RealBuffer) destination;

        final var dstTail = dst.writableTail(1);
        final var toRead = (int) Math.min(byteCount, Segment.SIZE - dstTail.limit);
        final int read;
        try {
            read = in.read(dstTail.data, dstTail.limit, toRead);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
        if (read > 0) {
            dstTail.limit += read;
            dst.byteSize += read;
        } else {
            if (dstTail.pos == dstTail.limit) {
                // We allocated a tail segment, but didn't end up needing it. Recycle!
                dst.head = dstTail.pop();
                SegmentPool.recycle(dstTail);
            }
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "InputStreamRawReader: Finished reading {0}/{1} bytes from the InputStream to " +
                            "Buffer#{2} (size={3}){4}",
                    read, byteCount, destination.hashCode(), destination.bytesAvailable(), System.lineSeparator());
        }

        return read;
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
        return "reader(" + in + ")";
    }
}
