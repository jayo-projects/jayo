/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.samples.tls;

import jayo.Buffer;
import jayo.RawReader;
import jayo.network.NetworkEndpoint;
import jayo.network.NetworkServer;
import jayo.tls.TlsEndpoint;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;

/**
 * Server example. Accepts one connection and echos bytes sent by the client into standard output.
 * <p>
 * To test, execute the following command in a terminal: <br>
 * <code>
 * openssl s_client -connect localhost:10000
 * </code>
 */
public class TlsSocketServer {
    public static void main(String[] args) throws IOException, GeneralSecurityException {

        // initialize the SSLContext, a configuration holder, reusable object
        SSLContext sslContext = ContextFactory.authenticatedContext("TLSv1.2");

        try (NetworkServer server = NetworkServer.bindTcp(new InetSocketAddress(10000))) {
            // accept encrypted connections
            System.out.println("Waiting for connection...");
            try (NetworkEndpoint accepted = server.accept();
                 // create the TlsEndpoint, combining the socket endpoint and the SSLContext, using minimal options
                 TlsEndpoint tlsEndpoint = TlsEndpoint.serverBuilder(sslContext).build(accepted);
                 RawReader decryptedReader = tlsEndpoint.getReader()) {
                Buffer buffer = Buffer.create();
                // write to stdout all data sent by the client
                while (decryptedReader.readAtMostTo(buffer, 10000L) != -1) {
                    System.out.print(buffer.readString());
                }
            }
        }
    }
}
