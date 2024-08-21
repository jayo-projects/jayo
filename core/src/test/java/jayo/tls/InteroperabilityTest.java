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

import jayo.tls.helpers.SocketGroups;
import jayo.tls.helpers.SocketPairFactory;
import jayo.tls.helpers.SslContextFactory;
import jayo.tls.helpers.TlsTestUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

import static jayo.tls.helpers.InteroperabilityUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class InteroperabilityTest {
    private final SslContextFactory sslContextFactory = new SslContextFactory();
    private final SocketPairFactory factory =
            new SocketPairFactory(sslContextFactory.getDefaultContext(), SslContextFactory.certificateCommonName);

    private final Random random = new Random();

    private final int dataSize = SslContextFactory.tlsMaxDataSize * 10;

    private final byte[] data = new byte[dataSize];

    {
        random.nextBytes(data);
    }

    private final int margin = random.nextInt(100);

    private void writerLoop(Writer writer, boolean renegotiate) {
        TlsTestUtil.cannotFail(() -> {
            int remaining = dataSize;
            while (remaining > 0) {
                if (renegotiate) writer.renegotiate();
                int chunkSize = random.nextInt(remaining) + 1; // 1 <= chunkSize <= remaining
                writer.write(data, dataSize - remaining, chunkSize);
                remaining -= chunkSize;
            }
        });
    }

    private void readerLoop(Reader reader) {
        TlsTestUtil.cannotFail(() -> {
            byte[] receivedData = new byte[dataSize + margin];
            int remaining = dataSize;
            while (remaining > 0) {
                int chunkSize = random.nextInt(remaining + margin) + 1; // 1 <= chunkSize <= remaining + margin
                int c = reader.read(receivedData, dataSize - remaining, chunkSize);
                assertNotEquals(-1, c, "read must not return -1 when there were bytesProduced remaining");
                assertTrue(c <= remaining);
                assertTrue(c > 0, "blocking read must return a positive number");
                remaining -= c;
            }
            assertEquals(0, remaining);
            assertArrayEquals(data, Arrays.copyOfRange(receivedData, 0, dataSize));
        });
    }

    /**
     * Test a half-duplex interaction, with renegotiation before reversing the direction of the flow (as in HTTP)
     */
    private void halfDuplexStream(Writer serverWriter, Reader clientReader, Writer clientWriter, Reader serverReader)
            throws IOException, InterruptedException {
        Thread clientWriterThread = new Thread(() -> writerLoop(clientWriter, true), "client-writer");
        Thread serverWriterThread = new Thread(() -> writerLoop(serverWriter, true), "server-writer");
        Thread clientReaderThread = new Thread(() -> readerLoop(clientReader), "client-reader");
        Thread serverReaderThread = new Thread(() -> readerLoop(serverReader), "server-reader");
        serverReaderThread.start();
        clientWriterThread.start();
        serverReaderThread.join();
        clientWriterThread.join();
        clientReaderThread.start();
        // renegotiate three times, to test idempotency
        for (int i = 0; i < 3; i++) {
            serverWriter.renegotiate();
        }
        serverWriterThread.start();
        clientReaderThread.join();
        serverWriterThread.join();
        serverWriter.close();
        clientWriter.close();
    }

    /**
     * Test a full-duplex interaction, without any renegotiation
     */
    private void fullDuplexStream(Writer serverWriter, Reader clientReader, Writer clientWriter, Reader serverReader)
            throws IOException, InterruptedException {
        Thread clientWriterThread = new Thread(() -> writerLoop(clientWriter, false), "client-writer");
        Thread serverWriterThread = new Thread(() -> writerLoop(serverWriter, false), "server-writer");
        Thread clientReaderThread = new Thread(() -> readerLoop(clientReader), "client-reader");
        Thread serverReaderThread = new Thread(() -> readerLoop(serverReader), "server-reader");
        serverReaderThread.start();
        clientWriterThread.start();
        clientReaderThread.start();
        serverWriterThread.start();
        serverReaderThread.join();
        clientWriterThread.join();
        clientReaderThread.join();
        serverWriterThread.join();
        clientWriter.close();
        serverWriter.close();
    }

    // OLD IO -> OLD IO

    // "old-io -> old-io (half duplex)
    @Test
    public void testOldToOldHalfDuplex() throws IOException, InterruptedException {
        SocketGroups.OldOldSocketPair socketPair = factory.oldOld(Optional.empty());
        halfDuplexStream(
                new SSLSocketWriter(socketPair.server),
                new SocketReader(socketPair.client),
                new SSLSocketWriter(socketPair.client),
                new SocketReader(socketPair.server));
    }

    // old-io -> old-io (full duplex)
    @Test
    public void testOldToOldFullDuplex() throws IOException, InterruptedException {
        SocketGroups.OldOldSocketPair socketPair = factory.oldOld(Optional.empty());
        fullDuplexStream(
                new SSLSocketWriter(socketPair.server),
                new SocketReader(socketPair.client),
                new SSLSocketWriter(socketPair.client),
                new SocketReader(socketPair.server));
    }

    // IO -> OLD IO

    // io -> old-io (half duplex)
    @Test
    public void testIoToOldHalfDuplex() throws IOException, InterruptedException {
        SocketGroups.IoOldSocketPair socketPair = factory.ioOld(Optional.empty());
        halfDuplexStream(
                new SSLSocketWriter(socketPair.server),
                new EndpointReader(socketPair.client.tls),
                new TlsEndpointWriter(socketPair.client.tls),
                new SocketReader(socketPair.server));
    }

    // io -> old-io (full duplex)
    @Test
    public void testIoToOldFullDuplex() throws IOException, InterruptedException {
        SocketGroups.IoOldSocketPair socketPair = factory.ioOld(Optional.empty());
        fullDuplexStream(
                new SSLSocketWriter(socketPair.server),
                new EndpointReader(socketPair.client.tls),
                new TlsEndpointWriter(socketPair.client.tls),
                new SocketReader(socketPair.server));
    }

    // OLD IO -> IO

    // old-io -> io (half duplex)
    @Test
    public void testOldToIoHalfDuplex() throws IOException, InterruptedException {
        SocketGroups.OldIoSocketPair socketPair = factory.oldIo(Optional.empty());
        halfDuplexStream(
                new TlsEndpointWriter(socketPair.server.tls),
                new SocketReader(socketPair.client),
                new SSLSocketWriter(socketPair.client),
                new EndpointReader(socketPair.server.tls));
    }

    // old-io -> io (full duplex)
    @Test
    public void testOldToIoFullDuplex() throws IOException, InterruptedException {
        SocketGroups.OldIoSocketPair socketPair = factory.oldIo(Optional.empty());
        fullDuplexStream(
                new TlsEndpointWriter(socketPair.server.tls),
                new SocketReader(socketPair.client),
                new SSLSocketWriter(socketPair.client),
                new EndpointReader(socketPair.server.tls));
    }
}
