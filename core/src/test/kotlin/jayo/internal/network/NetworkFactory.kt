/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network

import jayo.network.NetworkEndpoint
import jayo.network.NetworkServer
import jayo.network.kotlin
import java.net.StandardProtocolFamily
import java.net.StandardSocketOptions
import kotlin.time.Duration.Companion.seconds

enum class NetworkFactory {
    TCP_IO {
        override fun networkServerBuilder() =
            NetworkServer.builderForIO().kotlin {
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                maxPendingConnections = 2
                option(StandardSocketOptions.SO_REUSEADDR, true)
                serverOption(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override fun networkEndpointBuilder() =
            NetworkEndpoint.builderForIO().kotlin {
                connectTimeout = 10.seconds
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                option(StandardSocketOptions.SO_REUSEADDR, true)
            }
    },
    TCP_NIO {
        override fun networkServerBuilder() =
            NetworkServer.builderForNIO().kotlin {
                protocolFamily = StandardProtocolFamily.INET6
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                maxPendingConnections = 2
                option(StandardSocketOptions.SO_REUSEADDR, true)
                serverOption(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override fun networkEndpointBuilder() =
            NetworkEndpoint.builderForNIO().kotlin {
                protocolFamily = StandardProtocolFamily.INET6
                connectTimeout = 10.seconds
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                option(StandardSocketOptions.SO_REUSEADDR, true)
            }
    },
    ;

    abstract fun networkServerBuilder(): NetworkServer.Builder<*>
    abstract fun networkEndpointBuilder(): NetworkEndpoint.Builder<*>
}
