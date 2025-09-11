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

import jayo.tls.helpers.CertificateFactory;
import jayo.tls.helpers.SocketGroups;
import jayo.tls.helpers.SocketPairFactory;
import jayo.tls.helpers.TlsTestUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

import static jayo.tls.helpers.InteroperabilityUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class InteroperabilityTest {
    private final CertificateFactory certificateFactory = new CertificateFactory();
    private final SocketPairFactory factory =
            new SocketPairFactory(certificateFactory, CertificateFactory.CERTIFICATE_COMMON_NAME);

    private final Random random = new Random();

    private final int dataSize = CertificateFactory.TLS_MAX_DATA_SIZE * 10;

    private final byte[] data = new byte[dataSize];

    {
        random.nextBytes(data);
    }

    private final int margin = random.nextInt(100);

    private void writerLoop(Writer writer) {
        TlsTestUtil.cannotFail(() -> {
            int remaining = dataSize;
            while (remaining > 0) {
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
        Thread clientWriterThread = new Thread(() -> writerLoop(clientWriter), "client-writer");
        Thread serverWriterThread = new Thread(() -> writerLoop(serverWriter), "server-writer");
        Thread clientReaderThread = new Thread(() -> readerLoop(clientReader), "client-reader");
        Thread serverReaderThread = new Thread(() -> readerLoop(serverReader), "server-reader");
        serverReaderThread.start();
        clientWriterThread.start();
        serverReaderThread.join();
        clientWriterThread.join();
        clientReaderThread.start();
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
        Thread clientWriterThread = new Thread(() -> writerLoop(clientWriter), "client-writer");
        Thread serverWriterThread = new Thread(() -> writerLoop(serverWriter), "server-writer");
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
                new TlsSocketWriter(socketPair.server),
                new EndpointReader(socketPair.client),
                new TlsSocketWriter(socketPair.client),
                new EndpointReader(socketPair.server));
    }

    // old-io -> old-io (full duplex)
    @Test
    public void testOldToOldFullDuplex() throws IOException, InterruptedException {
        SocketGroups.OldOldSocketPair socketPair = factory.oldOld(Optional.empty());
        fullDuplexStream(
                new TlsSocketWriter(socketPair.server),
                new EndpointReader(socketPair.client),
                new TlsSocketWriter(socketPair.client),
                new EndpointReader(socketPair.server));
    }

    // IO -> OLD IO

    // io -> old-io (half duplex)
    @Test
    public void testIoToOldHalfDuplex() throws IOException, InterruptedException {
        SocketGroups.IoOldSocketPair socketPair = factory.ioOld(Optional.empty());
        halfDuplexStream(
                new TlsSocketWriter(socketPair.server),
                new EndpointReader(socketPair.client.tls),
                new TlsSocketWriter(socketPair.client.tls),
                new EndpointReader(socketPair.server));
    }

    // io -> old-io (full duplex)
    @Test
    public void testIoToOldFullDuplex() throws IOException, InterruptedException {
        SocketGroups.IoOldSocketPair socketPair = factory.ioOld(Optional.empty());
        fullDuplexStream(
                new TlsSocketWriter(socketPair.server),
                new EndpointReader(socketPair.client.tls),
                new TlsSocketWriter(socketPair.client.tls),
                new EndpointReader(socketPair.server));
    }

    // OLD IO -> IO

    // old-io -> io (half duplex)
    @Test
    public void testOldToIoHalfDuplex() throws IOException, InterruptedException {
        SocketGroups.OldIoSocketPair socketPair = factory.oldIo(Optional.empty());
        halfDuplexStream(
                new TlsSocketWriter(socketPair.server.tls),
                new EndpointReader(socketPair.client),
                new TlsSocketWriter(socketPair.client),
                new EndpointReader(socketPair.server.tls));
    }

    // old-io -> io (full duplex)
    @Test
    public void testOldToIoFullDuplex() throws IOException, InterruptedException {
        SocketGroups.OldIoSocketPair socketPair = factory.oldIo(Optional.empty());
        fullDuplexStream(
                new TlsSocketWriter(socketPair.server.tls),
                new EndpointReader(socketPair.client),
                new TlsSocketWriter(socketPair.client),
                new EndpointReader(socketPair.server.tls));
    }

    // NIO -> OLD IO

    // nio -> old-io (half duplex)
    @Test
    public void testNioToOldHalfDuplex() throws IOException, InterruptedException {
        SocketGroups.IoOldSocketPair socketPair = factory.nioOld(Optional.empty());
        halfDuplexStream(
                new TlsSocketWriter(socketPair.server),
                new EndpointReader(socketPair.client.tls),
                new TlsSocketWriter(socketPair.client.tls),
                new EndpointReader(socketPair.server));
    }

    // nio -> old-io (full duplex)
    @Test
    public void testNioToOldFullDuplex() throws IOException, InterruptedException {
        SocketGroups.IoOldSocketPair socketPair = factory.nioOld(Optional.empty());
        fullDuplexStream(
                new TlsSocketWriter(socketPair.server),
                new EndpointReader(socketPair.client.tls),
                new TlsSocketWriter(socketPair.client.tls),
                new EndpointReader(socketPair.server));
    }

    // OLD IO -> IO

    // old-io -> nio (half duplex)
    @Test
    public void testOldToNioHalfDuplex() throws IOException, InterruptedException {
        SocketGroups.OldIoSocketPair socketPair = factory.oldNio(Optional.empty());
        halfDuplexStream(
                new TlsSocketWriter(socketPair.server.tls),
                new EndpointReader(socketPair.client),
                new TlsSocketWriter(socketPair.client),
                new EndpointReader(socketPair.server.tls));
    }

    // old-io -> nio (full duplex)
    @Test
    public void testOldToNioFullDuplex() throws IOException, InterruptedException {
        SocketGroups.OldIoSocketPair socketPair = factory.oldNio(Optional.empty());
        fullDuplexStream(
                new TlsSocketWriter(socketPair.server.tls),
                new EndpointReader(socketPair.client),
                new TlsSocketWriter(socketPair.client),
                new EndpointReader(socketPair.server.tls));
    }
}
