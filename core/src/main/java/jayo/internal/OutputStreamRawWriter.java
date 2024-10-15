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
import jayo.RawWriter;
import jayo.JayoException;
import jayo.external.CancelToken;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import static java.lang.System.Logger.Level.TRACE;
import static jayo.external.JayoUtils.checkOffsetAndCount;

public final class OutputStreamRawWriter implements RawWriter {
    private static final System.Logger LOGGER = System.getLogger("jayo.OutputStreamRawWriter");

    private final @NonNull OutputStream out;

    public OutputStreamRawWriter(final @NonNull OutputStream out) {
        this.out = Objects.requireNonNull(out);
    }

    @Override
    public void write(final @NonNull Buffer reader, final @NonNegative long byteCount) {
        Objects.requireNonNull(reader);
        checkOffsetAndCount(reader.byteSize(), 0L, byteCount);
        if (!(reader instanceof RealBuffer _reader)) {
            throw new IllegalArgumentException("reader must be an instance of RealBuffer");
        }

        // get cancel token immediately, if present it will be used in all I/O calls
        final var cancelToken = CancellableUtils.getCancelToken();

        if (byteCount == 0L) {
            CancelToken.throwIfReached(cancelToken);
            return;
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "OutputStreamRawWriter: Start writing {0} bytes from Buffer(SegmentQueue#{1}; " +
                            "size={2}) to the OutputStream{3}",
                    byteCount, _reader.segmentQueue.hashCode(), _reader.byteSize(), System.lineSeparator());
        }

        var remaining = byteCount;
        var head = _reader.segmentQueue.head();
        assert head != null;
        while (remaining > 0L) {
            var headLimit = head.limitVolatile();
            if (head.pos == headLimit) {
                final var oldHead = head;
                if (!head.tryRemove()) {
                    throw new IllegalStateException("Non tail segment must be removable");
                }
                head = _reader.segmentQueue.removeHead(head);
                assert head != null;
                headLimit = head.limitVolatile();
                SegmentPool.recycle(oldHead);
            }

            CancelToken.throwIfReached(cancelToken);

            final var toWrite = (int) Math.min(remaining, headLimit - head.pos);
            try {
                out.write(head.data, head.pos, toWrite);
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
            head.pos += toWrite;
            _reader.segmentQueue.decrementSize(toWrite);
            remaining -= toWrite;
        }
        if (head.pos == head.limitVolatile() && head.tryRemove() && head.validateRemove()) {
            _reader.segmentQueue.removeHead(head);
            SegmentPool.recycle(head);
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "OutputStreamRawWriter: Finished writing {0}/{1} bytes from " +
                            "Buffer(SegmentQueue={2}{3}) to the OutputStream{4}",
                    byteCount - remaining, byteCount, System.lineSeparator(), _reader.segmentQueue,
                    System.lineSeparator());
        }
    }

    @Override
    public void flush() {
        try {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "OutputStreamRawWriter: flushing to the OutputStream {0}{1}",
                        out, System.lineSeparator());
            }

            out.flush();
            // File specific : opinionated action to force to synchronize with the underlying device when calling
            // rawWriter.flush()
            if (out instanceof FileOutputStream fileOutputStream) {
                fileOutputStream.getFD().sync();
            }
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public void close() {
        try {
            out.close();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public String toString() {
        return "writer(" + out + ")";
    }
}
