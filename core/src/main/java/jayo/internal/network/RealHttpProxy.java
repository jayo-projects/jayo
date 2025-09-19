/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import jayo.network.HttpProxyAuth;
import jayo.network.Proxy;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class RealHttpProxy extends AbstractProxy implements Proxy.Http {
    public RealHttpProxy(final @NonNull InetSocketAddress address,
                         final @Nullable String username,
                         final char @Nullable [] password,
                         final @Nullable Charset charset) {
        super(address, username, password, (charset != null) ? charset : StandardCharsets.UTF_8);
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

    @Override
    public @Nullable HttpProxyAuth getAuthentication() {
        if (username == null) { // either all auth parameters are set, or none
            return null;
        }

        return new Auth();
    }

    public final class Auth implements HttpProxyAuth {
        @Override
        public @NonNull String getUsername() {
            assert username != null;
            return username;
        }

        @Override
        public char @NonNull [] getPassword() {
            assert password != null;
            return new String(password.decrypt(), charset).toCharArray();
        }

        @Override
        public @NonNull Charset getCharset() {
            return charset;
        }
    }
}
