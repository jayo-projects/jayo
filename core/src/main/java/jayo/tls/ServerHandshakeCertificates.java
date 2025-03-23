/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
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

package jayo.tls;

import jayo.internal.tls.RealHandshakeCertificates;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Objects;

/**
 * Certificates to identify which peers to trust and also to earn the trust of those peers in kind. Client and server
 * exchange these certificates during the handshake phase of a TLS connection.
 * <h3>Server Authentication</h3>
 * This is the most common form of TLS authentication: clients verify that servers are trusted and that they own the
 * hostnames that they represent. <b>Server authentication is required</b>.
 * <p>
 * To perform server authentication:
 * <ul>
 * <li>The {@linkplain ServerHandshakeCertificates server's handshake certificates} must have a
 * {@linkplain HeldCertificate held certificate} (a certificate and its private key). The
 * {@linkplain HeldCertificate.Builder#addSubjectAlternativeName(String) certificate's subject alternative names} must
 * match the server's hostname. The server must also have a (possibly-empty) chain of intermediate certificates to
 * establish trust from a root certificate to the server's certificate. The
 * {@linkplain ServerHandshakeCertificates.Builder#addTrustedCertificate(X509Certificate) root certificate} is not
 * included in this chain.
 * <li>The {@linkplain ClientHandshakeCertificates client's handshake certificates} must include a set of trusted root
 * certificates. They will be used to authenticate the server's certificate chain. Typically, this is a
 * {@linkplain ClientHandshakeCertificates.Builder#addPlatformTrustedCertificates(boolean) set of well-known root
 * certificates} that is distributed with the HTTP client or its platform. It may be replaced or augmented by
 * {@linkplain ClientHandshakeCertificates.Builder#addTrustedCertificate(X509Certificate) certificates private to an
 * organization or service}.
 * </ul>
 * <h3>Client Authentication</h3>
 * This is authentication of the client by the server during the TLS handshake. <b>Client authentication is optional</b>.
 * <p>
 * To perform client authentication:
 * <ul>
 * <li>The {@linkplain ClientHandshakeCertificates client's handshake certificates} must have a
 * {@linkplain HeldCertificate held certificate} (a certificate and its private key). The client must also have a
 * (possibly-empty) chain of intermediate certificates to establish trust from a root certificate to the client's
 * certificate. The {@linkplain ClientHandshakeCertificates.Builder#addTrustedCertificate(X509Certificate) root
 * certificate} is not included in this chain.
 * <li>The {@linkplain ServerHandshakeCertificates server's handshake certificates} must include a set of trusted root
 * certificates. They will be used to authenticate the client's certificate chain. This is not the same set of root
 * certificates used in server authentication. Instead, it will be a
 * {@linkplain ServerHandshakeCertificates.Builder#addTrustedCertificate(X509Certificate) small set of roots private to
 * an organization or service}.
 * </ul>
 *
 * @see ClientHandshakeCertificates
 */
public sealed interface ServerHandshakeCertificates permits RealHandshakeCertificates {
    static @NonNull Builder builder(final @NonNull HeldCertificate heldCertificate,
                                    final @NonNull X509Certificate @NonNull ... intermediates) {
        Objects.requireNonNull(heldCertificate);

        return new RealHandshakeCertificates.ServerBuilder(
                heldCertificate,
                Arrays.copyOf(intermediates, intermediates.length)); // Defensive copy.
    }

    @NonNull
    X509KeyManager getKeyManager();

    @NonNull
    X509TrustManager getTrustManager();

    /**
     * The builder used to create a {@link ServerHandshakeCertificates}.
     */
    sealed interface Builder permits RealHandshakeCertificates.ServerBuilder {
        /**
         * Add a trusted root certificate to use when authenticating a peer. Peers must provide a chain of certificates
         * whose root is one of these.
         */
        @NonNull
        Builder addTrustedCertificate(final @NonNull X509Certificate certificate);

        @NonNull
        ServerHandshakeCertificates build();
    }
}
