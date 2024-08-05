/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from kotlinx-io (https://github.com/Kotlin/kotlinx-io), original copyright is below
 *
 * Copyright 2017-2023 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENCE file.
 *
 */

package jayo.internal;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Reference counting SegmentCopyTracker tracking the number of shared segment copies.
 * Every {@link #addCopy} call increments the counter, every {@link #removeCopy} decrements it.
 * <p>
 * After calling {@link #removeCopy} the same number of time {@link #addCopy} was called, this tracker returns to the
 * unshared state.
 */
final class SegmentCopyTracker {
    @SuppressWarnings("FieldMayBeFinal")
    private volatile int copyCount = 0;

    // AtomicIntegerFieldUpdater mechanics
    private static final AtomicIntegerFieldUpdater<SegmentCopyTracker> COPY_COUNT =
            AtomicIntegerFieldUpdater.newUpdater(SegmentCopyTracker.class, "copyCount");

    boolean isShared() {
        return copyCount > 0;
    }

    /**
     * Track a new copy created by sharing an associated segment.
     */
    void addCopy() {
        COPY_COUNT.incrementAndGet(this);
    }

    /**
     * Records reclamation of a shared segment copy associated with this tracker.
     * If a tracker was in unshared state, this call should not affect an internal state.
     *
     * @return {@code true} if the segment was not shared <i>before</i> this call.
     */
    boolean removeCopy() {
        // The value could not be incremented from `0` under the race, so once it zero, it remains zero in the scope of
        // this call.
        if (copyCount == 0) {
            return false;
        }

        final var updatedValue = COPY_COUNT.decrementAndGet(this);
        // If there are several copies, the last decrement will update copyCount from 0 to -1.
        // That would be the last standing copy, and we can recycle it.
        // If, however, the decremented value falls below -1, it's an error as there were more `removeCopy` than
        // `addCopy` calls.
        if (updatedValue >= 0) {
            return true;
        }
        if (updatedValue < -1) {
            throw new IllegalStateException("Shared copies count is negative: " + updatedValue + 1);
        }
        copyCount = 0;
        return false;
    }
}
