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

package jayo.tls;

import jayo.internal.tls.RealHandshakeCertificates;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * Certificates to identify which peers to trust and also to earn the trust of those peers in kind. Client and server
 * exchange these certificates during the handshake phase of a TLS connection.
 * <h3>Server Authentication</h3>
 * This is the most common form of TLS authentication: clients verify that servers are trusted and that they own the
 * hostnames that they represent. Server authentication is required.
 * <p>
 * To perform server authentication:
 * <ul>
 * <li>The server's handshake certificates must have a {@linkplain HeldCertificate held certificate} (a certificate and
 * its private key). The certificate's subject alternative names must match the server's hostname. The server must also
 * have is a (possibly-empty) chain of intermediate certificates to establish trust from a root certificate to the
 * server's certificate. The root certificate is not included in this chain.
 * <li>The client's handshake certificates must include a set of trusted root certificates. They will be used to
 * authenticate the server's certificate chain. Typically, this is a set of well-known root certificates that is
 * distributed with the HTTP client or its platform. It may be augmented by certificates private to an organization or
 * service.
 * </ul>
 * <h3>Client Authentication</h3>
 * This is authentication of the client by the server during the TLS handshake. Client authentication is optional.
 * <p>
 * To perform client authentication:
 * <ul>
 * <li>The client's handshake certificates must have a {@linkplain HeldCertificate held certificate} (a certificate and
 * its private key). The client must also have a (possibly-empty) chain of intermediate certificates to establish trust
 * from a root certificate to the client's certificate. The root certificate is not included in this chain.
 * <li>The server's handshake certificates must include a set of trusted root certificates. They will be used to
 * authenticate the client's certificate chain. Typically, this is not the same set of root certificates used in server
 * authentication. Instead, it will be a small set of roots private to an organization or service.
 * </ul>
 */
public sealed interface HandshakeCertificates permits RealHandshakeCertificates {
    static @NonNull Builder builder() {
        return new RealHandshakeCertificates.Builder();
    }

    @NonNull
    X509KeyManager getKeyManager();

    @NonNull
    X509TrustManager getTrustManager();

    @NonNull
    SSLContext sslContext();

    /**
     * The configuration used to create a {@link HandshakeCertificates}.
     */
    sealed interface Builder permits RealHandshakeCertificates.Builder {
        /**
         * Configure the certificate chain to use when being authenticated. The first certificate is the held
         * certificate, further certificates are included in the handshake so the peer can build a trusted path to a
         * trusted root certificate.
         * <p>
         * The chain should include all intermediate certificates but does not need the root certificate that we expect
         * to be known by the remote peer. The peer already has that certificate so transmitting it is unnecessary.
         */
        @NonNull
        Builder heldCertificate(final @NonNull HeldCertificate heldCertificate,
                                final @NonNull X509Certificate @NonNull ... intermediates);

        /**
         * Add a trusted root certificate to use when authenticating a peer. Peers must provide a chain of certificates
         * whose root is one of these.
         */
        @NonNull
        Builder addTrustedCertificate(final @NonNull X509Certificate certificate);

        /**
         * Add all the host platform's trusted root certificates.
         * <p>
         * Most TLS clients that connect to hosts on the public Internet should call this method. Otherwise, it is
         * necessary to manually prepare a comprehensive set of trusted roots.
         * <p>
         * If the host platform is compromised or misconfigured this may contain untrustworthy root certificates.
         * Applications that connect to a known set of servers may be able to mitigate this problem with certificate
         * pinning.
         */
        @NonNull
        Builder addPlatformTrustedCertificates();

        /**
         * Configures this to not authenticate the HTTPS server on to {@code hostname}. This makes the user vulnerable
         * to man-in-the-middle attacks and should only be used only in private development environments and only to
         * carry test data.
         * <p>
         * The server’s TLS certificate <b>does not need to be signed</b> by a trusted certificate authority. Instead,
         * it will trust any well-formed certificate, even if it is self-signed. This is necessary for testing against
         * localhost or in development environments where a certificate authority is not possible.
         * <p>
         * The server’s TLS certificate still must match the requested hostname. For example, if the certificate is
         * issued to {@code example.com} and the request is to {@code localhost}, the connection will fail. Use a custom
         * {@linkplain javax.net.ssl.HostnameVerifier HostnameVerifier} to ignore such problems.
         * <p>
         * Other TLS features are still used but provide no security benefits in absence of the above gaps. For example,
         * an insecure TLS connection is capable of negotiating HTTP/2 with ALPN, and it also has a regular-looking
         * handshake.
         *
         * @param hostname the exact hostname from the URL for insecure connections.
         */
        @NonNull
        Builder addInsecureHost(final @NonNull String hostname);

        @NonNull
        HandshakeCertificates build();
    }
}
