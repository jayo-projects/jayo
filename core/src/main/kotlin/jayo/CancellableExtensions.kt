/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-Cancellable") // A leading '-' hides this class from Java.

package jayo

import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Wait at most `timeout` time before aborting an operation. Using a per-operation timeout means that as long as forward
 * progress is being made, no sequence of operations will fail.
 *
 * If `timeout == null && unit == null`, operations will run indefinitely. (Operating system timeouts may still apply)
 */
public fun <T> cancelScope(
    timeout: Duration? = null,
    deadline: Duration? = null,
    block: CancelScope.() -> T
): T {
    val cancellable = Cancellable.builder().apply {
        if (timeout != null) {
            timeout(timeout.toJavaDuration())
        }
        if (deadline != null) {
            deadline(deadline.toJavaDuration())
        }
    }.build()

    return cancellable.executeCancellable(block)
}
