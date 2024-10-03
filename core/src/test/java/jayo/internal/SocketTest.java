/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.Jayo;
import jayo.endpoints.SocketEndpoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class SocketTest {
    @Test
    void socketTest() throws InterruptedException, IOException {
        var freePortNumber = 54321;
        try (var serverSocket = new ServerSocket(freePortNumber)) {
            var serverThread = Thread.startVirtualThread(() -> {
                try (var acceptedSocketEndpoint = SocketEndpoint.from(serverSocket.accept());
                     var serverWriter = Jayo.buffer(acceptedSocketEndpoint.getWriter())) {
                    serverWriter.writeUtf8("The Answer to the Ultimate Question of Life is ")
                            .writeUtf8CodePoint('4')
                            .writeUtf8CodePoint('2');
                } catch (IOException e) {
                    fail("Unexpected exception", e);
                }
            });
            try (var clientSocketEndpoint = SocketEndpoint.from(new Socket("localhost", freePortNumber));
                 var clientReader = Jayo.buffer(clientSocketEndpoint.getReader())) {
                assertThat(clientReader.readUtf8String())
                        .isEqualTo("The Answer to the Ultimate Question of Life is 42");
            }
            serverThread.join();
        }
    }
}
