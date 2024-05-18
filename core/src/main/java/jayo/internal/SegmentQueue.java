/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A singly or doubly-linked queue of segments. It allows to access {@link #head()} and {@link #tail()}, make a few
 * {@link java.util.Queue}-like useful operations and keep count of the byte size.
 */
sealed abstract class SegmentQueue<NODE extends SegmentQueue.Node<NODE>> implements AutoCloseable
        permits AsyncSegmentQueue, SyncSegmentQueue {

    /**
     * @return the first segment of this queue, or {@code null} if this queue is empty.
     */
    abstract @Nullable NODE head();

    abstract @NonNull NODE lockedReadableHead();

    /**
     * @return the last segment of this queue, or {@code null} if this queue is empty.
     */
    abstract @Nullable NODE tail();

    /**
     * Removes and returns the head of this queue.
     */
    abstract @NonNull Segment removeHead();

    /**
     * Splits this head of this queue into two segments. Returns the first segment that contains the data in
     * {@code [pos..pos+byteCount)}. The second segment, the one that already existed, now contains the data in
     * {@code [pos+byteCount..limit)}. This is useful when moving partial segments from one buffer to another.
     *
     * @return the new head's segment = the prefix.
     * @throws NoSuchElementException if this queue is empty.
     */
    abstract @NonNull NODE splitHead(final @NonNegative int byteCount);

    /**
     * Inserts the specified segment at the end of this queue.
     *
     * @param segment the segment that will be the new tail of this queue.
     */
    abstract void addTail(final @NonNull Segment segment);

    /**
     * @return a tail segment queue node that we can write at least {@code minimumCapacity} bytes to, creating it if
     * necessary.
     * @throws IllegalArgumentException if {@code minimumCapacity < 1 || minimumCapacity > Segment.SIZE}
     */
    abstract boolean withWritableTail(final int minimumCapacity,
                                      final @NonNull ToBooleanFunction<@NonNull Segment> writeAction);

    abstract @Nullable NODE lockedNonRemovedTailOrNull();

    /**
     * Removes and returns the last segment of this queue.
     * This method throws an exception if this queue is empty.
     *
     * @return the tail segment that was removed.
     * @throws NoSuchElementException if this queue is empty.
     */
    abstract @NonNull Segment removeTail();

    /**
     * Iterates over all segments in this queue.
     */
    final void forEach(final @NonNull Consumer<SafeSegment> action) {
        Objects.requireNonNull(action);
        var cur = head();
        while (cur != null) {
            action.accept(cur.segment());
            cur = cur.next();
        }
    }

    abstract @NonNull NODE swapForUnsharedCopy(final @NonNull NODE sharedNode);

    /**
     * @return the number of bytes immediately accessible for read in this queue.
     */
    abstract @NonNegative long size();

    /**
     * Increment the byte size by {@code increment}.
     *
     * @param increment the value to add.
     * @throws IllegalArgumentException If {@code increment < 1L}
     */
    abstract void incrementSize(final long increment);

    /**
     * Decrement the byte size by {@code decrement}.
     *
     * @param decrement the value to subtract.
     * @throws IllegalArgumentException If {@code decrement < 1L}
     */
    abstract void decrementSize(final long decrement);

    /**
     * If current byte size is greater or equal than {@code expectedSize}, return it immediately. Else block until
     * enough read operations are finished so that the expected size is reached. If there is no read operation left,
     * return the terminal byte size. It returns {@code 0L} when the buffer is exhausted.
     *
     * @param expectedSize the expected byte size
     * @return the real byte size.
     */
    abstract @NonNegative long expectSize(final long expectedSize);

    abstract boolean isDoublyLinked();

    @Override
    public void close() {
        // NOP by default
    }

    static sealed abstract class Node<NODE extends Node<NODE>> permits AsyncSegmentQueue.Node, SyncSegmentQueue.Node {
        /**
         * @return the next segment in a queue.
         */
        abstract @Nullable NODE next();

        /**
         * @return the previous segment in a queue.
         */
        abstract @Nullable NODE prev();

        abstract @NonNull SafeSegment segment();

        abstract void unlock();
    }

    @FunctionalInterface
    interface ToBooleanFunction<T> {
        /**
         * Applies this function to the given argument.
         *
         * @param value the function argument
         * @return the function result
         */
        boolean applyAsBoolean(T value);
    }
}
