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
import jayo.tls.helpers.SocketGroups.SocketPair;
import jayo.tls.helpers.SocketPairFactory;
import jayo.tls.helpers.SocketPairFactory.ChuckSizes;
import jayo.tls.helpers.SocketPairFactory.ChunkSizeConfig;
import jayo.tls.helpers.SslContextFactory;
import jayo.tls.helpers.TlsTestUtil;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BlockingTest {
    private static final int dataSize = 60 * 1000;
    private final SslContextFactory sslContextFactory = new SslContextFactory();
    private final SocketPairFactory factory = new SocketPairFactory(sslContextFactory.getDefaultContext());

    // Test a half-duplex interaction, with renegotiation before reversing the direction of the flow (as in HTTP)
    @TestFactory
    public Collection<DynamicTest> testIoHalfDuplexWireRenegotiations() {
        List<Integer> sizes = Stream.iterate(1, x -> x < SslContextFactory.tlsMaxDataSize * 4, x -> x * 2)
                .toList();
        List<Integer> reversedSizes = TlsTestUtil.reversed(sizes);
        List<DynamicTest> ret = new ArrayList<>();
        for (int i = 0; i < sizes.size(); i++) {
            int size1 = sizes.get(i);
            int size2 = reversedSizes.get(i);
            ret.add(DynamicTest.dynamicTest(
                    String.format("testIoHalfDuplexWireRenegotiations() - size1=%d, size2=%d", size1, size2), () -> {
                        SocketPair socketPair = factory.ioIo(
                                Optional.empty(),
                                Optional.of(new ChunkSizeConfig(
                                        new ChuckSizes(Optional.of(size1), Optional.of(size2)),
                                        new ChuckSizes(Optional.of(size1), Optional.of(size2)))),
                                false);
                        Loops.halfDuplex(socketPair, dataSize, true);
                        System.out.printf("%5d -eng-> %5d -net-> %5d -eng-> %5d\n", size1, size2, size1, size2);
                    }));
        }
        return ret;
    }

    // Test a full-duplex interaction, without any renegotiation
    @TestFactory
    public Collection<DynamicTest> testIoFullDuplex() {
        List<Integer> sizes = Stream.iterate(1, x -> x < SslContextFactory.tlsMaxDataSize * 4, x -> x * 2)
                .collect(Collectors.toList());
        List<Integer> reversedSizes = TlsTestUtil.reversed(sizes);
        List<DynamicTest> ret = new ArrayList<>();
        for (int i = 0; i < sizes.size(); i++) {
            int size1 = sizes.get(i);
            int size2 = reversedSizes.get(i);
            ret.add(DynamicTest.dynamicTest(
                    String.format("testIoFullDuplex() - size1=%d, size2=%d", size1, size2), () -> {
                        SocketPair socketPair = factory.ioIo(
                                Optional.empty(),
                                Optional.of(new ChunkSizeConfig(
                                        new ChuckSizes(Optional.of(size1), Optional.of(size2)),
                                        new ChuckSizes(Optional.of(size1), Optional.of(size2)))),
                                false);
                        Loops.fullDuplex(socketPair, dataSize);
                        System.out.printf("%5d -eng-> %5d -net-> %5d -eng-> %5d\n", size1, size2, size1, size2);
                    }));
        }
        return ret;
    }

    // Test a half-duplex interaction, with renegotiation before reversing the direction of the flow (as in HTTP)
    @TestFactory
    public Collection<DynamicTest> testNioHalfDuplexWireRenegotiations() {
        List<Integer> sizes = Stream.iterate(1, x -> x < SslContextFactory.tlsMaxDataSize * 4, x -> x * 2)
                .toList();
        List<Integer> reversedSizes = TlsTestUtil.reversed(sizes);
        List<DynamicTest> ret = new ArrayList<>();
        for (int i = 0; i < sizes.size(); i++) {
            int size1 = sizes.get(i);
            int size2 = reversedSizes.get(i);
            ret.add(DynamicTest.dynamicTest(
                    String.format("testNioHalfDuplexWireRenegotiations() - size1=%d, size2=%d", size1, size2), () -> {
                        SocketPair socketPair = factory.nioNio(
                                Optional.empty(),
                                Optional.of(new ChunkSizeConfig(
                                        new ChuckSizes(Optional.of(size1), Optional.of(size2)),
                                        new ChuckSizes(Optional.of(size1), Optional.of(size2)))),
                                false);
                        Loops.halfDuplex(socketPair, dataSize, true);
                        System.out.printf("%5d -eng-> %5d -net-> %5d -eng-> %5d\n", size1, size2, size1, size2);
                    }));
        }
        return ret;
    }

    // Test a full-duplex interaction, without any renegotiation
    @TestFactory
    public Collection<DynamicTest> testNioFullDuplex() {
        List<Integer> sizes = Stream.iterate(1, x -> x < SslContextFactory.tlsMaxDataSize * 4, x -> x * 2)
                .collect(Collectors.toList());
        List<Integer> reversedSizes = TlsTestUtil.reversed(sizes);
        List<DynamicTest> ret = new ArrayList<>();
        for (int i = 0; i < sizes.size(); i++) {
            int size1 = sizes.get(i);
            int size2 = reversedSizes.get(i);
            ret.add(DynamicTest.dynamicTest(
                    String.format("testNioFullDuplex() - size1=%d, size2=%d", size1, size2), () -> {
                        SocketPair socketPair = factory.nioNio(
                                Optional.empty(),
                                Optional.of(new ChunkSizeConfig(
                                        new ChuckSizes(Optional.of(size1), Optional.of(size2)),
                                        new ChuckSizes(Optional.of(size1), Optional.of(size2)))),
                                false);
                        Loops.fullDuplex(socketPair, dataSize);
                        System.out.printf("%5d -eng-> %5d -net-> %5d -eng-> %5d\n", size1, size2, size1, size2);
                    }));
        }
        return ret;
    }
}
