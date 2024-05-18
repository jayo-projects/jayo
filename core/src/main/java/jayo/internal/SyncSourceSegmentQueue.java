/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.RawSource;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

final class SyncSourceSegmentQueue extends SyncSegmentQueue {
    private final @NonNull RawSource source;
    private final @NonNull RealBuffer buffer;
    private boolean closed = false;

    SyncSourceSegmentQueue(final @NonNull RawSource source) {
        this.source = Objects.requireNonNull(source);
        this.buffer = new RealBuffer(this);
    }

    @Override
    @NonNegative
    long expectSize(final long expectedSize) {
        if (expectedSize < 1L) {
            throw new IllegalArgumentException("expectedSize < 1 : " + expectedSize);
        }
        // fast-path : current size is enough
        final var currentSize = size();
        if (currentSize >= expectedSize || closed) {
            return currentSize;
        }
        // else read from source until expected size is reached or source is exhausted
        var remaining = expectedSize - currentSize;
        while (remaining > 0L) {
            final var toRead = Math.max(remaining, Segment.SIZE);
            final var read = source.readAtMostTo(buffer, toRead);
            if (read == -1) {
                break;
            }
            remaining -= read;
        }
        return size();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
    }

    @NonNull
    RealBuffer getBuffer() {
        return buffer;
    }
}
