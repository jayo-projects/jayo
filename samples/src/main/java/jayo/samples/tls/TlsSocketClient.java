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

import jayo.Jayo;
import jayo.Reader;
import jayo.Writer;
import jayo.endpoints.SocketEndpoint;
import jayo.tls.TlsEndpoint;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

/**
 * Client example. Connects to a public TLS reporting service.
 */
public class TlsSocketClient {
    private static final String DOMAIN = "www.howsmyssl.com";
    private static final String HTTP_LINE =
            "GET https://www.howsmyssl.com/a/check HTTP/1.0\nHost: www.howsmyssl.com\n\n";

    // initialize the SSLContext, a configuration holder, reusable object
    private static final SSLContext SSL_CONTEXT;

    static {
        try {
            SSL_CONTEXT = SSLContext.getDefault();
        } catch (NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void main(String... args) throws IOException {
        // connect raw socket channel normally
        try (Socket rawSocket = new Socket()) {
            rawSocket.connect(new InetSocketAddress(DOMAIN, 443));
            SocketEndpoint socketEndpoint = SocketEndpoint.from(rawSocket);
            // create the TlsEndpoint, combining the socket endpoint and the SSLContext, using minimal options
            try (TlsEndpoint tlsEndpoint = TlsEndpoint.clientBuilder(socketEndpoint, SSL_CONTEXT).build();
                 Writer toEncryptWriter = Jayo.buffer(tlsEndpoint.getWriter());
                 Reader decryptedReader = Jayo.buffer(tlsEndpoint.getReader())) {
                // do HTTP interaction and print result
                toEncryptWriter.write(HTTP_LINE, StandardCharsets.US_ASCII);
                toEncryptWriter.emit();

                // being HTTP 1.0, the server will just close the connection at the end
                String received = decryptedReader.readString();
                System.out.println(received);
            }
        }
    }
}
