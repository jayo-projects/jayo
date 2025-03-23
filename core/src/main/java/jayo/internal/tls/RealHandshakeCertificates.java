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

package jayo.internal.tls;

import jayo.tls.ClientHandshakeCertificates;
import jayo.tls.HeldCertificate;
import jayo.tls.JssePlatform;
import jayo.tls.ServerHandshakeCertificates;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;

public final class RealHandshakeCertificates
        implements ClientHandshakeCertificates, ServerHandshakeCertificates {
    private final @NonNull X509KeyManager keyManager;
    private final @NonNull X509TrustManager trustManager;

    private RealHandshakeCertificates(final @NonNull X509KeyManager keyManager,
                                      final @NonNull X509TrustManager trustManager) {
        assert keyManager != null;
        assert trustManager != null;

        this.keyManager = keyManager;
        this.trustManager = trustManager;
    }

    @Override
    public @NonNull X509KeyManager getKeyManager() {
        return keyManager;
    }

    @Override
    public @NonNull X509TrustManager getTrustManager() {
        return trustManager;
    }

    @NonNull
    SSLContext sslContext() {
        final var sslContext = JssePlatform.get().newSSLContext();
        try {
            sslContext.init(new KeyManager[]{keyManager}, new TrustManager[]{trustManager}, new SecureRandom());
        } catch (KeyManagementException e) {
            throw new IllegalStateException("A key management exception occurred during init of the SSLContext", e);
        }
        return sslContext;
    }

    public static sealed abstract class Builder {
        @Nullable
        HeldCertificate heldCertificate = null;
        @NonNull
        X509Certificate @Nullable [] intermediates = null;
        final @NonNull Collection<X509Certificate> trustedCertificates = new LinkedHashSet<>();
        final @NonNull Collection<String> insecureHosts = new LinkedHashSet<>();

        @NonNull
        RealHandshakeCertificates buildInternal() {
            if (heldCertificate != null && heldCertificate.getKeyPair().getPrivate().getFormat() == null) {
                throw new IllegalArgumentException("KeyStoreException : unable to support unencodable private key");
            }

            final var keyManager = TlsUtils.newKeyManager(
                    null,
                    heldCertificate,
                    (intermediates != null) ? intermediates : new X509Certificate[0]);
            final var trustManager =
                    TlsUtils.newTrustManager(null, trustedCertificates, insecureHosts);
            return new RealHandshakeCertificates(keyManager, trustManager);
        }
    }

    public static final class ClientBuilder extends Builder implements ClientHandshakeCertificates.Builder {
        private boolean addPlatformTrustedCertificates = true;

        @Override
        public @NonNull ClientBuilder heldCertificate(final @NonNull HeldCertificate heldCertificate,
                                                      final @NonNull X509Certificate @NonNull ... intermediates) {
            Objects.requireNonNull(heldCertificate);

            this.heldCertificate = heldCertificate;
            this.intermediates = Arrays.copyOf(intermediates, intermediates.length); // Defensive copy.
            return this;
        }

        @Override
        public @NonNull ClientBuilder addPlatformTrustedCertificates(final boolean addPlatformTrustedCertificates) {
            this.addPlatformTrustedCertificates = addPlatformTrustedCertificates;
            return this;
        }

        @Override
        public @NonNull ClientBuilder addTrustedCertificate(final @NonNull X509Certificate certificate) {
            Objects.requireNonNull(certificate);
            this.trustedCertificates.add(certificate);
            return this;
        }

        @Override
        public @NonNull ClientBuilder addInsecureHost(final @NonNull String hostname) {
            Objects.requireNonNull(hostname);

            this.insecureHosts.add(hostname);
            return this;
        }

        @Override
        public @NonNull ClientHandshakeCertificates build() {
            if (addPlatformTrustedCertificates) {
                final var platformTrustManager = JssePlatform.get().getDefaultTrustManager();
                Collections.addAll(trustedCertificates, platformTrustManager.getAcceptedIssuers());
            }

            return buildInternal();
        }
    }

    public static final class ServerBuilder extends Builder implements ServerHandshakeCertificates.Builder {
        public ServerBuilder(final @NonNull HeldCertificate heldCertificate,
                             final @NonNull X509Certificate @NonNull [] intermediates) {
            assert heldCertificate != null;
            assert intermediates != null;

            this.heldCertificate = heldCertificate;
            this.intermediates = intermediates;
        }

        @Override
        public ServerHandshakeCertificates.@NonNull Builder addTrustedCertificate(@NonNull X509Certificate certificate) {
            Objects.requireNonNull(certificate);
            this.trustedCertificates.add(certificate);
            return this;
        }

        @Override
        public @NonNull ServerHandshakeCertificates build() {
            return buildInternal();
        }
    }
}
