/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.tools;

import jayo.internal.RealBasicLock;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * A basic lock that only supports {@link #lock()} and {@link #unlock()}. It is reentrant and only supports 2 concurrent
 * threads.
 */
public sealed interface BasicLock extends Lock permits RealBasicLock {
    static BasicLock create() {
        return new RealBasicLock();
    }

    @Override
    default void lockInterruptibly() {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean tryLock() {
        throw new UnsupportedOperationException();
    }

    @Override
    default boolean tryLock(final long time, final @NonNull TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    default @NonNull Condition newCondition() {
        throw new UnsupportedOperationException();
    }
}
