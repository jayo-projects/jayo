/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network

import jayo.network.*
import java.net.StandardSocketOptions
import kotlin.time.Duration.Companion.seconds

enum class NetworkFactory {
    TCP_NIO {
        override fun networkServerBuilder() =
            networkServerBuilder {
                protocol = NetworkProtocol.IPv6
                maxPendingConnections = 2
                option(StandardSocketOptions.SO_REUSEADDR, true)
                serverOption(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override fun networkSocketBuilder() =
            networkSocketBuilder {
                protocol = NetworkProtocol.IPv6
                connectTimeout = 30.seconds
                option(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override val isIo get() = false
    },
    TCP_IO {
        override fun networkServerBuilder() =
            networkServerBuilder {
                useNio = false
                maxPendingConnections = 2
                option(StandardSocketOptions.SO_REUSEADDR, true)
                serverOption(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override fun networkSocketBuilder() =
            networkSocketBuilder {
                useNio = false
                connectTimeout = 30.seconds
                option(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override val isIo get() = true
    },
    ;

    abstract fun networkServerBuilder(): NetworkServer.Builder
    abstract fun networkSocketBuilder(): NetworkSocket.Builder
    abstract val isIo: Boolean
}
