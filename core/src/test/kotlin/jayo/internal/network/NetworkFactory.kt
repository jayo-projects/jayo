/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network

import jayo.network.NetworkEndpoint
import jayo.network.NetworkProtocol
import jayo.network.NetworkServer
import jayo.network.kotlin
import jayo.scheduling.TaskRunner
import java.net.StandardSocketOptions
import kotlin.time.Duration.Companion.seconds

enum class NetworkFactory {
    TCP_NIO {
        override fun networkServerConfig() =
            NetworkServer.configForNIO().kotlin {
                protocol = NetworkProtocol.IPv6
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                maxPendingConnections = 2
                option(StandardSocketOptions.SO_REUSEADDR, true)
                serverOption(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override fun networkEndpointConfig() =
            NetworkEndpoint.configForNIO().kotlin {
                protocol = NetworkProtocol.IPv6
                connectTimeout = 10.seconds
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                option(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override val isIo get() = false
    },
    TCP_NIO_ASYNC {
        override fun networkServerConfig() =
            NetworkServer.configForNIO().kotlin {
                bufferAsync = TaskRunner.create("NetworkFactory-")
                protocol = NetworkProtocol.IPv6
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                maxPendingConnections = 2
                option(StandardSocketOptions.SO_REUSEADDR, true)
                serverOption(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override fun networkEndpointConfig() =
            NetworkEndpoint.configForNIO().kotlin {
                bufferAsync = TaskRunner.create("NetworkFactory-")
                protocol = NetworkProtocol.IPv6
                connectTimeout = 10.seconds
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                option(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override val isIo get() = false
    },
    TCP_IO {
        override fun networkServerConfig() =
            NetworkServer.configForIO().kotlin {
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                maxPendingConnections = 2
                option(StandardSocketOptions.SO_REUSEADDR, true)
                serverOption(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override fun networkEndpointConfig() =
            NetworkEndpoint.configForIO().kotlin {
                connectTimeout = 10.seconds
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                option(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override val isIo get() = true
    },
    TCP_IO_ASYNC {
        override fun networkServerConfig() =
            NetworkServer.configForIO().kotlin {
                bufferAsync = TaskRunner.create("NetworkFactory-")
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                maxPendingConnections = 2
                option(StandardSocketOptions.SO_REUSEADDR, true)
                serverOption(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override fun networkEndpointConfig() =
            NetworkEndpoint.configForIO().kotlin {
                bufferAsync = TaskRunner.create("NetworkFactory-")
                connectTimeout = 10.seconds
                readTimeout = 10.seconds
                writeTimeout = 10.seconds
                option(StandardSocketOptions.SO_REUSEADDR, true)
            }

        override val isIo get() = true
    },
    ;

    abstract fun networkServerConfig(): NetworkServer.Config<*>
    abstract fun networkEndpointConfig(): NetworkEndpoint.Config<*>
    abstract val isIo: Boolean
}
