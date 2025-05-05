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

import jayo.internal.tls.RealHandshake;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLSession;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Objects;

/**
 * A record of a TLS handshake on a secured connection.
 * <ul>
 *     <li>For HTTPS clients, the client is <b>local</b> and the remote server is its <b>peer</b>.
 *     <li>For HTTPS servers, the server is <b>local</b> and the remote client is its <b>peer</b>.
 * </ul>
 * This value object describes a completed handshake.
 */
public sealed interface Handshake permits RealHandshake {
    /**
     * Build a Jayo Handshake from a {@link SSLSession}
     */
    static @NonNull Handshake get(final @NonNull SSLSession session, final @Nullable Protocol protocol) {
        Objects.requireNonNull(session);
        return RealHandshake.get(session, (protocol != null) ? protocol : Protocol.HTTP_1_1);
    }

    static @NonNull Handshake get(final @NonNull Protocol protocol,
                                  final @NonNull TlsVersion tlsVersion,
                                  final @NonNull CipherSuite cipherSuite,
                                  final @NonNull List<Certificate> localCertificates,
                                  final @NonNull List<Certificate> peerCertificates) {
        Objects.requireNonNull(protocol);
        Objects.requireNonNull(tlsVersion);
        Objects.requireNonNull(cipherSuite);
        Objects.requireNonNull(localCertificates);
        Objects.requireNonNull(peerCertificates);

        final var peerCertificatesCopy = List.copyOf(peerCertificates);
        return new RealHandshake(
                protocol,
                tlsVersion,
                cipherSuite,
                List.copyOf(localCertificates),
                () -> peerCertificatesCopy
        );
    }

    /**
     * @return the protocol used for this TLS connection.
     */
    @NonNull
    Protocol getProtocol();

    /**
     * @return the TLS version used for this TLS connection.
     */
    @NonNull
    TlsVersion getTlsVersion();

    /**
     * @return the cipher suite used for this TLS connection.
     */
    @NonNull
    CipherSuite getCipherSuite();

    /**
     * @return a possibly empty list of certificates that identify this peer.
     */
    @NonNull
    List<Certificate> getLocalCertificates();

    /**
     * @return a possibly empty list of certificates that identify the remote peer.
     */
    @NonNull
    List<Certificate> getPeerCertificates();

    /**
     * @return the local principal, or null if local is anonymous.
     */
    @Nullable
    Principal getLocalPrincipal();

    /**
     * @return the remote peer's principal, or null if that peer is anonymous.
     */
    @Nullable
    Principal getPeerPrincipal();
}
