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

import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class pools segments in an array of [SegmentCache]. Though this code is lock-free it does use a sentinel [DOOR]
 * value to defend against races. Conflicted operations are not retried, so there is no chance of blocking despite the
 * term "lock".
 * <p>
 * On [take], a caller swaps the Thread's segment cache with the [DOOR] sentinel. If the segment cache was not already
 * locked, the caller pop the first segment from the cache.
 * <p>
 * On [recycle], a caller swaps the Thread's segment cache with the [DOOR] sentinel. If the segment cache was not
 * already locked, the caller push the Segment in the last position of the cache.
 * <p>
 * On conflict, operations succeed, but segments are not pooled. For example, a [take] that loses a race allocates a
 * new segment regardless of the pool size.
 * A [recycle] call that loses a race will not increase the size of the pool.
 * Under significant contention, this pool will have fewer hits and the VM will do more GC and memory allocations.
 */
@SuppressWarnings("unchecked")
public final class SegmentPool {
    // un-instantiable
    private SegmentPool() {
    }


    private static final int SEGMENTS_POOL_SIZE = 256;

    /**
     * For tests only : the maximum number of segments to pool per hash bucket.
     */
    // TODO: Is this a good maximum size?
    static final int SEGMENTS_POOL_MAX_BYTE_SIZE = SEGMENTS_POOL_SIZE * Segment.SIZE;

    /**
     * The number of hash buckets. This number needs to balance keeping the pool small and contention low. We use the
     * number of processors rounded up to the nearest power of two.
     * For example a machine with 6 cores will have 8 hash buckets.
     */
    private static final int HASH_BUCKET_COUNT = Integer.highestOneBit(Runtime.getRuntime().availableProcessors() * 2 - 1);

    /**
     * A sentinel segment cache to indicate that the cache is currently being modified.
     */
    private static final SegmentCache DOOR = new SegmentCache(0);

    /**
     * Hash buckets each contain a singly-linked queue of segments. The index/key is a hash function of thread ID
     * because it may reduce contention or increase locality.
     * <p>
     * We don't use ThreadLocal because we don't know how many threads the host process has, and we don't want to leak
     * memory for the duration of a thread's life.
     */
    private static final @NonNull AtomicReference<@Nullable SegmentCache> @NonNull [] hashBuckets;

    static {
        hashBuckets = new AtomicReference[HASH_BUCKET_COUNT];
        // null value implies an empty bucket
        Arrays.setAll(hashBuckets, _unused -> new AtomicReference<@Nullable SegmentCache>());
    }

    /**
     * For testing only. Returns a snapshot of the number of bytes currently in the pool. If the pool is segmented such
     * as by thread, this returns the byte count accessible to the calling thread.
     */
    static int getByteCount() {
        final var cache = cacheRef().get();
        return (cache != null) ? cache.count * Segment.SIZE : 0;
    }

    static @NonNull Segment take() {
        final var cacheRef = cacheRef();

        // Hold the door !!!
        final var cache = cacheRef.getAndSet(DOOR);
        if (cache == DOOR) {
            // A take() or recycle() is currently in progress. Return a new segment.
            return new Segment();
        }
        if (cache == null || cache.isEmpty()) {
            // We acquired the lock but the cache was empty. Unlock and return a new segment.
            cacheRef.set(cache);
            return new Segment();
        }

        // We acquired the lock and the cache was not empty. Pop the first element and return it.
        final var segment = cache.pop();
        cacheRef.set(cache);
        segment.pos = 0;
        segment.limit = 0;
        return segment;
    }

    static void recycle(final @NonNull Segment segment) {
        Objects.requireNonNull(segment);
        if (segment.shared) {
            return; // This segment cannot be recycled.
        }
        final var cacheRef = cacheRef();

        // Hold the door !!!
        var cache = cacheRef.getAndSet(DOOR);
        if (cache == DOOR) {
            return; // A take() or recycle() is currently in progress.
        }


        // cache was null, create it
        if (cache == null) {
            cache = new SegmentCache(SEGMENTS_POOL_SIZE);
        }
        cache.push(segment);

        cacheRef.set(cache);
    }

    private static AtomicReference<@Nullable SegmentCache> cacheRef() {
        // Get a final value in [0..HASH_BUCKET_COUNT) based on the current thread.
        final var hashBucketIndex = getHashBucketIndex(Thread.currentThread());
        return hashBuckets[hashBucketIndex];
    }

    static int getHashBucketIndex(final @NonNull Thread thread) {
        Objects.requireNonNull(thread);
        return (int) (thread.threadId() & (HASH_BUCKET_COUNT - 1L));
    }

    /**
     * A simple cache of segments.
     */
    private static class SegmentCache {
        private final @NonNegative int cacheSize;
        /**
         * the array of elements
         */
        private final @Nullable Segment @NonNull [] segments;

        /**
         * the number of elements in the cache
         */
        private @NonNegative int count = 0;

        /**
         * the index of the first valid element (undefined if count == 0)
         */
        private @NonNegative int start = 0;

        private SegmentCache(final @NonNegative int cacheSize) {
            this.cacheSize = cacheSize;
            segments = new Segment[cacheSize];
        }

        private boolean isEmpty() {
            return count == 0;
        }

        private @NonNull Segment pop() {
            assert (count > 0);
            final var segment = segments[start];
            segments[start] = null;
            start = (start + 1) % cacheSize;
            count--;
            assert segment != null;
            return segment;
        }

        private void push(final @NonNull Segment segment) {
            Objects.requireNonNull(segment);
            if (count >= cacheSize) {
                return;
            }
            final var next = (start + count) % cacheSize;
            segments[next] = segment;
            count++;
        }
    }
}
