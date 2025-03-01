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

    private final @NonNull ReadableByteChannel in;

    public ReadableByteChannelRawReader(final @NonNull ReadableByteChannel in) {
        this.in = Objects.requireNonNull(in);
    }

    /**
     * Execute a single read from the ReadableByteChannel, that reads up to byteCount bytes of data from the readable
     * channel. A smaller number may be read.
     *
     * @return the number of bytes actually read.
     */
    @Override
    public long readAtMostTo(final @NonNull Buffer writer, final long byteCount) {
        Objects.requireNonNull(writer);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        if (!(writer instanceof RealBuffer _writer)) {
            throw new IllegalArgumentException("writer must be an instance of RealBuffer");
        }

        final var cancelToken = CancellableUtils.getCancelToken();
        CancelToken.throwIfReached(cancelToken);

        if (byteCount == 0L) {
            return 0L;
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "ReadableByteChannelRawReader: Start reading up to {0} bytes from the" +
                            "ReadableByteChannel to {1}Buffer(SegmentQueue={2}){3}",
                    byteCount, System.lineSeparator(), _writer.segmentQueue, System.lineSeparator());
        }

        final var bytesRead = _writer.segmentQueue.withWritableTail(1, tail -> {
            final var toRead = (int) Math.min(byteCount, Segment.SIZE - tail.limit);
            final int read;
            try {
                read = in.read(tail.asWriteByteBuffer(toRead));
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
            if (read > 0) {
                tail.limit += read;
            }
            return read;
        });

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "ReadableByteChannelRawReader: Finished reading {0}/{1} bytes from the " +
                            "ReadableByteChannel to {2}Buffer(SegmentQueue={3}){4}",
                    bytesRead, byteCount, System.lineSeparator(), _writer.segmentQueue, System.lineSeparator());
        }

        return bytesRead;
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
