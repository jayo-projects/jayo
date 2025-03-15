/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network

import jayo.*
import jayo.internal.network.SocksNetworkEndpoint.COMMAND_CONNECT
import jayo.internal.network.SocksNetworkEndpoint.SOCKS_V4
import jayo.network.NetworkEndpoint
import jayo.network.NetworkServer
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executor

/**
 * A partial implementation of SOCKS Protocol Version 4.
 */
class Socks4ProxyServer(
    builder: NetworkServer.Builder,
    private val executor: Executor,
    private val username: String?
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
            if (socksVersion != SOCKS_V4) {
                throw JayoProtocolException("Socks version must be 4, is $socksVersion")
            }
            val command = fromReader.readByte()
            if (command != COMMAND_CONNECT) {
                throw JayoProtocolException("Failed to read a command")
            }
            // Read an IPv4 address.
            val port = fromReader.readShort().toInt() and 0xffff
            val inetAddress = InetAddress.getByAddress(fromReader.readByteArray(4L))
            // read the username
            val sb = StringBuilder()
            var b = fromReader.readByte()
            while (b != 0.toByte()) {
                sb.append(byteArrayOf(b).toString(Charsets.ISO_8859_1))
                b = fromReader.readByte()
            }
            val decodedUsername = sb.toString()

            // Connect to the caller's specified host.
            val toNetworkEndpoint = NetworkEndpoint.connectTcp(InetSocketAddress(inetAddress, port))
            val toNetworkEndpointAddress = toNetworkEndpoint.localAddress
            val localAddress = toNetworkEndpointAddress.address.address

            // Write the reply.
            val authStatus = if (username == null || username == decodedUsername) {
                90.toByte()
            } else {
                93.toByte()
            }
            fromWriter.writeByte(SOCKS_V4)
                .writeByte(authStatus)
                .writeShort(toNetworkEndpointAddress.port.toShort())
                .write(localAddress)
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
