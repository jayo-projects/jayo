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
import jayo.network.NetworkEndpoint;
import jayo.network.NetworkServer;

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

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private NetworkServer networkServer;
    private final Set<NetworkEndpoint> openNetworkEndpoints = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void start() {
        networkServer = NetworkServer.bindTcp(new InetSocketAddress(0 /* find free port */));
        executor.execute(this::acceptSockets);
    }

    public void shutdown() {
        networkServer.close();
        executor.shutdown();
    }

    public Proxy proxy() {
        return new Proxy(Proxy.Type.SOCKS,
                InetSocketAddress.createUnresolved("localhost", networkServer.getLocalAddress().getPort()));
    }

    private void acceptSockets() {
        try {
            while (true) {
                final NetworkEndpoint from = networkServer.accept();
                openNetworkEndpoints.add(from);
                executor.execute(() -> handleClient(from));
            }
        } catch (JayoException e) {
            System.out.println("shutting down because of: " + e);
        } finally {
            for (NetworkEndpoint networkEndpoint : openNetworkEndpoints) {
                closeQuietly(networkEndpoint);
            }
        }
    }

    private void handleClient(final NetworkEndpoint client) {
        final Reader fromReader = client.getReader();
        final Writer fromWriter = client.getWriter();
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
                case ADDRESS_TYPE_DOMAIN_NAME -> InetAddress.getByName(fromReader.readString(fromReader.readByte()));
                default -> throw new JayoProtocolException("Unknown address type " + addressType);
            };
            int port = fromReader.readShort() & 0xffff;

            // Connect to the caller's specified host.
            final NetworkEndpoint toNetworkEndpoint = NetworkEndpoint.connectTcp(new InetSocketAddress(inetAddress, port));
            openNetworkEndpoints.add(toNetworkEndpoint);
            InetSocketAddress toNetworkEndpointAddress = toNetworkEndpoint.getLocalAddress();
            byte[] localAddress = toNetworkEndpointAddress.getAddress().getAddress();
            if (localAddress.length != 4) {
                throw new JayoProtocolException("Caller's specified host local address must be IPv4");
            }

            // Write the reply.
            fromWriter.writeByte(VERSION_5)
                    .writeByte(REPLY_SUCCEEDED)
                    .writeByte((byte) 0)
                    .writeByte(ADDRESS_TYPE_IPV4)
                    .write(localAddress)
                    .writeShort((short) toNetworkEndpointAddress.getPort())
                    .flush();

            // Connect readers to writers in both directions.
            final var toWriter = toNetworkEndpoint.getWriter();
            executor.execute(() -> transfer(client, fromReader, toWriter));
            final var toReader = toNetworkEndpoint.getReader();
            executor.execute(() -> transfer(toNetworkEndpoint, toReader, fromWriter));
        } catch (JayoException | IOException e) {
            closeQuietly(client);
            openNetworkEndpoints.remove(client);
            System.out.println("connect failed for " + client + ": " + e);
        }
    }

    /**
     * Read data from {@code reader} and write it to {@code writer}. This doesn't use {@link Writer#writeAllFrom(RawReader)}
     * because that method doesn't flush aggressively, and we need that.
     */
    private void transfer(NetworkEndpoint readerNetworkEndpoint, RawReader reader, RawWriter writer) {
        try {
            Buffer buffer = Buffer.create();
            for (long byteCount; (byteCount = reader.readAtMostTo(buffer, 8192L)) != -1; ) {
                writer.writeFrom(buffer, byteCount);
                writer.flush();
            }
        } catch (JayoClosedResourceException ignored) {
        } finally {
            closeQuietly(writer);
            closeQuietly(reader);
            closeQuietly(readerNetworkEndpoint);
            openNetworkEndpoints.remove(readerNetworkEndpoint);
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
            for (String line; (line = reader.readLine()) != null; ) {
                System.out.println(line);
            }
        }

        proxyServer.shutdown();
    }
}
