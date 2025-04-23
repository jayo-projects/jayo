/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.*;
import jayo.network.JayoSocketException;
import jayo.network.NetworkEndpoint;
import jayo.network.Proxy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.*;
import java.nio.charset.StandardCharsets;

public final class SocksNetworkEndpoint implements NetworkEndpoint {
    static final byte SOCKS_V5 = 5;
    static final byte SOCKS_V4 = 4;

    static final byte METHOD_NO_AUTHENTICATION_REQUIRED = 0;
    //    private static final byte METHOD_GSSAPI = 1;
    static final byte METHOD_USER_PASSWORD = 2;
    private static final byte NO_METHODS = -1;

    static final byte COMMAND_CONNECT = 1;
//    private static final byte COMMAND_BIND = 2;
//    private static final byte COMMAND_UDP_ASSOCIATION = 3;

    static final byte ADDRESS_TYPE_IPV4 = 1;
    static final byte ADDRESS_TYPE_DOMAIN_NAME = 3;
    static final byte ADDRESS_TYPE_IPV6 = 4;

    static final byte REQUEST_OK = 0;
    private static final byte GENERAL_FAILURE = 1;
    private static final byte NOT_ALLOWED = 2;
    private static final byte NETWORK_UNREACHABLE = 3;
    private static final byte HOST_UNREACHABLE = 4;
    private static final byte CONNECTION_REFUSED = 5;
    private static final byte TTL_EXPIRED = 6;
    private static final byte COMMAND_NOT_SUPPORTED = 7;
    private static final byte ADDRESS_TYPE_NOT_SUPPORTED = 8;

    private final @NonNull RealSocksProxy proxy;
    private final @NonNull NetworkEndpoint delegate;
    private final @NonNull Reader reader;
    private final @NonNull Writer writer;
    private final @NonNull InetSocketAddress peerAddress;

    SocksNetworkEndpoint(final @NonNull RealSocksProxy proxy,
                         final @NonNull NetworkEndpoint delegate,
                         final @NonNull InetSocketAddress peerAddress) {
        assert proxy != null;
        assert delegate != null;
        assert peerAddress != null;

        this.proxy = proxy;
        this.delegate = delegate;
        this.peerAddress = peerAddress;

        reader = delegate.getReader();
        writer = delegate.getWriter();

        // initialize the proxy communication
        try {
            switch (proxy.getVersion()) {
                case SOCKS_V5 -> connectV5();
                case SOCKS_V4 -> {
                    // SOCKS V4 doesn't support DOMAIN type socket addresses
                    if (peerAddress.isUnresolved()) {
                        throw new JayoUnknownHostException(peerAddress.toString());
                    }
                    if (!(peerAddress.getAddress() instanceof Inet4Address)) {
                        throw new JayoSocketException("SOCKS4 : SOCKS V4 only supports IPv4 socket address");
                    }
                    connectV4();
                }
                default -> throw new IllegalArgumentException("Unexpected proxy version: " + proxy.getVersion());
            }
        } catch (JayoEOFException ignored) {
            throw new JayoSocketException("Reply from SOCKS server badly formatted");
        }
    }

    private void connectV5() {
        writer.writeByte(SOCKS_V5)
                .writeByte((byte) 2)
                .writeByte(METHOD_NO_AUTHENTICATION_REQUIRED)
                .writeByte(METHOD_USER_PASSWORD)
                .emit();

        final var data = new byte[4];
        reader.readTo(data, 0, 2);
        if (data[0] != SOCKS_V5) {
            throw new JayoSocketException("SOCKS5 : Your proxy is not a SOCKS V5 proxy");
        }
        if (data[1] == NO_METHODS) {
            throw new JayoSocketException("SOCKS5 : No acceptable authentication methods");
        }
        if (!authenticate(data[1])) {
            throw new JayoSocketException("SOCKS5 : Authentication failed");
        }
        writer.writeByte(SOCKS_V5)
                .writeByte(COMMAND_CONNECT)
                .writeByte((byte) 0);

        // transmit peer IP address or hostname if unresolved
        if (peerAddress.isUnresolved()) {
            writer.writeByte(ADDRESS_TYPE_DOMAIN_NAME)
                    .writeByte((byte) peerAddress.getHostName().length())
                    .write(peerAddress.getHostName().getBytes(StandardCharsets.ISO_8859_1));
        } else if (peerAddress.getAddress() instanceof Inet6Address) {
            writer.writeByte(ADDRESS_TYPE_IPV6)
                    .write(peerAddress.getAddress().getAddress());
        } else {
            writer.writeByte(ADDRESS_TYPE_IPV4)
                    .write(peerAddress.getAddress().getAddress());
        }
        // transmit peer port
        writer.writeByte((byte) ((peerAddress.getPort() >> 8) & 0xff))
                .writeByte((byte) ((peerAddress.getPort()) & 0xff))
                .emit();

        reader.readTo(data);
        final var exception = switch (data[1]) {
            case REQUEST_OK -> {
                // success
                final var addressLength = switch (data[3]) {
                    case ADDRESS_TYPE_DOMAIN_NAME -> (reader.readByte() & 0xFF);
                    case ADDRESS_TYPE_IPV6 -> 16;
                    case ADDRESS_TYPE_IPV4 -> 4;
                    default -> throw new JayoSocketException("SOCKS5 : Reply from SOCKS server contains wrong code");
                };
                reader.skip(addressLength + 2);
                yield null;
            }
            case GENERAL_FAILURE -> new JayoSocketException("SOCKS5 : SOCKS server general failure");
            case NOT_ALLOWED -> new JayoSocketException("SOCKS5 : Connection not allowed by ruleset");
            case NETWORK_UNREACHABLE -> new JayoSocketException("SOCKS5 : Network unreachable");
            case HOST_UNREACHABLE -> new JayoSocketException("SOCKS5 : Host unreachable");
            case CONNECTION_REFUSED -> new JayoSocketException("SOCKS5 : Connection refused");
            case TTL_EXPIRED -> new JayoSocketException("SOCKS5 : TTL expired");
            case COMMAND_NOT_SUPPORTED -> new JayoSocketException("SOCKS5 : Command not supported");
            case ADDRESS_TYPE_NOT_SUPPORTED -> new JayoSocketException("SOCKS5 : address type not supported");
            default -> new JayoSocketException("SOCKS5 : Reply from SOCKS server contains bad status");
        };
        if (exception != null) {
            reader.close();
            writer.close();
            throw exception;
        }
    }

    private boolean authenticate(final byte method) {
        // No Authentication -> all good
        if (method == METHOD_NO_AUTHENTICATION_REQUIRED) {
            return true;
        }

        if (method == METHOD_USER_PASSWORD) {
            byte[] username = null;
            byte[] password = null; // encoded in ISO_8859_1
            if (proxy.username != null) {
                // user defined credentials
                username = proxy.username.getBytes(StandardCharsets.ISO_8859_1);
                if (proxy.password != null) {
                    password = proxy.password.decrypt();
                }
            } else {
                // try to prompt the JVM User for credentials
                final InetAddress address;
                try {
                    address = InetAddress.getByName(proxy.getHost());
                } catch (UnknownHostException e) {
                    throw JayoException.buildJayoException(e);
                }
                final var passwordAuthentication = Authenticator.requestPasswordAuthentication(
                        proxy.getHost(), address, proxy.getPort(), "SOCKS5", "SOCKS authentication", null);
                if (passwordAuthentication != null) {
                    username = passwordAuthentication.getUserName().getBytes(StandardCharsets.ISO_8859_1);
                    password = new String(passwordAuthentication.getPassword()).getBytes(StandardCharsets.ISO_8859_1);
                }
            }

            if (username == null) {
                return false;
            }

            writer.writeByte((byte) 1)
                    .writeByte((byte) username.length)
                    .write(username);
            if (password != null) {
                writer.writeByte((byte) password.length)
                        .write(password);
            } else {
                writer.writeByte((byte) 0);
            }
            writer.emit();

            final var data = new byte[2];
            var authFailed = false;
            try {
                reader.readTo(data);
                if (data[1] != 0) {
                    authFailed = true;
                }
            } catch (JayoEOFException ignored) {
                authFailed = true;
            }

            if (authFailed) {
                // https://www.ietf.org/rfc/rfc1929.txt : the connection must be closed if authentication fails
                writer.close();
                reader.close();
                return false;
            }

            // User/password auth success
            return true;
        }
        return false;
    }

    private void connectV4() {
        assert proxy.username != null;

        writer.writeByte(SOCKS_V4)
                .writeByte(COMMAND_CONNECT)
                // transmit peer port
                .writeByte((byte) ((peerAddress.getPort() >> 8) & 0xff))
                .writeByte((byte) ((peerAddress.getPort()) & 0xff))
                // transmit peer IPv4 address
                .write(peerAddress.getAddress().getAddress())
                // write username
                .write(proxy.username.getBytes(StandardCharsets.ISO_8859_1))
                .writeByte((byte) 0)
                .emit();

        final var data = new byte[8];
        reader.readTo(data);
        if (data[0] != 0 && data[0] != SOCKS_V4) {
            throw new JayoSocketException("SOCKS4 : Reply from SOCKS server has bad version");
        }
        final var exception = switch (data[1]) {
            // Success
            case 90 -> null;
            case 91 -> new JayoSocketException("SOCKS4 : SOCKS request rejected");
            case 92 -> new JayoSocketException("SOCKS4 : SOCKS server couldn't reach destination");
            case 93 -> new JayoSocketException("SOCKS4 : Authentication failed");
            default -> new JayoSocketException("SOCKS4 : Reply from SOCKS server contains bad status");
        };
        if (exception != null) {
            reader.close();
            writer.close();
            throw exception;
        }
    }

    @Override
    public @NonNull InetSocketAddress getLocalAddress() {
        return delegate.getLocalAddress();
    }

    @Override
    public @NonNull InetSocketAddress getPeerAddress() {
        return peerAddress;
    }

    @Override
    public Proxy.@NonNull Socks getProxy() {
        return proxy;
    }

    @Override
    public <T> @Nullable T getOption(final @NonNull SocketOption<T> name) {
        return delegate.getOption(name);
    }

    @Override
    public @NonNull Reader getReader() {
        return reader;
    }

    @Override
    public @NonNull Writer getWriter() {
        return writer;
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public @NonNull Object getUnderlying() {
        return delegate.getUnderlying();
    }
}
