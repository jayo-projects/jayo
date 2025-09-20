/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.network.Proxy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;

public sealed abstract class AbstractProxy implements Proxy permits RealHttpProxy, RealSocksProxy {
    private final @NonNull String host;
    private final int port;

    final @Nullable String username;
    final @Nullable SecurePassword password;
    final @NonNull Charset charset;

    AbstractProxy(final @NonNull InetSocketAddress address,
                  final @Nullable String username,
                  final @Nullable String password,
                  final @NonNull Charset charset) {
        assert address != null;
        assert charset != null;

        host = host(address);
        port = address.getPort();

        this.username = username;
        this.password = (password != null) ? new SecurePassword(password, charset) : null;
        this.charset = charset;
    }

    /**
     * @return a host string containing either an actual host name or a numeric IP address.
     * @implNote this implementation is extracted from the {@code RouteSelector} class of OkHttp.
     */
    private static @NonNull String host(final @NonNull InetSocketAddress inetSocketAddress) {
        assert inetSocketAddress != null;

        // The InetSocketAddress was specified with a string (either a numeric IP or a host name). If it is a name, all
        // IPs for that name should be tried. If it is an IP address, only that IP address should be tried.
        if (inetSocketAddress.getAddress() == null) {
            return inetSocketAddress.getHostString();
        }

        // The InetSocketAddress has a specific address: we should only try that address. Therefore, we return the
        // address and ignore any host name that may be available.
        return inetSocketAddress.getAddress().getHostAddress();
    }

    @Override
    public final @NonNull String getHost() {
        return host;
    }

    @Override
    public final int getPort() {
        return port;
    }
}
