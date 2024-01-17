/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-Cancellable") // A leading '-' hides this class from Java.

package jayo

import kotlin.time.DurationUnit
import kotlin.time.toTimeUnit

/**
 * Wait at most `timeout` time before aborting an operation. Using a per-operation timeout means that as long as forward
 * progress is being made, no sequence of operations will fail.
 *
 * If `timeout == null && unit == null`, operations will run indefinitely. (Operating system timeouts may still apply)
 */
public fun <T> cancelScope(timeout: Long? = null,
                           timeoutUnit: DurationUnit? = null,
                           deadline: Long? = null,
                           deadlineUnit: DurationUnit? = null,
                           block: CancelScope.() -> T): T {
    if ((timeout != null && timeoutUnit == null) || (timeout == null && timeoutUnit != null)) {
        throw IllegalArgumentException("timeout and timeoutUnit must be both present or both null")
    }
    if ((deadline != null && deadlineUnit == null) || (deadline == null && deadlineUnit != null)) {
        throw IllegalArgumentException("deadline and deadlineUnit must be both present or both null")
    }
    
    val cancellableBuilder = Cancellable.Builder()
    if (timeout != null && timeoutUnit != null) {
        cancellableBuilder.timeout(timeout, timeoutUnit.toTimeUnit())
    }
    if (deadline != null && deadlineUnit != null) {
        cancellableBuilder.deadline(deadline, deadlineUnit.toTimeUnit())
    }
    return cancellableBuilder.build().executeCancellable(block)
}

/** Sets a timeout of now plus `timeout` time. */
public fun Cancellable.Builder.timeout(timeout: Long, unit: DurationUnit) {
    timeout(timeout, unit.toTimeUnit())
}

/** Sets a deadline of now plus `duration` time. */
public fun Cancellable.Builder.deadline(duration: Long, unit: DurationUnit) {
    deadline(duration, unit.toTimeUnit())
}
