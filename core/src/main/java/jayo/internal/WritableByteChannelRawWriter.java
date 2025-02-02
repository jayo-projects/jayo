/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.Buffer;
import jayo.JayoException;
import jayo.RawWriter;
import jayo.external.CancelToken;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Objects;

import static java.lang.System.Logger.Level.TRACE;
import static jayo.external.JayoUtils.checkOffsetAndCount;

public final class WritableByteChannelRawWriter implements RawWriter {
    private static final System.Logger LOGGER = System.getLogger("jayo.WritableByteChannelRawWriter");

    private final @NonNull WritableByteChannel out;

    public WritableByteChannelRawWriter(final @NonNull WritableByteChannel out) {
        this.out = Objects.requireNonNull(out);
    }

    @Override
    public void write(final @NonNull Buffer reader, final long byteCount) {
        Objects.requireNonNull(reader);
        checkOffsetAndCount(reader.bytesAvailable(), 0L, byteCount);
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
            LOGGER.log(TRACE, "WritableByteChannelRawWriter: Start writing {0} bytes from " +
                            "Buffer(SegmentQueue#{1}; size={2}) to the WritableByteChannel{3}",
                    byteCount, _reader.segmentQueue.hashCode(), _reader.bytesAvailable(), System.lineSeparator());
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
            final int written;
            try {
                written = out.write(head.asReadByteBuffer(toWrite));
            } catch (IOException e) {
                throw JayoException.buildJayoException(e);
            }
            head.pos += written;
            _reader.segmentQueue.decrementSize(written);
            remaining -= written;
        }
        if (head.pos == head.limitVolatile() && head.tryRemove() && head.validateRemove()) {
            _reader.segmentQueue.removeHead(head);
            SegmentPool.recycle(head);
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "WritableByteChannelRawWriter: Finished writing {0}/{1} bytes from " +
                            "Buffer(SegmentQueue={2}{3}) to the WritableByteChannel{4}",
                    byteCount - remaining, byteCount, System.lineSeparator(), _reader.segmentQueue,
                    System.lineSeparator());
        }
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
