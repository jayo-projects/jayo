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
import jayo.endpoints.SocketEndpoint;
import jayo.tls.TlsEndpoint;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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

        // connect server socket normally
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(10000));

            // accept raw connections normally
            System.out.println("Waiting for connection...");
            try (Socket rawSocket = serverSocket.accept()) {
                SocketEndpoint socketEndpoint = SocketEndpoint.from(rawSocket);
                // create the TlsEndpoint, combining the socket endpoint and the SSLContext, using minimal options
                try (TlsEndpoint tlsEndpoint = TlsEndpoint.serverBuilder(socketEndpoint, sslContext).build();
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
}
