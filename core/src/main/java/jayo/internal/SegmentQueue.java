/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import jayo.external.NonNegative;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The sentinel of this circular lock-free and wait-free doubly-linked queue of segments. It allows to access
 * {@link #head()} and {@link #tail()}, and keep count of the byte size.
 */
sealed class SegmentQueue extends Segment implements AutoCloseable
        permits SynchronousSourceSegmentQueue, SourceSegmentQueue, SinkSegmentQueue {
    final static long MAX_BYTE_SIZE = 128 * 1024;

    // VarHandle mechanics
    static final VarHandle STATUS;

    static {
        try {
            final var l = MethodHandles.lookup();
            STATUS = l.findVarHandle(Segment.class, "status", byte.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private @NonNegative long size = 0L;

    SegmentQueue() {
        super(new byte[0], 0, 0, false, true);
        prev = this;
        next = this;
        status = SENTINEL;
    }

    /**
     * Retrieves the first segment of this queue.
     *
     * @return the first segment of this queue, or {@code null} if this queue is empty.
     */
    @Nullable Segment head() {
        var head = next;
        return (head != this) ? head : null;
    }

    /**
     * Returns the first segment of this queue, and switch it to the "TRANSFERRING" state.
     * This method throws an exception if this queue is empty.
     *
     * @return the head's segment.
     * @throws NoSuchElementException if this queue is empty.
     */
    final @NonNull TransferringHead headForTransferring() {
        final var head = head();
        if (head == null) {
            throw new NoSuchElementException("queue must not be empty to call headForTransferring");
        }
        final var previousState = (byte) STATUS.compareAndExchange(head, AVAILABLE, TRANSFERRING);
        return switch (previousState) {
            case AVAILABLE, TRANSFERRING -> new TransferringHead(head, false);
            case WRITING -> new TransferringHead(head, true);
            default -> throw new IllegalStateException("Unexpected state " + previousState + ". The head queue " +
                    "node should be in 'AVAILABLE' or 'WRITING' state before transferring.");
        };
    }

    record TransferringHead(@NonNull Segment head, boolean isWriting) {
    }

    /**
     * Retrieves the last segment of this queue.
     * <p>
     * If this queue is currently empty, block until a segment becomes available after a read operation.
     *
     * @return the last segment of this queue, or {@code null} if this queue is empty and there is no read
     * operation left.
     */
    final @Nullable Segment tail() {
        final var tail = prev;
        return (tail != this) ? tail : null;
    }

    final @Nullable Segment tailWithState() {
        final var tail = prev;
        if (tail == this) {
            return null;
        }
        if (!STATUS.compareAndSet(tail, AVAILABLE, WRITING)) {
            return null;
        }
        return tail;
    }

    @NonNull Segment forceRemoveHead() {
        // default to removeHead
        final var removedHead = removeHead();
        assert removedHead != null;
        return removedHead;
    }

    /**
     * Removes and returns the first segment of this queue, or null if segment is not in the "AVAILABLE" nor the
     * "TRANSFERRING" state.
     * This method throws an exception if this queue is empty.
     *
     * @return the head segment that was removed, or null.
     * @throws NoSuchElementException if this queue is empty.
     */
    @Nullable Segment removeHead() {
        final var head = next;
        if (head == this) {
            throw new NoSuchElementException("queue must not be empty to call removeHead");
        }

        final var newHead = head.next;
        next = newHead;
        newHead.prev = this;
        // clean head for recycling
        head.prev = null;
        head.next = null;
        return head;
    }

    /**
     * Splits this head of this queue into two segments. Returns the first segment that contains the data in
     * {@code [pos..pos+byteCount)}. The second segment contains the data in {@code [pos+byteCount..limit)}. This is
     * useful when moving partial segments from one buffer to another.
     *
     * @return the head's segment that was split.
     * @throws NoSuchElementException if this queue is empty.
     */
    final @NonNull Segment splitHead(final @NonNegative int byteCount, final boolean wasWriting) {
        final var head = next;
        if (head == this) {
            throw new NoSuchElementException("queue must not be empty to call splitHead");
        }
        final var prefix = head.split(byteCount);
        prefix.prev = this;
        prefix.next = head;
        prefix.status = TRANSFERRING;
        head.prev = prefix;
        next = prefix;
        finishTransfer(head, wasWriting);
        return prefix;
    }

    final void finishTransfer(final @NonNull Segment segment, boolean wasWriting) {
        if (!wasWriting) {
            STATUS.compareAndSet(segment, TRANSFERRING, AVAILABLE);
        }
    }

    final void finishWrite(final @NonNull Segment segment) {
        STATUS.compareAndSet(segment, WRITING, AVAILABLE);
    }

    /**
     * Inserts the specified segment at the end of this queue.
     *
     * @param segment the segment that will be the new tail of this queue.
     */
    final void addTail(final @NonNull Segment segment) {
        Objects.requireNonNull(segment);
        segment.next = this;
        final var oldTail = prev;
        segment.prev = oldTail;
        prev = segment;
        oldTail.next = segment;
    }

    /**
     * Removes and returns the last segment of this queue.
     * This method throws an exception if this queue is empty.
     *
     * @return the tail segment that was removed.
     * @throws NoSuchElementException if this queue is empty.
     */
    @NonNull Segment removeTail() {
        final var tail = prev;
        if (tail == this) {
            throw new NoSuchElementException("queue must not be empty to call removeTail");
        }
        final var newTail = prev.prev;
        prev = newTail;
        prev.next = this;
        // clean tail for recycling
        tail.prev = null;
        tail.next = null;
        return tail;
    }

    /**
     * @return a tail segment queue node that we can write at least {@code minimumCapacity} bytes to, creating it if
     * necessary.
     * @throws IllegalArgumentException if {@code minimumCapacity < 1 || minimumCapacity > Segment.SIZE}
     */
    final @NonNull Segment writableSegment(final int minimumCapacity) {
        if (minimumCapacity < 1 || minimumCapacity > Segment.SIZE) {
            throw new IllegalArgumentException("unexpected capacity : " + minimumCapacity);
        }

        final var tail = tail();
        if (tail == null || !tail.owner || tail.limit + minimumCapacity > Segment.SIZE) {
            // acquire a new empty segment to fill up.
            return SegmentPool.take();
        }

        // tail has enough space, return it
        return tail;
    }

    /**
     * @return a tail segment queue node that we can write at least {@code 1} byte to, creating it if necessary.
     * The status of the returned segment is guaranteed to be {@code WRITING}.
     */
    final @NonNull Segment writableSegmentWithState() {
        final var tail = tail();
        if (tail == null) {
            // acquire a first empty segment.
            return newEmptySegment();
        }

        // requested size does not fit in tail segment, or tail is not writable
        if (!tail.owner || tail.limit + 1 > Segment.SIZE) {
            // acquire a new empty segment to fill up.
            return newEmptySegment();
        }

        if (!STATUS.compareAndSet(tail, AVAILABLE, WRITING)) {
            // tail is not available, acquire a new empty segment.
            return newEmptySegment();
        }

        // tail has enough space, return it
        return tail;
    }

    private @NonNull Segment newEmptySegment() {
        final var segment = SegmentPool.take();
        segment.status = WRITING;
        return segment;
    }

    /**
     * Iterates over all segments in this queue.
     */
    void forEach(@NonNull Consumer<Segment> action) {
        Objects.requireNonNull(action);
        var cur = next;
        while (cur != this) {
            action.accept(cur);
            cur = cur.next;
        }
    }

    /**
     * @return the number of bytes immediately accessible for read in this queue.
     */
    @NonNegative
    long size() {
        return size;
    }

    /**
     * Increment the byte size by {@code increment}.
     *
     * @param increment the value to add.
     * @throws IllegalArgumentException If {@code increment < 1L}
     */
    void incrementSize(long increment) {
        size += increment;
    }

    /**
     * Decrement the byte size by {@code decrement}.
     *
     * @param decrement the value to subtract.
     * @throws IllegalArgumentException If {@code decrement < 1L}
     */
    void decrementSize(long decrement) {
        size -= decrement;
    }

    /**
     * If current byte size is greater or equal than {@code expectedSize}, return it immediately. Else block until
     * enough read operations are finished so that the expected size is reached. If there is no read operation left,
     * return the terminal byte size. It returns {@code 0L} when the buffer is exhausted.
     * <p>
     * Tip : pass {@code expectedByteSize = Long.MaxValue} to block until the source is exhausted.
     *
     * @param expectedSize the expected byte size
     * @return the real byte size.
     */
    long expectSize(long expectedSize) {
        throw new IllegalStateException("expectSize is only needed for Source mode, " +
                "it must not be used for Buffer mode");
    }

    @Override
    public void close() {
        // default is NOP
    }
}
