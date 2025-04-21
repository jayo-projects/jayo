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

import jayo.*;
import jayo.tls.helpers.SocketGroups.SocketGroup;
import jayo.tls.helpers.SocketGroups.SocketPair;
import jayo.tls.helpers.SocketPairFactory;
import jayo.tls.helpers.SocketPairFactory.ChuckSizes;
import jayo.tls.helpers.SocketPairFactory.ChunkSizeConfig;
import jayo.tls.helpers.CertificateFactory;
import jayo.tls.helpers.TlsTestUtil;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CloseTest {
    private final CertificateFactory certificateFactory = new CertificateFactory();
    private final SocketPairFactory factory = new SocketPairFactory(certificateFactory);
    private final byte[] data = new byte[]{15};

    /**
     * Less than a TLS message to force read/write loops
     */
    private final Optional<Integer> internalBufferSize = Optional.of(10);

    @Test
    void testTcpImmediateClose() throws InterruptedException {
        SocketPair socketPair = factory.ioIo(
                Optional.empty(),
                Optional.of(new ChunkSizeConfig(
                        new ChuckSizes(internalBufferSize, Optional.empty()),
                        new ChuckSizes(internalBufferSize, Optional.empty()))),
                false);
        SocketGroup clientGroup = socketPair.client;
        SocketGroup serverGroup = socketPair.server;
        TlsEndpoint client = clientGroup.tls;
        TlsEndpoint server = serverGroup.tls;
        Runnable clientFn = TlsTestUtil.cannotFailRunnable(() -> {
            Writer clientWriter = client.getWriter()
                    .write(data);
            clientGroup.plain.close();
            assertThat(clientGroup.tls.isShutdownSent()).isFalse();
            assertThat(clientGroup.tls.isShutdownReceived()).isFalse();
            assertThat(clientGroup.tls.isOpen()).isFalse();
            assertThatThrownBy(clientWriter::flush).isInstanceOf(JayoClosedResourceException.class);
        });
        Runnable serverFn = TlsTestUtil.cannotFailRunnable(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(1);
            Reader serverReader = server.getReader();
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
            assertThat(serverGroup.tls.isShutdownReceived()).isFalse();
            assertThat(serverGroup.tls.isShutdownSent()).isFalse();
            assertThat(serverGroup.tls.isOpen()).isTrue();
            // repeated
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
        });
        Thread clientThread = new Thread(clientFn, "client-thread");
        Thread serverThread = new Thread(serverFn, "server-thread");
        clientThread.start();
        serverThread.start();
        clientThread.join();
        serverThread.join();
        clientGroup.tls.close();
        serverGroup.tls.close();
    }

    @Test
    void testTcpClose() throws InterruptedException {
        SocketPair socketPair = factory.ioIo(
                Optional.empty(),
                Optional.of(new ChunkSizeConfig(
                        new ChuckSizes(internalBufferSize, Optional.empty()),
                        new ChuckSizes(internalBufferSize, Optional.empty()))),
                false);
        SocketGroup clientGroup = socketPair.client;
        SocketGroup serverGroup = socketPair.server;
        TlsEndpoint client = clientGroup.tls;
        TlsEndpoint server = serverGroup.tls;
        Runnable clientFn = TlsTestUtil.cannotFailRunnable(() -> {
            Writer clientWriter = client.getWriter()
                    .write(data);
            clientWriter.flush();
            clientGroup.plain.close();
            assertThat(clientGroup.tls.isShutdownSent()).isFalse();
            assertThat(clientGroup.tls.isShutdownReceived()).isFalse();
            clientWriter.write(data);
            assertThatThrownBy(clientWriter::flush).isInstanceOf(JayoClosedResourceException.class);
        });
        Runnable serverFn = TlsTestUtil.cannotFailRunnable(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(1);
            Reader serverReader = server.getReader();
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(1);
            buffer.flip();
            assertThat(buffer).isEqualTo(ByteBuffer.wrap(data));
            buffer.clear();
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
            assertThat(serverGroup.tls.isShutdownReceived()).isFalse();
            assertThat(serverGroup.tls.isShutdownSent()).isFalse();
            // repeated
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
        });
        Thread clientThread = new Thread(clientFn, "client-thread");
        Thread serverThread = new Thread(serverFn, "server-thread");
        clientThread.start();
        serverThread.start();
        clientThread.join();
        serverThread.join();
        clientGroup.tls.close();
        serverGroup.tls.close();
    }

    @Test
    void testClose() throws InterruptedException {
        SocketPair socketPair = factory.ioIo(
                Optional.empty(),
                Optional.of(new ChunkSizeConfig(
                        new ChuckSizes(internalBufferSize, Optional.empty()),
                        new ChuckSizes(internalBufferSize, Optional.empty()))),
                false);
        SocketGroup clientGroup = socketPair.client;
        SocketGroup serverGroup = socketPair.server;
        TlsEndpoint client = clientGroup.tls;
        TlsEndpoint server = serverGroup.tls;
        Runnable clientFn = TlsTestUtil.cannotFailRunnable(() -> {
            Writer clientWriter = client.getWriter()
                    .write(data);
            clientWriter.flush();
            client.close();
            assertThat(clientGroup.tls.isShutdownSent()).isTrue();
            assertThat(clientGroup.tls.isShutdownReceived()).isFalse();
            clientWriter.write(data);
            assertThatThrownBy(clientWriter::flush).isInstanceOf(JayoClosedResourceException.class);
        });
        Runnable serverFn = TlsTestUtil.cannotFailRunnable(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(1);
            Reader serverReader = server.getReader();
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(1);
            assertThat(serverGroup.tls.isOpen()).isTrue();
            buffer.flip();
            assertThat(buffer).isEqualTo(ByteBuffer.wrap(data));
            buffer.clear();
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
            assertThat(serverGroup.tls.isShutdownReceived()).isTrue();
            assertThat(serverGroup.tls.isShutdownSent()).isFalse();
            assertThat(serverGroup.tls.isOpen()).isFalse();
            // repeated
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
            server.close();
            assertThatThrownBy(() -> serverReader.readAtMostTo(buffer)).isInstanceOf(JayoClosedResourceException.class);
        });
        Thread clientThread = new Thread(clientFn, "client-thread");
        Thread serverThread = new Thread(serverFn, "server-thread");
        clientThread.start();
        serverThread.start();
        clientThread.join();
        serverThread.join();
    }

    @Test
    void testCloseAndWait() throws InterruptedException {
        SocketPair socketPair = factory.ioIo(
                Optional.empty(),
                Optional.of(new ChunkSizeConfig(
                        new ChuckSizes(internalBufferSize, Optional.empty()),
                        new ChuckSizes(internalBufferSize, Optional.empty()))),
                true);
        SocketGroup clientGroup = socketPair.client;
        SocketGroup serverGroup = socketPair.server;
        TlsEndpoint client = clientGroup.tls;
        TlsEndpoint server = serverGroup.tls;
        Runnable clientFn = TlsTestUtil.cannotFailRunnable(() -> {
            Writer clientWriter = client.getWriter()
                    .write(data);
            clientWriter.flush();
            client.close();
            assertThat(clientGroup.tls.isShutdownSent()).isTrue();
            assertThat(clientGroup.tls.isShutdownReceived()).isTrue();
            clientWriter.write(data);
            assertThatThrownBy(clientWriter::flush).isInstanceOf(JayoClosedResourceException.class);
        });
        Runnable serverFn = TlsTestUtil.cannotFailRunnable(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(1);
            Reader serverReader = server.getReader();
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(1);
            buffer.flip();
            assertThat(buffer).isEqualTo(ByteBuffer.wrap(data));
            buffer.clear();
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
            // repeated
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
            server.close();
            assertThat(serverGroup.tls.isShutdownReceived()).isTrue();
            assertThat(serverGroup.tls.isShutdownSent()).isTrue();
            assertThatThrownBy(() -> serverReader.readAtMostTo(buffer)).isInstanceOf(JayoClosedResourceException.class);
        });
        Thread clientThread = new Thread(clientFn, "client-thread");
        Thread serverThread = new Thread(serverFn, "server-thread");
        clientThread.start();
        serverThread.start();
        clientThread.join();
        serverThread.join();
    }

    @Test
    void testCloseAndWaitForever() throws InterruptedException {
        SocketPair socketPair = factory.ioIo(
                Optional.empty(),
                Optional.of(new ChunkSizeConfig(
                        new ChuckSizes(internalBufferSize, Optional.empty()),
                        new ChuckSizes(internalBufferSize, Optional.empty()))),
                true);
        SocketGroup clientGroup = socketPair.client;
        SocketGroup serverGroup = socketPair.server;
        TlsEndpoint client = clientGroup.tls;
        TlsEndpoint server = serverGroup.tls;
        Runnable clientFn = TlsTestUtil.cannotFailRunnable(() -> {
            Writer clientWriter = client.getWriter()
                    .write(data);
            clientWriter.flush();
            client.close();
        });
        Runnable serverFn = TlsTestUtil.cannotFailRunnable(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(1);
            Reader serverReader = server.getReader();
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(1);
            buffer.flip();
            assertThat(buffer).isEqualTo(ByteBuffer.wrap(data));
            buffer.clear();
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
            assertThat(serverGroup.tls.isShutdownReceived()).isTrue();
            assertThat(serverGroup.tls.isShutdownSent()).isFalse();
            // repeated
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
        });
        Thread clientThread = new Thread(clientFn, "client-thread");
        Thread serverThread = new Thread(serverFn, "server-thread");
        clientThread.start();
        serverThread.start();
        clientThread.join(100);
        serverThread.join();
        assertThat(clientThread.isAlive()).isTrue();
        serverGroup.tls.close();
        clientThread.join();
    }

    @Test
    void testShutdownAndForget() throws InterruptedException {
        SocketPair socketPair = factory.ioIo(
                Optional.empty(),
                Optional.of(new ChunkSizeConfig(
                        new ChuckSizes(internalBufferSize, Optional.empty()),
                        new ChuckSizes(internalBufferSize, Optional.empty()))),
                false);
        SocketGroup clientGroup = socketPair.client;
        SocketGroup serverGroup = socketPair.server;
        TlsEndpoint client = clientGroup.tls;
        TlsEndpoint server = serverGroup.tls;
        Runnable clientFn = TlsTestUtil.cannotFailRunnable(() -> {
            Writer clientWriter = client.getWriter()
                    .write(data);
            clientWriter.flush();
            assertThat(client.shutdown()).isFalse();
            assertThat(clientGroup.tls.isShutdownSent()).isTrue();
            assertThat(clientGroup.tls.isShutdownReceived()).isFalse();
            clientWriter.write(data);
            assertThatThrownBy(clientWriter::flush).isInstanceOf(JayoClosedResourceException.class);
        });
        Runnable serverFn = TlsTestUtil.cannotFailRunnable(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(1);
            Reader serverReader = server.getReader();
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(1);
            buffer.flip();
            assertThat(buffer).isEqualTo(ByteBuffer.wrap(data));
            buffer.clear();
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
            assertThat(serverGroup.tls.isShutdownReceived()).isTrue();
            assertThat(serverGroup.tls.isShutdownSent()).isFalse();
            // repeated
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
        });
        Thread clientThread = new Thread(clientFn, "client-thread");
        Thread serverThread = new Thread(serverFn, "server-thread");
        clientThread.start();
        serverThread.start();
        clientThread.join();
        serverThread.join();
        client.close();
        server.close();
    }

    @Test
    void testShutdownAndWait() throws InterruptedException {
        SocketPair socketPair = factory.ioIo(
                Optional.empty(),
                Optional.of(new ChunkSizeConfig(
                        new ChuckSizes(internalBufferSize, Optional.empty()),
                        new ChuckSizes(internalBufferSize, Optional.empty()))),
                false);
        SocketGroup clientGroup = socketPair.client;
        SocketGroup serverGroup = socketPair.server;
        TlsEndpoint client = clientGroup.tls;
        TlsEndpoint server = serverGroup.tls;
        Runnable clientFn = TlsTestUtil.cannotFailRunnable(() -> {
            Writer clientWriter = client.getWriter()
                    .write(data);
            clientWriter.flush();
            // send first close_notify
            assertThat(client.shutdown()).isFalse();
            assertThat(clientGroup.tls.isShutdownSent()).isTrue();
            assertThat(clientGroup.tls.isShutdownReceived()).isFalse();
            clientWriter.write(data);
            assertThatThrownBy(clientWriter::flush).isInstanceOf(JayoClosedResourceException.class);
            // wait for second close_notify
            assertThat(client.shutdown()).isTrue();
            assertThat(clientGroup.tls.isShutdownSent()).isTrue();
            assertThat(clientGroup.tls.isShutdownReceived()).isTrue();
        });
        Runnable serverFn = TlsTestUtil.cannotFailRunnable(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(1);
            Reader serverReader = server.getReader();
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(1);
            buffer.flip();
            assertThat(buffer).isEqualTo(ByteBuffer.wrap(data));
            buffer.clear();
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
            assertThat(serverGroup.tls.isShutdownReceived()).isTrue();
            assertThat(serverGroup.tls.isShutdownSent()).isFalse();
            // repeated
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
            // send second close_notify
            assertThat(server.shutdown()).isTrue();
            assertThat(serverGroup.tls.isShutdownSent()).isTrue();
            assertThat(serverGroup.tls.isShutdownReceived()).isTrue();
        });
        Thread clientThread = new Thread(clientFn, "client-thread");
        Thread serverThread = new Thread(serverFn, "server-thread");
        clientThread.start();
        serverThread.start();
        clientThread.join();
        serverThread.join();
        client.close();
        server.close();
    }

    @Test
    void testShutdownAndWaitForever() throws InterruptedException {
        SocketPair socketPair = factory.ioIo(
                Optional.empty(),
                Optional.of(new ChunkSizeConfig(
                        new ChuckSizes(internalBufferSize, Optional.empty()),
                        new ChuckSizes(internalBufferSize, Optional.empty()))),
                false);
        SocketGroup clientGroup = socketPair.client;
        SocketGroup serverGroup = socketPair.server;
        TlsEndpoint client = clientGroup.tls;
        TlsEndpoint server = serverGroup.tls;
        Runnable clientFn = TlsTestUtil.cannotFailRunnable(() -> {
            Writer clientWriter = client.getWriter()
                    .write(data);
            clientWriter.flush();
            // send first close_notify
            assertThat(client.shutdown()).isFalse();
            assertThat(clientGroup.tls.isShutdownSent()).isTrue();
            assertThat(clientGroup.tls.isShutdownReceived()).isFalse();
            clientWriter.write(data);
            assertThatThrownBy(clientWriter::flush).isInstanceOf(JayoClosedResourceException.class);
            // wait 100ms for second close_notify, JayoTimeoutException proves it would hang forever
            assertThatThrownBy(() ->
                    Cancellable.run(Duration.ofMillis(100), _unused -> clientGroup.tls.shutdown())
            ).isInstanceOf(JayoTimeoutException.class);
        });
        Runnable serverFn = TlsTestUtil.cannotFailRunnable(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(1);
            Reader serverReader = server.getReader();
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(1);
            buffer.flip();
            assertThat(buffer).isEqualTo(ByteBuffer.wrap(data));
            buffer.clear();
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
            assertThat(serverGroup.tls.isShutdownReceived()).isTrue();
            assertThat(serverGroup.tls.isShutdownSent()).isFalse();
            // repeated
            assertThat(serverReader.readAtMostTo(buffer)).isEqualTo(-1);
            // do not send second close_notify
        });
        Thread clientThread = new Thread(clientFn, "client-thread");
        Thread serverThread = new Thread(serverFn, "server-thread");
        clientThread.start();
        serverThread.start();
        serverThread.join();
        clientThread.join();
        client.close();
        server.close();
    }
}
