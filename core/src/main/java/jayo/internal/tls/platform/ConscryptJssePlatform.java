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
import org.conscrypt.Conscrypt;
import org.conscrypt.ConscryptHostnameVerifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;

/**
 * Platform using Conscrypt (<a href="https://conscrypt.org">conscrypt</a>) if installed as the first Security Provider.
 * <p>
 * Requires org.conscrypt:conscrypt-openjdk-uber >= 2.1.0 on the classpath.
 */
public final class ConscryptJssePlatform extends JdkJssePlatform {
    public static boolean isSupported() {
        try {
            // Trigger an early exception over a fatal error, prefer a RuntimeException over Error.
            Class.forName("org.conscrypt.Conscrypt$Version", false,
                    ConscryptJssePlatform.class.getClassLoader());

            // Bump this version if we ever have a binary incompatibility
            return Conscrypt.isAvailable() && atLeastVersion(2, 1, 0);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            return false;
        }
    }

    public static @Nullable ConscryptJssePlatform buildIfSupported() {
        return isSupported() ? new ConscryptJssePlatform() : null;
    }

    static boolean atLeastVersion(int major, int minor, int patch) {
        final var conscryptVersion = Conscrypt.version();

        if (conscryptVersion.major() != major) {
            return conscryptVersion.major() > major;
        }

        if (conscryptVersion.minor() != minor) {
            return conscryptVersion.minor() > minor;
        }

        return conscryptVersion.patch() >= patch;
    }

    private final @NonNull Provider provider;

    private ConscryptJssePlatform() {
        provider = Conscrypt.newProvider();
    }

    // See release notes https://groups.google.com/forum/#!forum/conscrypt for version differences
    @Override
    @NonNull
    SSLContext newSSLContext(final @NonNull String version) throws NoSuchAlgorithmException {
        // supports TLSv1.3 by default (version api is >= 1.4.0)
        return SSLContext.getInstance(version, provider);
    }

    @Override
    public @NonNull X509TrustManager getDefaultTrustManager() {
        final var x509TrustManager = super.getDefaultTrustManager();
        // Disabled because Jayo will run anyway
        Conscrypt.setHostnameVerifier(x509TrustManager, DisabledHostnameVerifier.getInstance());
        return x509TrustManager;
    }

    @Override
    public void configureTlsExtensions(final @NonNull SSLEngine sslEngine, final @NonNull List<Protocol> protocols) {
        Objects.requireNonNull(sslEngine);
        Objects.requireNonNull(protocols);

        if (Conscrypt.isConscrypt(sslEngine)) {
            // Enable session tickets.
            Conscrypt.setUseSessionTickets(sslEngine, true);

            // Enable ALPN.
            final var names = alpnProtocolNames(protocols);
            Conscrypt.setApplicationProtocols(sslEngine, names.toArray(String[]::new));
        } else {
            super.configureTlsExtensions(sslEngine, protocols);
        }
    }

    @Override
    public @Nullable String getSelectedProtocol(final @NonNull SSLEngine sslEngine) {
        Objects.requireNonNull(sslEngine);

        if (Conscrypt.isConscrypt(sslEngine)) {
            return Conscrypt.getApplicationProtocol(sslEngine);
        }
        // else
        return super.getSelectedProtocol(sslEngine);
    }

    private static final class DisabledHostnameVerifier implements ConscryptHostnameVerifier {
        private static ConscryptHostnameVerifier INSTANCE;

        private DisabledHostnameVerifier() {
        }

        private static ConscryptHostnameVerifier getInstance() {
            if (INSTANCE == null) {
                INSTANCE = new DisabledHostnameVerifier();
            }

            return INSTANCE;
        }

        @Override
        public boolean verify(X509Certificate[] x509Certificates, String s, SSLSession sslSession) {
            return true;
        }
    }
}
