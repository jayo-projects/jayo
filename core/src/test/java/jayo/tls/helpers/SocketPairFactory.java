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

import jayo.Endpoint;
import jayo.internal.network.ChunkingEndpoint;
import jayo.network.NetworkEndpoint;
import jayo.network.NetworkServer;
import jayo.tls.*;
import jayo.tls.helpers.SocketGroups.*;

import javax.crypto.Cipher;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Create pairs of connected sockets (using the loopback interface). Additionally, all the raw (non-encrypted) socket
 * channel are wrapped with a chunking decorator that partitions the bytesProduced of any read or write operation.
 */
public class SocketPairFactory {
    private static final Logger LOGGER = Logger.getLogger(SocketPairFactory.class.getName());

    private static final int maxAllowedKeyLength;

    static {
        try {
            maxAllowedKeyLength = Cipher.getMaxAllowedKeyLength("AES");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static class ChunkSizeConfig {
        public final ChuckSizes clientChuckSize;
        public final ChuckSizes serverChunkSize;

        public ChunkSizeConfig(ChuckSizes clientChuckSize, ChuckSizes serverChunkSize) {
            this.clientChuckSize = clientChuckSize;
            this.serverChunkSize = serverChunkSize;
        }
    }

    public static class ChuckSizes {
        public final Optional<Integer> internalSize;
        public final Optional<Integer> externalSize;

        public ChuckSizes(Optional<Integer> internalSize, Optional<Integer> externalSize) {
            this.internalSize = internalSize;
            this.externalSize = externalSize;
        }
    }

    public final CertificateFactory certificateFactory;
    private final String serverName;
    public final SNIHostName clientSniHostName;
    private final SNIMatcher expectedSniHostName;
    public final InetAddress localhost;

    public SocketPairFactory(CertificateFactory certificateFactory, String serverName) {
        this.certificateFactory = certificateFactory;
        this.serverName = serverName;
        this.clientSniHostName = new SNIHostName(serverName);
        this.expectedSniHostName = SNIHostName.createSNIMatcher(serverName /* regex! */);
        try {
            this.localhost = InetAddress.getByName(null);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info(() -> String.format("AES max key length: %s", maxAllowedKeyLength));
    }

    public SocketPairFactory(CertificateFactory certificateFactory) {
        this(certificateFactory, CertificateFactory.CERTIFICATE_COMMON_NAME);
    }

    public void fixedCipherServerSslEngineCustomizer(Optional<String> cipher, TlsEndpoint.Parameterizer parameterizer) {
        cipher.ifPresentOrElse(c -> {
            final var cipherSuite = CipherSuite.fromJavaName(c);
            assertThat(parameterizer.getSupportedCipherSuites()).contains(cipherSuite);
            assertThat(parameterizer.getEnabledCipherSuites()).isNotEmpty();
            parameterizer.setEnabledCipherSuites(List.of(cipherSuite));
        }, () -> parameterizer.setEnabledTlsVersions(List.of(certificateFactory.version)));
    }

    public ServerHandshakeCertificates handshakeCertificatesFactory(ServerHandshakeCertificates handshakeCertificates,
                                                                    SNIServerName name) {
        if (name != null) {
            LOGGER.warning(() -> "ContextFactory, requested name: " + name);
            if (!expectedSniHostName.matches(name)) {
                throw new IllegalArgumentException(String.format("Received SNI $n does not match %s", serverName));
            }
            return handshakeCertificates;
        } else {
            throw new IllegalArgumentException("SNI expected");
        }
    }

    private void customizeClientSslEngine(ClientTlsEndpoint.Parameterizer parameterizer, Optional<String> cipher) {
        cipher.ifPresentOrElse(c -> parameterizer.setEnabledCipherSuites(List.of(CipherSuite.fromJavaName(c))),
                () -> parameterizer.setEnabledTlsVersions(List.of(certificateFactory.version)));
        parameterizer.setServerNames(Collections.singletonList(clientSniHostName));
    }

    private TlsEndpoint createTlsServerEndpoint(Optional<String> cipher, NetworkEndpoint endpoint) {
        final var parameterizer = ServerTlsEndpoint.builder(certificateFactory.getServerHandshakeCertificates())
                .createParameterizer(endpoint);
        cipher.ifPresent(c -> parameterizer.setEnabledCipherSuites(List.of(CipherSuite.fromJavaName(c))));
        return parameterizer.build();
    }

    private TlsEndpoint createTlsClientEndpoint(Optional<String> cipher,
                                                InetSocketAddress address,
                                                SNIHostName clientSniHostName) {
        final var parameterizer = ClientTlsEndpoint.builder(certificateFactory.getClientHandshakeCertificates())
                .createParameterizer(NetworkEndpoint.connectTcp(address));
        cipher.ifPresent(c -> parameterizer.setEnabledCipherSuites(List.of(CipherSuite.fromJavaName(c))));
        parameterizer.setServerNames(Collections.singletonList(clientSniHostName));
        return parameterizer.build();
    }

    public OldOldSocketPair oldOld(Optional<String> cipher) throws InterruptedException {
        NetworkServer server = NetworkServer.bindTcp(new InetSocketAddress(0 /* find free port */));
        AtomicReference<TlsEndpoint> tlsServer = new AtomicReference<>();
        var thread = new Thread(() -> {
            tlsServer.set(createTlsServerEndpoint(cipher, server.accept()));
            server.close();
        });
        thread.start();
        TlsEndpoint client = createTlsClientEndpoint(cipher, server.getLocalAddress(), clientSniHostName);
        thread.join();
        return new OldOldSocketPair(client, tlsServer.get());
    }

    public OldIoSocketPair oldIo(Optional<String> cipher) throws InterruptedException {
        NetworkServer networkServer = NetworkServer.builder().useNio(false)
                .bindTcp(new InetSocketAddress(0 /* find free port */));
        AtomicReference<NetworkEndpoint> serverEndpoint = new AtomicReference<>();
        AtomicReference<TlsEndpoint> tlsServer = new AtomicReference<>();
        var thread = new Thread(() -> {
            serverEndpoint.set(networkServer.accept());
            networkServer.close();
            final var parameterizer = ServerTlsEndpoint.builder(nameOpt ->
                            handshakeCertificatesFactory(certificateFactory.getServerHandshakeCertificates(), nameOpt))
                    .createParameterizer(serverEndpoint.get());
            fixedCipherServerSslEngineCustomizer(cipher, parameterizer);
            tlsServer.set(parameterizer.build());
        });
        thread.start();
        TlsEndpoint client = createTlsClientEndpoint(cipher, networkServer.getLocalAddress(), clientSniHostName);
        thread.join();
        return new OldIoSocketPair(client, new SocketGroup(tlsServer.get(), serverEndpoint.get()));
    }

    public IoOldSocketPair ioOld(Optional<String> cipher) throws InterruptedException {
        NetworkServer server = NetworkServer.bindTcp(new InetSocketAddress(0 /* find free port */));
        InetSocketAddress address = server.getLocalAddress();
        AtomicReference<TlsEndpoint> tlsServer = new AtomicReference<>();
        var thread = new Thread(() -> {
            tlsServer.set(createTlsServerEndpoint(cipher, server.accept()));
            server.close();
        });
        thread.start();
        NetworkEndpoint encryptedEndpoint = NetworkEndpoint.connectTcp(address);
        final var parameterizer = ClientTlsEndpoint.builder(certificateFactory.getClientHandshakeCertificates())
                .createParameterizer(encryptedEndpoint);
        customizeClientSslEngine(parameterizer, cipher);
        TlsEndpoint client = parameterizer.build();
        thread.join();
        return new IoOldSocketPair(new SocketGroup(client, encryptedEndpoint), tlsServer.get());
    }

    public SocketPair ioIo(
            Optional<String> cipher,
            Optional<ChunkSizeConfig> chunkSizeConfig,
            boolean waitForCloseConfirmation) {
        return ioIoN(cipher, 1, chunkSizeConfig, waitForCloseConfirmation)
                .get(0);
    }

    public List<SocketPair> ioIoN(
            Optional<String> cipher,
            int qtty,
            Optional<ChunkSizeConfig> chunkSizeConfig,
            boolean waitForCloseConfirmation) {
        try (NetworkServer server = NetworkServer.bindTcp(new InetSocketAddress(0 /* find free port */))) {
            int chosenPort = server.getLocalAddress().getPort();
            InetSocketAddress address = new InetSocketAddress(localhost, chosenPort);
            List<SocketPair> pairs = new ArrayList<>();
            for (int i = 0; i < qtty; i++) {
                AtomicReference<SocketGroup> serverPair = new AtomicReference<>();
                var thread = new Thread(() -> {
                    NetworkEndpoint rawServerEndpoint = server.accept();
                    Endpoint plainServerEndpoint;
                    if (chunkSizeConfig.isPresent()) {
                        Optional<Integer> internalSize = chunkSizeConfig.get().serverChunkSize.internalSize;
                        if (internalSize.isPresent()) {
                            plainServerEndpoint = new ChunkingEndpoint(rawServerEndpoint, internalSize.get());
                        } else {
                            plainServerEndpoint = rawServerEndpoint;
                        }
                    } else {
                        plainServerEndpoint = rawServerEndpoint;
                    }
                    final var parameterizer = ServerTlsEndpoint.builder(nameOpt ->
                                    handshakeCertificatesFactory(certificateFactory.getServerHandshakeCertificates(),
                                            nameOpt))
                            .waitForCloseConfirmation(waitForCloseConfirmation)
                            .createParameterizer(plainServerEndpoint);
                    fixedCipherServerSslEngineCustomizer(cipher, parameterizer);

                    final TlsEndpoint serverTlsEndpoint = parameterizer.build();
                    serverPair.set(new SocketGroup(serverTlsEndpoint, rawServerEndpoint));
                });
                thread.start();

                NetworkEndpoint rawClient = NetworkEndpoint.connectTcp(address);
                Endpoint plainClientEndpoint;
                if (chunkSizeConfig.isPresent()) {
                    Optional<Integer> internalSize = chunkSizeConfig.get().clientChuckSize.internalSize;
                    if (internalSize.isPresent()) {
                        plainClientEndpoint = new ChunkingEndpoint(rawClient, internalSize.get());
                    } else {
                        plainClientEndpoint = rawClient;
                    }
                } else {
                    plainClientEndpoint = rawClient;
                }
                final var parameterizer = ClientTlsEndpoint.builder(certificateFactory.getClientHandshakeCertificates())
                        .waitForCloseConfirmation(waitForCloseConfirmation)
                        .createParameterizer(plainClientEndpoint);
                customizeClientSslEngine(parameterizer, cipher);

                TlsEndpoint clientTlsEndpoint = parameterizer.build();
                SocketGroup clientPair = new SocketGroup(clientTlsEndpoint, rawClient);

                thread.join();
                LOGGER.info("Socket pair created");

                pairs.add(new SocketPair(clientPair, serverPair.get()));
            }
            return pairs;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public OldIoSocketPair oldNio(Optional<String> cipher) throws InterruptedException {
        NetworkServer server = NetworkServer.bindTcp(new InetSocketAddress(localhost, 0 /* find free port */));
        AtomicReference<NetworkEndpoint> encryptedEndpoint = new AtomicReference<>();
        AtomicReference<TlsEndpoint> tlsServer = new AtomicReference<>();
        var thread = new Thread(() -> {
            encryptedEndpoint.set(server.accept());
            server.close();
            final var parameterizer = ServerTlsEndpoint.builder(nameOpt ->
                            handshakeCertificatesFactory(certificateFactory.getServerHandshakeCertificates(), nameOpt))
                    .createParameterizer(encryptedEndpoint.get());
            fixedCipherServerSslEngineCustomizer(cipher, parameterizer);

            tlsServer.set(parameterizer.build());
        });
        thread.start();
        TlsEndpoint client = createTlsClientEndpoint(cipher, server.getLocalAddress(), clientSniHostName);
        thread.join();
        return new OldIoSocketPair(client, new SocketGroup(tlsServer.get(), encryptedEndpoint.get()));
    }

    public IoOldSocketPair nioOld(Optional<String> cipher) throws InterruptedException {
        NetworkServer server = NetworkServer.bindTcp(new InetSocketAddress(0 /* find free port */));
        InetSocketAddress address = server.getLocalAddress();
        int chosenPort = address.getPort();
        AtomicReference<TlsEndpoint> tlsServer = new AtomicReference<>();
        var thread = new Thread(() -> {
            tlsServer.set(createTlsServerEndpoint(cipher, server.accept()));
            server.close();
        });
        thread.start();
        NetworkEndpoint encryptedEndpoint = NetworkEndpoint.connectTcp(address);
        final var parameterizer = ClientTlsEndpoint.builder(certificateFactory.getClientHandshakeCertificates())
                .createParameterizer(encryptedEndpoint);
        customizeClientSslEngine(parameterizer, cipher);

        TlsEndpoint client = parameterizer.build();

        thread.join();
        return new IoOldSocketPair(new SocketGroup(client, encryptedEndpoint), tlsServer.get());
    }

    public SocketPair nioNio(
            Optional<String> cipher,
            Optional<ChunkSizeConfig> chunkSizeConfig,
            boolean waitForCloseConfirmation) {
        return ioIoN(cipher, 1, chunkSizeConfig, waitForCloseConfirmation)
                .get(0);
    }
}
