/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.samples.tls

import jayo.buffered
import jayo.endpoints.endpoint
import jayo.tls.TlsEndpoint
import java.net.Socket
import javax.net.ssl.SSLContext

private const val DOMAIN = "www.howsmyssl.com"
private const val HTTP_LINE = "GET https://www.howsmyssl.com/a/check HTTP/1.0\nHost: www.howsmyssl.com\n\n"

// initialize the SSLContext, a configuration holder, reusable object
private val sslContext = SSLContext.getDefault()

/** Client example. Connects to a public TLS reporting service. */
fun main() {
    // connect raw socket channel normally
    Socket(DOMAIN, 443).use { rawSocket ->
        // create the TlsEndpoint, combining the socket endpoint and the SSLEngine, using minimal options
        TlsEndpoint.clientBuilder(rawSocket.endpoint(), sslContext).build().use { tslEndpoint ->
            tslEndpoint.getWriter().buffered().use { toEncryptWriter ->
                tslEndpoint.getReader().buffered().use { decryptedReader ->
                    // do HTTP interaction and print result
                    toEncryptWriter.writeString(HTTP_LINE, Charsets.US_ASCII)
                    toEncryptWriter.emit()

                    // being HTTP 1.0, the server will just close the connection at the end
                    val received = decryptedReader.readUtf8String()
                    println(received)
                }
            }
        }
    }
}
