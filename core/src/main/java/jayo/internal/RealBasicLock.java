/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.tools.BasicLock;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.LockSupport;

public final class RealBasicLock implements BasicLock {
    @SuppressWarnings("FieldMayBeFinal")
    private volatile @Nullable Thread lockingThread = null;

    // VarHandle mechanics
    private static final VarHandle LOCKING_THREAD;

    static {
        try {
            final var l = MethodHandles.lookup();
            LOCKING_THREAD = l.findVarHandle(RealBasicLock.class, "lockingThread", Thread.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public void lock() {
        final var currentThread = Thread.currentThread();
        final var lockingThread = (Thread) LOCKING_THREAD.getAndSetRelease(this, currentThread);
        if (lockingThread != null && lockingThread != currentThread) {
            LockSupport.park();
        }
    }

    @Override
    public void unlock() {
        final var currentThread = Thread.currentThread();
        final var lockingThread = (Thread) LOCKING_THREAD.compareAndExchangeRelease(this, currentThread, null);
        if (lockingThread != currentThread) {
            LockSupport.unpark(lockingThread);
        }
    }
}
