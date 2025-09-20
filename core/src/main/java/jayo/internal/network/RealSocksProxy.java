/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.network.Proxy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class RealSocksProxy extends AbstractProxy implements Proxy.Socks {
    private static final Pattern NON_LATIN1_PATTERN = Pattern.compile("[^\\x00-\\xFF]+");

    private final int version;

    public RealSocksProxy(final @NonNull InetSocketAddress address,
                          final int version,
                          final @Nullable String username,
                          final @Nullable String password) {
        super(address, username, password, StandardCharsets.ISO_8859_1);
        if (username != null) {
            if (username.length() > 255) {
                throw new IllegalArgumentException("Username too long, must be less than 256 characters");
            }
            if (NON_LATIN1_PATTERN.matcher(username).find()) {
                throw new IllegalArgumentException("Invalid username, must be ISO_8859_1 compatible");
            }
        }
        if (password != null) {
            if (password.length() > 255) {
                throw new IllegalArgumentException("Password too long, must be less than 256 characters");
            }
            if (NON_LATIN1_PATTERN.matcher(password).find()) {
                throw new IllegalArgumentException("Invalid password, must be ISO_8859_1 compatible");
            }
        }
        this.version = version;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public boolean equals(final @Nullable Object other) {
        if (!(other instanceof RealSocksProxy that)) {
            return false;
        }

        return version == that.version
                && getHost().equals(that.getHost())
                && getPort() == that.getPort();
    }

    @Override
    public int hashCode() {
        var result = version;
        result = 31 * result + getHost().hashCode();
        result = 31 * result + getPort();
        return result;
    }

    @Override
    public String toString() {
        return "Socks v" + version + " @ " + getHost() + ":" + getPort();
    }
}
