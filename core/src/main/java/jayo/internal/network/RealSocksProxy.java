/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.network.SocksProxy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.regex.Pattern;

public final class RealSocksProxy implements SocksProxy {
    private static final Pattern NON_LATIN1_PATTERN = Pattern.compile("[^\\x00-\\xFF]+");

    final @NonNull String hostname;
    final int port;
    private final int version;
    final @Nullable String username;
    final @Nullable SecureString password;

    public RealSocksProxy(final @NonNull InetSocketAddress address,
                          final int version,
                          final @Nullable String username,
                          final char @Nullable [] password) {
        assert address != null;

        this.hostname = address.getHostString();
        this.port = address.getPort();
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
        this.version = version;
        this.username = username;
        this.password = (password != null) ? new SecureString(password) : null;
    }

    @Override
    public final @NonNull String getHostname() {
        return hostname;
    }

    @Override
    public final int getPort() {
        return port;
    }

    @Override
    public int getVersion() {
        return version;
    }
}
