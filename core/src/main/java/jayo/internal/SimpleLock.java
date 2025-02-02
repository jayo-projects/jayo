/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.jspecify.annotations.NonNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

final class SimpleLock implements Lock {
    @Override
    public void lock() {

    }

    @Override
    public void unlock() {

    }

    @Override
    public void lockInterruptibly() {

    }

    @Override
    public boolean tryLock() {
        return false;
    }

    @Override
    public boolean tryLock(long time, @NonNull TimeUnit unit) {
        return false;
    }

    @NonNull
    @Override
    public Condition newCondition() {
        return null;
    }
}
