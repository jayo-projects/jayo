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

public fun NetworkServer.NioConfig.kotlin(
    config: NioNetworkServerConfigDsl.() -> Unit
): NetworkServer.NioConfig {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    config(NioNetworkServerConfigDsl(this))
    return this
}

@JayoDslMarker
public class NioNetworkServerConfigDsl internal constructor(
    private val config: NetworkServer.NioConfig
) : NetworkServerConfigDsl(config) {
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
            config.protocolFamily(value)
        }
}

public fun NetworkServer.IoConfig.kotlin(config: IoNetworkServerConfigDsl.() -> Unit): NetworkServer.IoConfig {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    config(IoNetworkServerConfigDsl(this))
    return this
}

@JayoDslMarker
public class IoNetworkServerConfigDsl(config: NetworkServer.IoConfig) : NetworkServerConfigDsl(config)

public sealed class NetworkServerConfigDsl(private val config: NetworkServer.Config<*>) {
    /**
     * Sets the default read timeout of all read operations of the [accepted network endpoints][NetworkServer.accept] by
     * the [NetworkServer] built by this builder. Default is zero. A timeout of zero is interpreted as an infinite
     * timeout.
     */
    public var readTimeout: Duration
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            config.readTimeout(value.toJavaDuration())
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
            config.writeTimeout(value.toJavaDuration())
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
        config.option(name, value)
    }

    /**
     * Sets the value of a socket option to set on the [NetworkServer] built by this builder.
     *
     * @param name  The socket option
     * @param value The value of the socket option. A value of `null` may be a valid value for some socket options.
     * @see java.net.StandardSocketOptions
     */
    public fun <T> serverOption(name: SocketOption<T>, value: T?) {
        config.serverOption(name, value)
    }

    /**
     * Sets the maximum number of pending connections on the [NetworkServer] built by this builder. Default is zero. If
     * the value is zero, an implementation specific default is used.
     */
    public var maxPendingConnections: Int
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(@NonNegative value) {
            config.maxPendingConnections(value)
        }
}
