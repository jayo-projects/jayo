/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network

import jayo.network.NetworkEndpoint
import jayo.network.NetworkProtocol
import jayo.network.NetworkServer
import jayo.network.kotlin
import java.net.StandardSocketOptions
import kotlin.time.Duration.Companion.seconds

enum class NetworkFactory {
    TCP_NIO {
        override fun networkServerBuilder() =
            NetworkServer.builder().kotlin {
                protocol = NetworkProtocol.IPv6
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                maxPendingConnections = 2
                option(StandardSocketOptions.SO_REUSEADDR, true)
                serverOption(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override fun networkEndpointBuilder() =
            NetworkEndpoint.builder().kotlin {
                protocol = NetworkProtocol.IPv6
                connectTimeout = 10.seconds
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                option(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override val isIo get() = false
    },
    TCP_IO {
        override fun networkServerBuilder() =
            NetworkServer.builder().kotlin {
                useNio = false
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                maxPendingConnections = 2
                option(StandardSocketOptions.SO_REUSEADDR, true)
                serverOption(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override fun networkEndpointBuilder() =
            NetworkEndpoint.builder().kotlin {
                useNio = false
                connectTimeout = 10.seconds
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                option(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override val isIo get() = true
    },
    ;

    abstract fun networkServerBuilder(): NetworkServer.Builder
    abstract fun networkEndpointBuilder(): NetworkEndpoint.Builder
    abstract val isIo: Boolean
}
