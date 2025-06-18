/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.network.Proxy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;

public final class RealHttpProxy extends AbstractProxy implements Proxy.Http {
    public RealHttpProxy(final @NonNull InetSocketAddress address,
                         final @Nullable String username,
                         final char @Nullable [] password) {
        super(address, username, password);
    }

    @Override
    public boolean equals(final @Nullable Object other) {
        if (!(other instanceof RealHttpProxy that)) {
            return false;
        }

        return getHost().equals(that.getHost())
                && getPort() == that.getPort();
    }

    @Override
    public int hashCode() {
        var result = getHost().hashCode();
        result = 31 * result + getPort();
        return result;
    }

    @Override
    public String toString() {
        return "HTTP @ " + getHost() + ":" + getPort();
    }
}
