/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.Buffer;
import jayo.RawWriter;
import jayo.exceptions.JayoException;
import jayo.external.CancelToken;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.util.Arrays;
import java.util.Objects;

import static java.lang.System.Logger.Level.TRACE;
import static jayo.external.JayoUtils.checkOffsetAndCount;
import static jayo.internal.RealAsyncTimeout.TIMEOUT_WRITE_SIZE;

public final class GatheringByteChannelRawWriter implements RawWriter {
    private static final System.Logger LOGGER = System.getLogger("jayo.ScatteringByteChannelRawWriter");

    private final @NonNull GatheringByteChannel out;

    public GatheringByteChannelRawWriter(final @NonNull GatheringByteChannel out) {
        this.out = Objects.requireNonNull(out);
    }

    @Override
    public void write(final @NonNull Buffer reader, final @NonNegative long byteCount) {
        Objects.requireNonNull(reader);
        checkOffsetAndCount(reader.byteSize(), 0L, byteCount);
        if (!(reader instanceof RealBuffer _reader)) {
            throw new IllegalArgumentException("reader must be an instance of RealBuffer");
        }
        if (!out.isOpen()) {
            throw new IllegalStateException("Channel is closed");
        }

        // get cancel token immediately, if present it will be used in all I/O calls
        final var cancelToken = CancellableUtils.getCancelToken();

        if (byteCount == 0L) {
            CancelToken.throwIfReached(cancelToken);
            return;
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "WritableByteChannelRawWriter: Start writing {0} bytes from " +
                            "Buffer(SegmentQueue#{1}; size={2}) to the WritableByteChannel{3}",
                    byteCount, _reader.segmentQueue.hashCode(), _reader.byteSize(), System.lineSeparator());
        }

        var remaining = byteCount;
        while (remaining > 0L) {
            /*
             * Don't write more than 4 full segments (~67 KiB) of data at a time. Otherwise, slow connections may suffer
             * timeouts even when they're making (slow) progress. Without this, writing a single 1 MiB buffer may never
             * succeed on a sufficiently slow connection.
             */
            final var toWrite = (int) Math.min(remaining, TIMEOUT_WRITE_SIZE);
            write(_reader.segmentQueue, toWrite, cancelToken);
            remaining -= toWrite;
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "WritableByteChannelRawWriter: Finished writing {0}/{1} bytes from " +
                            "Buffer(SegmentQueue={2}{3}) to the WritableByteChannel{4}",
                    byteCount - remaining, byteCount, System.lineSeparator(), _reader.segmentQueue,
                    System.lineSeparator());
        }
    }

    private void write(final @NonNull SegmentQueue segmentQueue,
                       final @NonNegative int byteCount,
                       final @Nullable RealCancelToken cancelToken) {
        segmentQueue.withHeadsAsByteBuffers(byteCount, sources -> {
            var remaining = byteCount;
            var firstSourceIndex = 0; // index of the first source in the sources array with remaining bytes to write
            var finished = false;
            while (!finished) {
                CancelToken.throwIfReached(cancelToken);
                int written;
                try {
                    written = (int) out.write(sources, firstSourceIndex, sources.length - firstSourceIndex);
                } catch (IOException e) {
                    throw JayoException.buildJayoException(e);
                }
                remaining -= written;
                finished = remaining == 0;
                if (!finished) {
                    // we must ignore the X first fully written byteArrays in the next iteration's write call
                    firstSourceIndex = (int) Arrays.stream(sources)
                            .takeWhile(byteBuffer -> !byteBuffer.hasRemaining())
                            .count();
                }
            }
            return byteCount;
        });
    }

    @Override
    public void flush() {
        try {
            // File specific : opinionated action to force to synchronize with the underlying device when calling
            // rawWriter.flush()
            if (out instanceof FileChannel fileChannel) {
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
        return "writer(" + out + ")";
    }
}
