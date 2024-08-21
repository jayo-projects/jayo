/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-Endpoint") // A leading '-' hides this class from Java.

package jayo.endpoints

import java.net.Socket

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
    