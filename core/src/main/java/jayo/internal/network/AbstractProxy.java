/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.network.Proxy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.regex.Pattern;

public sealed abstract class AbstractProxy implements Proxy permits RealHttpProxy, RealSocksProxy {
    private static final Pattern NON_LATIN1_PATTERN = Pattern.compile("[^\\x00-\\xFF]+");

    private final @NonNull String host;
    private final int port;
    final @Nullable String username;
    final @Nullable SecureString password;

    AbstractProxy(final @NonNull InetSocketAddress address,
                  final @Nullable String username,
                  final char @Nullable [] password) {
        assert address != null;

        host = host(address);
        port = address.getPort();

        if (username != null) {
            if (username.length() > 255) {
                throw new IllegalArgumentException("Username too long, must be less than 256 characters");
            }
            if (NON_LATIN1_PATTERN.matcher(username).find()) {
                throw new IllegalArgumentException("Invalid username, must be ISO_8859_1 compatible");
            }
        }
        if (password != null) {
            if (password.length > 255) {
                throw new IllegalArgumentException("Password too long, must be less than 256 characters");
            }
            for (final char charAt : password) {
                if (charAt > 255) {
                    throw new IllegalArgumentException("Invalid password, must be ISO_8859_1 compatible");
                }
            }
        }
        this.username = username;
        this.password = (password != null) ? new SecureString(password) : null;
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
