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
import jayo.RawSink;
import jayo.exceptions.JayoException;
import jayo.external.CancelToken;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.util.Objects;

import static java.lang.System.Logger.Level.TRACE;
import static jayo.external.JayoUtils.checkOffsetAndCount;

public final class OutputStreamRawSink implements RawSink {
    private static final System.Logger LOGGER = System.getLogger("jayo.OutputStreamRawSink");

    private final @NonNull OutputStream out;
    private final @Nullable SeekableByteChannel bc;

    public OutputStreamRawSink(final @NonNull OutputStream out) {
        this(out, null);
    }

    public OutputStreamRawSink(final @NonNull OutputStream out, final @Nullable SeekableByteChannel bc) {
        this.out = Objects.requireNonNull(out);
        this.bc = bc;
    }

    @Override
    public void write(final @NonNull Buffer source, final @NonNegative long byteCount) {
        checkOffsetAndCount(Objects.requireNonNull(source).byteSize(), 0L, byteCount);
        if (!(source instanceof RealBuffer _source)) {
            throw new IllegalArgumentException("source must be an instance of RealBuffer");
        }

        // get cancel token immediately, if present it will be used in all I/O calls
        final var cancelToken = CancellableUtils.getCancelToken();

        if (byteCount == 0L) {
            CancelToken.throwIfReached(cancelToken);
            return;
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "OutputStreamRawSink: Start writing {0} bytes to Buffer(SegmentQueue#{1}) from" +
                            " the OutputStream{2}",
                    byteCount, _source.segmentQueue.hashCode(), System.lineSeparator());
        }

        var remaining = byteCount;
        var head = _source.segmentQueue.headVolatile();
        var finished = false;
        while (!finished) {
            CancelToken.throwIfReached(cancelToken);

            assert head != null;
            final var currentLimit = head.limitVolatile();
            final var toWrite = (int) Math.min(remaining, currentLimit - head.pos);
            try {
                out.write(head.data, head.pos, toWrite);
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
            head.pos += toWrite;
            _source.segmentQueue.decrementSize(toWrite);
            remaining -= toWrite;
            finished = remaining == 0;
            if (head.pos == currentLimit) {
                final var oldHead = head;
                if (finished) {
                    if (head.tryRemove() && head.validateRemove()) {
                        _source.segmentQueue.removeHead(head);
                    }
                } else {
                    if (!head.tryRemove()) {
                        throw new IllegalStateException("Non tail segment should be removable");
                    }
                    head = _source.segmentQueue.removeHead(head);
                }
                SegmentPool.recycle(oldHead);
            }
        }
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "OutputStreamRawSink: Finished writing {0}/{1} bytes to Buffer(SegmentQueue#{2}) " +
                            "from the OutputStream{2}",
                    byteCount - remaining, byteCount, _source.segmentQueue.hashCode(), System.lineSeparator());
        }
    }

    @Override
    public void flush() {
        try {
            out.flush();
            // File specific : opinionated action to force to synchronize with the underlying device when calling
            // rawSink.flush()
            if (out instanceof FileOutputStream fileOutputStream) {
                fileOutputStream.getFD().sync();
            } else if (bc instanceof FileChannel fileChannel) {
                fileChannel.force(false);
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
        return "sink(" + out + ")";
    }
}
