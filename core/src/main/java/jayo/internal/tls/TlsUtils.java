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

import jayo.JayoException;
import jayo.tls.HeldCertificate;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

final class TlsUtils {
    // un-instantiable
    private TlsUtils() {
    }

    private static final char @NonNull [] password = "password".toCharArray();

    /**
     * @return a trust manager that trusts {@code trustedCertificates}.
     */
    static @NonNull X509TrustManager newTrustManager(final @Nullable String keyStoreType,
                                                     final @NonNull List<X509Certificate> trustedCertificates,
                                                     final @NonNull List<String> insecureHosts) {
        assert trustedCertificates != null;
        assert insecureHosts != null;

        final var trustStore = newEmptyKeyStore(keyStoreType);
        final TrustManagerFactory factory;
        try {
            for (var i = 0; i < trustedCertificates.size(); i++) {
                trustStore.setCertificateEntry("cert_" + i, trustedCertificates.get(i));
            }

            factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(trustStore);
        } catch (KeyStoreException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        final var trustManagers = factory.getTrustManagers();
        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager x509TrustManager)) {
            throw new IllegalStateException("Unexpected trust managers: " + Arrays.toString(trustManagers));
        }

        return insecureHosts.isEmpty()
                ? x509TrustManager
                : new InsecureExtendedTrustManager((X509ExtendedTrustManager) x509TrustManager, insecureHosts);
    }

    /**
     * Returns a key manager for the held certificate and its chain. Returns an empty key manager if
     * `heldCertificate` is null.
     */
    static @NonNull X509KeyManager newKeyManager(final @Nullable String keyStoreType,
                                                 final @Nullable HeldCertificate heldCertificate,
                                                 final @NonNull X509Certificate @NonNull ... intermediates) {
        final var keyStore = newEmptyKeyStore(keyStoreType);
        final KeyManagerFactory factory;
        try {
            if (heldCertificate != null) {
                final var chain = new Certificate[1 + intermediates.length];
                chain[0] = heldCertificate.getCertificate();
                System.arraycopy(intermediates, 0, chain, 1, intermediates.length);
                keyStore.setKeyEntry("private", heldCertificate.getKeyPair().getPrivate(), password, chain);
            }

            // https://github.com/bcgit/bc-java/issues/1160
            final var algorithm = switch (keyStore.getProvider().getName()) {
                case "BC", "BCFIPS" -> "PKIX";
                default -> KeyManagerFactory.getDefaultAlgorithm();
            };
            factory = KeyManagerFactory.getInstance(algorithm);
            factory.init(keyStore, password);
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new IllegalStateException(e);
        }
        final var keyManagers = factory.getKeyManagers();
        if (keyManagers.length != 1 || !(keyManagers[0] instanceof X509KeyManager x509KeyManager)) {
            throw new IllegalStateException("Unexpected key managers: " + Arrays.toString(keyManagers));
        }

        return x509KeyManager;
    }

    private static @NonNull KeyStore newEmptyKeyStore(final @Nullable String keyStoreType) {
        final KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance(keyStoreType != null ? keyStoreType : KeyStore.getDefaultType());
        } catch (KeyStoreException e) {
            throw new IllegalArgumentException(e);
        }
        final InputStream inputStream = null; // By convention, 'null' creates an empty key store.
        try {
            //noinspection ConstantValue
            keyStore.load(inputStream, password);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        } catch (NoSuchAlgorithmException | CertificateException e) {
            throw new IllegalArgumentException(e);
        }
        return keyStore;
    }
}
