/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.exceptions.JayoEOFException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.TRACE;


sealed class SegmentQueue implements Closeable
        permits SinkSegmentQueue, SourceSegmentQueue {
    final static long MAX_BYTE_SIZE = 128 * 1024;
    private final static AtomicInteger ID_GENERATOR = new AtomicInteger();

    private static final System.Logger LOGGER = System.getLogger("jayo.SegmentQueue");

    final @NonNegative int segmentQueueId;

    @SuppressWarnings("FieldMayBeFinal")
    private volatile @Nullable Segment head = null;
    @SuppressWarnings("FieldMayBeFinal")
    private volatile @Nullable Segment tail = null;
    private final @NonNull LongAdder size = new LongAdder();

    final ReentrantLock lock = new ReentrantLock();

    // VarHandle mechanics
    static final VarHandle HEAD;
    static final VarHandle TAIL;

    static {
        try {
            final var l = MethodHandles.lookup();
            HEAD = l.findVarHandle(SegmentQueue.class, "head", Segment.class);
            TAIL = l.findVarHandle(SegmentQueue.class, "tail", Segment.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    SegmentQueue() {
        segmentQueueId = ID_GENERATOR.incrementAndGet();
        if (segmentQueueId == Integer.MAX_VALUE - 1000) {
            // should not happen very often ;)
            ID_GENERATOR.set(0);
        }
    }

    final @Nullable Segment head() {
        final var currentHead = (Segment) HEAD.getVolatile(this);
        assert currentHead == null || currentHead.lastSegmentQueueId != segmentQueueId;
        return currentHead;
    }

    final @Nullable Segment tail() {
        return (Segment) TAIL.getVolatile(this);
    }

    final @NonNull Segment lockedReadableHead() {
        var currentHead = head();
        if (currentHead == null) {
            throw new JayoEOFException("size was: " + size());
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, """
                            SegmentQueue#{0}: lockedReadableHead() Acquiring head :
                            {1}{2}""",
                    segmentQueueId, currentHead, System.lineSeparator());
        }
        assert !currentHead.lock.isHeldByCurrentThread();

        currentHead.lock.lock();
        return currentHead;
    }

    final @Nullable Segment removeLockedHead(final @NonNull Segment currentHead, final boolean lockNext) {
        return removeLockedHead(currentHead, lockNext, false, true);
    }

    // this method must only be called from Buffer.write call
    final @Nullable Segment removeLockedHead(final @NonNull Segment currentHead,
                                             final boolean lockNext,
                                             final boolean wasSplit
    ) {
        return removeLockedHead(currentHead, lockNext, wasSplit, false);
    }

    private @Nullable Segment removeLockedHead(final @NonNull Segment currentHead,
                                               final boolean lockNext,
                                               final boolean wasSplit, // only provided by Buffer.write call
                                               final boolean unlockRemoved
    ) {
        Objects.requireNonNull(currentHead);
        if (!currentHead.lock.isHeldByCurrentThread()) {
            throw new RuntimeException();
        }
        assert currentHead.lock.isHeldByCurrentThread();

        currentHead.lastSegmentQueueId = segmentQueueId;
        final var newHead = currentHead.next();
        if (newHead != null) {
            if (lockNext) {
                assert !newHead.lock.isHeldByCurrentThread();
                newHead.lock.lock();
            }
            // todo do not do it, next will be re-assigned in recycle
            Segment.NEXT.compareAndSet(currentHead, newHead, null);
        } else {
            // if removed head was also the tail, remove tail as well
            if (!TAIL.compareAndSet(this, currentHead, null)) {
                throw new IllegalStateException("Removed head without next node imply replacing tail as well");
            }
        }
        if (!wasSplit) {
            if (!HEAD.compareAndSet(this, currentHead, newHead)) {
                throw new IllegalStateException("Could not replace head with its next node");
            }
        }
        if (unlockRemoved) {
            currentHead.unlock();
        }
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, """
                            SegmentQueue#{0}: removeLockedHead. currentHead :
                            {1}
                            newHead :
                            {2}{3}""",
                    segmentQueueId, currentHead, newHead, System.lineSeparator());
        }
        return newHead;
    }

    final boolean withWritableTail(final int minimumCapacity,
                                   final @NonNull ToBooleanFunction<@NonNull Segment> writeAction) {
        Objects.requireNonNull(writeAction);
        assert minimumCapacity > 0;
        assert minimumCapacity <= Segment.SIZE;

        final var currentTail = tail();
        if (currentTail == null) {
            return withNewWritableSegment(writeAction);
        }

        currentTail.lock.lock();
        try {
            if (currentTail.lastSegmentQueueId != segmentQueueId) {
                final var previousLimit = currentTail.limit;
                // current tail has enough room
                if (currentTail.owner && previousLimit + minimumCapacity <= Segment.SIZE) {
                    final var result = writeAction.applyAsBoolean(currentTail);
                    final var written = currentTail.limit - previousLimit;
                    if (written > 0) {
                        incrementSize(written);
                    }
                    return result;
                }

                // acquire a new empty segment to fill up.
                final var newTail = SegmentPool.take();
                final var result = writeAction.applyAsBoolean(newTail);
                final var written = newTail.limit;
                if (written > 0) {
                    if (!Segment.NEXT.compareAndSet(currentTail, null, newTail)) {
                        throw new IllegalStateException("Could not add new Segment after current tail, " +
                                "next node should be null");
                    }
                    if (!TAIL.compareAndSet(this, currentTail, newTail)) {
                        throw new IllegalStateException("Could not replace current tail with new tail");
                    }
                    incrementSize(written);
                } else {
                    // We allocated a tail segment, but didn't end up needing it. Recycle!
                    SegmentPool.recycle(newTail);
                }
                return result;
            }

            // the tail is removed = the queue is empty
            return withNewWritableSegment(writeAction);
        } finally {
            currentTail.lock.unlock();
        }
    }

    private boolean withNewWritableSegment(final @NonNull ToBooleanFunction<@NonNull Segment> writeAction) {
        // acquire a new empty segment to fill up.
        final var newTail = SegmentPool.take();
        final var result = writeAction.applyAsBoolean(newTail);
        final var written = newTail.limit;
        if (written > 0) {
            // previous tail was either null or removed
            HEAD.setVolatile(this, newTail);
            TAIL.setVolatile(this, newTail);
            incrementSize(written);
        } else {
            // We allocated a tail segment, but didn't end up needing it. Recycle!
            SegmentPool.recycle(newTail);
        }
        return result;
    }

    final @Nullable Segment lockedNonRemovedTailOrNull() {
        final var currentTail = tail();
        if (currentTail == null) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE,
                        "SegmentQueue#{0}: lockedNonRemovedTailOrNull() no current tail, return null{1}",
                        segmentQueueId, System.lineSeparator());
            }
            return null;
        }

        currentTail.lock.lock();
        if (currentTail.lastSegmentQueueId == segmentQueueId) {
            // tail was removed, return null
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, """
                                SegmentQueue#{0}: lockedNonRemovedTailOrNull() current tail was removed :
                                {1}
                                , return null{2}""",
                        segmentQueueId, currentTail, System.lineSeparator());
            }
            currentTail.unlock();
            return null;
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, """
                            SegmentQueue#{0}: lockedNonRemovedTailOrNull() locking and return current non removed tail :
                            {1}{2}""",
                    segmentQueueId, currentTail, System.lineSeparator());
        }
        return currentTail;
    }

    final @NonNull Segment lockedWritableTail(final int minimumCapacity) {
        assert minimumCapacity > 0;
        assert minimumCapacity <= Segment.SIZE;

        final var currentTail = tail();
        final var tailOrNull = lockedWritableTailOrNull(currentTail, minimumCapacity);
        if (tailOrNull != null) {
            return tailOrNull;
        }

        // no current writable tail that have the capacity we need, we add a new one
        final var newTail = SegmentPool.take();
        newTail.lock.lock();
        if (currentTail != null && currentTail.lastSegmentQueueId != segmentQueueId) {
            if (!Segment.NEXT.compareAndSet(currentTail, null, newTail)) {
                throw new IllegalStateException("Current tail next should be null");
            }
            currentTail.unlock();
        } else {
            if (!SegmentQueue.HEAD.compareAndSet(this, null, newTail)) {
                throw new IllegalStateException("Could not replace null head with new tail");
            }
        }
        return newTail;
    }

    private @Nullable Segment lockedWritableTailOrNull(final @Nullable Segment currentTail, final int minimumCapacity) {
        if (currentTail == null) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE,
                        "SegmentQueue#{0}: lockedWritableTailOrNull({1}) no current tail, return null{2}",
                        segmentQueueId, minimumCapacity, System.lineSeparator());
            }
            return null;
        }

        //assert !currentTail.lock.isHeldByCurrentThread();
        currentTail.lock.lock();
        if (currentTail.lastSegmentQueueId == segmentQueueId) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, """
                                SegmentQueue#{0}: lockedWritableTailOrNull({1}) current tail is removed
                                from current queue :
                                {2}
                                , return null{3}""",
                        segmentQueueId, minimumCapacity, currentTail, System.lineSeparator());
            }
            currentTail.unlock();
            return null;
        } else if (!currentTail.owner
                || currentTail.limit + minimumCapacity > Segment.SIZE) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, """
                                SegmentQueue#{0}: lockedWritableTailOrNull({1}) locking current non-writable tail :
                                {2}
                                , return null{3}""",
                        segmentQueueId, minimumCapacity, currentTail, System.lineSeparator());
            }
            return null;
        }

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, """
                            SegmentQueue#{0}: lockedWritableTailOrNull({1}) locking and return current writable tail :
                            {2}{3}""",
                    segmentQueueId, minimumCapacity, currentTail, System.lineSeparator());
        }
        return currentTail;
    }

    final @NonNull Segment addLockedTail(final @Nullable Segment currentTail,
                                         final @NonNull Segment newTail,
                                         final boolean unlockCurrentTail) {
        Objects.requireNonNull(newTail);
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, """
                            SegmentQueue#{0}: addLockedTail. currentTail :
                            {1}
                            , newTail :
                            {2}{3}""",
                    segmentQueueId, currentTail, newTail, System.lineSeparator());
        }
        if (currentTail == null) {
            if (!HEAD.compareAndSet(this, null, newTail)) {
                throw new IllegalStateException("Could not replace null head with new tail");
            }
            if (!TAIL.compareAndSet(this, null, newTail)) {
                throw new IllegalStateException("Could not replace null tail with new tail");
            }
            return newTail;
        }

        assert currentTail.lastSegmentQueueId != segmentQueueId;
        assert currentTail.lock.isHeldByCurrentThread();

        if (!Segment.NEXT.compareAndSet(currentTail, null, newTail)) {
            throw new IllegalStateException("Could not add new Segment after current tail, " +
                    "next node should be null");
        }
        if (!TAIL.compareAndSet(this, currentTail, newTail)) {
            throw new IllegalStateException("Could not replace current tail with new tail");
        }

        if (unlockCurrentTail) {
            currentTail.unlock();
        }

        return newTail;
    }

    @NonNegative
    long expectSize(final long expectedSize) {
        return size();
    }

    @NonNegative
    long size() {
        return size.longValue();
    }

    final void incrementSize(final @NonNegative long increment) {
        size.add(increment);
    }

    final void decrementSize(final @NonNegative long decrement) {
        size.add(-decrement);
    }

    /**
     * Iterates over all segments in this queue.
     */
    final void forEach(final @NonNull Consumer<Segment> action) {
        Objects.requireNonNull(action);
        var s = head();
        while (s != null) {
            action.accept(s);
            s = s.next();
        }
    }

    @Override
    public void close() {
        // NOP
    }

    @Override
    public String toString() {
        return "SegmentQueue{" +
                "segmentQueueId=" + segmentQueueId +
                ", size=" + size +
                ", lock=" + lock +
                "\n, head=" + head +
                "\n, tail=" + tail +
                '}';
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
