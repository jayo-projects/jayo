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
import org.jspecify.annotations.Nullable;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
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
 * match the server's hostname. The server must also have a (possibly empty) chain of intermediate certificates to
 * establish trust from a root certificate to the server's certificate. The
 * {@linkplain ServerHandshakeCertificates.Builder#addTrustedCertificate(X509Certificate) root certificate} is not
 * included in this chain, see below.
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
 * (possibly empty) chain of intermediate certificates to establish trust from a root certificate to the client's
 * certificate. The {@linkplain ClientHandshakeCertificates.Builder#addTrustedCertificate(X509Certificate) root
 * certificate} is not included in this chain, see above.
 * <li>The {@linkplain ServerHandshakeCertificates server's handshake certificates} must include a set of trusted root
 * certificates. They will be used to authenticate the client's certificate chain. This is a different set of root
 * certificates used in server authentication. Instead, it will be a
 * {@linkplain ServerHandshakeCertificates.Builder#addTrustedCertificate(X509Certificate) small set of roots private to
 * an organization or service}.
 * </ul>
 *
 * @see ServerHandshakeCertificates
 */
public sealed interface ClientHandshakeCertificates permits RealHandshakeCertificates {
    /**
     * Creates a default {@linkplain ClientHandshakeCertificates client handshake certificates} to secure TLS
     * connections. It provides good security based on the System's default trust manager.
     */
    static @NonNull ClientHandshakeCertificates create() {
        return new RealHandshakeCertificates();
    }

    /**
     * Creates a {@linkplain ClientHandshakeCertificates client handshake certificates} from a
     * {@link TrustManagerFactory}. This client will be able to authenticate to the server and does not support
     * authentication back from the server.
     * <p>
     * Most applications should not call this method, and instead use the {@linkplain #create() system defaults}, as it
     * includes special optimizations that can be lost if the implementations are decorated.
     */
    static @NonNull ClientHandshakeCertificates create(final @NonNull TrustManagerFactory tmf) {
        Objects.requireNonNull(tmf);
        return new RealHandshakeCertificates(tmf, null);
    }

    /**
     * Creates a {@linkplain ClientHandshakeCertificates client handshake certificates} from a
     * {@link TrustManagerFactory} and a {@link KeyManagerFactory}. This client will be able to authenticate to the
     *  server and supports authentication back from the server.
     * <p>
     * Most applications should not call this method, and instead use the {@linkplain #create() system defaults}, as it
     * includes special optimizations that can be lost if the implementations are decorated.
     */
    static @NonNull ClientHandshakeCertificates create(final @NonNull TrustManagerFactory tmf,
                                                       final @NonNull KeyManagerFactory kmf) {
        Objects.requireNonNull(tmf);
        Objects.requireNonNull(kmf);
        return new RealHandshakeCertificates(tmf, kmf);
    }

    /**
     * @return a builder to craft a {@linkplain ClientHandshakeCertificates client handshake certificates}.
     * <p>
     * Most applications should not call this method, and instead use the {@linkplain #create() system defaults}, as it
     * includes special optimizations that can be lost if the implementations are decorated.
     */
    static @NonNull Builder builder() {
        return new RealHandshakeCertificates.ClientBuilder();
    }

    @SuppressWarnings("NullableProblems")
    @NonNull
    X509TrustManager getTrustManager();

    @Nullable
    X509KeyManager getKeyManager();

    /**
     * The builder used to create a {@link ClientHandshakeCertificates}.
     */
    sealed interface Builder permits RealHandshakeCertificates.ClientBuilder {
        /**
         * Add all the host platform's trusted root certificates. Default is true.
         * <p>
         * Most TLS clients that connect to hosts on the public Internet <b>should not call this method with false</b>.
         * Otherwise, it is necessary to {@linkplain #addTrustedCertificate(X509Certificate) manually prepare a
         * comprehensive set of trusted roots}.
         * <p>
         * If the host platform is compromised or misconfigured, it may contain untrustworthy root certificates.
         * Applications that connect to a known set of servers may be able to mitigate this problem with certificate
         * pinning.
         */
        @NonNull
        Builder addPlatformTrustedCertificates(final boolean addPlatformTrustedCertificates);

        /**
         * Add a trusted root certificate to use when authenticating a peer. Peers must provide a chain of certificates
         * whose root is one of these.
         */
        @NonNull
        Builder addTrustedCertificate(final @NonNull X509Certificate certificate);

        /**
         * Configure the certificate chain to use when being authenticated. The first certificate is the held
         * certificate, further certificates are included in the handshake so the peer can build a trusted path to a
         * trusted root certificate.
         * <p>
         * The chain should include all intermediate certificates but does not need the root certificate that we expect
         * to be known by the remote peer. The peer already has that certificate, so transmitting it is unnecessary.
         */
        @NonNull
        Builder heldCertificate(final @NonNull HeldCertificate heldCertificate,
                                final @NonNull X509Certificate @NonNull ... intermediates);

        /**
         * Configures this to not authenticate the HTTPS server on to {@code hostname}. This makes the user vulnerable
         * to man-in-the-middle attacks and should only be used only in private development environments and only to
         * carry test data.
         * <p>
         * In this scenario, the server’s TLS certificate <b>does not need to be signed</b> by a trusted certificate
         * authority. Instead, it will trust any well-formed certificate, even if it is self-signed. This is necessary
         * for testing against localhost or in development environments where a certificate authority is not possible.
         * <p>
         * The server’s TLS certificate still must match the requested hostname. For example, if the certificate is
         * issued to {@code example.com} and the request is to {@code localhost}, the connection will fail. Use a custom
         * {@linkplain javax.net.ssl.HostnameVerifier HostnameVerifier} to ignore such problems.
         * <p>
         * Other TLS features are still used but provide no security benefits in the absence of the above gaps. For
         * example, an insecure TLS connection is capable of negotiating HTTP/2 with ALPN, and it also has a
         * regular-looking handshake.
         *
         * @param hostname the exact hostname from the URL for insecure connections.
         */
        @NonNull
        Builder addInsecureHost(final @NonNull String hostname);

        @NonNull
        ClientHandshakeCertificates build();
    }
}
