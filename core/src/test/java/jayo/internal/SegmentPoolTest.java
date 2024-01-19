/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

public class SegmentPoolTest {
    @Test
    void singleThreadRecycle() {
        TestUtil.takeAllPoolSegments();
        final var initialSegment = new SegmentQueue();
        initialSegment.prev = null;
        initialSegment.next = null;
        SegmentPool.recycle(initialSegment);
        final var newSegment = SegmentPool.take();

        assertThat(initialSegment).isSameAs(newSegment);
    }

    @Test
    void twoThreads() throws InterruptedException {
        TestUtil.takeAllPoolSegments();
        final var initialSegment = new SegmentQueue();
        initialSegment.prev = null;
        initialSegment.next = null;
        SegmentPool.recycle(initialSegment);

        final var initialThreadHashBucketIndex = SegmentPool.getHashBucketIndex(Thread.currentThread());
        final var segmentThreadId = getSegmentFromOtherVirtalThread(initialThreadHashBucketIndex, true);

        assertThat(initialSegment).isSameAs(segmentThreadId.segment);
        assertThat(Thread.currentThread().threadId()).isNotEqualTo(segmentThreadId.threadId);
    }

    @Test
    void thread1RecycleThread2Take() throws InterruptedException {
        TestUtil.takeAllPoolSegments();
        final var initialSegment = new SegmentQueue();
        initialSegment.prev = null;
        initialSegment.next = null;
        SegmentPool.recycle(initialSegment);

        final var initialThreadHashBucketIndex = SegmentPool.getHashBucketIndex(Thread.currentThread());
        final var segmentThreadId = getSegmentFromOtherVirtalThread(initialThreadHashBucketIndex, false);

        // take the segment in cache to reset to an all empty pool
        getSegmentFromOtherVirtalThread(initialThreadHashBucketIndex, true);

        assertThat(initialSegment).isNotSameAs(segmentThreadId.segment);
        assertThat(Thread.currentThread().threadId()).isNotEqualTo(segmentThreadId.threadId);
    }

    private SegmentThreadId getSegmentFromOtherVirtalThread(
            int initialThreadHashBucketIndex,
            boolean matching
    ) throws InterruptedException {
        final var segments = new ArrayList<Segment>();
        final Runnable runnable = () -> segments.add(SegmentPool.take());
        //create a new virtual Thread
        var newThread = TestUtil.newThread(runnable);
        if (matching) {
            // loop until we have a matching hash bucket
            while (SegmentPool.getHashBucketIndex(newThread) != initialThreadHashBucketIndex) {
                newThread = TestUtil.newThread(runnable);
            }
        } else {
            // loop until we have a non-matching hash bucket
            while (SegmentPool.getHashBucketIndex(newThread) == initialThreadHashBucketIndex) {
                newThread = TestUtil.newThread(runnable);
            }
        }
        // okay we now have another thread, run it !
        newThread.start();
        // wait until the virtual thread has finished its work and stopped executing
        newThread.join();

        return new SegmentThreadId(segments.getFirst(), newThread.threadId());
    }

    private record SegmentThreadId(Segment segment, long threadId) {
    }
}
