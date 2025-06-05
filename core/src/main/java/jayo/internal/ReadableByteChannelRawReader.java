/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.Buffer;
import jayo.JayoException;
import jayo.RawReader;
import jayo.tools.CancelToken;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.util.Objects;

import static java.lang.System.Logger.Level.TRACE;

public final class ReadableByteChannelRawReader implements RawReader {
    private static final System.Logger LOGGER = System.getLogger("jayo.ReadableByteChannelRawReader");

    private final @NonNull ReadableByteChannel rbc;

    public ReadableByteChannelRawReader(final @NonNull ReadableByteChannel rbc) {
        this.rbc = Objects.requireNonNull(rbc);
    }

    /**
     * Execute a single read from the ReadableByteChannel, which reads up to byteCount bytes of data from the readable
     * channel. A smaller number may be read.
     *
     * @return the number of bytes actually read.
     */
    @Override
    public long readAtMostTo(final @NonNull Buffer destination, final long byteCount) {
        Objects.requireNonNull(destination);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "ReadableByteChannelRawReader: Start reading up to {0} bytes from the" +
                            "ReadableByteChannel to Buffer#{1} (size={2}){3}",
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
            read = rbc.read(dstTail.asByteBuffer(dstTail.limit, toRead));
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
            LOGGER.log(TRACE, "ReadableByteChannelRawReader: Finished reading {0}/{1} bytes from the " +
                            "ReadableByteChannel to Buffer#{2} (size={3}){4}",
                    read, byteCount, destination.hashCode(), destination.bytesAvailable(), System.lineSeparator());
        }

        return read;
    }

    @Override
    public void close() {
        try {
            rbc.close();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public String toString() {
        return "reader(" + rbc + ")";
    }
}
