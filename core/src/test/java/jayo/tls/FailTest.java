/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.tls;

import jayo.Buffer;
import jayo.endpoints.Endpoint;
import jayo.endpoints.SocketChannelEndpoint;
import jayo.endpoints.SocketEndpoint;
import jayo.tls.helpers.SocketPairFactory;
import jayo.tls.helpers.SslContextFactory;
import jayo.tls.helpers.TlsTestUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FailTest {
    private final SslContextFactory sslContextFactory = new SslContextFactory();
    private final SocketPairFactory factory = new SocketPairFactory(sslContextFactory.getDefaultContext());

    @Test
    public void testIoPlanToTls() throws IOException, InterruptedException {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(factory.localhost, 0 /* find free port */));
        int chosenPort = ((InetSocketAddress) serverSocket.getLocalSocketAddress()).getPort();
        InetSocketAddress address = new InetSocketAddress(factory.localhost, chosenPort);
        SocketChannel clientChannel = SocketChannel.open(address);

        Socket rawServer = serverSocket.accept();
        Endpoint serverEndpoint = SocketEndpoint.from(rawServer);
        factory.createClientSslEngine(Optional.empty(), chosenPort);
        TlsEndpoint.ServerBuilder tlsServerEndpointBuilder = TlsEndpoint.serverBuilder(
                        serverEndpoint,
                        nameOpt -> factory.sslContextFactory(factory.clientSniHostName, factory.sslContext, nameOpt))
                .engineFactory(
                        sslContext -> factory.fixedCipherServerSslEngineFactory(Optional.empty(), sslContext));

        TlsEndpoint tlsServerEndpoint = tlsServerEndpointBuilder.build();

        Runnable serverFn = () -> TlsTestUtil.cannotFail(() -> {
            Buffer buffer = Buffer.create();
            assertThatThrownBy(() -> tlsServerEndpoint.getReader().readAtMostTo(buffer, 10000L))
                    .isInstanceOf(JayoTlsHandshakeException.class)
                    .hasMessage("Not a handshake record");
            tlsServerEndpoint.close();
        });
        Thread serverThread = new Thread(serverFn, "server-thread");
        serverThread.start();

        String message = "12345\n";
        clientChannel.write(ByteBuffer.wrap(message.getBytes()));
        ByteBuffer buffer = ByteBuffer.allocate(1);
        assertEquals(-1, clientChannel.read(buffer));
        clientChannel.close();

        serverThread.join();
    }

    @Test
    public void testNioPlanToTls() throws IOException, InterruptedException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(factory.localhost, 0 /* find free port */));
        int chosenPort = ((InetSocketAddress) serverSocketChannel.getLocalAddress()).getPort();
        InetSocketAddress address = new InetSocketAddress(factory.localhost, chosenPort);
        SocketChannel clientChannel = SocketChannel.open(address);

        SocketChannel rawServer = serverSocketChannel.accept();
        Endpoint serverEndpoint = SocketChannelEndpoint.from(rawServer);
        factory.createClientSslEngine(Optional.empty(), chosenPort);
        TlsEndpoint.ServerBuilder tlsServerEndpointBuilder = TlsEndpoint.serverBuilder(
                        serverEndpoint,
                        nameOpt -> factory.sslContextFactory(factory.clientSniHostName, factory.sslContext, nameOpt))
                .engineFactory(
                        sslContext -> factory.fixedCipherServerSslEngineFactory(Optional.empty(), sslContext));

        TlsEndpoint tlsServerEndpoint = tlsServerEndpointBuilder.build();

        Runnable serverFn = () -> TlsTestUtil.cannotFail(() -> {
            Buffer buffer = Buffer.create();
            assertThatThrownBy(() -> tlsServerEndpoint.getReader().readAtMostTo(buffer, 10000L))
                    .isInstanceOf(JayoTlsHandshakeException.class)
                    .hasMessage("Not a handshake record");
            tlsServerEndpoint.close();
        });
        Thread serverThread = new Thread(serverFn, "server-thread");
        serverThread.start();

        String message = "12345\n";
        clientChannel.write(ByteBuffer.wrap(message.getBytes()));
        ByteBuffer buffer = ByteBuffer.allocate(1);
        assertEquals(-1, clientChannel.read(buffer));
        clientChannel.close();

        serverThread.join();
    }
}
