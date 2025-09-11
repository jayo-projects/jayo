/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.tls.helpers;

import jayo.tls.helpers.SocketGroups.SocketGroup;
import jayo.tls.helpers.SocketGroups.SocketPair;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class Loops {
    private static final Logger logger = Logger.getLogger(Loops.class.getName());

    public static final long seed = 143000953L;

    /*
     * Note that it is necessary to use a multiple of 4 as buffer size for writing.
     * This is because the bytesProduced to write are generated using Random.nextBytes, that
     * always consumes full (4 byte) integers. A multiple of 4 then prevents "holes"
     * in the random sequence.
     */
    public static final int bufferSize = 4 * 5000;

    public static final String hashAlgorithm = "MD5"; // for speed

    /**
     * Test a half-duplex interaction, with (optional) renegotiation before reversing the direction of the flow (as in
     * HTTP)
     */
    public static void halfDuplex(SocketPair socketPair, int dataSize) {
        Thread clientWriterThread = new Thread(
                () -> Loops.writerLoop(dataSize, socketPair.client, false, false),
                "client-writer");
        Thread serverReaderThread = new Thread(
                () -> Loops.readerLoop(dataSize, socketPair.server, false, false), "server-reader");
        Thread serverWriterThread = new Thread(
                () -> Loops.writerLoop(dataSize, socketPair.server, true, true),
                "server-writer");
        Thread clientReaderThread = new Thread(
                () -> Loops.readerLoop(dataSize, socketPair.client, true, true), "client-reader");

        try {
            serverReaderThread.start();
            clientWriterThread.start();

            serverReaderThread.join();
            clientWriterThread.join();

            clientReaderThread.start();
            serverWriterThread.start();

            clientReaderThread.join();
            serverWriterThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void fullDuplex(SocketPair socketPair, int dataSize) {
        Thread clientWriterThread = new Thread(
                () -> Loops.writerLoop(dataSize, socketPair.client, false, false), "client-writer");
        Thread serverWriterThread = new Thread(
                () -> Loops.writerLoop(dataSize, socketPair.server, false, false), "server-write");
        Thread clientReaderThread =
                new Thread(() -> Loops.readerLoop(dataSize, socketPair.client, false, false), "client-reader");
        Thread serverReaderThread =
                new Thread(() -> Loops.readerLoop(dataSize, socketPair.server, false, false), "server-reader");

        try {
            serverReaderThread.start();
            clientWriterThread.start();
            clientReaderThread.start();
            serverWriterThread.start();

            serverReaderThread.join();
            clientWriterThread.join();
            clientReaderThread.join();
            serverWriterThread.join();

            socketPair.client.plain.cancel();
            socketPair.server.plain.cancel();

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writerLoop(
            int size,
            SocketGroup socketGroup,
            boolean shutdown,
            boolean close) {
        TlsTestUtil.cannotFail(() -> {
            logger.fine(() -> String.format(
                    "Starting writer loop, size: %s", size));
            SplittableRandom random = new SplittableRandom(seed);
            int bytesRemaining = size;
            byte[] bufferArray = new byte[bufferSize];
            final var writer = socketGroup.tls.getWriter();
            while (bytesRemaining > 0) {
                final var toWrite = Math.min(bufferSize, bytesRemaining);
                TlsTestUtil.nextBytes(random, bufferArray, toWrite);
                writer.write(bufferArray, 0, toWrite);
                writer.flush();
                bytesRemaining -= toWrite;
                assertThat(bytesRemaining).isNotNegative();
            }
            if (shutdown) {
                socketGroup.tls.shutdown();
            }
            if (close) {
                socketGroup.plain.cancel();
            }
            logger.fine("Finalizing writer loop");
        });
    }

    public static void readerLoop(int size, SocketGroup socketGroup, boolean readEof, boolean close) {
        TlsTestUtil.cannotFail(() -> {
            logger.fine(() -> String.format("Starting reader loop. Size: %s", size));
            byte[] readArray = new byte[bufferSize];
            int bytesRemaining = size;
            MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
            final var reader = socketGroup.tls.getReader();
            while (bytesRemaining > 0) {
                final var toRead = Math.min(bufferSize, bytesRemaining);
                int c = reader.readAtMostTo(readArray, 0, toRead);
                assertThat(c).isPositive();
                digest.update(readArray, 0, c);
                bytesRemaining -= c;
                assertThat(bytesRemaining).isNotNegative();
            }
            if (readEof) {
                assertThat(reader.readAtMostTo(readArray)).isEqualTo(-1);
            }
            byte[] actual = digest.digest();
            assertThat(actual).contains(expectedBytesHash.apply(size));
            if (close) {
                socketGroup.plain.cancel();
            }
            logger.fine("Finalizing reader loop");
        });
    }

    private static byte[] hash(int size) {
        try {
            MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
            SplittableRandom random = new SplittableRandom(seed);
            int generated = 0;
            int bufferSize = 4 * 1024;
            byte[] array = new byte[bufferSize];
            while (generated < size) {
                TlsTestUtil.nextBytes(random, array);
                int pending = size - generated;
                digest.update(array, 0, Math.min(bufferSize, pending));
                generated += bufferSize;
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static final Function<Integer, byte[]> expectedBytesHash = new TlsTestUtil.Memo<>(Loops::hash)::apply;
}
