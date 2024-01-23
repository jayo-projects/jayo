/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
 *
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.samples;

import jayo.*;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A partial implementation of SOCKS Protocol Version 5.
 * See <a href="https://www.ietf.org/rfc/rfc1928.txt">RFC 1928</a>.
 */
public final class SocksProxyServer {
    private static final byte VERSION_5 = 5;
    private static final byte METHOD_NO_AUTHENTICATION_REQUIRED = 0;
    private static final byte ADDRESS_TYPE_IPV4 = 1;
    private static final byte ADDRESS_TYPE_DOMAIN_NAME = 3;
    private static final byte COMMAND_CONNECT = 1;
    private static final byte REPLY_SUCCEEDED = 0;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private ServerSocket serverSocket;
    private final Set<Socket> openSockets = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void start() throws IOException {
        serverSocket = new ServerSocket(0);
        executor.execute(this::acceptSockets);
    }

    public void shutdown() throws IOException {
        serverSocket.close();
        executor.shutdown();
    }

    public Proxy proxy() {
        return new Proxy(Proxy.Type.SOCKS,
                InetSocketAddress.createUnresolved("localhost", serverSocket.getLocalPort()));
    }

    private void acceptSockets() {
        try {
            while (true) {
                final Socket from = serverSocket.accept();
                openSockets.add(from);
                executor.execute(() -> handleSocket(from));
            }
        } catch (IOException e) {
            System.out.println("shutting down because of: " + e);
        } finally {
            for (Socket socket : openSockets) {
                closeQuietly(socket);
            }
        }
    }

    private void handleSocket(final Socket fromSocket) {
        final Source fromSource = Jayo.buffer(Jayo.source(fromSocket));
        final Sink fromSink = Jayo.buffer(Jayo.sink(fromSocket));
        try {
            // Read the hello.
            int socksVersion = fromSource.readByte();
            if (socksVersion != VERSION_5) {
                throw new ProtocolException();
            }
            int methodCount = fromSource.readByte();
            boolean foundSupportedMethod = false;
            for (int i = 0; i < methodCount; i++) {
                final var method = fromSource.readByte();
                foundSupportedMethod |= method == METHOD_NO_AUTHENTICATION_REQUIRED;
            }
            if (!foundSupportedMethod) {
                throw new ProtocolException();
            }

            // Respond to hello.
            fromSink.writeByte(VERSION_5)
                    .writeByte(METHOD_NO_AUTHENTICATION_REQUIRED)
                    .emit();

            // Read a command.
            final var version = fromSource.readByte();
            final var command = fromSource.readByte();
            final var reserved = fromSource.readByte();
            if (version != VERSION_5 || command != COMMAND_CONNECT || reserved != 0) {
                throw new ProtocolException();
            }

            // Read an address.
            final var addressType = fromSource.readByte();
            final var inetAddress = switch (addressType) {
                case ADDRESS_TYPE_IPV4 -> InetAddress.getByAddress(fromSource.readByteArray(4L));
                case ADDRESS_TYPE_DOMAIN_NAME -> InetAddress.getByName(fromSource.readUtf8(fromSource.readByte()));
                default -> throw new ProtocolException();
            };
            int port = fromSource.readShort() & 0xffff;

            // Connect to the caller's specified host.
            final Socket toSocket = new Socket(inetAddress, port);
            openSockets.add(toSocket);
            byte[] localAddress = toSocket.getLocalAddress().getAddress();
            if (localAddress.length != 4) {
                throw new ProtocolException();
            }

            // Write the reply.
            fromSink.writeByte(VERSION_5)
                    .writeByte(REPLY_SUCCEEDED)
                    .writeByte((byte) 0)
                    .writeByte(ADDRESS_TYPE_IPV4)
                    .write(localAddress)
                    .writeShort((short) toSocket.getLocalPort())
                    .flush();

            // Connect sources to sinks in both directions.
            final var toSink = Jayo.sink(toSocket);
            executor.execute(() -> transfer(fromSocket, fromSource, toSink));
            final var toSource = Jayo.source(toSocket);
            executor.execute(() -> transfer(toSocket, toSource, fromSink));
        } catch (IOException e) {
            closeQuietly(fromSocket);
            openSockets.remove(fromSocket);
            System.out.println("connect failed for " + fromSocket + ": " + e);
        }
    }

    /**
     * Read data from {@code source} and write it to {@code sink}. This doesn't use {@link Sink#transferFrom(RawSource)}
     * because that method doesn't flush aggressively, and we need that.
     */
    private void transfer(Socket sourceSocket, RawSource source, RawSink sink) {
        try {
            Buffer buffer = Buffer.create();
            for (long byteCount; (byteCount = source.readAtMostTo(buffer, 8192L)) != -1; ) {
                sink.write(buffer, byteCount);
                sink.flush();
            }
        } finally {
            closeQuietly(sink);
            closeQuietly(source);
            closeQuietly(sourceSocket);
            openSockets.remove(sourceSocket);
        }
    }

    private void closeQuietly(AutoCloseable c) {
        try {
            c.close();
        } catch (Exception ignored) {
        }
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        SocksProxyServer proxyServer = new SocksProxyServer();
        proxyServer.start();

        URL url = new URI("https://github.com/jayo-projects/jayo/tree/main/samples/src/main/resources/jayo.txt")
                .toURL();
        URLConnection connection = url.openConnection(proxyServer.proxy());
        try (final var source = Jayo.buffer(Jayo.source(connection.getInputStream()))) {
            for (String line; (line = source.readUtf8Line()) != null; ) {
                System.out.println(line);
            }
        }

        proxyServer.shutdown();
    }
}
