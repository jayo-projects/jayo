/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package jayo.internal;

import jayo.*;
import jayo.external.CancelToken;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static jayo.internal.ReaderSegmentQueue.newSyncReaderSegmentQueue;

public final class RealInflaterRawReader implements InflaterRawReader {
    private final @NonNull ReaderSegmentQueue segmentQueue;
    private final @NonNull Inflater inflater;
    /**
     * When we call Inflater.setInput(), the inflater keeps our byte array until it needs input again.
     * This tracks how many bytes the inflater is currently holding on to.
     */
    private int bytesHeldByInflater = 0;
    private @Nullable Segment currentHead = null;
    private boolean closed = false;

    public RealInflaterRawReader(final @NonNull RawReader reader, final @NonNull Inflater inflater) {
        this(newSyncReaderSegmentQueue(Objects.requireNonNull(reader)), inflater);
    }

    /**
     * This internal constructor shares a buffer with its trusted caller. In general, we can't share a {@code Reader}
     * because the inflater holds input bytes until they are inflated.
     */
    RealInflaterRawReader(final @NonNull ReaderSegmentQueue segmentQueue, final @NonNull Inflater inflater) {
        assert segmentQueue != null;
        assert inflater != null;

        this.segmentQueue = segmentQueue;
        this.inflater = inflater;
    }

    @Override
    public long readAtMostTo(final @NonNull Buffer writer, final long byteCount) {
        Objects.requireNonNull(writer);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        if (!(writer instanceof RealBuffer _writer)) {
            throw new IllegalArgumentException("writer must be an instance of RealBuffer");
        }
        if (closed) {
            throw new JayoClosedResourceException();
        }
        if (byteCount == 0L) {
            return 0L;
        }

        final var bytesInflated = new Wrapper.Int();
        // Prepare the destination that we'll write into.
        _writer.segmentQueue.withWritableTail(1, tail -> {
            final var toRead = (int) Math.min(byteCount, Segment.SIZE - tail.limit());

            while (true) {
                // Prepare the reader that we'll read from.
                refill();
                try {
                    // Decompress the inflater's compressed data into the writer.
                    bytesInflated.value = inflater.inflate(tail.data, tail.limit(), toRead);
                } catch (DataFormatException e) {
                    throw new JayoException(new IOException(e));
                }
                // Release consumed bytes from the reader.
                releaseBytesAfterInflate();

                // Track produced bytes in the destination.
                if (bytesInflated.value > 0) {
                    tail.incrementLimitVolatile(bytesInflated.value);
                    return true;
                }

                if (inflater.finished() || inflater.needsDictionary()) {
                    bytesInflated.value = -1;
                    return true;
                }

                if (segmentQueue.expectSize(1L) == 0) {
                    throw new JayoEOFException("reader exhausted prematurely");
                }
            }
        });

        return bytesInflated.value;
    }

    @Override
    public long readOrInflateAtMostTo(final @NonNull Buffer writer, final long byteCount) {
        Objects.requireNonNull(writer);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        }
        if (!(writer instanceof RealBuffer _writer)) {
            throw new IllegalArgumentException("writer must be an instance of RealBuffer");
        }
        if (closed) {
            throw new JayoClosedResourceException();
        }

        final var cancelToken = CancellableUtils.getCancelToken();
        CancelToken.throwIfReached(cancelToken);

        if (byteCount == 0L) {
            return 0L;
        }

        final var bytesInflated = new Wrapper.Int();
        // Prepare the destination that we'll write into.
        _writer.segmentQueue.withWritableTail(1, tail -> {
            final var toRead = (int) Math.min(byteCount, Segment.SIZE - tail.limit());

            // Prepare the reader that we'll read from.
            refill();
            try {
                // Decompress the inflater's compressed data into the writer.
                bytesInflated.value = inflater.inflate(tail.data, tail.limit(), toRead);
            } catch (DataFormatException e) {
                throw new JayoException(new IOException(e));
            }
            // Release consumed bytes from the reader.
            releaseBytesAfterInflate();

            // Track produced bytes in the destination.
            if (bytesInflated.value > 0) {
                tail.incrementLimitVolatile(bytesInflated.value);
            } else {
                bytesInflated.value = 0;
            }
            return null;
        });

        return bytesInflated.value;
    }

    @Override
    public boolean refill() {
        if (!inflater.needsInput()) {
            return false;
        }

        // If there are no further bytes in the reader, we cannot refill.
        if (segmentQueue.expectSize(Segment.SIZE) == 0) {
            return true;
        }

        // Assign buffer bytes to the inflater.
        currentHead = segmentQueue.head();
        assert currentHead != null;
        bytesHeldByInflater = currentHead.limitVolatile() - currentHead.pos;
        inflater.setInput(currentHead.data, currentHead.pos, bytesHeldByInflater);

        return false;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        inflater.end();
        closed = true;
        segmentQueue.close();
    }

    @Override
    public String toString() {
        return "InflaterRawReader(" + segmentQueue.reader + ")";
    }

    /**
     * When the inflater has processed compressed data, remove it from the buffer.
     */
    private void releaseBytesAfterInflate() {
        if (bytesHeldByInflater == 0) {
            return;
        }

        assert currentHead != null;
        final var toRelease = bytesHeldByInflater - inflater.getRemaining();
        currentHead.pos += toRelease;
        segmentQueue.decrementSize(toRelease);

        if (currentHead.pos == currentHead.limitVolatile() && currentHead.tryRemove()
                && currentHead.validateRemove()) {
            segmentQueue.removeHead(currentHead);
            SegmentPool.recycle(currentHead);
            currentHead = null;
        }
        bytesHeldByInflater -= toRelease;
    }
}
