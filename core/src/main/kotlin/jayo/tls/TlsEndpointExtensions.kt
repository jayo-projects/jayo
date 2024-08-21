/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-TlsEndpoint") // A leading '-' hides this class from Java.

package jayo.tls

import jayo.JayoDslMarker
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public fun TlsEndpoint.ClientBuilder.build(config: TlsClientEndpointBuilderDsl.() -> Unit): TlsEndpoint {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    config(TlsClientEndpointBuilderDsl(this))
    return build()
}

@JayoDslMarker
@JvmInline
public value class TlsClientEndpointBuilderDsl internal constructor(private val builder: TlsEndpoint.ClientBuilder) {
    /**
     * Register a callback function to be executed when the TLS session is established (or re-established). The supplied
     * function will run in the same thread as the rest of the handshake, so it should ideally run as fast as possible.
     */
    public var sessionInitCallback: (SSLSession) -> Unit
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.sessionInitCallback(value)
        }

    /**
     * Whether to wait for TLS close confirmation when calling `close()` on the [TlsEndpoint.getReader] or the
     * [TlsEndpoint.getWriter]. Default is `false` to not wait and close immediately. The proper closing procedure can
     * then be triggered at any moment using [TlsEndpoint.shutdown].
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

public fun TlsEndpoint.ServerBuilder.build(config: TlsServerEndpointBuilderDsl.() -> Unit): TlsEndpoint {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    config(TlsServerEndpointBuilderDsl(this))
    return build()
}

@JayoDslMarker
@JvmInline
public value class TlsServerEndpointBuilderDsl internal constructor(private val builder: TlsEndpoint.ServerBuilder) {
    /**
     * Register a callback function to be executed when the TLS session is established (or re-established). The supplied
     * function will run in the same thread as the rest of the handshake, so it should ideally run as fast as possible.
     */
    public var sessionInitCallback: (SSLSession) -> Unit
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.sessionInitCallback(value)
        }

    /**
     * Whether to wait for TLS close confirmation when calling `close()` on the [TlsEndpoint.getReader] or the
     * [TlsEndpoint.getWriter]. Default is `false` to not wait and close immediately. The proper closing procedure can
     * then be triggered at any moment using [TlsEndpoint.shutdown].
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

    /**
     * The custom function that builds a [SSLEngine] from the [SSLContext] when it will be available during handshake.
     */
    public var engineFactory: (SSLContext) -> SSLEngine
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.engineFactory(value)
        }
}