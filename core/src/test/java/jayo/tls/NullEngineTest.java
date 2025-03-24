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

import jayo.tls.helpers.Loops;
import jayo.tls.helpers.SocketGroups;
import jayo.tls.helpers.SocketPairFactory;
import jayo.tls.helpers.SocketPairFactory.ChuckSizes;
import jayo.tls.helpers.SocketPairFactory.ChunkSizeConfig;
import jayo.tls.helpers.CertificateFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import javax.net.ssl.SSLEngine;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static jayo.tls.helpers.SocketPairFactory.NULL_CIPHER;

/**
 * Test using a null engine (pass-through). The purpose of the test is to remove the overhead of the real
 * {@link SSLEngine} to be able to test the overhead of the {@link TlsEndpoint} (todo).
 */
public class NullEngineTest {
    private final CertificateFactory certificateFactory = new CertificateFactory();
    private final SocketPairFactory factory = new SocketPairFactory(certificateFactory);
    private final int dataSize = 2 * 1024 * 1024;

    {
        // heat cache
        Loops.expectedBytesHash.apply(dataSize);
    }

    // null engine - half duplex - heap buffers
    @TestFactory
    public Collection<DynamicTest> testIoHalfDuplexHeapBuffers() {
        List<Integer> sizes = Stream.iterate(512, x -> x < CertificateFactory.TLS_MAX_DATA_SIZE * 4, x -> x * 2)
                .toList();
        List<DynamicTest> tests = new ArrayList<>();
        for (int size1 : sizes) {
            DynamicTest test = DynamicTest.dynamicTest(String.format("testIoHalfDuplexHeapBuffers() - Testing sizes: size1=%s", size1), () -> {
                SocketGroups.SocketPair socketPair = factory.ioIo(
                        Optional.of(NULL_CIPHER),
                        Optional.of(new ChunkSizeConfig(
                                new ChuckSizes(Optional.of(size1), Optional.empty()),
                                new ChuckSizes(Optional.of(size1), Optional.empty()))),
                        false);
                Loops.halfDuplex(socketPair, dataSize);
                System.out.printf("-eng-> %5d -net-> %5d -eng->\n", size1, size1);
            });
            tests.add(test);
        }
        return tests;
    }

    // null engine - half duplex - direct buffers
    @TestFactory
    public Collection<DynamicTest> testIoHalfDuplexDirectBuffers() {
        List<Integer> sizes = Stream.iterate(512, x -> x < CertificateFactory.TLS_MAX_DATA_SIZE * 4, x -> x * 2)
                .toList();
        List<DynamicTest> tests = new ArrayList<>();
        for (int size1 : sizes) {
            DynamicTest test = DynamicTest.dynamicTest(String.format("testIoHalfDuplexDirectBuffers() - Testing sizes: size1=%s", size1), () -> {
                SocketGroups.SocketPair socketPair = factory.ioIo(
                        Optional.of(NULL_CIPHER),
                        Optional.of(new ChunkSizeConfig(
                                new ChuckSizes(Optional.of(size1), Optional.empty()),
                                new ChuckSizes(Optional.of(size1), Optional.empty()))),
                        false);
                Loops.halfDuplex(socketPair, dataSize);
                System.out.printf("-eng-> %5d -net-> %5d -eng->\n", size1, size1);
            });
            tests.add(test);
        }
        return tests;
    }

    // null engine - half duplex - heap buffers
    @TestFactory
    public Collection<DynamicTest> testNioHalfDuplexHeapBuffers() {
        List<Integer> sizes = Stream.iterate(512, x -> x < CertificateFactory.TLS_MAX_DATA_SIZE * 4, x -> x * 2)
                .toList();
        List<DynamicTest> tests = new ArrayList<>();
        for (int size1 : sizes) {
            DynamicTest test = DynamicTest.dynamicTest(String.format("testNioHalfDuplexHeapBuffers() - Testing sizes: size1=%s", size1), () -> {
                SocketGroups.SocketPair socketPair = factory.nioNio(
                        Optional.of(NULL_CIPHER),
                        Optional.of(new ChunkSizeConfig(
                                new ChuckSizes(Optional.of(size1), Optional.empty()),
                                new ChuckSizes(Optional.of(size1), Optional.empty()))),
                        false);
                Loops.halfDuplex(socketPair, dataSize);
                System.out.printf("-eng-> %5d -net-> %5d -eng->\n", size1, size1);
            });
            tests.add(test);
        }
        return tests;
    }

    // null engine - half duplex - direct buffers
    @TestFactory
    public Collection<DynamicTest> testNioHalfDuplexDirectBuffers() {
        List<Integer> sizes = Stream.iterate(512, x -> x < CertificateFactory.TLS_MAX_DATA_SIZE * 4, x -> x * 2)
                .toList();
        List<DynamicTest> tests = new ArrayList<>();
        for (int size1 : sizes) {
            DynamicTest test = DynamicTest.dynamicTest(String.format("testNioHalfDuplexDirectBuffers() - Testing sizes: size1=%s", size1), () -> {
                SocketGroups.SocketPair socketPair = factory.nioNio(
                        Optional.of(NULL_CIPHER),
                        Optional.of(new ChunkSizeConfig(
                                new ChuckSizes(Optional.of(size1), Optional.empty()),
                                new ChuckSizes(Optional.of(size1), Optional.empty()))),
                        false);
                Loops.halfDuplex(socketPair, dataSize);
                System.out.printf("-eng-> %5d -net-> %5d -eng->\n", size1, size1);
            });
            tests.add(test);
        }
        return tests;
    }
}
