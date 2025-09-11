/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-ClientTlsSocket") // Leading '-' hides this class from Java.

package jayo.tls

import jayo.JayoDslMarker
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public fun ClientTlsSocket.Builder.kotlin(config: ClientTlsSocketBuilderDsl.() -> Unit): ClientTlsSocket.Builder {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    config(ClientTlsSocketBuilderDsl(this))
    return this
}

@JayoDslMarker
@JvmInline
public value class ClientTlsSocketBuilderDsl internal constructor(private val builder: ClientTlsSocket.Builder) {
    /**
     * Whether to wait for TLS close confirmation when calling [TlsSocket.cancel]. Default is `false` to not wait and
     * cancel immediately. The proper closing procedure can then be triggered at any moment using [TlsSocket.shutdown].
     *
     * Setting this to `true` will block (potentially until it times out, or indefinitely) the cancel operation until
     * the peer confirms the close on their side (sending a "close notify" alert). In this case it emulates the behavior
     * of [SSLSocket][javax.net.ssl.SSLSocket] when used in layered mode (and without autoClose).
     *
     * Even when this behavior is enabled, the cancel operation will not propagate any exception thrown during the
     * TLS close exchange and just proceed to cancel the underlying socket.
     *
     * @see TlsSocket.shutdown
     */
    public var waitForCloseConfirmation: Boolean
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.waitForCloseConfirmation(value)
        }
}
