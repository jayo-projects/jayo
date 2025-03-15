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

package jayo.internal.network

import jayo.*
import jayo.internal.network.SocksNetworkEndpoint.*
import jayo.network.NetworkEndpoint
import jayo.network.NetworkServer
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.util.concurrent.Executor

/**
 * A partial implementation of SOCKS Protocol Version 5, based on an Okio sample.
 * See [RFC 1928](https://www.ietf.org/rfc/rfc1928.txt).
 */
class Socks5ProxyServer(
    builder: NetworkServer.Builder,
    private val executor: Executor,
    private val credentials: PasswordAuthentication?
) : Closeable {
    private val networkServer: NetworkServer = builder.bindTcp(InetSocketAddress(0 /* find free port */))

    init {
        executor.execute { acceptClient() }
    }

    internal val address: InetSocketAddress =
        InetSocketAddress.createUnresolved("localhost", networkServer.localAddress.port)

    override fun close() {
        networkServer.close()
    }

    internal fun acceptClient() {
        val client = networkServer.accept()
        val fromReader = client.reader
        val fromWriter = client.writer

        try {
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

            // Read a command.
            val version = fromReader.readByte()
            val command = fromReader.readByte()
            val reserved = fromReader.readByte()
            if (version != SOCKS_V5 || command != COMMAND_CONNECT || reserved != 0.toByte()) {
                throw JayoProtocolException("Failed to read a command")
            }

            // Read an address.
            val inetAddress = when (val addressType = fromReader.readByte()) {
                ADDRESS_TYPE_IPV4 -> InetAddress.getByAddress(fromReader.readByteArray(4L))
                ADDRESS_TYPE_IPV6 -> InetAddress.getByAddress(fromReader.readByteArray(16L))
                ADDRESS_TYPE_DOMAIN_NAME -> {
                    val domainNameLength = fromReader.readByte()
                    InetAddress.getByName(fromReader.readString(domainNameLength.toLong()))
                }

                else -> throw JayoProtocolException("Unknown address type $addressType")
            }
            val port = fromReader.readShort().toInt() and 0xffff

            // Connect to the caller's specified host.
            val toNetworkEndpoint = NetworkEndpoint.connectTcp(InetSocketAddress(inetAddress, port))
            val toNetworkEndpointAddress = toNetworkEndpoint.localAddress
            val localAddress = toNetworkEndpointAddress.address.address

            // Write the reply.
            fromWriter.writeByte(SOCKS_V5)
                .writeByte(REQUEST_OK)
                .writeByte(0)
                .writeByte(if (localAddress.size == 4) ADDRESS_TYPE_IPV4 else ADDRESS_TYPE_IPV6)
                .write(localAddress)
                .writeShort(toNetworkEndpointAddress.port.toShort())
                .emit()

            // Connect readers to writers in both directions.
            val toWriter = toNetworkEndpoint.writer
            executor.execute { transfer(client, fromReader, toWriter) }
            val toReader = toNetworkEndpoint.reader
            executor.execute { transfer(toNetworkEndpoint, toReader, fromWriter) }
        } catch (e: JayoException) {
            client.close()
            println("connect failed for $client: $e")
        }
    }

    /**
     * Read data from `reader` and write it to `writer`. This doesn't use [Writer.transferFrom] because that method
     * doesn't flush aggressively, and we need that.
     */
    private fun transfer(readerNetworkEndpoint: NetworkEndpoint, reader: RawReader, writer: RawWriter) {
        try {
            val buffer = Buffer()
            var byteCount: Long
            while (reader.readAtMostTo(buffer, 8192L).also { byteCount = it } != -1L) {
                writer.write(buffer, byteCount)
                writer.flush()
            }
        } catch (_: JayoClosedResourceException) {
        } finally {
            writer.close()
            reader.close()
            readerNetworkEndpoint.close()
        }
    }
}
