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
import jayo.RawWriter;
import jayo.tools.CancelToken;
import org.jspecify.annotations.NonNull;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import static java.lang.System.Logger.Level.TRACE;
import static jayo.tools.JayoUtils.checkOffsetAndCount;

public final class OutputStreamRawWriter implements RawWriter {
    private static final System.Logger LOGGER = System.getLogger("jayo.OutputStreamRawWriter");

    private final @NonNull OutputStream out;

    public OutputStreamRawWriter(final @NonNull OutputStream out) {
        this.out = Objects.requireNonNull(out);
    }

    @Override
    public void write(final @NonNull Buffer source, final long byteCount) {
        Objects.requireNonNull(source);
        checkOffsetAndCount(source.bytesAvailable(), 0L, byteCount);

        if (byteCount == 0L) {
            return;
        }

        // get cancel token immediately, if present it will be used in all I/O calls
        final var cancelToken = CancellableUtils.getCancelToken();

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "OutputStreamRawWriter: Start writing {0} bytes from Buffer#{1} (size={2}) to " +
                            "the OutputStream{3}",
                    byteCount, source.hashCode(), source.bytesAvailable(), System.lineSeparator());
        }

        final var src = (RealBuffer) source;
        var remaining = byteCount;
        while (remaining > 0L) {
            CancelToken.throwIfReached(cancelToken);
            final var head = src.head;
            assert head != null;
            final var toWrite = (int) Math.min(remaining, head.limit - head.pos);
            try {
                out.write(head.data, head.pos, toWrite);
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
            head.pos += toWrite;
            src.byteSize -= toWrite;
            remaining -= toWrite;

            if (head.pos == head.limit) {
                src.head = head.pop();
                SegmentPool.recycle(head);
            }
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE,"OutputStreamRawWriter: Finished writing {0} bytes from Buffer#{1} (size={2}) to " +
                            "the OutputStream{3}",
                    byteCount, source.hashCode(), source.bytesAvailable(), System.lineSeparator());
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
            // File specific: opinionated action to force to synchronize with the underlying device when calling
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
