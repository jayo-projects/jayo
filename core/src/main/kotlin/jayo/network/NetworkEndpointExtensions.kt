/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-NetworkEndpoint") // A leading '-' hides this class from Java.

package jayo.network

import jayo.JayoDslMarker
import java.net.ProtocolFamily
import java.net.SocketOption
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration
import kotlin.time.toJavaDuration

public fun NetworkEndpoint.NioConfig.kotlin(
    config: NioNetworkEndpointConfigDsl.() -> Unit
): NetworkEndpoint.NioConfig {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    config(NioNetworkEndpointConfigDsl(this))
    return this
}

@JayoDslMarker
public class NioNetworkEndpointConfigDsl internal constructor(
    private val config: NetworkEndpoint.NioConfig
) : NetworkEndpointConfigDsl(config) {
    /**
     * Sets the connect timeout used in the [NetworkEndpoint.connect] method. Default is zero. A timeout of zero is
     * interpreted as an infinite timeout.
     */
    public var connectTimeout: Duration
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            config.connectTimeout(value.toJavaDuration())
        }

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

public fun NetworkEndpoint.IoConfig.kotlin(config: IoNetworkEndpointConfigDsl.() -> Unit): NetworkEndpoint.IoConfig {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    config(IoNetworkEndpointConfigDsl(this))
    return this
}

@JayoDslMarker
public class IoNetworkEndpointConfigDsl(config: NetworkEndpoint.IoConfig) : NetworkEndpointConfigDsl(config)

public sealed class NetworkEndpointConfigDsl(private val config: NetworkEndpoint.Config<*>) {
    /**
     * Sets the default read timeout of all read operations of the network endpoints produced by this builder. Default
     * is zero. A timeout of zero is interpreted as an infinite timeout.
     */
    public var readTimeout: Duration
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            config.readTimeout(value.toJavaDuration())
        }

    /**
     * Sets the default write timeout of all write operations of the network endpoints produced by this builder. Default
     * is zero. A timeout of zero is interpreted as an infinite timeout.
     */
    public var writeTimeout: Duration
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            config.writeTimeout(value.toJavaDuration())
        }

    /**
     * Sets the value of a socket option to set on the network endpoints produced by this builder.
     *
     * @param name  The socket option
     * @param value The value of the socket option. A value of `null` may be a valid value for some socket options.
     * @see java.net.StandardSocketOptions
     */
    public fun <T> option(name: SocketOption<T>, value: T?) {
        config.option(name, value)
    }
}
