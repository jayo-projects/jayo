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

import jayo.Endpoint;
import jayo.network.NetworkServer;
import jayo.tls.helpers.SocketPairFactory;
import jayo.tls.helpers.SslContextFactory;
import jayo.tls.helpers.TlsTestUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FailTest {
    private final SslContextFactory sslContextFactory = new SslContextFactory();
    private final SocketPairFactory factory = new SocketPairFactory(sslContextFactory.getDefaultContext());

    @Test
    public void testIoPlanToTls() throws IOException {
        NetworkServer server = NetworkServer.bindTcp(new InetSocketAddress(0 /* find free port */));
        int chosenPort = ((InetSocketAddress) server.getLocalAddress()).getPort();
        InetSocketAddress address = new InetSocketAddress(factory.localhost, chosenPort);
        SocketChannel clientChannel = SocketChannel.open(address);

        Endpoint serverEndpoint = server.accept();
        factory.createClientSslEngine(Optional.empty(), chosenPort);

        Runnable serverFn = () -> TlsTestUtil.cannotFail(() -> assertThatThrownBy(() ->
                TlsEndpoint.serverBuilder(serverEndpoint, nameOpt ->
                                factory.sslContextFactory(factory.sslContext, nameOpt))
                        .engineFactory(sslContext ->
                                factory.fixedCipherServerSslEngineFactory(Optional.empty(), sslContext))
                        .build())
                .isInstanceOf(JayoTlsHandshakeException.class)
                .hasMessage("Not a handshake record"));
        Thread serverThread = new Thread(serverFn, "server-thread");
        serverThread.start();
        String message = "12345\n";

        clientChannel.write(ByteBuffer.wrap(message.getBytes()));
        ByteBuffer buffer = ByteBuffer.allocate(1);
        assertEquals(-1, clientChannel.read(buffer));
        clientChannel.close();
    }

    @Test
    public void testNioPlanToTls() throws IOException, InterruptedException {
        NetworkServer server = NetworkServer.bindTcp(new InetSocketAddress(0 /* find free port */));
        int chosenPort = ((InetSocketAddress) server.getLocalAddress()).getPort();
        InetSocketAddress address = new InetSocketAddress(factory.localhost, chosenPort);
        SocketChannel clientChannel = SocketChannel.open(address);

        Endpoint serverEndpoint = server.accept();
        factory.createClientSslEngine(Optional.empty(), chosenPort);

        Runnable serverFn = () -> TlsTestUtil.cannotFail(() -> assertThatThrownBy(() ->
                TlsEndpoint.serverBuilder(serverEndpoint, nameOpt ->
                                factory.sslContextFactory(factory.sslContext, nameOpt))
                        .engineFactory(sslContext ->
                                factory.fixedCipherServerSslEngineFactory(Optional.empty(), sslContext))
                        .build())
                .isInstanceOf(JayoTlsHandshakeException.class)
                .hasMessage("Not a handshake record"));
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
