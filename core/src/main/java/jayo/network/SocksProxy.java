/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.network;

import jayo.internal.network.RealSocksProxy;
import org.jspecify.annotations.NonNull;

import java.net.InetSocketAddress;
import java.util.Objects;

public sealed interface SocksProxy permits RealSocksProxy {

    static @NonNull SocksProxy socks5(final @NonNull InetSocketAddress address) {
        Objects.requireNonNull(address);
        return new RealSocksProxy(address, 5, null, null);
    }

    static @NonNull SocksProxy socks5(final @NonNull InetSocketAddress address,
                                 final @NonNull String username,
                                 final char @NonNull [] password) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        return new RealSocksProxy(address, 5, username, password);
    }

    static @NonNull SocksProxy socks4(final @NonNull InetSocketAddress address) {
        Objects.requireNonNull(address);
        // username is required for Socks V4, that's why we default it to ""
        return new RealSocksProxy(address, 4, "", null);
    }

    static @NonNull SocksProxy socks4(final @NonNull InetSocketAddress address, final @NonNull String username) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(username);
        return new RealSocksProxy(address, 4, username, null);
    }

    @NonNull
    String getHostname();

    int getPort();

    int getVersion();
}
