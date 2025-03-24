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
import jayo.tls.ClientTlsEndpoint;
import jayo.tls.ServerHandshakeCertificates;
import jayo.tls.ServerTlsEndpoint;
import jayo.tls.TlsEndpoint;
import jayo.tls.helpers.SocketGroups.*;

import javax.crypto.Cipher;
import javax.net.ssl.*;
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

/**
 * Create pairs of connected sockets (using the loopback interface). Additionally, all the raw (non-encrypted) socket
 * channel are wrapped with a chunking decorator that partitions the bytesProduced of any read or write operation.
 */
public class SocketPairFactory {
    private static final Logger LOGGER = Logger.getLogger(SocketPairFactory.class.getName());

    public static final String NULL_CIPHER = "null-cipher";

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

    public SSLEngine fixedCipherServerSslEngineFactory(Optional<String> cipher, SSLContext sslContext) {
        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);
        cipher.ifPresent(c -> engine.setEnabledCipherSuites(new String[]{c}));
        return engine;
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

    public SSLEngine createClientSslEngine(SSLContext sslContext, Optional<String> cipher, int peerPort) {
        SSLEngine engine = sslContext.createSSLEngine(serverName, peerPort);
        engine.setUseClientMode(true);
        cipher.ifPresent(c -> engine.setEnabledCipherSuites(new String[]{c}));
        SSLParameters sslParams = engine.getSSLParameters(); // returns a value object
        sslParams.setEndpointIdentificationAlgorithm("HTTPS");
        sslParams.setServerNames(Collections.singletonList(clientSniHostName));
        engine.setSSLParameters(sslParams);
        return engine;
    }

    private TlsEndpoint createTlsServerEndpoint(Optional<String> cipher, NetworkEndpoint endpoint) {
        return ServerTlsEndpoint.builder(certificateFactory.getServerHandshakeCertificates())
                .engineFactory(sslContext -> {
                    SSLEngine engine = sslContext.createSSLEngine();
                    engine.setUseClientMode(false);
                    cipher.ifPresent(c -> engine.setEnabledCipherSuites(new String[]{c}));
                    return engine;
                }).build(endpoint);
    }

    private TlsEndpoint createTlsClientEndpoint(Optional<String> cipher,
                                                InetSocketAddress address,
                                                SNIHostName clientSniHostName) {
        return ClientTlsEndpoint.builder(certificateFactory.getClientHandshakeCertificates())
                .engineFactory(context -> {
                    SSLEngine engine = context.createSSLEngine();
                    engine.setUseClientMode(true);
                    cipher.ifPresent(c -> engine.setEnabledCipherSuites(new String[]{c}));
                    SSLParameters sslParameters = engine.getSSLParameters(); // returns a value object
                    sslParameters.setServerNames(Collections.singletonList(clientSniHostName));
                    engine.setSSLParameters(sslParameters);
                    return engine;
                })
                .build(NetworkEndpoint.connectTcp(address));
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
            tlsServer.set(ServerTlsEndpoint.builder(nameOpt ->
                            handshakeCertificatesFactory(certificateFactory.getServerHandshakeCertificates(), nameOpt))
                    .engineFactory(x -> fixedCipherServerSslEngineFactory(cipher, x))
                    .build(serverEndpoint.get()));
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
        TlsEndpoint client = ClientTlsEndpoint.builder(certificateFactory.getClientHandshakeCertificates())
                .engineFactory(context -> createClientSslEngine(context, cipher, address.getPort()))
                .build(encryptedEndpoint);
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
                    final TlsEndpoint serverTlsEndpoint;
                    if (cipher.equals(Optional.of(NULL_CIPHER))) {
                        serverTlsEndpoint = ServerTlsEndpoint.builder(certificateFactory.getServerHandshakeCertificates())
                                .engineFactory(c -> new NullSslEngine())
                                .waitForCloseConfirmation(waitForCloseConfirmation)
                                .build(plainServerEndpoint);
                    } else {
                        serverTlsEndpoint = ServerTlsEndpoint.builder(nameOpt ->
                                        handshakeCertificatesFactory(certificateFactory.getServerHandshakeCertificates(),
                                                nameOpt))
                                .waitForCloseConfirmation(waitForCloseConfirmation)
                                .engineFactory(ctx -> fixedCipherServerSslEngineFactory(cipher, ctx))
                                .build(plainServerEndpoint);

                    }
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
                TlsEndpoint clientTlsEndpoint = ClientTlsEndpoint.builder(certificateFactory.getClientHandshakeCertificates())
                        .engineFactory(context -> {
                            SSLEngine clientEngine;
                            if (cipher.equals(Optional.of(NULL_CIPHER))) {
                                clientEngine = new NullSslEngine();
                            } else {
                                clientEngine = createClientSslEngine(context, cipher, chosenPort);
                            }
                            return clientEngine;
                        })
                        .waitForCloseConfirmation(waitForCloseConfirmation)
                        .build(plainClientEndpoint);
                SocketGroup clientPair = new SocketGroup(clientTlsEndpoint, rawClient);

                thread.join();

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
            tlsServer.set(ServerTlsEndpoint.builder(nameOpt ->
                            handshakeCertificatesFactory(certificateFactory.getServerHandshakeCertificates(), nameOpt))
                    .engineFactory(x -> fixedCipherServerSslEngineFactory(cipher, x))
                    .build(encryptedEndpoint.get()));
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
        TlsEndpoint client = ClientTlsEndpoint.builder(certificateFactory.getClientHandshakeCertificates())
                .engineFactory(context -> createClientSslEngine(context, cipher, chosenPort))
                .build(encryptedEndpoint);
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
