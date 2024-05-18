/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.exceptions.JayoEOFException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;


sealed class AsyncSegmentQueue extends SegmentQueue<AsyncSegmentQueue.Node>
        permits AsyncSinkSegmentQueue, AsyncSourceSegmentQueue {
    final static long MAX_BYTE_SIZE = 128 * 1024;

    // VarHandle mechanics
    static final VarHandle HEAD;
    static final VarHandle TAIL;

    static {
        try {
            final var l = MethodHandles.lookup();
            HEAD = l.findVarHandle(AsyncSegmentQueue.class, "head", Node.class);
            TAIL = l.findVarHandle(AsyncSegmentQueue.class, "tail", Node.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("FieldMayBeFinal")
    private volatile @Nullable Node head = null;
    @SuppressWarnings("FieldMayBeFinal")
    private volatile @Nullable Node tail = null;
    private final @NonNull LongAdder size = new LongAdder();

    private final @NonNull RealBuffer buffer;

    volatile @Nullable RuntimeException exception = null;
    boolean closed = false;

    final ReentrantLock lock = new ReentrantLock();

    AsyncSegmentQueue() {
        buffer = new RealBuffer(this);
    }

    @Override
    final @Nullable Node head() {
        final var currentHead = (Node) HEAD.getVolatile(this);
        assert currentHead == null || !currentHead.isRemoved;
        return currentHead;
    }

    @Override
    final @Nullable Node tail() {
        return (Node) TAIL.getVolatile(this);
    }

    @Override
    final @NonNull Node lockedReadableHead() {
        var currentHead = head();
        if (currentHead == null) {
            throw new JayoEOFException("size was: " + size());
        }

        currentHead.lock.lock();
        return currentHead;
    }

    @Override
    final @NonNull Segment removeHead() {
        final var currentHead = head();
        assert currentHead != null;
        assert currentHead.lock.isHeldByCurrentThread();

        currentHead.isRemoved = true;
        final var newHead = (Node) Node.NEXT.getVolatile(currentHead);
        if (!HEAD.compareAndSet(this, currentHead, newHead)) {
            throw new IllegalStateException("Could not replace head with its next node");
        }
        // if removed head was also the tail, remove tail as well
        if (newHead == null) {
            if (!TAIL.compareAndSet(this, currentHead, null)) {
                throw new IllegalStateException("Removed head without next node imply replacing tail as well");
            }
        }
        return currentHead.segment;
    }

    @Override
    final @NonNull Node splitHead(final @NonNegative int byteCount) {
        var currentHead = head();
        assert currentHead != null;
        assert currentHead.lock.isHeldByCurrentThread();

        final var prefix = new Node(currentHead.segment.split(byteCount));
        prefix.next = currentHead;
        if (!HEAD.compareAndSet(this, currentHead, prefix)) {
            throw new IllegalStateException("Could not replace head with prefix");
        }

        // must unlock previous head and lock prefix
        currentHead.lock.unlock();
        prefix.lock.lock();
        return prefix;
    }

    @Override
    final boolean withWritableTail(final int minimumCapacity,
                                   final @NonNull ToBooleanFunction<@NonNull Segment> writeAction) {
        Objects.requireNonNull(writeAction);
        if (minimumCapacity < 1 || minimumCapacity > Segment.SIZE) {
            throw new IllegalArgumentException("unexpected capacity : " + minimumCapacity);
        }

        final var currentTail = tail();
        if (currentTail == null) {
            return withNewWritableSegment(writeAction);
        }

        var result = false;
        var written = -2;
        currentTail.lock.lock();
        try {
            if (!currentTail.isRemoved) {
                final var previousLimit = currentTail.segment.limit;
                if (currentTail.segment.owner && previousLimit + minimumCapacity <= Segment.SIZE) {
                    result = writeAction.applyAsBoolean(currentTail.segment);
                    written = currentTail.segment.limit - previousLimit;
                } else {
                    // acquire a new empty segment to fill up.
                    final var newSegment = SegmentPool.take();
                    result = writeAction.applyAsBoolean(newSegment);
                    written = newSegment.limit;
                    if (written > 0) {
                        final var newTail = new Node(newSegment);
                        if (!Node.NEXT.compareAndSet(currentTail, null, newTail)) {
                            throw new IllegalStateException("Could not add new Node after current tail, " +
                                    "next node should be null");
                        }
                        if (!TAIL.compareAndSet(this, currentTail, newTail)) {
                            throw new IllegalStateException("Could not replace current tail with new tail");
                        }
                    } else {
                        // We allocated a tail segment, but didn't end up needing it. Recycle!
                        SegmentPool.recycle(newSegment);
                    }
                }
            }
        } finally {
            currentTail.lock.unlock();
        }

        if (written > -2) {
            if (written > 0) {
                incrementSize(written);
            }
            return result;
        }
        // the tail is removed = the queue is empty
        return withNewWritableSegment(writeAction);
    }

    private boolean withNewWritableSegment(final @NonNull ToBooleanFunction<@NonNull Segment> writeAction) {
        // acquire a new empty segment to fill up.
        final var newSegment = SegmentPool.take();
        final var result = writeAction.applyAsBoolean(newSegment);
        final var written = newSegment.limit;
        if (written > 0) {
            final var newTail = new Node(newSegment);
            // previous tail was either null or removed
            HEAD.setVolatile(this, newTail);
            TAIL.setVolatile(this, newTail);
            incrementSize(written);
        } else {
            // We allocated a tail segment, but didn't end up needing it. Recycle!
            SegmentPool.recycle(newSegment);
        }
        return result;
    }

    @Override
    final @Nullable Node lockedNonRemovedTailOrNull() {
        final var currentTail = tail();
        if (currentTail == null) {
            return null;
        }

        if (!currentTail.isRemoved) {
            currentTail.lock.lock();
            return currentTail;
        }

        return null;
    }

    @Override
    final void addTail(final @NonNull Segment segment) {
        Objects.requireNonNull(segment);

        final var newTail = new Node(segment);

        var currentTail = tail();
        if (currentTail == null) {
            if (!HEAD.compareAndSet(this, null, newTail)) {
                throw new IllegalStateException("Could not replace null head with new tail");
            }
            if (!TAIL.compareAndSet(this, null, newTail)) {
                throw new IllegalStateException("Could not replace null tail with new tail");
            }
            return;
        }

        assert currentTail.lock.isHeldByCurrentThread();
        assert !currentTail.isRemoved;

        if (!Node.NEXT.compareAndSet(currentTail, null, newTail)) {
            throw new IllegalStateException("Could not add new Node after current tail, " +
                    "next node should be null");
        }
        if (!TAIL.compareAndSet(this, currentTail, newTail)) {
            throw new IllegalStateException("Could not replace current tail with new tail");
        }
    }

    @Override
    final @NonNegative long size() {
        throwIfNeeded();
        return size.longValue();
    }

    @Override
    final void incrementSize(final long increment) {
        size.add(increment);
    }

    @Override
    final void decrementSize(final long decrement) {
        size.add(-decrement);
    }

    @Override
    final boolean isDoublyLinked() {
        return false;
    }

    final @NonNull RealBuffer getBuffer() {
        return buffer;
    }

    final void throwIfNeeded() {
        final var currentException = exception;
        if (currentException != null && !closed) {
            // remove exception, then throw it
            exception = null;
            throw currentException;
        }
    }

    @Override
    final @NonNull Segment removeTail() {
        throw new IllegalStateException("removeTail() is only needed for UnsafeCursor, it must not be used for Source" +
                " mode");
    }

    @Override
    final @NonNull Node swapForUnsharedCopy(final @NonNull Node sharedNode) {
        throw new IllegalStateException("swapForUnsharedCopy() is only needed for UnsafeCursor, it must not be " +
                "used for Source mode");
    }

    @Override
    @NonNegative
    long expectSize(final long expectedSize) {
        throw new IllegalStateException("expectSize is only needed for Source mode, " +
                "it must not be used for Buffer mode");
    }

    static final class Node extends SegmentQueue.Node<Node> {
        // VarHandle mechanics
        private static final VarHandle NEXT;

        static {
            try {
                final var l = MethodHandles.lookup();
                NEXT = l.findVarHandle(Node.class, "next", Node.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private volatile @Nullable Node next = null;
        boolean isRemoved = false;
        final @NonNull Segment segment;

        final ReentrantLock lock = new ReentrantLock();

        private Node(final @NonNull Segment segment) {
            this.segment = Objects.requireNonNull(segment);
        }

        @Override
        @Nullable
        Node next() {
            return (Node) NEXT.getVolatile(this);
        }

        @Override
        @Nullable
        Node prev() {
            throw new IllegalStateException("SourceSegmentQueue is not a doubly-linked queue");
        }

        @Override
        @NonNull
        SafeSegment segment() {
            return segment;
        }

        @Override
        void unlock() {
            assert lock.isHeldByCurrentThread();
            lock.unlock();
        }

        @Override
        public String toString() {
            return "Node#" + hashCode() + "{" +
                    "next=" + next +
                    ", isRemoved=" + isRemoved +
                    ", segment=" + segment +
                    ", lock=" + lock +
                    '}';
        }
    }
}
