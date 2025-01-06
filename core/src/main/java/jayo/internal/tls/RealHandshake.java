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
import jayo.tls.CipherSuite;
import jayo.tls.Handshake;
import jayo.tls.TlsVersion;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class RealHandshake implements Handshake {
    public static @NonNull RealHandshake get(final @NonNull TlsVersion tlsVersion,
                                             final @NonNull CipherSuite cipherSuite,
                                             final @NonNull List<Certificate> localCertificates,
                                             final @NonNull List<Certificate> peerCertificates) {
        Objects.requireNonNull(tlsVersion);
        Objects.requireNonNull(cipherSuite);
        Objects.requireNonNull(localCertificates);
        Objects.requireNonNull(peerCertificates);

        final var peerCertificatesCopy = List.copyOf(peerCertificates);
        return new RealHandshake(
                tlsVersion,
                cipherSuite,
                List.copyOf(localCertificates),
                () -> peerCertificatesCopy
        );
    }

    public static @NonNull RealHandshake get(final @NonNull SSLSession session) {
        Objects.requireNonNull(session);

        final var cipherSuiteString = session.getCipherSuite();
        if (cipherSuiteString == null) {
            throw new IllegalStateException("Session's cipher suite is null");
        }
        if (cipherSuiteString.equals("TLS_NULL_WITH_NULL_NULL") || cipherSuiteString.equals("SSL_NULL_WITH_NULL_NULL")) {
            throw new JayoException("Session's cipherSuite == " + cipherSuiteString);
        }
        final var cipherSuite = CipherSuite.fromJavaName(cipherSuiteString);

        final var tlsVersionString = session.getProtocol();
        if (tlsVersionString == null) {
            throw new IllegalStateException("Session's TLS version (= protocol) is null");
        }
        if (tlsVersionString.equals("NONE")) {
            throw new JayoException("Session's TLS version == " + tlsVersionString);
        }
        final var tlsVersion = TlsVersion.fromJavaName(tlsVersionString);

        Certificate[] peerCertificates;
        try {
            peerCertificates = session.getPeerCertificates();
        } catch (SSLPeerUnverifiedException ignored) {
            peerCertificates = null;
        }
        final var peerCertificatesCopy = unmodifiableCertificateList(peerCertificates);

        return new RealHandshake(
                tlsVersion,
                cipherSuite,
                unmodifiableCertificateList(session.getLocalCertificates()),
                () -> peerCertificatesCopy
        );
    }

    private static @NonNull List<Certificate> unmodifiableCertificateList(final @Nullable Certificate[] certificates) {
        //noinspection Java9CollectionFactory
        return (certificates != null) ? Collections.unmodifiableList(Arrays.asList(certificates)) : List.of();
    }

    private final @NonNull TlsVersion tlsVersion;
    private final @NonNull CipherSuite cipherSuite;
    private final @NonNull List<Certificate> localCertificates;
    private final @NonNull Supplier<List<Certificate>> peerCertificatesFn;
    private @Nullable List<Certificate> peerCertificates = null;

    private RealHandshake(final @NonNull TlsVersion tlsVersion,
                         final @NonNull CipherSuite cipherSuite,
                         final @NonNull List<Certificate> localCertificates,
                         final @NonNull Supplier<@NonNull List<Certificate>> peerCertificatesFn) {
        assert tlsVersion != null;
        assert cipherSuite != null;
        assert localCertificates != null;
        assert peerCertificatesFn != null;

        this.tlsVersion = tlsVersion;
        this.cipherSuite = cipherSuite;
        this.localCertificates = localCertificates;
        this.peerCertificatesFn = peerCertificatesFn;
    }

    @Override
    public @NonNull TlsVersion getTlsVersion() {
        return tlsVersion;
    }

    @Override
    public @NonNull CipherSuite getCipherSuite() {
        return cipherSuite;
    }

    @Override
    public @NonNull List<Certificate> getLocalCertificates() {
        return localCertificates;
    }

    @Override
    public @NonNull List<Certificate> getPeerCertificates() {
        if (peerCertificates != null) {
            return peerCertificates;
        }

        peerCertificates = peerCertificatesFn.get();
        return peerCertificates;
    }

    @Override
    public @Nullable Principal getLocalPrincipal() {
        if (!localCertificates.isEmpty()
                && localCertificates.get(0) instanceof X509Certificate localX509Certificate) {
            return localX509Certificate.getSubjectX500Principal();
        }
        return null;
    }

    @Override
    public @Nullable Principal getPeerPrincipal() {
        final var peerCertificates = getPeerCertificates();
        if (!peerCertificates.isEmpty() && peerCertificates.get(0) instanceof X509Certificate peerX509Certificate) {
            return peerX509Certificate.getSubjectX500Principal();
        }
        return null;
    }

    @Override
    public boolean equals(final @Nullable Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof RealHandshake _other)) {
            return false;
        }
        return tlsVersion == _other.tlsVersion
                && Objects.equals(cipherSuite, _other.cipherSuite)
                && Objects.equals(localCertificates, _other.localCertificates)
                && Objects.equals(getPeerCertificates(), _other.getPeerCertificates());
    }

    @Override
    public int hashCode() {
        var result = 17;
        result = 31 * result + tlsVersion.hashCode();
        result = 31 * result + cipherSuite.hashCode();
        result = 31 * result + localCertificates.hashCode();
        result = 31 * result + getPeerCertificates().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Handshake{" +
                "tlsVersion=" + tlsVersion +
                ", cipherSuite=" + cipherSuite +
                ", localCertificates=" + localCertificates.stream().map(RealHandshake::certificateName).toList() +
                ", peerCertificates=" + getPeerCertificates().stream().map(RealHandshake::certificateName).toList() +
                '}';
    }

    private static String certificateName(final @NonNull Certificate certificate) {
        assert certificate != null;

        if (certificate instanceof X509Certificate x509Certificate) {
            return x509Certificate.getSubjectX500Principal().toString();
        }
        // else
        return certificate.getType();
    }
}
