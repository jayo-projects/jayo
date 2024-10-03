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

import jayo.endpoints.Endpoint;
import jayo.endpoints.SocketChannelEndpoint;
import jayo.endpoints.SocketEndpoint;
import jayo.tls.TlsEndpoint;
import jayo.tls.helpers.SocketGroups.*;

import javax.crypto.Cipher;
import javax.net.ssl.*;
import java.io.IOException;
import java.net.*;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
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

    private final SSLSocketFactory sslSocketFactory;
    private final SSLServerSocketFactory sslServerSocketFactory;

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
        this.sslSocketFactory = sslContext.getSocketFactory();
        this.sslServerSocketFactory = sslContext.getServerSocketFactory();
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

    public SSLContext sslContextFactory(
            SNIServerName expectedName, SSLContext sslContext, SNIServerName name) {
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

    private SSLServerSocket createSslServerSocket(Optional<String> cipher) {
        try {
            SSLServerSocket serverSocket =
                    (SSLServerSocket) sslServerSocketFactory.createServerSocket(0 /* find free port */);
            cipher.ifPresent(c -> serverSocket.setEnabledCipherSuites(new String[]{c}));
            return serverSocket;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SSLSocket createSslSocket(Optional<String> cipher, InetAddress host, int port, String requestedHost) {
        try {
            SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket(host, port);
            cipher.ifPresent(c -> socket.setEnabledCipherSuites(new String[]{c}));
            return socket;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public OldOldSocketPair oldOld(Optional<String> cipher) {
        try {
            SSLServerSocket serverSocket = createSslServerSocket(cipher);
            int chosenPort = serverSocket.getLocalPort();
            SSLSocket client = createSslSocket(cipher, localhost, chosenPort, serverName);
            SSLParameters sslParameters = client.getSSLParameters(); // returns a value object
            sslParameters.setServerNames(Collections.singletonList(clientSniHostName));
            client.setSSLParameters(sslParameters);
            SSLSocket server = (SSLSocket) serverSocket.accept();
            serverSocket.close();
            return new OldOldSocketPair(client, server);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public OldIoSocketPair oldIo(Optional<String> cipher) {
        try {
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(localhost, 0 /* find free port */));
            int chosenPort = ((InetSocketAddress) serverSocket.getLocalSocketAddress()).getPort();
            SSLSocket client = createSslSocket(cipher, localhost, chosenPort, serverName);
            SSLParameters sslParameters = client.getSSLParameters(); // returns a value object
            sslParameters.setServerNames(Collections.singletonList(clientSniHostName));
            client.setSSLParameters(sslParameters);
            Socket rawServer = serverSocket.accept();
            Endpoint encryptedEndpoint = SocketEndpoint.from(rawServer);
            serverSocket.close();
            TlsEndpoint server = TlsEndpoint.serverBuilder(encryptedEndpoint, nameOpt ->
                            sslContextFactory(clientSniHostName, sslContext, nameOpt))
                    .engineFactory(x -> fixedCipherServerSslEngineFactory(cipher, x))
                    .build();
            return new OldIoSocketPair(client, new SocketGroup(server, rawServer));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public IoOldSocketPair ioOld(Optional<String> cipher) {
        try {
            SSLServerSocket serverSocket = createSslServerSocket(cipher);
            int chosenPort = serverSocket.getLocalPort();
            InetSocketAddress address = new InetSocketAddress(localhost, chosenPort);
            Socket rawClient = new Socket();
            rawClient.connect(address);
            Endpoint encryptedEndpoint = SocketEndpoint.from(rawClient);
            SSLSocket server = (SSLSocket) serverSocket.accept();
            serverSocket.close();
            TlsEndpoint client = TlsEndpoint.clientBuilder(encryptedEndpoint, createClientSslEngine(cipher, chosenPort))
                    .build();
            return new IoOldSocketPair(new SocketGroup(client, rawClient), server);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(localhost, 0 /* find free port */));
            int chosenPort = ((InetSocketAddress) serverSocket.getLocalSocketAddress()).getPort();
            InetSocketAddress address = new InetSocketAddress(localhost, chosenPort);
            List<SocketPair> pairs = new ArrayList<>();
            for (int i = 0; i < qtty; i++) {
                Socket rawClient = new Socket();
                rawClient.connect(address);

                Endpoint rawClientEndpoint = SocketEndpoint.from(rawClient);
                Endpoint plainClientEndpoint;
                if (chunkSizeConfig.isPresent()) {
                    Optional<Integer> internalSize = chunkSizeConfig.get().clientChuckSize.internalSize;
                    if (internalSize.isPresent()) {
                        plainClientEndpoint = new ChunkingEndpoint(rawClientEndpoint, internalSize.get());
                    } else {
                        plainClientEndpoint = rawClientEndpoint;
                    }
                } else {
                    plainClientEndpoint = rawClientEndpoint;
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

                Socket rawServer = serverSocket.accept();
                Endpoint rawServerEndpoint = SocketEndpoint.from(rawServer);
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
                                    sslContextFactory(clientSniHostName, sslContext, nameOpt))
                            .engineFactory(ctx -> fixedCipherServerSslEngineFactory(cipher, ctx));
                }
                TlsEndpoint serverTlsEndpoint = serverTlsEndpointBuilder
                        .waitForCloseConfirmation(waitForCloseConfirmation)
                        .build();
                SocketGroup serverPair = new SocketGroup(serverTlsEndpoint, rawServer);

                pairs.add(new SocketPair(clientPair, serverPair));
            }
            return pairs;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public OldIoSocketPair oldNio(Optional<String> cipher) {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(localhost, 0 /* find free port */));
            int chosenPort = ((InetSocketAddress) serverSocketChannel.getLocalAddress()).getPort();
            SSLSocket client = createSslSocket(cipher, localhost, chosenPort, serverName);
            SSLParameters sslParameters = client.getSSLParameters(); // returns a value object
            sslParameters.setServerNames(Collections.singletonList(clientSniHostName));
            client.setSSLParameters(sslParameters);
            SocketChannel rawServer = serverSocketChannel.accept();
            Endpoint encryptedEndpoint = SocketChannelEndpoint.from(rawServer);
            serverSocketChannel.close();
            TlsEndpoint server = TlsEndpoint.serverBuilder(encryptedEndpoint, nameOpt ->
                            sslContextFactory(clientSniHostName, sslContext, nameOpt))
                    .engineFactory(x -> fixedCipherServerSslEngineFactory(cipher, x))
                    .build();
            return new OldIoSocketPair(client, new SocketGroup(server, rawServer.socket()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public IoOldSocketPair nioOld(Optional<String> cipher) {
        try {
            SSLServerSocket serverSocket = createSslServerSocket(cipher);
            int chosenPort = serverSocket.getLocalPort();
            InetSocketAddress address = new InetSocketAddress(localhost, chosenPort);
            SocketChannel rawClient = SocketChannel.open(address);
            Endpoint encryptedEndpoint = SocketChannelEndpoint.from(rawClient);
            SSLSocket server = (SSLSocket) serverSocket.accept();
            serverSocket.close();
            TlsEndpoint client = TlsEndpoint.clientBuilder(encryptedEndpoint, createClientSslEngine(cipher, chosenPort))
                    .build();
            return new IoOldSocketPair(new SocketGroup(client, rawClient.socket()), server);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
            serverSocketChannel.bind(new InetSocketAddress(localhost, 0 /* find free port */));
            int chosenPort = ((InetSocketAddress) serverSocketChannel.getLocalAddress()).getPort();
            InetSocketAddress address = new InetSocketAddress(localhost, chosenPort);
            List<SocketPair> pairs = new ArrayList<>();
            for (int i = 0; i < qtty; i++) {
                SocketChannel rawClient = SocketChannel.open(address);

                Endpoint rawClientEndpoint = SocketChannelEndpoint.from(rawClient);
                Endpoint plainClientEndpoint;
                if (chunkSizeConfig.isPresent()) {
                    Optional<Integer> internalSize = chunkSizeConfig.get().clientChuckSize.internalSize;
                    if (internalSize.isPresent()) {
                        plainClientEndpoint = new ChunkingEndpoint(rawClientEndpoint, internalSize.get());
                    } else {
                        plainClientEndpoint = rawClientEndpoint;
                    }
                } else {
                    plainClientEndpoint = rawClientEndpoint;
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
                SocketGroup clientPair = new SocketGroup(clientTlsEndpoint, rawClient.socket());

                SocketChannel rawServer = serverSocketChannel.accept();
                Endpoint rawServerEndpoint = SocketChannelEndpoint.from(rawServer);
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
                                    sslContextFactory(clientSniHostName, sslContext, nameOpt))
                            .engineFactory(ctx -> fixedCipherServerSslEngineFactory(cipher, ctx));
                }
                TlsEndpoint serverTlsEndpoint = serverTlsEndpointBuilder
                        .waitForCloseConfirmation(waitForCloseConfirmation)
                        .build();
                SocketGroup serverPair = new SocketGroup(serverTlsEndpoint, rawServer.socket());

                pairs.add(new SocketPair(clientPair, serverPair));
            }
            return pairs;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
