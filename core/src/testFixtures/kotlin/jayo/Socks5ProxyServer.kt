/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from OkHttp (https://github.com/square/okhttp), original copyright is below
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

package jayo

import jayo.internal.network.*
import jayo.network.JayoSocketException
import jayo.network.NetworkServer
import jayo.network.NetworkSocket
import jayo.network.Proxy
import jayo.tools.JayoUtils
import java.lang.System.Logger.Level.INFO
import java.lang.System.Logger.Level.WARNING
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A limited implementation of SOCKS Protocol Version 5.
 * See [RFC 1928](https://www.ietf.org/rfc/rfc1928.txt).
 */
class Socks5ProxyServer(private val credentials: PasswordAuthentication? = null) {
    private lateinit var networkServer: NetworkServer
    private lateinit var executor: ExecutorService
    private val connectionCount = AtomicInteger()
    private val openSockets: MutableSet<RawSocket> = ConcurrentHashMap.newKeySet()

    fun play() {
        networkServer = NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */))
        executor = JayoUtils.executorService("Socks5ProxyServer", false)

        executor.execute {
            val threadName = "Socks5ProxyServer ${networkServer.localAddress.port}"
            Thread.currentThread().name = threadName
            try {
                while (true) {
                    val socket = networkServer.accept()
                    connectionCount.incrementAndGet()
                    service(socket)
                }
            } catch (e: JayoSocketException) {
                LOGGER.log(INFO, "$threadName done accepting connections: ${e.message}")
            } catch (e: JayoException) {
                LOGGER.log(WARNING, "$threadName failed unexpectedly", e)
            } finally {
                for (socket in openSockets) {
                    socket.closeQuietly()
                }
                Thread.currentThread().name = "Socks5ProxyServer"
            }
        }
    }

    fun proxy(): Proxy {
        val address = InetSocketAddress.createUnresolved("localhost", networkServer.localAddress.port)
        return if (credentials != null) {
            Proxy.socks5(address, credentials.userName, String(credentials.password))
        } else {
            Proxy.socks5(address)
        }
    }

    fun connectionCount(): Int = connectionCount.get()

    fun shutdown() {
        networkServer.close()
        executor.shutdown()
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            throw JayoException("Gave up waiting for executor to shut down")
        }
    }

    private fun service(from: NetworkSocket) {
        val name = "SocksProxy ${from.peerAddress}"
        threadName(name) {
            try {
                val fromReader = from.reader.buffered()
                val fromWriter = from.writer.buffered()
                hello(fromReader, fromWriter)
                acceptCommand(from.localAddress, fromReader, fromWriter)
                openSockets.add(from)
            } catch (je: JayoException) {
                LOGGER.log(WARNING, "$name failed", je)
                from.closeQuietly()
            }
        }
    }

    private fun hello(fromReader: Reader, fromWriter: Writer) {
        // Read the hello.
        val socksVersion = fromReader.readByte()
        if (socksVersion != SOCKS_V5) {
            throw JayoProtocolException("Socks version must be 5, is $socksVersion")
        }
        val methodCount = fromReader.readByte()
        val expectedAuthMethod = if (credentials != null) {
            METHOD_USER_PASSWORD
        } else {
            METHOD_NO_AUTHENTICATION_REQUIRED
        }
        var foundSupportedMethod = false
        repeat(methodCount.toInt()) {
            val method = fromReader.readByte()
            foundSupportedMethod = foundSupportedMethod or (method == expectedAuthMethod)
        }
        if (!foundSupportedMethod) {
            throw JayoProtocolException("Method 'No authentication required' is not supported")
        }

        // Respond to hello.
        if (credentials != null) {
            fromWriter.writeByte(SOCKS_V5)
                .writeByte(METHOD_USER_PASSWORD)
                .emit()
            val reserved = fromReader.readByte()
            if (reserved != 1.toByte()) {
                throw JayoProtocolException("Failed to read client credentials")
            }
            val usernameByteSize = fromReader.readByte().toLong()
            val username = fromReader.readString(usernameByteSize, Charsets.ISO_8859_1)
            val passwordByteSize = fromReader.readByte().toLong()
            val password = if (passwordByteSize == 0L) {
                null
            } else {
                fromReader.readString(passwordByteSize, Charsets.ISO_8859_1)
            }
            val authStatus = if (username == credentials.userName && password == String(credentials.password)) {
                0.toByte()
            } else {
                1.toByte()
            }
            fromWriter.writeByte(SOCKS_V5)
                .writeByte(authStatus)
                .emit()
        } else {
            fromWriter.writeByte(SOCKS_V5)
                .writeByte(METHOD_NO_AUTHENTICATION_REQUIRED)
                .emit()
        }
    }

    private fun acceptCommand(
        fromAddress: InetSocketAddress,
        fromReader: Reader,
        fromWriter: Writer,
    ) {
        // Read the command.
        val version = fromReader.readByte()
        val command = fromReader.readByte()
        val reserved = fromReader.readByte()
        if (version != SOCKS_V5 || command != COMMAND_CONNECT || reserved != 0.toByte()) {
            throw JayoProtocolException("Failed to read a command")
        }

        // Read an address.
        val toAddress = when (val addressType = fromReader.readByte()) {
            ADDRESS_TYPE_IPV4 -> InetAddress.getByAddress(fromReader.readByteArray(4L))
            ADDRESS_TYPE_IPV6 -> InetAddress.getByAddress(fromReader.readByteArray(16L))
            ADDRESS_TYPE_DOMAIN_NAME -> {
                val domainNameLength = fromReader.readByte()
                val domainName = fromReader.readString(domainNameLength.toLong())
                // Resolve HOSTNAME_THAT_ONLY_THE_PROXY_KNOWS to localhost.
                when {
                    domainName.equals(HOSTNAME_THAT_ONLY_THE_PROXY_KNOWS, ignoreCase = true) -> {
                        InetAddress.getByName("localhost")
                    }

                    else -> InetAddress.getByName(domainName)
                }
            }

            else -> throw JayoProtocolException("Unknown address type $addressType")
        }
        val port = fromReader.readShort().toInt() and 0xffff

        // Connect to the caller's specified host.
        val toNetworkSocket = NetworkSocket.connectTcp(InetSocketAddress(toAddress, port))
        val toNetworkSocketAddress = toNetworkSocket.localAddress
        val localAddress = toNetworkSocketAddress.address.address

        // Write the reply.
        fromWriter.writeByte(SOCKS_V5)
            .writeByte(REQUEST_OK)
            .writeByte(0)
            .writeByte(if (localAddress.size == 4) ADDRESS_TYPE_IPV4 else ADDRESS_TYPE_IPV6)
            .write(localAddress)
            .writeShort(toNetworkSocketAddress.port.toShort())
            .emit()

        // Connect readers to writers in both directions.
        val toWriter = toNetworkSocket.writer
        executor.execute { transfer(fromAddress, toAddress, fromReader, toWriter) }
        val toReader = toNetworkSocket.reader
        executor.execute { transfer(fromAddress, toAddress, toReader, fromWriter) }
    }

    /**
     * Read data from `reader` and write it to `writer`. This doesn't use [Writer.writeAllFrom] because that method
     * doesn't flush aggressively, and we need that.
     */
    private fun transfer(
        fromAddress: InetSocketAddress,
        toAddress: InetAddress,
        reader: RawReader,
        writer: RawWriter
    ) {
        val name = "Socks5ProxyServer ${fromAddress.address} to $toAddress"
        threadName(name) {
            val buffer = Buffer()
            try {
                writer.use {
                    reader.use {
                        while (true) {
                            val byteCount = reader.readAtMostTo(buffer, 8192L)
                            if (byteCount == -1L) break
                            writer.writeFrom(buffer, byteCount)
                            writer.flush()
                        }
                    }
                }
            } catch (je: JayoException) {
                LOGGER.log(WARNING, "$name failed", je)
            }
        }
    }

    companion object {
        const val HOSTNAME_THAT_ONLY_THE_PROXY_KNOWS = "onlyProxyCanResolveMe.org"
        private val LOGGER = System.getLogger(Socks5ProxyServer::class.java.name)
    }
}
