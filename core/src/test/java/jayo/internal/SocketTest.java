/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.Jayo;
import jayo.Reader;
import jayo.Writer;
import jayo.network.NetworkSocket;
import jayo.network.NetworkServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketTest {
    @Test
    void socketTest() throws InterruptedException {
        // Let the system pick up a local free port
        try (NetworkServer listener = NetworkServer.bindTcp(new InetSocketAddress(0))) {
            Thread serverThread = new Thread(() -> {
                NetworkSocket serverSocket = listener.accept();
                try (Writer serverWriter = Jayo.buffer(serverSocket.getWriter())) {
                    serverWriter.write("The Answer to the Ultimate Question of Life is ")
                            .writeUtf8CodePoint('4')
                            .writeUtf8CodePoint('2');
                }
            });
            serverThread.start();
            NetworkSocket clientSocket = NetworkSocket.connectTcp(listener.getLocalAddress());
            try (Reader clientReader = Jayo.buffer(clientSocket.getReader())) {
                assertThat(clientReader.readString())
                        .isEqualTo("The Answer to the Ultimate Question of Life is 42");
            }
            serverThread.join();
        }
    }
}
