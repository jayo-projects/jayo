/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio) and kotlinx-io (https://github.com/Kotlin/kotlinx-io), original
 * copyrights are below
 *
 * Copyright 2017-2023 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENCE file.
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

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;

/**
 * This class pools segments in a lock-free singly-linked queue of {@linkplain Segment segments}. Though this code is
 * lock-free it does use a sentinel {@link #DOOR} value to defend against races. To reduce the contention, the pool
 * consists of several buckets (see {@link #HASH_BUCKET_COUNT}), each holding a reference to its own segments cache.
 * Every {@link #take()} or {@link #recycle(Segment)} choose one of the buckets depending on a
 * {@link Thread#currentThread()}'s {@linkplain JavaVersionUtils#threadId(Thread) threadId}.
 * <p>
 * On {@link #take()}, a caller swaps the Thread's corresponding segment cache with the {@link #DOOR} sentinel. If the
 * segment cache was not already locked, the caller pop the first segment from the cache.
 * <p>
 * On {@link #recycle(Segment)}, a caller swaps the head with a new node whose successor is the replaced head.
 * <p>
 * On conflict, operations are retried until they succeed.
 * <p>
 * This tracks the number of bytes in each queue in its {@code Segment.limit} property. Each element has a limit that's
 * one segment size greater than its successor element. The maximum size of the pool is a product of {@code #MAX_SIZE}
 * and {@code #HASH_BUCKET_COUNT}.
 * <p>
 * {@code #MAX_SIZE} is kept relatively small to avoid excessive memory consumption in case of a large
 * {@code #HASH_BUCKET_COUNT}.
 * For better handling of scenarios with high segments demand, a second-level pool is enabled and can be tuned by
 * setting up a value of `jayo.pool.size.bytes` system property.
 * <p>
 * The second-level pool use half of the {@code #HASH_BUCKET_COUNT} and if an initially selected bucket is empty on
 * {@link #take()} or full or {@link #recycle(Segment)}, all other buckets will be inspected before finally giving up
 * (which means allocating a new segment on {@link #take()}, or loosing a reference to a segment on
 * {@link #recycle(Segment)}). That second-level pool is used as a backup in case when {@link #take()} or
 * {@link #recycle(Segment)} failed due to an empty or exhausted segments chain in a corresponding first-level bucket
 * (one of {@code #HASH_BUCKET_COUNT}).
 */
@SuppressWarnings("unchecked")
public final class SegmentPool {
    private static final System.Logger LOGGER = System.getLogger("jayo.SegmentPool");

    // un-instantiable
    private SegmentPool() {
    }

    /**
     * The maximum number of bytes to pool per hash bucket.
     */
    // TODO: Is this a good maximum size?
    static final int MAX_SIZE = 8 * Segment.SIZE; // ~150 KiB.

    /**
     * The number of hash buckets. This number needs to balance keeping the pool small and contention low. We use the
     * number of processors rounded up to the nearest power of two.
     * For example a machine with 6 cores will have 8 hash buckets.
     */
    private static final int HASH_BUCKET_COUNT =
            Integer.highestOneBit(Runtime.getRuntime().availableProcessors() * 2 - 1);

    private static final int HASH_BUCKET_COUNT_L2;

    private static final int DEFAULT_SECOND_LEVEL_POOL_TOTAL_SIZE = 4 * 1024 * 1024; // 4MB

    private static final int SECOND_LEVEL_POOL_TOTAL_SIZE;

    private static final int SECOND_LEVEL_POOL_BUCKET_SIZE;

    /**
     * A sentinel segment to indicate that the cache is currently being modified.
     */
    private static final Segment DOOR = new Segment(new byte[0], 0, 0, null, false);

    /**
     * Hash buckets each contain a singly-linked queue of segments. The index/key is a hash function of thread ID
     * because it may reduce contention or increase locality.
     * <p>
     * We don't use ThreadLocal because we don't know how many threads the host process has, and we don't want to leak
     * memory for the duration of a thread's life.
     */
    private static final @NonNull AtomicReference<@Nullable Segment> @NonNull [] HASH_BUCKETS;
    private static final @NonNull AtomicReference<@Nullable Segment> @NonNull [] HASH_BUCKETS_L2;

    static {
        final var hashBucketCountL2 = HASH_BUCKET_COUNT / 2;
        HASH_BUCKET_COUNT_L2 = (hashBucketCountL2 > 0) ? hashBucketCountL2 : 1;

        // SegmentPool.SECOND_LEVEL_POOL_TOTAL_SIZE System property overriding.
        String systemSecondLevelPoolTotalSize = null;
        try {
            systemSecondLevelPoolTotalSize = System.getProperty("jayo.pool.size.bytes");
        } catch (Throwable t) { // whatever happens, recover
            LOGGER.log(ERROR,
                    "Exception when resolving the provided second level pool size, fallback to default = {0}",
                    DEFAULT_SECOND_LEVEL_POOL_TOTAL_SIZE);
        } finally {
            var secondLevelPoolTotalSize = 0;
            if (systemSecondLevelPoolTotalSize != null && !systemSecondLevelPoolTotalSize.isBlank()) {
                try {
                    secondLevelPoolTotalSize = Integer.parseInt(systemSecondLevelPoolTotalSize);
                } catch (NumberFormatException _unused) {
                    LOGGER.log(ERROR, "{0} is not a valid size, fallback to default second level pool size = {1}",
                            systemSecondLevelPoolTotalSize, DEFAULT_SECOND_LEVEL_POOL_TOTAL_SIZE);
                }
            }
            SECOND_LEVEL_POOL_TOTAL_SIZE =
                    (secondLevelPoolTotalSize > 0) ? secondLevelPoolTotalSize : DEFAULT_SECOND_LEVEL_POOL_TOTAL_SIZE;
            LOGGER.log(INFO, "Jayo will use second level pool size of = {0} bytes", SECOND_LEVEL_POOL_TOTAL_SIZE);
        }

        SECOND_LEVEL_POOL_BUCKET_SIZE = Math.max(SECOND_LEVEL_POOL_TOTAL_SIZE / HASH_BUCKET_COUNT_L2, Segment.SIZE);

        HASH_BUCKETS = new AtomicReference[HASH_BUCKET_COUNT];
        // null value implies an empty bucket
        Arrays.setAll(HASH_BUCKETS, _unused -> new AtomicReference<@Nullable Segment>());

        HASH_BUCKETS_L2 = new AtomicReference[HASH_BUCKET_COUNT_L2];
        // null value implies an empty bucket
        Arrays.setAll(HASH_BUCKETS_L2, _unused -> new AtomicReference<@Nullable Segment>());
    }

    /**
     * For testing only. Returns a snapshot of the number of bytes currently in the pool. If the pool is segmented such
     * as by thread, this returns the byte count accessible to the calling thread.
     */
    static int getByteCount() {
        final var first = HASH_BUCKETS[l1BucketId(Thread.currentThread())].get();
        return (first != null) ? first.limit : 0;
    }

    static @NonNull Segment take() {
        final var firstRef = HASH_BUCKETS[l1BucketId(Thread.currentThread())];

        while (true) {
            // Hold the door !!!
            final var first = firstRef.getAndSet(DOOR);
            if (first == DOOR) {
                // We didn't acquire the lock. Let's try again
                continue;
            }

            if (first == null) {
                // We acquired the lock but the pool was empty.
                // Unlock the bucket and acquire a segment from the second level cache
                firstRef.set(null);

                return takeL2();
            }

            // We acquired the lock and the pool was not empty. Pop the first element and return it.
            firstRef.set(first.next);

            // cleanup segment to cache.
            first.next = null;
            first.pos = 0;
            first.owner = true;
            first.limit = 0;
            first.status = Segment.WRITING;

            return first;
        }
    }

    private static @NonNull Segment takeL2() {
        var bucketId = l2BucketId(Thread.currentThread());
        var attempts = 0;

        while (true) {
            final var firstRef = HASH_BUCKETS_L2[bucketId];

            // Hold the door !!!
            final var first = firstRef.getAndSet(DOOR);
            if (first == DOOR) {
                // We didn't acquire the lock. Let's try again
                continue;
            }

            if (first == null) {
                // We acquired the lock but the pool was empty.
                // Unlock the current bucket and select a new one.
                // If all buckets were already scanned, allocate a new segment.
                firstRef.set(null);

                if (attempts < HASH_BUCKET_COUNT_L2) {
                    bucketId = (bucketId + 1) & (HASH_BUCKET_COUNT_L2 - 1);
                    attempts++;
                    continue;
                }

                return new Segment();
            }

            // We acquired the lock and the pool was not empty. Pop the first element and return it.
            firstRef.set(first.next);

            // cleanup segment to cache.
            first.next = null;
            first.pos = 0;
            first.owner = true;
            first.limit = 0;
            first.status = Segment.WRITING;

            return first;
        }
    }

    static void recycle(final @NonNull Segment segment) {
        assert segment != null;

        final var segmentCopyTracker = segment.copyTracker;

        // This segment cannot be recycled.
        if (segmentCopyTracker != null && segmentCopyTracker.removeCopy()) {
            segment.next = null;
            return;
        }

        final var firstRef = HASH_BUCKETS[l1BucketId(Thread.currentThread())];

        while (true) {
            var first = firstRef.get();
            if (first == DOOR) {
                continue; // A take() is currently in progress.
            }

            final var firstLimit = (first != null) ? first.limit : 0;
            if (firstLimit >= MAX_SIZE) {
                recycleL2(segment);
                return;
            }

            segment.next = first;
            segment.limit = firstLimit + Segment.SIZE;

            if (firstRef.compareAndSet(first, segment)) {
                return;
            }
        }
    }

    private static void recycleL2(final @NonNull Segment segment) {
        var bucketId = l2BucketId(Thread.currentThread());
        var attempts = 0;

        while (true) {
            final var firstRef = HASH_BUCKETS_L2[bucketId];
            var first = firstRef.get();

            if (first == DOOR) {
                continue; // A take() is currently in progress.
            }

            final var firstLimit = (first != null) ? first.limit : 0;
            if (firstLimit + Segment.SIZE > SECOND_LEVEL_POOL_BUCKET_SIZE) {
                // The current bucket is full, try to find another one and return the segment there.
                if (attempts < HASH_BUCKET_COUNT_L2) {
                    attempts++;
                    bucketId = (bucketId + 1) & (HASH_BUCKET_COUNT_L2 - 1);
                    continue;
                }
                // L2 pool is full.
                segment.next = null;
                return;
            }

            segment.next = first;
            segment.limit = firstLimit + Segment.SIZE;

            if (firstRef.compareAndSet(first, segment)) {
                return;
            }
        }
    }

    static int l1BucketId(final @NonNull Thread thread) {
        return bucketId(thread, HASH_BUCKET_COUNT - 1L);
    }

    private static int l2BucketId(final @NonNull Thread thread) {
        return bucketId(thread, HASH_BUCKET_COUNT_L2 - 1L);
    }

    static int bucketId(final @NonNull Thread thread, final long mask) {
        Objects.requireNonNull(thread);
        return (int) (JavaVersionUtils.threadId(thread) & mask);
    }
}
