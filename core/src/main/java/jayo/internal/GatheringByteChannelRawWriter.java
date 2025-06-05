/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.Buffer;
import jayo.JayoClosedResourceException;
import jayo.JayoException;
import jayo.RawWriter;
import jayo.tools.CancelToken;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.util.Arrays;
import java.util.Objects;

import static java.lang.System.Logger.Level.TRACE;
import static jayo.internal.RealAsyncTimeout.TIMEOUT_WRITE_SIZE;
import static jayo.tools.JayoUtils.checkOffsetAndCount;

public final class GatheringByteChannelRawWriter implements RawWriter {
    private static final System.Logger LOGGER = System.getLogger("jayo.ScatteringByteChannelRawWriter");

    private final @NonNull GatheringByteChannel gbc;

    public GatheringByteChannelRawWriter(final @NonNull GatheringByteChannel gbc) {
        this.gbc = Objects.requireNonNull(gbc);
    }

    @Override
    public void write(final @NonNull Buffer source, final long byteCount) {
        Objects.requireNonNull(source);
        checkOffsetAndCount(source.bytesAvailable(), 0L, byteCount);
        if (!gbc.isOpen()) {
            throw new JayoClosedResourceException();
        }

        // get the cancel token immediately, if present it will be used in all I/O calls
        final var cancelToken = CancellableUtils.getCancelToken();

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "GatheringByteChannelRawWriter: Start writing {0} bytes from Buffer#{1} " +
                            "(size={2}) to the WritableByteChannel{3}",
                    byteCount, source.hashCode(), source.bytesAvailable(), System.lineSeparator());
        }

        final var src = (RealBuffer) source;
        var remaining = byteCount;
        while (remaining > 0L) {
            /*
             * Don't write more than 4 full segments (~67 KiB) of data at a time. Otherwise, slow connections may suffer
             * timeouts even when they're making (slow) progress. Without this, writing a single 1 MiB buffer may never
             * succeed on a sufficiently slow connection.
             */
            final var toWrite = (int) Math.min(remaining, TIMEOUT_WRITE_SIZE);
            write(src, toWrite, cancelToken);
            remaining -= toWrite;
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "GatheringByteChannelRawWriter: Finished writing {0} bytes from Buffer#{2} " +
                            "(size={2}) to the WritableByteChannel{3}",
                    byteCount, source.hashCode(), source.bytesAvailable(), System.lineSeparator());
        }
    }

    private void write(final @NonNull RealBuffer src,
                       final int byteCount,
                       final @Nullable RealCancelToken cancelToken) {
        assert src != null;

        src.withHeadsAsByteBuffers(byteCount, sources -> {
            var remaining = byteCount;
            var firstSourceIndex = 0; // index of the first source in the array of sources with remaining bytes to write
            while (true) {
                CancelToken.throwIfReached(cancelToken);
                int written;
                try {
                    written = (int) gbc.write(sources, firstSourceIndex, sources.length - firstSourceIndex);
                } catch (IOException e) {
                    throw JayoException.buildJayoException(e);
                }
                remaining -= written;
                if (remaining == 0) {
                    break; // done
                }

                // we must ignore the X first fully written byteArrays in the next iteration's writing call
                firstSourceIndex = (int) Arrays.stream(sources)
                        .takeWhile(byteBuffer -> !byteBuffer.hasRemaining())
                        .count();
            }
            return byteCount;
        });
    }

    @Override
    public void flush() {
        try {
            // File specific: opinionated action to force to synchronize with the underlying device when calling
            // rawWriter.flush()
            if (gbc instanceof FileChannel fileChannel) {
                fileChannel.force(false);
            }
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public void close() {
        try {
            gbc.close();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public String toString() {
        return "writer(" + gbc + ")";
    }
}
