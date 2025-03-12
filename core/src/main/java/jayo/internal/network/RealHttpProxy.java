/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.network.Proxy;
import org.jspecify.annotations.NonNull;

import java.net.InetSocketAddress;

public final class RealHttpProxy implements Proxy.Http {
    private final @NonNull String hostname;
    private final int port;

    public RealHttpProxy(final @NonNull InetSocketAddress address) {
        assert address != null;

        this.hostname = address.getHostString();
        this.port = address.getPort();
    }

    @Override
    public @NonNull String getHostname() {
        return hostname;
    }

    @Override
    public int getPort() {
        return port;
    }
}
