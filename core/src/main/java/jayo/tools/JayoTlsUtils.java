/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
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

package jayo.tools;

import jayo.internal.tls.RealHandshakeCertificates;
import jayo.tls.ClientHandshakeCertificates;
import jayo.tls.HeldCertificate;
import jayo.tls.ServerHandshakeCertificates;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import java.util.Objects;

/**
 * This class exposes TLS methods for test purpose
 */
public final class JayoTlsUtils {
    // un-instantiable
    private JayoTlsUtils() {
    }

    private static @Nullable ClientHandshakeCertificates LOCALHOST = null;

    public static @NonNull ClientHandshakeCertificates localhost() {
        if (LOCALHOST == null) {
            // Generate a self-signed cert for the server to serve and the client to trust.
            final var heldCertificate = HeldCertificate.builder()
                    .commonName("localhost")
                    .addSubjectAlternativeName("localhost")
                    .addSubjectAlternativeName("localhost.localdomain")
                    .build();
            LOCALHOST = ClientHandshakeCertificates.builder()
                    .heldCertificate(heldCertificate)
                    .addTrustedCertificate(heldCertificate.getCertificate())
                    .build();
        }
        return LOCALHOST;
    }

    public static @NonNull SSLContext handshakeCertSSLContext(
            final @NonNull ClientHandshakeCertificates clientCertificates) {
        Objects.requireNonNull(clientCertificates);
        return ((RealHandshakeCertificates) clientCertificates).getSslContext();
    }

    public static @NonNull SSLContext handshakeCertSSLContext(
            final @NonNull ServerHandshakeCertificates serverCertificates) {
        Objects.requireNonNull(serverCertificates);
        return ((RealHandshakeCertificates) serverCertificates).getSslContext();
    }
}
