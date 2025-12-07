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

import jayo.*;
import jayo.network.NetworkSocket;
import jayo.tls.ClientTlsSocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Client example. Connects to a public TLS reporting service.
 */
public class TlsSocketClient {
    private static final String DOMAIN = "www.howsmyssl.com";
    private static final String HTTP_LINE =
            "GET https://www.howsmyssl.com/a/check HTTP/1.0\nHost: www.howsmyssl.com\n\n";

    public static void main(String... args) throws IOException {
        RawSocket client = NetworkSocket.connectTcp(new InetSocketAddress(DOMAIN, 443));
        // create the ClientTlsSocket using minimal options
        Socket tlsSocket = Jayo.buffer(ClientTlsSocket.create(client));
        try (Writer toEncryptWriter = tlsSocket.getWriter();
             Reader decryptedReader = tlsSocket.getReader()) {
            // do HTTP interaction and print result
            toEncryptWriter.write(HTTP_LINE, StandardCharsets.US_ASCII);
            toEncryptWriter.emit();

            // being HTTP 1.0, the server will just close the connection at the end
            String received = decryptedReader.readString();
            System.out.println(received);
        }
    }
}
