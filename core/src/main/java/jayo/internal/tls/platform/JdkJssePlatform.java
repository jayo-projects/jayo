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

import jayo.tls.Protocol;
import jayo.tls.JssePlatform;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

/**
 * The default Security Provider included in the JDK.
 */
public sealed class JdkJssePlatform implements JssePlatform permits BouncyCastleJssePlatform, ConscryptJssePlatform {
    @Override
    public @NonNull SSLContext newSSLContext() {
        try {
            return SSLContext.getInstance("TLS");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public @NonNull X509TrustManager platformTrustManager() {
        final TrustManagerFactory factory;
        try {
            factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
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
    public void configureTlsExtensions(
            final @NonNull SSLEngine sslEngine,
            final @Nullable String hostname,
            final @NonNull List<Protocol> protocols) {
        assert sslEngine != null;
        assert protocols != null;

        final var sslParameters = sslEngine.getSSLParameters();
        // Enable ALPN.
        final var names = alpnProtocolNames(protocols);
        sslParameters.setApplicationProtocols(names.toArray(String[]::new));
        sslEngine.setSSLParameters(sslParameters);
    }

    public @Nullable String getSelectedProtocol(final @NonNull SSLEngine sslEngine) {
        assert sslEngine != null;

        try {
            final var protocol = sslEngine.getApplicationProtocol();
            // SSLEngine.getApplicationProtocol() returns "" if application protocols values will not be used. Observed
            // if you didn't specify SSLParameters.setApplicationProtocols
            if (protocol == null || protocol.isEmpty()) {
                return null;
            }
            return protocol;
        } catch (UnsupportedOperationException ignored) {
            // https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLEngine.html#getApplicationProtocol()
            return null;
        }
    }

    static @NonNull List<String> alpnProtocolNames(final @NonNull List<Protocol> protocols) {
        assert protocols != null;

        return protocols.stream()
                .filter(p -> !Protocol.HTTP_1_0.equals(p))
                .map(Object::toString)
                .toList();
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName();
    }
}
