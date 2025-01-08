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

public fun NetworkEndpoint.NioBuilder.kotlin(
    config: NioNetworkEndpointBuilderDsl.() -> Unit
): NetworkEndpoint.NioBuilder {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    config(NioNetworkEndpointBuilderDsl(this))
    return this
}

@JayoDslMarker
public class NioNetworkEndpointBuilderDsl internal constructor(
    private val builder: NetworkEndpoint.NioBuilder
) : NetworkEndpointBuilderDsl(builder) {
    /**
     * Sets the connect timeout used in the [Builder.connect][NetworkEndpoint.Builder.connect] method. Default is
     * zero. A timeout of zero is interpreted as an infinite timeout.
     */
    public var connectTimeout: Duration
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.connectTimeout(value.toJavaDuration())
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
            builder.protocolFamily(value)
        }
}

public fun NetworkEndpoint.IoBuilder.kotlin(config: IoNetworkEndpointBuilderDsl.() -> Unit): NetworkEndpoint.IoBuilder {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    config(IoNetworkEndpointBuilderDsl(this))
    return this
}

@JayoDslMarker
public class IoNetworkEndpointBuilderDsl(builder: NetworkEndpoint.IoBuilder) : NetworkEndpointBuilderDsl(builder)

public sealed class NetworkEndpointBuilderDsl(private val builder: NetworkEndpoint.Builder<*>) {
    /**
     * Sets the default read timeout of all read operations of the network endpoints produced by this builder. Default
     * is zero. A timeout of zero is interpreted as an infinite timeout.
     */
    public var readTimeout: Duration
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.readTimeout(value.toJavaDuration())
        }

    /**
     * Sets the default write timeout of all write operations of the network endpoints produced by this builder. Default
     * is zero. A timeout of zero is interpreted as an infinite timeout.
     */
    public var writeTimeout: Duration
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.writeTimeout(value.toJavaDuration())
        }

    /**
     * Sets the value of a socket option to set on the network endpoints produced by this builder.
     *
     * @param name  The socket option
     * @param value The value of the socket option. A value of `null` may be a valid value for some socket options.
     * @see java.net.StandardSocketOptions
     */
    public fun <T> option(name: SocketOption<T>, value: T?) {
        builder.option(name, value)
    }
}
