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
import jayo.tls.helpers.SslContextFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Tag("slow")
public class CipherTest {
    private final List<String> protocols;
    private final int dataSize = 200 * 1000;

    public CipherTest() {
        try {
            String[] allProtocols =
                    SSLContext.getDefault().getSupportedSSLParameters().getProtocols();
            protocols = Arrays.stream(allProtocols)
                    .filter(x -> !x.equals("SSLv2Hello"))
                    .collect(Collectors.toList());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException();
        }
    }

    // Test a half-duplex interaction, with renegotiation before reversing the direction of the flow (as in HTTP)
    @TestFactory
    public Collection<DynamicTest> testIoHalfDuplexWithRenegotiation() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String protocol : protocols) {
            SslContextFactory ctxFactory = new SslContextFactory(protocol);
            for (String cipher : ctxFactory.getAllCiphers()) {
                tests.add(DynamicTest.dynamicTest(
                        String.format("testIoHalfDuplexWithRenegotiation() - protocol: %s, cipher: %s", protocol, cipher),
                        () -> {
                            SocketPairFactory socketFactory = new SocketPairFactory(ctxFactory.getDefaultContext());
                            SocketPair socketPair = socketFactory.ioIo(
                                    Optional.of(cipher), Optional.empty(), false);
                            Loops.halfDuplex(socketPair, dataSize);
                            final var tlVersion = socketPair
                                    .client
                                    .tls
                                    .getHandshake()
                                    .getTlsVersion();
                            String p = String.format("%s (%s)", protocol, tlVersion);
                            System.out.printf("%-18s %-50s\n", p, cipher);
                        }));
            }
        }
        return tests;
    }

    // Test a full-duplex interaction, without any renegotiation
    @TestFactory
    public Collection<DynamicTest> testIoFullDuplex() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String protocol : protocols) {
            SslContextFactory ctxFactory = new SslContextFactory(protocol);
            for (String cipher : ctxFactory.getAllCiphers()) {
                tests.add(DynamicTest.dynamicTest(
                        String.format("testIoFullDuplex() - protocol: %s, cipher: %s", protocol, cipher), () -> {
                            SocketPairFactory socketFactory = new SocketPairFactory(ctxFactory.getDefaultContext());
                            SocketPair socketPair = socketFactory.ioIo(
                                    Optional.of(cipher), Optional.empty(), false);
                            Loops.fullDuplex(socketPair, dataSize);
                            final var tlVersion = socketPair
                                    .client
                                    .tls
                                    .getHandshake()
                                    .getTlsVersion();
                            String p = String.format("%s (%s)", protocol, tlVersion);
                            System.out.printf("%-18s %-50s\n", p, cipher);
                        }));
            }
        }
        return tests;
    }

    // Test a half-duplex interaction, with renegotiation before reversing the direction of the flow (as in HTTP)
    @TestFactory
    public Collection<DynamicTest> testNioHalfDuplexWithRenegotiation() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String protocol : protocols) {
            SslContextFactory ctxFactory = new SslContextFactory(protocol);
            for (String cipher : ctxFactory.getAllCiphers()) {
                tests.add(DynamicTest.dynamicTest(
                        String.format("testNioHalfDuplexWithRenegotiation() - protocol: %s, cipher: %s", protocol, cipher),
                        () -> {
                            SocketPairFactory socketFactory = new SocketPairFactory(ctxFactory.getDefaultContext());
                            SocketPair socketPair = socketFactory.nioNio(
                                    Optional.of(cipher), Optional.empty(), false);
                            Loops.halfDuplex(socketPair, dataSize);
                            final var tlVersion = socketPair
                                    .client
                                    .tls
                                    .getHandshake()
                                    .getTlsVersion();
                            String p = String.format("%s (%s)", protocol, tlVersion);
                            System.out.printf("%-18s %-50s\n", p, cipher);
                        }));
            }
        }
        return tests;
    }

    // Test a full-duplex interaction, without any renegotiation
    @TestFactory
    public Collection<DynamicTest> testNioFullDuplex() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String protocol : protocols) {
            SslContextFactory ctxFactory = new SslContextFactory(protocol);
            for (String cipher : ctxFactory.getAllCiphers()) {
                tests.add(DynamicTest.dynamicTest(
                        String.format("testNioFullDuplex() - protocol: %s, cipher: %s", protocol, cipher), () -> {
                            SocketPairFactory socketFactory = new SocketPairFactory(ctxFactory.getDefaultContext());
                            SocketPair socketPair = socketFactory.nioNio(
                                    Optional.of(cipher), Optional.empty(), false);
                            Loops.fullDuplex(socketPair, dataSize);
                            final var tlVersion = socketPair
                                    .client
                                    .tls
                                    .getHandshake()
                                    .getTlsVersion();
                            String p = String.format("%s (%s)", protocol, tlVersion);
                            System.out.printf("%-18s %-50s\n", p, cipher);
                        }));
            }
        }
        return tests;
    }
}
