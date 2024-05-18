/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.exceptions.JayoEOFException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

sealed class SyncSegmentQueue extends SegmentQueue<SyncSegmentQueue.Node> permits SyncSourceSegmentQueue {
    private @Nullable Node head = null;
    private @Nullable Node tail = null;
    private @NonNegative long size = 0L;

    final @Nullable Node head() {
        return head;
    }

    @Override
    final @NonNull Node lockedReadableHead() {
        var currentHead = head;
        if (currentHead == null) {
            throw new JayoEOFException("Head is null, size = " + size);
        }
        return currentHead;
    }

    @Override
    final @Nullable Node tail() {
        return tail;
    }

    @Override
    final @NonNull Segment removeHead() {
        final var currentHead = head;
        assert currentHead != null;

        final var newHead = currentHead.next;
        // if removed head was also the tail, remove tail as well
        if (newHead == null) {
            tail = null;
        } else {
            newHead.prev = null;
        }
        head = newHead;
        return currentHead.segment;
    }

    @Override
    final @NonNull Node splitHead(final @NonNegative int byteCount) {
        final var currentHead = head;
        assert currentHead != null;

        final var prefix = new Node(currentHead.segment.split(byteCount));
        prefix.next = currentHead;
        currentHead.prev = prefix;
        head = prefix;
        return prefix;
    }

    @Override
    final boolean withWritableTail(final int minimumCapacity, @NonNull ToBooleanFunction<@NonNull Segment> writeAction) {
        Objects.requireNonNull(writeAction);
        if (minimumCapacity < 1 || minimumCapacity > Segment.SIZE) {
            throw new IllegalArgumentException("unexpected capacity : " + minimumCapacity);
        }

        final var currentTail = tail;
        if (currentTail == null) {
            return withNewWritableSegment(writeAction, null);
        }

        final var previousLimit = currentTail.segment.limit;
        if (currentTail.segment.owner && previousLimit + minimumCapacity <= Segment.SIZE) {
            final var result = writeAction.applyAsBoolean(currentTail.segment);
            final int written = currentTail.segment.limit - previousLimit;
            if (written > 0) {
                incrementSize(written);
            }
            return result;
        }
        return withNewWritableSegment(writeAction, currentTail);
    }

    private boolean withNewWritableSegment(final @NonNull ToBooleanFunction<@NonNull Segment> writeOperation,
                                           final @Nullable Node currentTail) {
        // acquire a new empty segment to fill up.
        final var newSegment = SegmentPool.take();
        final var result = writeOperation.applyAsBoolean(newSegment);
        final var written = newSegment.limit;
        if (written > 0) {
            final var newTail = new Node(newSegment);
            if (currentTail != null) {
                currentTail.next = newTail;
                newTail.prev = currentTail;
            } else {
                head = newTail;
            }
            tail = newTail;
            incrementSize(written);
        } else {
            // We allocated a tail segment, but didn't end up needing it. Recycle!
            SegmentPool.recycle(newSegment);
        }
        return result;
    }

    @Override
    final @Nullable Node lockedNonRemovedTailOrNull() {
        return tail;
    }

    @Override
    final void addTail(final @NonNull Segment segment) {
        Objects.requireNonNull(segment);

        final var newTail = new Node(segment);

        final var currentTail = tail;
        if (currentTail == null) {
            tail = newTail;
            head = newTail;
            return;
        }

        currentTail.next = newTail;
        newTail.prev = currentTail;
        tail = newTail;
    }

    @Override
    final @NonNull Segment removeTail() {
        final var currentTail = tail;
        assert currentTail != null;

        final var newTail = currentTail.prev;
        // if removed tail was also the head, remove head as well
        if (newTail == null) {
            head = null;
        } else {
            newTail.next = null;
        }
        tail = newTail;
        return currentTail.segment;
    }

    @Override
    final @NonNull Node swapForUnsharedCopy(final @NonNull Node sharedNode) {
        Objects.requireNonNull(sharedNode);
        assert sharedNode.segment.shared;

        final var unsharedCopy = new Node(sharedNode.segment.unsharedCopy());
        unsharedCopy.next = sharedNode.next;
        unsharedCopy.prev = sharedNode.prev;

        if (sharedNode.next != null) {
            sharedNode.next.prev = unsharedCopy;
        } else {
            tail = unsharedCopy;
        }

        if (sharedNode.prev != null) {
            sharedNode.prev.next = unsharedCopy;
        } else {
            head = unsharedCopy;
        }

        return unsharedCopy;
    }

    @Override
    final @NonNegative long size() {
        return size;
    }

    @Override
    final void incrementSize(final long increment) {
        size += increment;
    }

    @Override
    final void decrementSize(final long decrement) {
        size -= decrement;
    }

    @Override
    final boolean isDoublyLinked() {
        return true;
    }

    @Override
    @NonNegative
    long expectSize(final long expectedSize) {
        throw new IllegalStateException("expectSize is only needed for Source mode, " +
                "it must not be used for Buffer mode");
    }

    static final class Node extends SegmentQueue.Node<Node> {
        private @Nullable Node next = null;
        private @Nullable Node prev = null;
        private final @NonNull Segment segment;

        private Node(final @NonNull Segment segment) {
            this.segment = Objects.requireNonNull(segment);
        }

        @Override
        @Nullable
        Node next() {
            return next;
        }

        @Override
        @Nullable
        Node prev() {
            return prev;
        }

        @Override
        @NonNull
        SafeSegment segment() {
            return segment;
        }

        @Override
        void unlock() {
            // NOP
        }
    }
}
