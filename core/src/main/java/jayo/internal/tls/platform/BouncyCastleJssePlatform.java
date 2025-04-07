/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from OkHttp (https://github.com/square/okhttp), original copyright is below
 *
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.internal.tls.platform;

import org.bouncycastle.jsse.BCSSLEngine;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.*;
import java.util.Arrays;
import java.util.Objects;

/**
 * Platform using BouncyCastle if installed as the first Security Provider.
 * <p>
 * Requires org.bouncycastle:bctls-jdk18on on the classpath.
 */
public final class BouncyCastleJssePlatform extends JdkJssePlatform {
    public static boolean isSupported() {
        try {
            // Trigger an early exception over a fatal error, prefer a RuntimeException over Error.
            Class.forName("org.bouncycastle.jsse.provider.BouncyCastleJsseProvider", false,
                    BouncyCastleJssePlatform.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static @Nullable BouncyCastleJssePlatform buildIfSupported(boolean isFips) {
        return isSupported() ? new BouncyCastleJssePlatform(isFips) : null;
    }

    private final @NonNull Provider provider;

    private BouncyCastleJssePlatform(final boolean isFips) {
        provider = isFips ? new BouncyCastleJsseProvider("fips:BCFIPS") : new BouncyCastleJsseProvider();
    }

    @Override
    public @NonNull SSLContext newSSLContext() {
        try {
            return SSLContext.getInstance("TLS", provider);
        } catch (NoSuchAlgorithmException e) {
            // The system has no TLS. Just give up.
            throw new AssertionError("'TLS' is not supported: " + e.getMessage(), e);
        }
    }

    @Override
    public @NonNull X509TrustManager getDefaultTrustManager() {
        final TrustManagerFactory factory;
        try {
            factory = TrustManagerFactory.getInstance("PKIX", BouncyCastleJsseProvider.PROVIDER_NAME);
            factory.init((KeyStore) null);
        } catch (NoSuchAlgorithmException | KeyStoreException | NoSuchProviderException e) {
            throw new IllegalStateException(e);
        }
        final var trustManagers = factory.getTrustManagers();
        if (trustManagers.length != 1
                || !(trustManagers[0] instanceof X509TrustManager x509TrustManager)) {
            throw new IllegalStateException("Unexpected default trust managers: " + Arrays.toString(trustManagers));
        }
        return x509TrustManager;
    }

    @Override
    public @Nullable String getSelectedProtocol(final @NonNull SSLEngine sslEngine) {
        Objects.requireNonNull(sslEngine);

        if (sslEngine instanceof BCSSLEngine bouncyCastleSSLEngine) {
            final var protocol = bouncyCastleSSLEngine.getApplicationProtocol();
            // Handles both un-configured and none selected.
            if (protocol == null || protocol.isEmpty()) {
                return null;
            }
            return protocol;
        }
        // else
        return super.getSelectedProtocol(sslEngine);
    }
}
