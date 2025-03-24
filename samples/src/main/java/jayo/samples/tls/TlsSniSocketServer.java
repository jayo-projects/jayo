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
import jayo.tls.ServerHandshakeCertificates;
import jayo.tls.ServerTlsEndpoint;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.function.Function;

/**
 * Server example. Accepts one connection and echos bytes sent by the client into standard output.
 * <p>
 * To test, execute the following command in a terminal: <br>
 * <code>
 * openssl s_client -connect localhost:10000 -servername domain.com
 * </code>
 */
public class TlsSniSocketServer {
    public static void main(String[] args) throws IOException, GeneralSecurityException {
        // initialize the server handshake certificates, a configuration holder, reusable object
        ServerHandshakeCertificates handshakeCertificates = CertificateFactory.authenticatedCertificate();

        /*
         * Set the SSLContext factory with a lambda expression. In this case we reject the connection in all cases
         * except when the supplied domain matches exacting, in which case we just return our default context. A real
         * implementation would have more than one context to return according to the supplied name.
         */
        Function<SNIServerName, ServerHandshakeCertificates> exampleHandshakeCertificatesFactory =
                sniServerName -> {
            if (sniServerName instanceof SNIHostName hostName && hostName.getAsciiName().equals("domain.com")) {
                return handshakeCertificates;
            }
            return null;
        };

        try (NetworkServer server = NetworkServer.bindTcp(new InetSocketAddress(10000))) {
            // accept encrypted connections
            System.out.println("Waiting for connection...");
            try (NetworkEndpoint accepted = server.accept();
                 // create the ServerTlsEndpoint, combining the socket endpoint and the SSLContext, using minimal options
                 ServerTlsEndpoint tlsEndpoint = ServerTlsEndpoint.builder(exampleHandshakeCertificatesFactory).build(accepted);
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
