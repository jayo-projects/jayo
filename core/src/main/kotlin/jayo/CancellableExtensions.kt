/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-Cancellable") // A leading '-' hides this class from Java.

package jayo

import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Execute `block` and return its optional result in a cancellable context, throwing a
 * {@linkplain JayoInterruptedIOException JayoInterruptedIOException} if a cancellation occurred.
 * All operations invoked in this code block, and in children threads, will respect the cancel scope : timeout,
 * deadline, manual cancellation, await for {@link Condition} signal...
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

    return cancellable.call(block)
}
