/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
 *
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.internal

import jayo.Jayo.inMemorySocketPair
import jayo.RawSocket
import jayo.network.NetworkServer
import jayo.network.NetworkSocket
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

enum class RawSocketPairFactory {
    /** Implements a RawSocket using the `java.nio.channels.SocketChannel` API on OS sockets. */
    NETWORK_NIO {
        override fun createSocketPair(): Array<RawSocket> {
            val serverSocket = NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */))

            val socketBFuture = CompletableFuture<RawSocket>()
            thread(name = "createSocketPair") {
                socketBFuture.complete(serverSocket.accept())
            }

            val socketA = NetworkSocket.connectTcp(serverSocket.localAddress)

            return arrayOf(socketA, socketBFuture.get())
        }
    },

    /** Implements a RawSocket using the `java.net.Socket` API on OS sockets. */
    NETWORK_IO {
        override fun createSocketPair(): Array<RawSocket> {
            val serverSocket = NetworkServer.builder()
                .useNio(false)
                .bindTcp(InetSocketAddress(0 /* find free port */))

            val socketBFuture = CompletableFuture<RawSocket>()
            thread(name = "createSocketPair") {
                socketBFuture.complete(serverSocket.accept())
            }

            val socketA = NetworkSocket.builder()
                .useNio(false)
                .openTcp()
                .connect(serverSocket.localAddress)

            return arrayOf(socketA, socketBFuture.get())
        }
    },

    IN_MEMORY {
        override fun createSocketPair() = inMemorySocketPair(1024)
    };

    /** @return two mutually connected sockets. */
    abstract fun createSocketPair(): Array<RawSocket>
}