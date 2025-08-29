/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-NetworkEndpoint") // Leading '-' hides this class from Java.

package jayo.network

import jayo.JayoDslMarker
import java.net.SocketOption
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration
import kotlin.time.toJavaDuration

public fun NetworkEndpoint.Builder.kotlin(
    config: NetworkEndpointBuilderDsl.() -> Unit
): NetworkEndpoint.Builder {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    config(NetworkEndpointBuilderDsl(this))
    return this
}

@JayoDslMarker
@JvmInline
public value class NetworkEndpointBuilderDsl(private val builder: NetworkEndpoint.Builder) {
    /**
     * Sets the timeout for establishing the connection to the peer, including the proxy initialization if one is used.
     * Default is zero. A timeout of zero is interpreted as an infinite timeout.
     */
    public var connectTimeout: Duration
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.connectTimeout(value.toJavaDuration())
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

    /**
     * Sets the [network protocol][NetworkProtocol] to use when opening the underlying NIO sockets. The default protocol
     * is platform (and possibly configuration) dependent and therefore unspecified.
     *
     * **This option is only available for Java NIO**, so Java NIO mode is forced when this parameter is set !
     *
     * See [java.net.preferIPv4Stack](https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html#Ipv4IPv6)
     * system property
     */
    public var protocol: NetworkProtocol
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.protocol(value)
        }

    /**
     * If true the socket will be a Java NIO one, if false it will be a Java IO one. Default is `true`.
     */
    public var useNio: Boolean
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.useNio(value)
        }
}

public var NetworkEndpoint.readTimeout: Duration
    @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
    get() = error("unsupported")
    set(value) = this.setReadTimeout(value.toJavaDuration())

public var NetworkEndpoint.writeTimeout: Duration
    @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
    get() = error("unsupported")
    set(value) = this.setWriteTimeout(value.toJavaDuration())
