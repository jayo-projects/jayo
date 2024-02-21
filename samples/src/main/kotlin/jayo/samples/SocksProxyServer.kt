/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
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

package jayo.samples

import jayo.*
import java.io.IOException
import java.net.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private const val VERSION_5: Byte = 5
private const val METHOD_NO_AUTHENTICATION_REQUIRED: Byte = 0
private const val ADDRESS_TYPE_IPV4: Byte = 1
private const val ADDRESS_TYPE_DOMAIN_NAME: Byte = 3
private const val COMMAND_CONNECT: Byte = 1
private const val REPLY_SUCCEEDED: Byte = 0

/**
 * A partial implementation of SOCKS Protocol Version 5.
 * See [RFC 1928](https://www.ietf.org/rfc/rfc1928.txt).
 */
class KotlinSocksProxyServer {
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private lateinit var serverSocket: ServerSocket
    private val openSockets: MutableSet<Socket> = Collections.newSetFromMap(ConcurrentHashMap())

    @Throws(IOException::class)
    fun start() {
        serverSocket = ServerSocket(0)
        executor.execute { acceptSockets() }
    }

    @Throws(IOException::class)
    fun shutdown() {
        serverSocket.close()
        executor.shutdown()
    }

    fun proxy(): Proxy = Proxy(
        Proxy.Type.SOCKS,
        InetSocketAddress.createUnresolved("localhost", serverSocket.localPort),
    )

    private fun acceptSockets() {
        try {
            while (true) {
                val from = serverSocket.accept()
                openSockets.add(from)
                executor.execute { handleSocket(from) }
            }
        } catch (e: IOException) {
            println("shutting down: $e")
        } finally {
            for (socket in openSockets) {
                socket.close()
            }
        }
    }

    private fun handleSocket(fromSocket: Socket) {
        val fromSource = fromSocket.source().buffered()
        val fromSink = fromSocket.sink().buffered()
        try {
            // Read the hello.
            val socksVersion = fromSource.readByte()
            if (socksVersion != VERSION_5) {
                throw ProtocolException()
            }
            val methodCount = fromSource.readByte()
            var foundSupportedMethod = false
            for (i in 0 until methodCount) {
                val method = fromSource.readByte()
                foundSupportedMethod = foundSupportedMethod or (method == METHOD_NO_AUTHENTICATION_REQUIRED)
            }
            if (!foundSupportedMethod) throw ProtocolException()

            // Respond to hello.
            fromSink.writeByte(VERSION_5)
                .writeByte(METHOD_NO_AUTHENTICATION_REQUIRED)
                .emit()

            // Read a command.
            val version = fromSource.readByte()
            val command = fromSource.readByte()
            val reserved = fromSource.readByte()
            if (version != VERSION_5 || command != COMMAND_CONNECT || reserved != 0.toByte()) {
                throw ProtocolException()
            }

            // Read an address.
            val addressType = fromSource.readByte()
            val inetAddress = when (addressType) {
                ADDRESS_TYPE_IPV4 -> InetAddress.getByAddress(fromSource.readByteArray(4L))
                ADDRESS_TYPE_DOMAIN_NAME -> {
                    val domainNameLength = fromSource.readByte()
                    InetAddress.getByName(fromSource.readUtf8(domainNameLength.toLong()))
                }

                else -> throw ProtocolException()
            }
            val port = fromSource.readShort().toInt() and 0xffff

            // Connect to the caller's specified host.
            val toSocket = Socket(inetAddress, port)
            openSockets.add(toSocket)
            val localAddress = toSocket.localAddress.address
            if (localAddress.size != 4) throw ProtocolException()

            // Write the reply.
            fromSink.writeByte(VERSION_5)
                .writeByte(REPLY_SUCCEEDED)
                .writeByte(0)
                .writeByte(ADDRESS_TYPE_IPV4)
                .write(localAddress)
                .writeShort(toSocket.localPort.toShort())
                .emit()

            // Connect sources to sinks in both directions.
            val toSink = toSocket.sink()
            executor.execute { transfer(fromSocket, fromSource, toSink) }
            val toSource = toSocket.source()
            executor.execute { transfer(toSocket, toSource, fromSink) }
        } catch (e: IOException) {
            fromSocket.close()
            openSockets.remove(fromSocket)
            println("connect failed for $fromSocket: $e")
        }
    }

    /**
     * Read data from `source` and write it to `sink`. This doesn't use [Sink.transferFrom] because that method doesn't
     * flush aggressively, and we need that.
     */
    private fun transfer(sourceSocket: Socket, source: RawSource, sink: RawSink) {
        try {
            val buffer = Buffer()
            var byteCount: Long
            while (source.readAtMostTo(buffer, 8192L).also { byteCount = it } != -1L) {
                sink.write(buffer, byteCount)
                sink.flush()
            }
        } finally {
            sink.close()
            source.close()
            sourceSocket.close()
            openSockets.remove(sourceSocket)
        }
    }
}

fun main() {
    val proxyServer = KotlinSocksProxyServer()
    proxyServer.start()

    val url =
        URI("https://raw.githubusercontent.com/jayo-projects/jayo/main/samples/src/main/resources/jayo.txt").toURL()
    val connection = url.openConnection(proxyServer.proxy())
    connection.getInputStream().source().buffered().use { source ->
        generateSequence { source.readUtf8Line() }
            .forEach(::println)
    }

    proxyServer.shutdown()
}
