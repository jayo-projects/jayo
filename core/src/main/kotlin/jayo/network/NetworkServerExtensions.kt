/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-NetworkServer") // Leading '-' hides this class from Java.

package jayo.network

import jayo.JayoDslMarker
import java.net.SocketOption
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public fun networkServerBuilder(config: NetworkServerBuilderDsl.() -> Unit): NetworkServer.Builder {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    val builder = NetworkServer.builder()
    config(NetworkServerBuilderDsl(builder))
    return builder
}

@JayoDslMarker
@JvmInline
public value class NetworkServerBuilderDsl(private val builder: NetworkServer.Builder) {
    /**
     * Sets the value of a socket option to set on the [accepted network sockets][NetworkServer.accept].
     *
     * @param name  The socket option
     * @param value The value of the socket option. A value of `null` may be a valid value for some socket options.
     * @see java.net.StandardSocketOptions
     */
    public fun <T> option(name: SocketOption<T>, value: T?) {
        builder.option(name, value)
    }

    /**
     * Sets the value of a socket option to set on the [NetworkServer] that will be built using this builder.
     *
     * @param name  The socket option
     * @param value The value of the socket option. A value of `null` may be a valid value for some socket options.
     * @see java.net.StandardSocketOptions
     */
    public fun <T> serverOption(name: SocketOption<T>, value: T?) {
        builder.serverOption(name, value)
    }

    /**
     * Sets the maximum number of pending connections on the [NetworkServer] that will be built using this builder.
     * Default is zero. If the value is zero, an implementation-specific default is used.
     */
    public var maxPendingConnections: Int
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.maxPendingConnections(value)
        }

    /**
     * Sets the [network protocol][NetworkProtocol] to use when opening the underlying NIO server sockets: `IPv4` or
     * `IPv6`. The default protocol is platform (and possibly configuration) dependent and therefore unspecified.
     *
     * **This option is only available for Java NIO**, so Java NIO mode is forced when this parameter is set!
     *
     * See [java.net.preferIPv4Stack](https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html#Ipv4IPv6)
     * system property
     */
    public var protocol: NetworkProtocol
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.networkProtocol(value)
        }

    /**
     * If true, the underlying server socket will be a Java NIO one, if false, it will be a Java IO one. Default is
     * `true`.
     */
    public var useNio: Boolean
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.useNio(value)
        }
}
