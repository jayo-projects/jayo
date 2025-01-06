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
import jayo.network.NetworkEndpoint;
import jayo.network.NetworkServer;
import jayo.tls.TlsEndpoint;
import jayo.tls.helpers.SocketGroups.*;

import javax.crypto.Cipher;
import javax.net.ssl.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

    public final SSLContext sslContext;
    private final String serverName;
    public final SNIHostName clientSniHostName;
    private final SNIMatcher expectedSniHostName;
    public final InetAddress localhost;

    public SocketPairFactory(SSLContext sslContext, String serverName) {
        this.sslContext = sslContext;
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

    public SocketPairFactory(SSLContext sslContext) {
        this(sslContext, SslContextFactory.certificateCommonName);
    }

    public SSLEngine fixedCipherServerSslEngineFactory(Optional<String> cipher, SSLContext sslContext) {
        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(false);
        cipher.ifPresent(c -> engine.setEnabledCipherSuites(new String[]{c}));
        return engine;
    }

    public SSLContext sslContextFactory(SSLContext sslContext, SNIServerName name) {
        if (name != null) {
            LOGGER.warning(() -> "ContextFactory, requested name: " + name);
            if (!expectedSniHostName.matches(name)) {
                throw new IllegalArgumentException(String.format("Received SNI $n does not match %s", serverName));
            }
            return sslContext;
        } else {
            throw new IllegalArgumentException("SNI expected");
        }
    }

    public SSLEngine createClientSslEngine(Optional<String> cipher, int peerPort) {
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
        return TlsEndpoint.serverBuilder(endpoint, sslContext)
                .engineFactory(sslContext -> {
                    SSLEngine engine = sslContext.createSSLEngine();
                    engine.setUseClientMode(false);
                    cipher.ifPresent(c -> engine.setEnabledCipherSuites(new String[]{c}));
                    return engine;
                })
                .build();
    }

    private TlsEndpoint createTlsClientEndpoint(Optional<String> cipher, SocketAddress address) {
        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(true);
        cipher.ifPresent(c -> engine.setEnabledCipherSuites(new String[]{c}));
        return TlsEndpoint.clientBuilder(NetworkEndpoint.connectTcp(address), engine).build();
    }

    public OldOldSocketPair oldOld(Optional<String> cipher) {
        NetworkServer server = NetworkServer.bindTcp(new InetSocketAddress(0 /* find free port */));
        TlsEndpoint client = createTlsClientEndpoint(cipher, server.getLocalAddress());
        assert client.getSslEngine() != null;
        SSLParameters sslParameters = client.getSslEngine().getSSLParameters(); // returns a value object
        sslParameters.setServerNames(Collections.singletonList(clientSniHostName));
        client.getSslEngine().setSSLParameters(sslParameters);
        TlsEndpoint tlsServer = createTlsServerEndpoint(cipher, server.accept());
        server.close();
        return new OldOldSocketPair(client, tlsServer);
    }

    public OldIoSocketPair oldIo(Optional<String> cipher) {
        NetworkServer networkServer = NetworkServer.builderForIO().bind(new InetSocketAddress(0 /* find free port */));
        TlsEndpoint client = createTlsClientEndpoint(cipher, networkServer.getLocalAddress());
        assert client.getSslEngine() != null;
        SSLParameters sslParameters = client.getSslEngine().getSSLParameters(); // returns a value object
        sslParameters.setServerNames(Collections.singletonList(clientSniHostName));
        client.getSslEngine().setSSLParameters(sslParameters);
        NetworkEndpoint serverEndpoint = networkServer.accept();
        networkServer.close();
        TlsEndpoint server = TlsEndpoint.serverBuilder(serverEndpoint, nameOpt ->
                        sslContextFactory(sslContext, nameOpt))
                .engineFactory(x -> fixedCipherServerSslEngineFactory(cipher, x))
                .build();
        return new OldIoSocketPair(client, new SocketGroup(server, serverEndpoint));
    }

    public IoOldSocketPair ioOld(Optional<String> cipher) {
        NetworkServer server = NetworkServer.bindTcp(new InetSocketAddress(0 /* find free port */));
        InetSocketAddress address = (InetSocketAddress) server.getLocalAddress();
        NetworkEndpoint encryptedEndpoint = NetworkEndpoint.connectTcp(address);
        TlsEndpoint tlsServer = createTlsServerEndpoint(cipher, server.accept());
        server.close();
        TlsEndpoint client =
                TlsEndpoint.clientBuilder(encryptedEndpoint, createClientSslEngine(cipher, address.getPort()))
                        .build();
        return new IoOldSocketPair(new SocketGroup(client, encryptedEndpoint), tlsServer);
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
            int chosenPort = ((InetSocketAddress) server.getLocalAddress()).getPort();
            InetSocketAddress address = new InetSocketAddress(localhost, chosenPort);
            List<SocketPair> pairs = new ArrayList<>();
            for (int i = 0; i < qtty; i++) {
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
                SSLEngine clientEngine;
                if (cipher.equals(Optional.of(NULL_CIPHER))) {
                    clientEngine = new NullSslEngine();
                } else {
                    clientEngine = createClientSslEngine(cipher, chosenPort);
                }
                TlsEndpoint clientTlsEndpoint = TlsEndpoint.clientBuilder(plainClientEndpoint, clientEngine)
                        .waitForCloseConfirmation(waitForCloseConfirmation)
                        .build();
                SocketGroup clientPair = new SocketGroup(clientTlsEndpoint, rawClient);

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
                TlsEndpoint.ServerBuilder serverTlsEndpointBuilder;
                if (cipher.equals(Optional.of(NULL_CIPHER))) {
                    serverTlsEndpointBuilder = TlsEndpoint.serverBuilder(plainServerEndpoint, new NullSslContext());
                } else {
                    serverTlsEndpointBuilder = TlsEndpoint.serverBuilder(plainServerEndpoint, nameOpt ->
                                    sslContextFactory(sslContext, nameOpt))
                            .engineFactory(ctx -> fixedCipherServerSslEngineFactory(cipher, ctx));
                }
                TlsEndpoint serverTlsEndpoint = serverTlsEndpointBuilder
                        .waitForCloseConfirmation(waitForCloseConfirmation)
                        .build();
                SocketGroup serverPair = new SocketGroup(serverTlsEndpoint, rawServerEndpoint);

                pairs.add(new SocketPair(clientPair, serverPair));
            }
            return pairs;
        }
    }

    public OldIoSocketPair oldNio(Optional<String> cipher) {
        NetworkServer server = NetworkServer.bindTcp(new InetSocketAddress(localhost, 0 /* find free port */));
        TlsEndpoint client = createTlsClientEndpoint(cipher, server.getLocalAddress());
        assert client.getSslEngine() != null;
        SSLParameters sslParameters = client.getSslEngine().getSSLParameters(); // returns a value object
        sslParameters.setServerNames(Collections.singletonList(clientSniHostName));
        client.getSslEngine().setSSLParameters(sslParameters);
        NetworkEndpoint encryptedEndpoint = server.accept();
        server.close();
        TlsEndpoint tlsServer = TlsEndpoint.serverBuilder(encryptedEndpoint, nameOpt ->
                        sslContextFactory(sslContext, nameOpt))
                .engineFactory(x -> fixedCipherServerSslEngineFactory(cipher, x))
                .build();
        return new OldIoSocketPair(client, new SocketGroup(tlsServer, encryptedEndpoint));
    }

    public IoOldSocketPair nioOld(Optional<String> cipher) {
        NetworkServer server = NetworkServer.bindTcp(new InetSocketAddress(0 /* find free port */));
        InetSocketAddress address = (InetSocketAddress) server.getLocalAddress();
        int chosenPort = address.getPort();
        NetworkEndpoint encryptedEndpoint = NetworkEndpoint.connectTcp(address);
        TlsEndpoint tlsServer = createTlsServerEndpoint(cipher, server.accept());
        server.close();
        TlsEndpoint client = TlsEndpoint.clientBuilder(encryptedEndpoint, createClientSslEngine(cipher, chosenPort))
                .build();
        return new IoOldSocketPair(new SocketGroup(client, encryptedEndpoint), tlsServer);
    }

    public SocketPair nioNio(
            Optional<String> cipher,
            Optional<ChunkSizeConfig> chunkSizeConfig,
            boolean waitForCloseConfirmation) {
        return ioIoN(cipher, 1, chunkSizeConfig, waitForCloseConfirmation)
                .get(0);
    }

    public List<SocketPair> nioNioN(
            Optional<String> cipher,
            int qtty,
            Optional<ChunkSizeConfig> chunkSizeConfig,
            boolean waitForCloseConfirmation) {
        try (NetworkServer server = NetworkServer.bindTcp(new InetSocketAddress(localhost, 0 /* find free port */))) {
            int chosenPort = ((InetSocketAddress) server.getLocalAddress()).getPort();
            InetSocketAddress address = new InetSocketAddress(localhost, chosenPort);
            List<SocketPair> pairs = new ArrayList<>();
            for (int i = 0; i < qtty; i++) {
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
                SSLEngine clientEngine;
                if (cipher.equals(Optional.of(NULL_CIPHER))) {
                    clientEngine = new NullSslEngine();
                } else {
                    clientEngine = createClientSslEngine(cipher, chosenPort);
                }
                TlsEndpoint clientTlsEndpoint = TlsEndpoint.clientBuilder(plainClientEndpoint, clientEngine)
                        .waitForCloseConfirmation(waitForCloseConfirmation)
                        .build();
                SocketGroup clientPair = new SocketGroup(clientTlsEndpoint, rawClient);

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
                TlsEndpoint.ServerBuilder serverTlsEndpointBuilder;
                if (cipher.equals(Optional.of(NULL_CIPHER))) {
                    serverTlsEndpointBuilder = TlsEndpoint.serverBuilder(plainServerEndpoint, new NullSslContext());
                } else {
                    serverTlsEndpointBuilder = TlsEndpoint.serverBuilder(plainServerEndpoint, nameOpt ->
                                    sslContextFactory(sslContext, nameOpt))
                            .engineFactory(ctx -> fixedCipherServerSslEngineFactory(cipher, ctx));
                }
                TlsEndpoint serverTlsEndpoint = serverTlsEndpointBuilder
                        .waitForCloseConfirmation(waitForCloseConfirmation)
                        .build();
                SocketGroup serverPair = new SocketGroup(serverTlsEndpoint, rawServerEndpoint);

                pairs.add(new SocketPair(clientPair, serverPair));
            }
            return pairs;
        }
    }
}
