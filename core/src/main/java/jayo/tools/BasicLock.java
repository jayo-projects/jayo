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
 * A basic lock that only supports {@link #lock()} and {@link #unlock()}. It is not reentrant and only support 2
 * concurrent threads.
 */
public interface BasicLock extends Lock {
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
    default boolean tryLock(long time, @NonNull TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    default Condition newCondition() {
        throw new UnsupportedOperationException();
    }
}
