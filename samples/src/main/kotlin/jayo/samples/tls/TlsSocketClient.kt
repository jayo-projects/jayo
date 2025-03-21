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
import jayo.network.NetworkEndpoint
import jayo.tls.TlsEndpoint
import java.net.InetSocketAddress

private const val DOMAIN = "www.howsmyssl.com"
private const val HTTP_LINE = "GET https://www.howsmyssl.com/a/check HTTP/1.0\nHost: www.howsmyssl.com\n\n"

/** Client example. Connects to a public TLS reporting service. */
fun main() {
    NetworkEndpoint.connectTcp(InetSocketAddress(DOMAIN, 443)).use { client ->
        // create the TlsEndpoint using minimal options
        TlsEndpoint.createClient(client).use { tslEndpoint ->
            tslEndpoint.writer.buffered().use { toEncryptWriter ->
                tslEndpoint.reader.buffered().use { decryptedReader ->
                    // do HTTP interaction and print result
                    toEncryptWriter.write(HTTP_LINE, Charsets.US_ASCII)
                    toEncryptWriter.emit()

                    // being HTTP 1.0, the server will just close the connection at the end
                    val received = decryptedReader.readString()
                    println(received)
                }
            }
        }
    }
}
