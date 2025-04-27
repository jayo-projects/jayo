/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import static java.lang.System.Logger.Level.TRACE;
import static jayo.internal.Utils.NULL_SEGMENT_REF;

sealed class SegmentQueue implements AutoCloseable permits WriterSegmentQueue, ReaderSegmentQueue {
    static final long MAX_BYTE_SIZE = 128 * 1024;

    private static final System.Logger LOGGER = System.getLogger("jayo.SegmentQueue");

    final @NonNull Lock lock;

    private final @NonNull LongAdder size = new LongAdder();

    @Nullable
    Segment tail = null;
    volatile @NonNull SegmentRef headRef = NULL_SEGMENT_REF;

    // VarHandle mechanics
    static final VarHandle HEAD_REF;

    static {
        try {
            final var l = MethodHandles.lookup();
            HEAD_REF = l.findVarHandle(SegmentQueue.class, "headRef", SegmentRef.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public SegmentQueue() {
        this(new ReentrantLock());
    }

    SegmentQueue(final @NonNull Lock lock) {
        assert lock != null;
        this.lock = lock;
    }

    final @Nullable Segment head() {
        return headRef.value();
    }

    final @Nullable Segment removeHead(final @NonNull Segment currentHead, final boolean nonLast) {
        assert currentHead != null;

        final Segment newHead;
        if (nonLast) {
            newHead = removeHeadMaybeSplit(currentHead, false);
        } else {
            lock.lock();
            try {
                if (!currentHead.tryAndValidateRemove()) {
                    return null;
                }
                newHead = removeHeadMaybeSplit(currentHead, false);
            } finally {
                lock.unlock();
            }
        }

        SegmentPool.recycle(currentHead);
        return newHead;
    }

    final @Nullable Segment removeHeadMaybeSplit(final @NonNull Segment currentHead, final boolean notReplaceHead) {
        assert currentHead != null;

        final var newHead = currentHead.next;
        if (newHead != null) {
            if (notReplaceHead) {
                Segment.NEXT.setRelease(currentHead, null);
            }
        } else {
            // if removed head was also the tail, remove tail as well
            assert tail == currentHead;
            tail = null;
        }
        if (!notReplaceHead) {
            assert currentHead == headRef.value();
            HEAD_REF.setRelease(this, new SegmentRef.Immediate(newHead));
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

        // 1) build the ByteBuffer list to read from
        final var byteBuffers = new ArrayList<ByteBuffer>();
        var remaining = toRead;
        lock.lock();
        try {
            var segment = headRef.value();
            while (true) {
                assert segment != null;
                final var toReadInSegment = Math.min(remaining, segment.limit - segment.pos);
                byteBuffers.add(segment.asReadByteBuffer(toReadInSegment));
                remaining -= toReadInSegment;
                if (remaining == 0) {
                    break;
                }
                segment = segment.next;
            }
        } finally {
            lock.unlock();
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
        var head = headRef.value();
        while (!finished) {
            assert head != null;
            final var currentLimit = head.limit;
            final var readFromHead = Math.min(remaining, currentLimit - head.pos);
            head.pos += readFromHead;
            decrementSize(readFromHead);
            remaining -= readFromHead;
            finished = remaining == 0;
            if (head.pos == currentLimit) {
                if (finished) {
                    removeHead(head, false);
                } else {
                    head = removeHead(head, true);
                }
            }
        }

        return read;
    }

    final void withCompactedHeadAsByteBuffer(final int toRead,
                                             final @NonNull ToIntFunction<@NonNull ByteBuffer> readAction) {
        assert readAction != null;
        assert toRead > 0;

        final Segment head;
        lock.lock();
        try {
            head = headRef.value();
            assert head != null;

            // 1) adapt the head segment to red from, compact it if needed
            final var availableInHead = head.limit - head.pos;
            if (availableInHead < toRead) {
                // must compact several segments in the head alone.
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, "Compact head start, SegmentQueue:{0}{1},{2}",
                            System.lineSeparator(), this, System.lineSeparator());
                }

                // 1.1) shift bytes in head
                System.arraycopy(head.data, head.pos, head.data, 0, availableInHead);
                head.limit = head.limit - head.pos;
                head.pos = 0;

                // 1.2) copy bytes from next segments into head
                var remaining = toRead - availableInHead;
                var segment = head.next;
                var finished = false;
                while (!finished) {
                    assert segment != null;
                    final var toCopy = Math.min(remaining, segment.limit - segment.pos);
                    System.arraycopy(segment.data, segment.pos, head.data, head.limit, toCopy);
                    segment.pos += toCopy;
                    head.limit += toCopy;
                    remaining -= toCopy;
                    finished = remaining == 0;
                    if (segment.pos == segment.limit) {
                        final var oldSegment = segment;
                        segment = segment.next;
                        if (finished) {
                            if (oldSegment.tryAndValidateRemove()) {
                                assert head.next == oldSegment;
                                Segment.NEXT.setRelease(head, segment);
                                if (segment == null) {
                                    // this segment was the tail, now head is the tail
                                    assert tail == oldSegment;
                                    tail = head;
                                }
                            }
                        } else {
                            assert head.next == oldSegment;
                            Segment.NEXT.setRelease(head, segment);
                        }
                        SegmentPool.recycle(oldSegment);
                    }
                }

                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, "Compact head end, SegmentQueue:{0}{1},{2}",
                            System.lineSeparator(), this, System.lineSeparator());
                }
            }
        } finally {
            lock.unlock();
        }

        // 2) call readAction
        final var read = readAction.applyAsInt(head.asReadByteBuffer(toRead));
        if (read <= 0) {
            return;
        }

        // 3) apply changes to head segment
        head.pos += read;
        decrementSize(read);
        if (head.pos == head.limit) {
            removeHead(head, true); // pass true, head may or may not be the last segment
        }
    }

    final <T> T withWritableTail(final int minimumCapacity,
                                 final @NonNull Function<@NonNull Segment, T> writeAction) {
        assert writeAction != null;
        assert minimumCapacity > 0;
        assert minimumCapacity <= Segment.SIZE;

        var needsNewSegment = true;
        final Segment writableTail;
        var previousLimit = 0L;
        lock.lock();
        try {
            if (tail == null || !tail.tryWrite()) {
                writableTail = null;
            } else {
                writableTail = tail;
            }

            if (writableTail != null) {
                previousLimit = writableTail.limit;

                // the current tail has enough room
                if (writableTail.owner && previousLimit + minimumCapacity <= Segment.SIZE) {
                    needsNewSegment = false;
                }
            }
        } finally {
            lock.unlock();
        }

        if (needsNewSegment) {
            return withNewWritableSegment(writeAction, writableTail);
        }

        final var result = writeAction.apply(writableTail);

        lock.lock();
        try {
            var written = tail.limit - previousLimit;
            tail.finishWrite();
            incrementSize(written);
            return result;
        } finally {
            lock.unlock();
        }
    }

    private <T> T withNewWritableSegment(final @NonNull Function<@NonNull Segment, T> writeAction,
                                         final @Nullable Segment currentTail) {
        // acquire a new empty segment to fill up.
        final var newTail = SegmentPool.take();
        final var result = writeAction.apply(newTail);

        var mustRecycle = false;
        lock.lock();
        try {
            final var written = newTail.limit;
            if (written > 0) {
                if (currentTail != null) {
                    assert currentTail.next == null;
                    Segment.NEXT.setRelease(currentTail, newTail);
                    currentTail.finishWrite();
                } else {
                    assert headRef.value() == null;
                    HEAD_REF.setRelease(this, new SegmentRef.Immediate(newTail));
                }
                tail = newTail;
                newTail.finishWrite();
                incrementSize(written);
            } else {
                if (currentTail != null) {
                    currentTail.finishWrite();
                }
                // We allocated a tail segment, but didn't end up needing it. Recycle!
                mustRecycle = true;
            }
        } finally {
            lock.unlock();
        }

        if (mustRecycle) {
            SegmentPool.recycle(newTail);
        }

        return result;
    }

    final @Nullable Segment nonRemovedTailOrNull() {
        if (tail == null) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE,
                        "SegmentQueue#{0}: nonRemovedTailOrNull() no current tail, return null{1}",
                        hashCode(), System.lineSeparator());
            }
            return null;
        }

        if (tail.tryWrite()) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, """
                                SegmentQueue#{0}: nonRemovedTailOrNull() switch to write status and return current non removed tail :
                                {1}{2}""",
                        hashCode(), tail, System.lineSeparator());
            }
            return tail;
        }

        // tail was removed, return null
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, """
                            SegmentQueue#{0}: nonRemovedTailOrNull() current tail was removed :
                            {1}
                            , return null{2}""",
                    hashCode(), tail, System.lineSeparator());
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
                assert headRef.value() == null;
                assert tail == null;
                HEAD_REF.setRelease(this, new SegmentRef.Immediate(newTail));
                tail = newTail;
                return newTail;
            }

            final var previousTail = tail;
            tail = newTail;
            if (previousTail == currentTail) {
                assert currentTail.next == null;
                Segment.NEXT.setRelease(currentTail, newTail);
                if (finishWriteInSegments) {
                    currentTail.finishWrite();
                }
            } else {
                assert headRef.value() == null;
                HEAD_REF.setRelease(this, new SegmentRef.Immediate(newTail));
            }

            return newTail;
        } finally {
            if (finishWriteInSegments) {
                newTail.finishWrite();
            }
        }
    }

    long expectSize(final long expectedSize) {
        assert expectedSize > 0L;
        return size();
    }

    long size() {
        return size.longValue();
    }

    final void incrementSize(final long increment) {
        assert increment >= 0;
        if (increment == 0L) {
            return;
        }
        size.add(increment);
    }

    final void decrementSize(final long decrement) {
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
        var segment = headRef.value();
        while (segment != null) {
            action.accept(segment);
            segment = segment.next;
        }
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public String toString() {
        return "SegmentQueue#" + hashCode() + "{" +
                " size=" + size + System.lineSeparator() +
                ", head=" + headRef.value() + System.lineSeparator() +
                ", tail=" + tail +
                '}';
    }
}
