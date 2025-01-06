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
import jayo.network.NetworkEndpoint
import jayo.network.NetworkServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
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
    private val executor = Executors.newCachedThreadPool()
    private lateinit var networkServer: NetworkServer
    private val openNetworkEndpoints: MutableSet<NetworkEndpoint> = Collections.newSetFromMap(ConcurrentHashMap())

    fun start() {
        networkServer = NetworkServer.bindTcp(InetSocketAddress(0 /* find free port */))
        executor.execute { acceptClients() }
    }

    fun shutdown() {
        networkServer.close()
        executor.shutdown()
    }

    fun proxy(): Proxy = Proxy(
        Proxy.Type.SOCKS,
        InetSocketAddress.createUnresolved("localhost", (networkServer.localAddress as InetSocketAddress).port),
    )

    private fun acceptClients() {
        try {
            while (true) {
                val from = networkServer.accept()
                openNetworkEndpoints.add(from)
                executor.execute { handleClient(from) }
            }
        } catch (e: JayoException) {
            println("shutting down: $e")
        } finally {
            for (networkEndpoint in openNetworkEndpoints) {
                networkEndpoint.close()
            }
        }
    }

    private fun handleClient(client: NetworkEndpoint) {
        val fromReader = client.reader.buffered()
        val fromWriter = client.writer.buffered()
        try {
            // Read the hello.
            val socksVersion = fromReader.readByte()
            if (socksVersion != VERSION_5) {
                throw JayoProtocolException("Socks version must be 5, is $socksVersion")
            }
            val methodCount = fromReader.readByte()
            var foundSupportedMethod = false
            repeat(methodCount.toInt()) {
                val method = fromReader.readByte()
                foundSupportedMethod = foundSupportedMethod or (method == METHOD_NO_AUTHENTICATION_REQUIRED)
            }
            if (!foundSupportedMethod) {
                throw JayoProtocolException("Method 'No authentication required' is not supported")
            }

            // Respond to hello.
            fromWriter.writeByte(VERSION_5)
                .writeByte(METHOD_NO_AUTHENTICATION_REQUIRED)
                .emit()

            // Read a command.
            val version = fromReader.readByte()
            val command = fromReader.readByte()
            val reserved = fromReader.readByte()
            if (version != VERSION_5 || command != COMMAND_CONNECT || reserved != 0.toByte()) {
                throw JayoProtocolException("Failed to read a command")
            }

            // Read an address.
            val inetAddress = when (val addressType = fromReader.readByte()) {
                ADDRESS_TYPE_IPV4 -> InetAddress.getByAddress(fromReader.readByteArray(4L))
                ADDRESS_TYPE_DOMAIN_NAME -> {
                    val domainNameLength = fromReader.readByte()
                    InetAddress.getByName(fromReader.readString(domainNameLength.toLong()))
                }

                else -> throw JayoProtocolException("Unknown address type $addressType")
            }
            val port = fromReader.readShort().toInt() and 0xffff

            // Connect to the caller's specified host.
            val toNetworkEndpoint = NetworkEndpoint.connectTcp(InetSocketAddress(inetAddress, port))
            openNetworkEndpoints.add(toNetworkEndpoint)
            val toNetworkEndpointAddress = toNetworkEndpoint.localAddress as InetSocketAddress
            val localAddress = toNetworkEndpointAddress.address.address
            if (localAddress.size != 4) {
                throw JayoProtocolException("Caller's specified host local address must be IPv4")
            }

            // Write the reply.
            fromWriter.writeByte(VERSION_5)
                .writeByte(REPLY_SUCCEEDED)
                .writeByte(0)
                .writeByte(ADDRESS_TYPE_IPV4)
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
            openNetworkEndpoints.remove(client)
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
        } finally {
            writer.close()
            reader.close()
            readerNetworkEndpoint.close()
            openNetworkEndpoints.remove(readerNetworkEndpoint)
        }
    }
}

fun main() {
    val proxyServer = KotlinSocksProxyServer()
    proxyServer.start()

    val url =
        URI("https://raw.githubusercontent.com/jayo-projects/jayo/main/samples/src/main/resources/jayo.txt").toURL()
    val connection = url.openConnection(proxyServer.proxy())
    connection.getInputStream().reader().buffered().use { reader ->
        generateSequence { reader.readLine() }
            .forEach(::println)
    }

    proxyServer.shutdown()
}
