/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.network.Proxy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;

public final class RealSocksProxy extends AbstractProxy implements Proxy.Socks {
    private final int version;

    public RealSocksProxy(final @NonNull InetSocketAddress address,
                          final int version,
                          final @Nullable String username,
                          final char @Nullable [] password) {
        super(address, username, password);
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
