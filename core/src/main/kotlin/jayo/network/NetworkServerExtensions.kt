/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-NetworkServer") // A leading '-' hides this class from Java.

package jayo.network

import jayo.JayoDslMarker
import jayo.external.NonNegative
import java.net.ProtocolFamily
import java.net.SocketOption
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration
import kotlin.time.toJavaDuration

public fun NetworkServer.NioBuilder.kotlin(
    config: NioNetworkServerBuilderDsl.() -> Unit
): NetworkServer.NioBuilder {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    config(NioNetworkServerBuilderDsl(this))
    return this
}

@JayoDslMarker
public class NioNetworkServerBuilderDsl internal constructor(
    private val builder: NetworkServer.NioBuilder
) : NetworkServerBuilderDsl(builder) {
    /**
     * Sets the [protocol family][ProtocolFamily] to use when opening the underlying NIO sockets. The default protocol
     * family is platform (and possibly configuration) dependent and therefore unspecified.
     *
     * See [java.net.preferIPv4Stack](https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html#Ipv4IPv6)
     * system property
     */
    public var protocolFamily: ProtocolFamily
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.protocolFamily(value)
        }
}

public fun NetworkServer.IoBuilder.kotlin(config: IoNetworkServerBuilderDsl.() -> Unit): NetworkServer.IoBuilder {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    config(IoNetworkServerBuilderDsl(this))
    return this
}

@JayoDslMarker
public class IoNetworkServerBuilderDsl(builder: NetworkServer.IoBuilder) : NetworkServerBuilderDsl(builder)

public sealed class NetworkServerBuilderDsl(private val builder: NetworkServer.Builder<*>) {
    /**
     * Sets the default read timeout of all read operations of the [accepted network endpoints][NetworkServer.accept] by
     * the [NetworkServer] built by this builder. Default is zero. A timeout of zero is interpreted as an infinite
     * timeout.
     */
    public var readTimeout: Duration
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.readTimeout(value.toJavaDuration())
        }

    /**
     * Sets the default write timeout of all write operations of the [accepted network endpoints][NetworkServer.accept]
     * by the [NetworkServer] built by this builder. Default is zero. A timeout of zero is interpreted as an infinite
     * timeout.
     */
    public var writeTimeout: Duration
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.writeTimeout(value.toJavaDuration())
        }

    /**
     * Sets the value of a socket option to set on the [accepted network endpoints][NetworkServer.accept] by the
     * [NetworkServer] built by this builder.
     *
     * @param name  The socket option
     * @param value The value of the socket option. A value of `null` may be a valid value for some socket options.
     * @see java.net.StandardSocketOptions
     */
    public fun <T> option(name: SocketOption<T>, value: T?) {
        builder.option(name, value)
    }

    /**
     * Sets the value of a socket option to set on the [NetworkServer] built by this builder.
     *
     * @param name  The socket option
     * @param value The value of the socket option. A value of `null` may be a valid value for some socket options.
     * @see java.net.StandardSocketOptions
     */
    public fun <T> serverOption(name: SocketOption<T>, value: T?) {
        builder.serverOption(name, value)
    }

    /**
     * Sets the maximum number of pending connections on the [NetworkServer] built by this builder. Default is zero. If
     * the value is zero, an implementation specific default is used.
     */
    public var maxPendingConnections: Int
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(@NonNegative value) {
            builder.maxPendingConnections(value)
        }
}
