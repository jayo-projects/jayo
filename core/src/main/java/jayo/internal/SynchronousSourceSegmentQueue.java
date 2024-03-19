/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import jayo.RawSource;
import jayo.external.NonNegative;

import java.util.Objects;
import java.util.function.Consumer;

final class SynchronousSourceSegmentQueue extends SegmentQueue {
    private final @NonNull RawSource source;
    private final @NonNull RealBuffer buffer;
    private boolean closed = false;

    SynchronousSourceSegmentQueue(final @NonNull RawSource source) {
        this.source = Objects.requireNonNull(source);
        this.buffer = new RealBuffer(this);
    }

    /**
     * Retrieves the first segment of this queue.
     * <p>
     * If this queue is currently empty or if head is exhausted ({@code head.pos == head.limit}) block until a segment
     * node becomes available or head is not exhausted anymore after a read operation.
     *
     * @return the first segment of this queue, or {@code null} if this queue is empty and there is no read
     * operation left to do.
     */
    @Override
    @Nullable Segment head() {
        // fast-path
        final var currentHead = super.head();
        if (currentHead != null) {
            return currentHead;
        }
        // read from source once and return head
        expectSize(1L);
        return super.head();
    }

    @Override
    @NonNegative long expectSize(final long expectedSize) {
        if (expectedSize < 1L) {
            throw new IllegalArgumentException("expectedSize < 1 : " + expectedSize);
        }
        // fast-path : current size is enough
        final var currentSize = size();
        if (currentSize >= expectedSize || closed) {
            return currentSize;
        }
        // else read from source until expected size is reached or source is exhausted
        var remaining = expectedSize;
        while (remaining > 0) {
            final var toRead = Math.max(expectedSize, Segment.SIZE);
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

    @NonNull RealBuffer getBuffer() {
        return buffer;
    }

    @Override
    @NonNull Segment removeTail() {
        throw new IllegalStateException("removeTail is only needed for UnsafeCursor in Buffer mode, " +
                "it must not be used for Source mode");
    }

    @Override
    void forEach(@NonNull Consumer<Segment> action) {
        throw new IllegalStateException("forEach is only needed for hash in Buffer mode, " +
                "it must not be used for Source mode");
    }
}
