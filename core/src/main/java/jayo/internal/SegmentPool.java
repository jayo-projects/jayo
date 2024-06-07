/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
 *
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static jayo.internal.Segment.WRITING;

/**
 * This class pools segments in a singly-linked queue of {@linkplain Segment segments}. Though this code is lock-free it
 * does use a sentinel {@link #DOOR} value to defend against races. Conflicted operations are not retried, so there is
 * no chance of blocking despite the term "lock".
 * <p>
 * On {@link #take()}, a caller swaps the Thread's corresponding segment cache with the {@link #DOOR} sentinel. If the
 * segment cache was not already locked, the caller pop the first segment from the cache.
 * <p>
 * On {@link #recycle(Segment)} a caller swaps the Thread's corresponding segment cache with the {@link #DOOR} sentinel.
 * If the segment cache was not already locked, the caller push the segment in the last position of the cache.
 * <p>
 * On conflict, operations succeed, but segments are not pooled. For example, a {@link #take()} that loses a race
 * allocates a new segment regardless of the pool size. A {@link #recycle(Segment)} call that loses a race will not
 * increase the size of the pool.
 * <p>
 * Under significant contention, this pool will have fewer hits and the VM will do more GC and memory allocations.
 */
@SuppressWarnings("unchecked")
public final class SegmentPool {
    // un-instantiable
    private SegmentPool() {
    }

    /**
     * The maximum byte size in all segments per hash bucket in the pool.
     */
    // TODO: Is this a good maximum size?
    static final int MAX_SIZE = 256 * Segment.SIZE;

    /**
     * The number of hash buckets. This number needs to balance keeping the pool small and contention low. We use the
     * number of processors rounded up to the nearest power of two.
     * For example a machine with 6 cores will have 8 hash buckets.
     */
    private static final int HASH_BUCKET_COUNT = Integer.highestOneBit(Runtime.getRuntime().availableProcessors() * 2 - 1);

    /**
     * A sentinel segment to indicate that the cache is currently being modified.
     */
    private static final Segment DOOR = new Segment(new byte[0], 0, 0, false, false);

    /**
     * Hash buckets each contain a singly-linked queue of segments. The index/key is a hash function of thread ID
     * because it may reduce contention or increase locality.
     * <p>
     * We don't use ThreadLocal because we don't know how many threads the host process has, and we don't want to leak
     * memory for the duration of a thread's life.
     */
    private static final @NonNull AtomicReference<@Nullable Segment> @NonNull [] hashBuckets;

    static {
        hashBuckets = new AtomicReference[HASH_BUCKET_COUNT];
        // null value implies an empty bucket
        Arrays.setAll(hashBuckets, _unused -> new AtomicReference<@Nullable Segment>());
    }

    /**
     * For testing only. Returns a snapshot of the number of bytes currently in the pool. If the pool is segmented such
     * as by thread, this returns the byte count accessible to the calling thread.
     */
    static int getByteCount() {
        final var first = firstRef().get();
        return (first != null) ? first.limit() : 0;
    }

    static @NonNull Segment take() {
        final var firstRef = firstRef();

        // Hold the door !!!
        final var first = firstRef.getAndSet(DOOR);
        if (first == DOOR) {
            // A take() or recycle() is currently in progress. Return a new segment.
            return new Segment();
        }
        if (first == null) {
            // We acquired the lock but the cache was empty. Unlock and return a new segment.
            firstRef.set(null);
            return new Segment();
        }

        // We acquired the lock and the cache was not empty. Pop the first element and return it.
        firstRef.set((Segment) Segment.NEXT.get(first));

        // cleanup segment to cache, must be done here because these are non-volatile fields. We ensure the thread that
        // take this segment has these values
        Segment.NEXT.set(first, null);
        first.pos = 0;
        first.limitVolatile(0);
        Segment.STATUS.setVolatile(first, WRITING);

        return first;
    }

    static void recycle(final @NonNull Segment segment) {
        Objects.requireNonNull(segment);

        if (segment.shared) {
            Segment.NEXT.set(segment, null);
            return; // This segment cannot be recycled.
        }
        final var firstRef = firstRef();

        // Hold the door !!!
        var first = firstRef.getAndSet(DOOR);
        if (first == DOOR) {
            Segment.NEXT.set(segment, null);
            return; // A take() or recycle() is currently in progress.
        }


        final var firstLimit = (first != null) ? first.limit() : 0;
        if (firstLimit >= MAX_SIZE) {
            firstRef.set(first); // Pool is full.
            Segment.NEXT.set(segment, null);
            return;
        }

        Segment.NEXT.set(segment, first);
        segment.limit(firstLimit + Segment.SIZE);

        firstRef.set(segment);
    }

    private static AtomicReference<@Nullable Segment> firstRef() {
        // Get a final value in [0..HASH_BUCKET_COUNT) based on the current thread.
        final var hashBucketIndex = getHashBucketIndex(Thread.currentThread());
        return hashBuckets[hashBucketIndex];
    }

    static int getHashBucketIndex(final @NonNull Thread thread) {
        Objects.requireNonNull(thread);
        return (int) (thread.threadId() & (HASH_BUCKET_COUNT - 1L));
    }
}
