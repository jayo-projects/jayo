/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-Endpoint") // A leading '-' hides this class from Java.

package jayo.endpoints

import java.net.Socket
import java.nio.channels.SocketChannel

/**
 * @return an endpoint bound to the provided [Socket]. This socket must be [connected][Socket.isConnected] and not
 * [closed][Socket.isClosed].
 *
 * Prefer this over using `socket.inputStream.reader()` and `socket.outputStream.writer()` because this endpoint honors
 * timeouts. When a read or write operation times out, the underlying socket is asynchronously closed by a watchdog
 * thread.
 *
 * @throws IllegalArgumentException if the socket is not [connected][Socket.isConnected] or is [closed][Socket.isClosed]
 */
public fun Socket.endpoint(): SocketEndpoint = SocketEndpoint.from(this)

/**
 * @return an endpoint bound to the provided [Socket]. This socket channel must be
 * [connected][SocketChannel.isConnected] and [open][SocketChannel.isOpen].
 *
 * Prefer this over using `socketChannel.reader()` and `socketChannel.writer()` because this endpoint honors timeouts.
 * When a read or write operation times out, the underlying socket channel is asynchronously closed by a watchdog
 * thread.
 *
 * @throws IllegalArgumentException if the socket channel is not [connected][Socket.isConnected] or not
 * [open][SocketChannel.isOpen]
 */
public fun SocketChannel.endpoint(): SocketChannelEndpoint = SocketChannelEndpoint.from(this)
    