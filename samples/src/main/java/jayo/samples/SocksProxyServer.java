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
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.samples;

import jayo.*;
import jayo.endpoints.Endpoint;
import jayo.endpoints.SocketEndpoint;
import jayo.exceptions.JayoException;
import jayo.exceptions.JayoProtocolException;

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
                executor.execute(() -> handleSocket(Endpoint.from(from)));
            }
        } catch (IOException e) {
            System.out.println("shutting down because of: " + e);
        } finally {
            for (Socket socket : openSockets) {
                closeQuietly(socket);
            }
        }
    }

    private void handleSocket(final SocketEndpoint fromSocketEndpoint) {
        final Reader fromReader = Jayo.buffer(fromSocketEndpoint.getReader());
        final Writer fromWriter = Jayo.buffer(fromSocketEndpoint.getWriter());
        try {
            // Read the hello.
            final int socksVersion = fromReader.readByte();
            if (socksVersion != VERSION_5) {
                throw new JayoProtocolException("Socks version must be 5, is " + socksVersion);
            }
            int methodCount = fromReader.readByte();
            boolean foundSupportedMethod = false;
            for (int i = 0; i < methodCount; i++) {
                final var method = fromReader.readByte();
                foundSupportedMethod |= method == METHOD_NO_AUTHENTICATION_REQUIRED;
            }
            if (!foundSupportedMethod) {
                throw new JayoProtocolException("Method 'No authentication required' is not supported");
            }

            // Respond to hello.
            fromWriter.writeByte(VERSION_5)
                    .writeByte(METHOD_NO_AUTHENTICATION_REQUIRED)
                    .emit();

            // Read a command.
            final var version = fromReader.readByte();
            final var command = fromReader.readByte();
            final var reserved = fromReader.readByte();
            if (version != VERSION_5 || command != COMMAND_CONNECT || reserved != 0) {
                throw new JayoProtocolException("Failed to read a command");
            }

            // Read an address.
            final var addressType = fromReader.readByte();
            final var inetAddress = switch (addressType) {
                case ADDRESS_TYPE_IPV4 -> InetAddress.getByAddress(fromReader.readByteArray(4L));
                case ADDRESS_TYPE_DOMAIN_NAME ->
                        InetAddress.getByName(fromReader.readUtf8String(fromReader.readByte()));
                default -> throw new JayoProtocolException("Unknown address type " + addressType);
            };
            int port = fromReader.readShort() & 0xffff;

            // Connect to the caller's specified host.
            final Socket toSocket = new Socket(inetAddress, port);
            openSockets.add(toSocket);
            byte[] localAddress = toSocket.getLocalAddress().getAddress();
            if (localAddress.length != 4) {
                throw new JayoProtocolException("Caller's specified host local address must be IPv4");
            }

            // Write the reply.
            fromWriter.writeByte(VERSION_5)
                    .writeByte(REPLY_SUCCEEDED)
                    .writeByte((byte) 0)
                    .writeByte(ADDRESS_TYPE_IPV4)
                    .write(localAddress)
                    .writeShort((short) toSocket.getLocalPort())
                    .flush();

            final var toSocketEndpoint = Endpoint.from(toSocket);
            // Connect readers to writers in both directions.
            final var toWriter = toSocketEndpoint.getWriter();
            executor.execute(() -> transfer(fromSocketEndpoint, fromReader, toWriter));
            final var toReader = toSocketEndpoint.getReader();
            executor.execute(() -> transfer(toSocketEndpoint, toReader, fromWriter));
        } catch (JayoException | IOException e) {
            closeQuietly(fromSocketEndpoint);
            openSockets.remove(fromSocketEndpoint.getUnderlying());
            System.out.println("connect failed for " + fromSocketEndpoint + ": " + e);
        }
    }

    /**
     * Read data from {@code reader} and write it to {@code writer}. This doesn't use {@link Writer#transferFrom(RawReader)}
     * because that method doesn't flush aggressively, and we need that.
     */
    private void transfer(SocketEndpoint readerSocketEndpoint, RawReader reader, RawWriter writer) {
        try {
            Buffer buffer = Buffer.create();
            for (long byteCount; (byteCount = reader.readAtMostTo(buffer, 8192L)) != -1; ) {
                writer.write(buffer, byteCount);
                writer.flush();
            }
        } finally {
            closeQuietly(writer);
            closeQuietly(reader);
            closeQuietly(readerSocketEndpoint);
            openSockets.remove(readerSocketEndpoint.getUnderlying());
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

        URL url = new URI(
                "https://raw.githubusercontent.com/jayo-projects/jayo/main/samples/src/main/resources/jayo.txt")
                .toURL();
        URLConnection connection = url.openConnection(proxyServer.proxy());
        try (final var reader = Jayo.buffer(Jayo.reader(connection.getInputStream()))) {
            for (String line; (line = reader.readUtf8Line()) != null; ) {
                System.out.println(line);
            }
        }

        proxyServer.shutdown();
    }
}
