/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-ClientTlsEndpoint") // A leading '-' hides this class from Java.

package jayo.tls

import jayo.JayoDslMarker
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public fun ClientTlsEndpoint.Builder.kotlin(config: ClientTlsEndpointBuilderDsl.() -> Unit): ClientTlsEndpoint.Builder {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    config(ClientTlsEndpointBuilderDsl(this))
    return this
}

@JayoDslMarker
@JvmInline
public value class ClientTlsEndpointBuilderDsl internal constructor(private val builder: ClientTlsEndpoint.Builder) {
    /**
     * Whether to wait for TLS close confirmation when calling `close()` on this TLS endpoint or its
     * [reader][TlsEndpoint.getReader] or [writer][TlsEndpoint.getWriter]. Default is `false` to not wait and close
     * immediately. The proper closing procedure can then be triggered at any moment using [TlsEndpoint.shutdown].
     *
     * Setting this to `true` will block (potentially until it times out, or indefinitely) the close operation until the
     * counterpart confirms the close on their side (sending a close_notify alert). In this case it emulates the
     * behavior of [SSLSocket][javax.net.ssl.SSLSocket] when used in layered mode (and without autoClose).
     *
     * Even when this behavior is enabled, the close operation will not propagate any exception thrown during the TLS
     * close exchange and just proceed to close the underlying reader or writer.
     *
     * @see TlsEndpoint.shutdown
     */
    public var waitForCloseConfirmation: Boolean
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.waitForCloseConfirmation(value)
        }
}
