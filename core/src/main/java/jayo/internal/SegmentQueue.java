/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import static java.lang.System.Logger.Level.TRACE;

sealed class SegmentQueue implements AutoCloseable permits WriterSegmentQueue, ReaderSegmentQueue {
    final static long MAX_BYTE_SIZE = 128 * 1024;

    private static final System.Logger LOGGER = System.getLogger("jayo.SegmentQueue");

    @SuppressWarnings("FieldMayBeFinal")
    private volatile @Nullable Segment head = null;
    @SuppressWarnings("FieldMayBeFinal")
    private volatile @Nullable Segment tail = null;
    private final @NonNull LongAdder size = new LongAdder();

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

    final @Nullable Segment head() {
        return (Segment) HEAD.getVolatile(this);
    }

    final @Nullable Segment tail() {
        return (Segment) TAIL.getVolatile(this);
    }

    final @Nullable Segment removeHead(final @NonNull Segment currentHead) {
        return removeHead(currentHead, null);
    }

    // this method must only be called from Buffer.write call
    final @Nullable Segment removeHead(final @NonNull Segment currentHead, final @Nullable Boolean wasSplit) {
        assert currentHead != null;
        assert (byte) Segment.STATUS.get(currentHead) == Segment.REMOVING
                || (byte) Segment.STATUS.get(currentHead) == Segment.TRANSFERRING;

        final var newHead = currentHead.nextVolatile();
        if (newHead != null) {
            if (wasSplit != null) {
                Segment.NEXT.compareAndSet(currentHead, newHead, null);
            }
        } else {
            // if removed head was also the tail, remove tail as well
            TAIL.compareAndSet(this, currentHead, null);
        }
        if (!Boolean.TRUE.equals(wasSplit)) {
            HEAD.compareAndSet(this, currentHead, newHead);
        }
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, """
                            SegmentQueue#{0}: removeHead(). currentHead :
                            {1}
                            newHead :
                            {2}{3}""",
                    hashCode(), currentHead, newHead, System.lineSeparator());
        }
        return newHead;
    }

    final int withHeadsAsByteBuffers(final int toRead,
                                     final @NonNull ToIntFunction<@NonNull ByteBuffer @NonNull []> readAction) {
        assert readAction != null;
        assert toRead > 0;

        var head = head();
        var segment = head;

        // 1) build the ByteBuffer list to read from
        final var byteBuffers = new ArrayList<ByteBuffer>();
        var remaining = toRead;
        while (true) {
            assert segment != null;
            final var toReadInSegment = Math.min(remaining, segment.limitVolatile() - segment.pos);
            byteBuffers.add(segment.asReadByteBuffer(toReadInSegment));
            remaining -= toReadInSegment;
            if (remaining == 0) {
                break;
            }
            segment = segment.nextVolatile();
        }
        final var sources = byteBuffers.toArray(new ByteBuffer[0]);

        // 2) call readAction
        final var read = readAction.applyAsInt(sources);
        if (read <= 0) {
            return read;
        }

        // 3) apply changes to head segments
        remaining = read;
        var finished = false;
        while (!finished) {
            assert head != null;
            final var currentLimit = head.limitVolatile();
            final var readFromHead = Math.min(remaining, currentLimit - head.pos);
            head.pos += readFromHead;
            decrementSize(readFromHead);
            remaining -= readFromHead;
            finished = remaining == 0;
            if (head.pos == currentLimit) {
                final var oldHead = head;
                if (finished) {
                    if (head.tryRemove() && head.validateRemove()) {
                        removeHead(head);
                    }
                } else {
                    if (!head.tryRemove()) {
                        throw new IllegalStateException("Non tail segment should be removable");
                    }
                    head = removeHead(head);
                }
                SegmentPool.recycle(oldHead);
            }
        }

        return read;
    }

    final void withCompactedHeadAsByteBuffer(final int toRead,
                                             final @NonNull ToIntFunction<@NonNull ByteBuffer> readAction) {
        assert readAction != null;
        assert toRead > 0;

        final var head = head();
        assert head != null;

        // 1) adapt the head segment to red from, compact it if needed
        final var availableInHead = head.limitVolatile() - head.pos;
        if (availableInHead < toRead) {
            // must compact several segments in the head alone.
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "Compact head start, SegmentQueue:{0}{1},{2}",
                        System.lineSeparator(), this, System.lineSeparator());
            }

            // 1.1) shift bytes in head
            System.arraycopy(head.data, head.pos, head.data, 0, availableInHead);
            head.limit(head.limit() - head.pos);
            head.pos = 0;

            // 1.2) copy bytes from next segments into head
            var remaining = toRead - availableInHead;
            var segment = head.nextVolatile();
            var finished = false;
            while (!finished) {
                assert segment != null;
                final var toCopy = Math.min(remaining, segment.limitVolatile() - segment.pos);
                System.arraycopy(segment.data, segment.pos, head.data, head.limit(), toCopy);
                segment.pos += toCopy;
                head.limit(head.limit() + toCopy);
                remaining -= toCopy;
                finished = remaining == 0;
                if (segment.pos == segment.limitVolatile()) {
                    final var oldSegment = segment;
                    segment = segment.nextVolatile();
                    if (finished) {
                        if (oldSegment.tryRemove() && oldSegment.validateRemove()) {
                            if (!Segment.NEXT.compareAndSet(head, oldSegment, segment)) {
                                throw new IllegalStateException(
                                        "Could not swap head, previous next should be the current next");
                            }
                            if (segment == null) {
                                // this segment was the tail, now head is the tail
                                if (!TAIL.compareAndSet(this, oldSegment, head)) {
                                    throw new IllegalStateException("Could not replace old tail with enlarged head");
                                }
                            }
                        }
                    } else {
                        if (!oldSegment.tryRemove()) {
                            throw new IllegalStateException("Non tail segment should be removable");
                        }
                        if (!Segment.NEXT.compareAndSet(head, oldSegment, segment)) {
                            throw new IllegalStateException(
                                    "Could not swap head, previous next should be the current next");
                        }
                    }
                    SegmentPool.recycle(oldSegment);
                }
            }

            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "Compact head end, SegmentQueue:{0}{1},{2}",
                        System.lineSeparator(), this, System.lineSeparator());
            }
        }

        // 2) call readAction
        final var read = readAction.applyAsInt(head.asReadByteBuffer(toRead));
        if (read <= 0) {
            return;
        }

        // 3) apply changes to head segment
        head.pos += read;
        decrementSize(read);
        if (head.pos == head.limitVolatile()) {
            if (head.tryRemove() && head.validateRemove()) {
                removeHead(head);
            }
            SegmentPool.recycle(head);
        }
    }

    final <T> T withWritableTail(final int minimumCapacity,
                                 final @NonNull Function<@NonNull Segment, T> writeAction) {
        assert writeAction != null;
        assert minimumCapacity > 0;
        assert minimumCapacity <= Segment.SIZE;

        final var currentTail = tail();
        if (currentTail == null || !currentTail.tryWrite()) {
            return withNewWritableSegment(writeAction, null);
        }

        assert (byte) Segment.STATUS.get(currentTail) == Segment.WRITING;
        final var previousLimit = currentTail.limit();
        // current tail has enough room
        if (currentTail.owner && previousLimit + minimumCapacity <= Segment.SIZE) {
            var written = 0;
            try {
                final var result = writeAction.apply(currentTail);
                written = currentTail.limit() - previousLimit;
                return result;
            } finally {
                currentTail.finishWrite();
                incrementSize(written);
            }
        }

        return withNewWritableSegment(writeAction, currentTail);
    }

    private <T> T withNewWritableSegment(final @NonNull Function<@NonNull Segment, T> writeAction,
                                         final @Nullable Segment currentTail) {
        // acquire a new empty segment to fill up.
        final var newTail = SegmentPool.take();
        final var result = writeAction.apply(newTail);
        final var written = newTail.limit();
        if (written > 0) {
            try {
                if (currentTail != null) {
                    try {
                        if (!Segment.NEXT.compareAndSet(currentTail, null, newTail)) {
                            throw new IllegalStateException("Could not add new Segment after current tail, " +
                                    "next node should be null");
                        }
                    } finally {
                        currentTail.finishWrite();
                    }
                } else {
                    HEAD.setVolatile(this, newTail);
                }
                TAIL.setVolatile(this, newTail);
            } finally {
                newTail.finishWrite();
                incrementSize(written);
            }
        } else {
            if (currentTail != null) {
                currentTail.finishWrite();
            }
            // We allocated a tail segment, but didn't end up needing it. Recycle!
            SegmentPool.recycle(newTail);
        }
        return result;
    }

    final @Nullable Segment nonRemovedTailOrNull() {
        final var currentTail = tail();
        if (currentTail == null) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE,
                        "SegmentQueue#{0}: nonRemovedTailOrNull() no current tail, return null{1}",
                        hashCode(), System.lineSeparator());
            }
            return null;
        }

        if (currentTail.tryWrite()) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, """
                                SegmentQueue#{0}: nonRemovedTailOrNull() switch to write status and return current non removed tail :
                                {1}{2}""",
                        hashCode(), currentTail, System.lineSeparator());
            }
            return currentTail;
        }

        // tail was removed, return null
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, """
                            SegmentQueue#{0}: nonRemovedTailOrNull() current tail was removed :
                            {1}
                            , return null{2}""",
                    hashCode(), currentTail, System.lineSeparator());
        }
        return null;
    }

    final @NonNull Segment addWritableTail(final @Nullable Segment currentTail,
                                           final @NonNull Segment newTail,
                                           final boolean finishWriteInSegments) {
        assert newTail != null;
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, """
                            SegmentQueue#{0}: addWritableTail. currentTail :
                            {1}
                            , newTail :
                            {2}{3}""",
                    hashCode(), currentTail, newTail, System.lineSeparator());
        }
        try {
            if (currentTail == null) {
                if (!HEAD.compareAndSet(this, null, newTail)) {
                    throw new IllegalStateException("Could not replace null head with new tail");
                }
                if (!TAIL.compareAndSet(this, null, newTail)) {
                    throw new IllegalStateException("Could not replace null tail with new tail");
                }
                return newTail;
            }


            final var previousTail = TAIL.getAndSet(this, newTail);
            if (previousTail == currentTail) {
                if (!Segment.NEXT.compareAndSet(currentTail, null, newTail)) {
                    throw new IllegalStateException("Could not add new Segment after current tail, " +
                            "next node should be null");
                }
                if (finishWriteInSegments) {
                    currentTail.finishWrite();
                }
            } else {
                if (!HEAD.compareAndSet(this, null, newTail)) {
                    throw new IllegalStateException("Could not replace null head with new tail");
                }
            }

            return newTail;
        } finally {
            if (finishWriteInSegments) {
                newTail.finishWrite();
            }
        }
    }

    @NonNegative
    long expectSize(final long expectedSize) {
        assert expectedSize > 0L;
        return size();
    }

    @NonNegative
    long size() {
        return size.longValue();
    }

    final void incrementSize(final @NonNegative long increment) {
        assert increment >= 0;
        if (increment == 0L) {
            return;
        }
        size.add(increment);
    }

    final void decrementSize(final @NonNegative long decrement) {
        assert decrement >= 0;
        if (decrement == 0L) {
            return;
        }
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
            s = s.nextVolatile();
        }
    }

    @Override
    public void close() {
        // NOP
    }

    @Override
    public String toString() {
        return "SegmentQueue#" + hashCode() + "{" +
                " size=" + size + System.lineSeparator() +
                ", head=" + head + System.lineSeparator() +
                ", tail=" + tail +
                '}';
    }
}
