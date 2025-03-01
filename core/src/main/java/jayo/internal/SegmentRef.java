/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.locks.Lock;

sealed abstract class SegmentRef {
    abstract @Nullable Segment value();

    static final class Immediate extends SegmentRef {
        private final @Nullable Segment segment;

        Immediate(final @Nullable Segment segment) {
            this.segment = segment;
        }

        @Override
        @Nullable Segment value() {
            return segment;
        }
    }

    static final class Deferred extends SegmentRef {
        private final @NonNull Lock lock;
        private volatile boolean isSet = false;
        private @Nullable Segment segment;

        Deferred(final @NonNull Lock lock) {
            this.lock = lock;
            lock.lock();
        }

        @Override
        @NonNull Segment value() {
            if (isSet) {
                return segment;
            }
            lock.lock();
            try {
                return segment;
            } finally {
                lock.unlock();
            }
        }

        void segment(final @Nullable Segment segment) {
            this.segment = segment;
            isSet = true;
            lock.unlock();
        }
    }
}
