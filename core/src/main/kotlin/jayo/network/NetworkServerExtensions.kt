/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-NetworkServer") // Leading '-' hides this class from Java.

package jayo.network

import jayo.JayoDslMarker
import jayo.scheduling.TaskRunner
import java.net.SocketOption
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration
import kotlin.time.toJavaDuration

public fun NetworkServer.Builder.kotlin(
    config: NetworkServerBuilderDsl.() -> Unit
): NetworkServer.Builder {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    config(NetworkServerBuilderDsl(this))
    return this
}

@JayoDslMarker
@JvmInline
public value class NetworkServerBuilderDsl(private val builder: NetworkServer.Builder) {
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
     * Read and write operations on the [accepted network endpoints][NetworkServer.accept] by the [NetworkServer] built
     * by this configuration are seamlessly processed **asynchronously** in distinct runnable tasks using the provided
     * [TaskRunner].
     */
    public var bufferAsync: TaskRunner
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.bufferAsync(value)
        }

    /**
     * Sets the value of a socket option to set on the [accepted network endpoints][NetworkServer.accept] by the
     * [NetworkServer] built by this configuration.
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
        set(value) {
            builder.maxPendingConnections(value)
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
     * If true the underlying server sockets will be Java NIO ones, if false they will be Java IO ones. Default is
     * `true`.
     */
    public var useNio: Boolean
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.useNio(value)
        }
}
