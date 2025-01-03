/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.Jayo;
import jayo.network.NetworkEndpoint;
import jayo.network.NetworkServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

public class SocketTest {
    @Test
    void socketTest() throws InterruptedException {
        // Let the system pick up a local free port
        try (var listener = NetworkServer.bindTcp(new InetSocketAddress(0))) {
            var serverThread = Thread.startVirtualThread(() -> {
                try (var serverEndpoint = listener.accept();
                     var serverWriter = Jayo.buffer(serverEndpoint.getWriter())) {
                    serverWriter.write("The Answer to the Ultimate Question of Life is ")
                            .writeUtf8CodePoint('4')
                            .writeUtf8CodePoint('2');
                }
            });
            try (var clientEndpoint = NetworkEndpoint.connectTcp(listener.getLocalAddress());
                 var clientReader = Jayo.buffer(clientEndpoint.getReader())) {
                assertThat(clientReader.readString())
                        .isEqualTo("The Answer to the Ultimate Question of Life is 42");
            }
            serverThread.join();
        }
    }
}
