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

import static jayo.internal.Segment.AVAILABLE;

/**
 * This class pools segments in a lock-free singly-linked queue. Though this code is lock-free it does use a sentinel
 * {@link #LOCK} to defend against races. On conflict, operations are not retried and succeed immediately, so there is
 * no chance of blocking despite the term "lock", but segments are not pushed into the queue.
 * <p>
 * On conflict, operations are not retried and succeed immediately, so there is no chance of blocking, but segments are
 * not pushed into the queue.
 * For example, a {@link #take()} that loses a race allocates a new segment regardless of the cache size. A
 * {@link #recycle(Segment)} call that loses a race will not increase the size of the cache.
 * <p>
 * Under significant contention, this cache will have fewer hits and the VM will do more GC and zero filling of memory
 * chunks.
 * <p>
 * This pool is a thread-safe static singleton.
 * <ul>
 * <li>On {@link #take}, the caller polls the head of the queue.
 * <li>On {@link #recycle}, the caller offers the segment back to the pool.
 * </ul>
 */
@SuppressWarnings("unchecked")
public final class SegmentPool {
    // un-instantiable
    private SegmentPool() {
    }

    /**
     * The maximum number of segments to pool per hash bucket.
     */
    // TODO: Is this a good maximum size? Do we ever have that many idle segments?
    static final int MAX_SIZE = 128 * 1024;

    /**
     * The number of hash buckets. This number needs to balance keeping the pool small and contention low. We use the
     * number of processors rounded up to the nearest power of two.
     * For example a machine with 6 cores will have 8 hash buckets.
     */
    private static final int HASH_BUCKET_COUNT = Integer.highestOneBit(Runtime.getRuntime().availableProcessors() * 2 - 1);

    /**
     * A sentinel segment to indicate that the linked list is currently being modified.
     */
    private static final Segment LOCK = new Segment(new byte[0], 0, 0, false, false);

    /**
     * Hash buckets each contain a singly-linked queue of segments. The index/key is a hash function of thread ID
     * because it may reduce contention or increase locality.
     * <p>
     * We don't use ThreadLocal because we don't know how many threads the host process has, and we don't want to leak
     * memory for the duration of a thread's life.
     */
    private static final AtomicReference<Segment>[] hashBuckets;

    static {
        hashBuckets = new AtomicReference[HASH_BUCKET_COUNT];
        // null value implies an empty bucket
        Arrays.setAll(hashBuckets, _unused -> new AtomicReference<Segment>());
    }

    /**
     * For testing only. Returns a snapshot of the number of bytes currently in the pool. If the pool
     * is segmented such as by thread, this returns the byte count accessible to the calling thread.
     */
    static int getByteCount() {
        final var first = firstRef().get();
        return (first != null) ? first.limit : 0;
    }

    public static @NonNull Segment take() {
        final var firstRef = firstRef();
        final var first = firstRef.getAndSet(LOCK);
        if (first == LOCK) {
            // We didn't acquire the lock. Return a new segment.
            return new Segment();
        }
        if (first == null) {
            // We acquired the lock but the pool was empty. Unlock and return a new segment.
            firstRef.set(null);
            return new Segment();
        }

        // We acquired the lock and the pool was not empty. Pop the first element and return it.
        firstRef.set(first.next);
        first.next = null;
        first.limit = 0;
        return first;
    }

    static void recycle(final @Nullable Segment segment) {
        if (segment == null) {
            return;
        }
        if (segment.next != null || segment.prev != null) {
            throw new IllegalArgumentException("next and prev properties of the segment must be null");
        }
        if (segment.shared) {
            return; // This segment cannot be recycled.
        }
        final var firstRef = firstRef();

        final var first = firstRef.getAndSet(LOCK);
        if (first == LOCK) {
            return; // A take() or recycle() is currently in progress.
        }
        final var firstLimit = (first != null) ? first.limit : 0;
        if (firstLimit >= MAX_SIZE) {
            firstRef.set(first); // Pool is full.
            return;
        }

        segment.next = first;
        segment.pos = 0;
        segment.limit = firstLimit + Segment.SIZE;
        segment.status = AVAILABLE;

        firstRef.set(segment);
    }

    private static AtomicReference<Segment> firstRef() {
        // Get a final varue in [0..HASH_BUCKET_COUNT) based on the current thread.
        final var hashBucketIndex = getHashBucketIndex(Thread.currentThread());
        return hashBuckets[hashBucketIndex];
    }

    static int getHashBucketIndex(final @NonNull Thread thread) {
        Objects.requireNonNull(thread);
        return (int) (thread.threadId() & (HASH_BUCKET_COUNT - 1L));
    }
}
