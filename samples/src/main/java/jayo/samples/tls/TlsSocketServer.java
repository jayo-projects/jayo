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
import jayo.RawSocket;
import jayo.network.NetworkServer;
import jayo.tls.ServerHandshakeCertificates;
import jayo.tls.ServerTlsSocket;

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

        // initialize the server handshake certificates, a configuration holder, reusable object
        ServerHandshakeCertificates handshakeCertificates = CertificateFactory.authenticatedCertificate();

        try (NetworkServer server = NetworkServer.bindTcp(new InetSocketAddress(10000))) {
            // accept encrypted connections
            System.out.println("Waiting for connection...");
            RawSocket accepted = server.accept();
            // create the ServerTlsSocket, combining the socket socket and the server handshake certificates,
            // using minimal options
            RawSocket tlsSocket = ServerTlsSocket.builder(handshakeCertificates).build(accepted);
            try (RawReader decryptedReader = tlsSocket.getReader()) {
                Buffer buffer = Buffer.create();
                // write to stdout all data sent by the client
                while (decryptedReader.readAtMostTo(buffer, 10000L) != -1) {
                    System.out.print(buffer.readString());
                }
            }
        }
    }
}
