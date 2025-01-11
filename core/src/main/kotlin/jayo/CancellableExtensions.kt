/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-Cancellable") // A leading '-' hides this class from Java.

package jayo

import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Execute `block` and return its optional result in a cancellable context, throwing a [JayoInterruptedIOException] if a
 * cancellation occurred. All operations invoked in this code block, including children threads, will wait at most
 * `timeout` time before aborting, and will also respect the cancel scope actions : manual cancellation, await for
 * [Condition][java.util.concurrent.locks.Condition] signal...
 *
 * The provided timeout, if any, is used to sets a deadline of now plus `timeout` time. This deadline will start when
 * the associated cancellable code `block` will execute. All I/O operations invoked in this cancellable code block, and
 * in children threads, will regularly check if this deadline is reached.
 */
public fun <T> cancelScope(
    timeout: Duration? = null,
    block: CancelScope.() -> T
): T =
    if (timeout != null) {
        Cancellable.call(timeout.toJavaDuration(), block)
    } else {
        Cancellable.call(block)
}
