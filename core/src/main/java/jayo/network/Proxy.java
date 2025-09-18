/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.network;

import jayo.internal.network.AbstractProxy;
import jayo.internal.network.RealHttpProxy;
import jayo.internal.network.RealSocksProxy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.util.Objects;

/**
 * Represents a network proxy, which is an intermediary server that forwards requests and responses between a client
 * and a server. Subtypes are {@linkplain Proxy.Socks SOCKS} and  {@linkplain Proxy.Http HTTP}.
 */
public sealed interface Proxy permits AbstractProxy, Proxy.Http, Proxy.Socks {
    /**
     * @param address the socket address of the proxy server.
     * @return a SOCKS 5 proxy.
     */
    static @NonNull Socks socks5(final @NonNull InetSocketAddress address) {
        Objects.requireNonNull(address);
        return new RealSocksProxy(address, 5, null, null);
    }

    /**
     * @param address the socket address of the proxy server.
     * @param username the username to use for proxy authentication.
     * @param password the password to use for proxy authentication.
     * @return a SOCKS 5 proxy that requires authentication.
     */
    static @NonNull Socks socks5(final @NonNull InetSocketAddress address,
                                 final @NonNull String username,
                                 final char @NonNull [] password) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        return new RealSocksProxy(address, 5, username, password);
    }

    /**
     * @param address the socket address of the proxy server.
     * @return a SOCKS 4 proxy.
     */
    static @NonNull Socks socks4(final @NonNull InetSocketAddress address) {
        Objects.requireNonNull(address);
        // a username is required for Socks V4, that's why we default it to ""
        return new RealSocksProxy(address, 4, "", null);
    }

    /**
     * @param address the socket address of the proxy server.
     * @param username the username to use for proxy authentication.
     * @return a SOCKS 4 proxy that requires authentication.
     */
    static @NonNull Socks socks4(final @NonNull InetSocketAddress address, final @NonNull String username) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(username);
        return new RealSocksProxy(address, 4, username, null);
    }

    /**
     * @param address the socket address of the proxy server.
     * @return an HTTP proxy.
     */
    static @NonNull Http http(final @NonNull InetSocketAddress address) {
        Objects.requireNonNull(address);
        return new RealHttpProxy(address, null, null);
    }

    /**
     * @param address the socket address of the proxy server.
     * @param username the username to use for proxy authentication.
     * @param password the password to use for proxy authentication.
     * @return an HTTP proxy that requires authentication.
     */
    static @NonNull Http http(final @NonNull InetSocketAddress address,
                              final @NonNull String username,
                              final char @NonNull [] password) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        return new RealHttpProxy(address, username, password);
    }

    /**
     * @return the proxy host string containing either an actual host name or a numeric IP address.
     */
    @NonNull
    String getHost();

    /**
     * @return the proxy port.
     */
    int getPort();

    /**
     * A SOCKS proxy.
     */
    sealed interface Socks extends Proxy permits RealSocksProxy {
        int getVersion();
    }

    /**
     * An HTTP proxy.
     */
    sealed interface Http extends Proxy permits RealHttpProxy {
        @Nullable
        PasswordAuthentication getAuthentication();
    }
}