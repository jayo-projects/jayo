/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.network;

import jayo.internal.network.AbstractProxy;
import jayo.internal.network.RealHttpProxy;
import jayo.internal.network.RealSocksProxy;
import org.jspecify.annotations.NonNull;

import java.net.InetSocketAddress;
import java.util.Objects;

public sealed interface Proxy permits AbstractProxy, Proxy.Http, Proxy.Socks {
    static @NonNull Socks socks5(final @NonNull InetSocketAddress address) {
        Objects.requireNonNull(address);
        return new RealSocksProxy(address, 5, null, null);
    }

    static @NonNull Socks socks5(final @NonNull InetSocketAddress address,
                                 final @NonNull String username,
                                 final char @NonNull [] password) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        return new RealSocksProxy(address, 5, username, password);
    }

    static @NonNull Socks socks4(final @NonNull InetSocketAddress address) {
        Objects.requireNonNull(address);
        // a username is required for Socks V4, that's why we default it to ""
        return new RealSocksProxy(address, 4, "", null);
    }

    static @NonNull Socks socks4(final @NonNull InetSocketAddress address, final @NonNull String username) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(username);
        return new RealSocksProxy(address, 4, username, null);
    }

    static @NonNull Http http(final @NonNull InetSocketAddress address) {
        Objects.requireNonNull(address);
        return new RealHttpProxy(address, null, null);
    }

    static @NonNull Http http(final @NonNull InetSocketAddress address,
                              final @NonNull String username,
                              final char @NonNull [] password) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        return new RealHttpProxy(address, username, password);
    }

    /**
     * @return a host string containing either an actual host name or a numeric IP address.
     */
    @NonNull
    String getHost();

    int getPort();

    sealed interface Socks extends Proxy permits RealSocksProxy {
        int getVersion();
    }

    sealed interface Http extends Proxy permits RealHttpProxy {
    }
}