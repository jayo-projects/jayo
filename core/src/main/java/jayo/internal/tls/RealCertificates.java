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

import jayo.Buffer;
import jayo.bytestring.ByteString;
import org.jspecify.annotations.NonNull;

import java.security.cert.Certificate;
import java.security.cert.*;
import java.util.Objects;

public final class RealCertificates {
    // un-instantiable
    private RealCertificates() {
    }

    public static @NonNull X509Certificate decodeCertificatePem(final @NonNull String certificatePem) {
        Objects.requireNonNull(certificatePem);

        try {
            final var certificateFactory = CertificateFactory.getInstance("X.509");
            final var certificates =
                    certificateFactory.generateCertificates(Buffer.create().write(certificatePem).asInputStream());

            return single(certificates);
        } catch (CertificateException e) {
            throw new IllegalArgumentException("failed to decode certificate", e);
        }
    }

    public static @NonNull String certificatePem(final @NonNull X509Certificate certificate) {
        Objects.requireNonNull(certificate);

        final var certificatePemSb = new StringBuilder();
        certificatePemSb.append("-----BEGIN CERTIFICATE-----\n");
        try {
            encodeBase64Lines(certificatePemSb, ByteString.of(certificate.getEncoded()));
        } catch (CertificateEncodingException e) {
            throw new IllegalArgumentException("Could not encode certificate", e);
        }
        certificatePemSb.append("-----END CERTIFICATE-----\n");

        return certificatePemSb.toString();
    }

    static void encodeBase64Lines(final @NonNull StringBuilder sb, final @NonNull ByteString data) {
        final var base64 = data.base64();
        for (var i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
    }

    static @NonNull X509Certificate single(final @NonNull Iterable<? extends Certificate> iterable) {
        final var iterator = iterable.iterator();
        if (!iterator.hasNext()) {
            throw new IllegalArgumentException("failed to decode certificate, certificate collection is empty.");
        }
        final var single = iterator.next();
        if (iterator.hasNext()) {
            throw new IllegalArgumentException(
                    "failed to decode certificate, certificate collection has more than one element.");
        }
        if (!(single instanceof X509Certificate singleX509Certificate)) {
            throw new IllegalArgumentException("failed to decode certificate, only X509Certificates are supported.");
        }
        return singleX509Certificate;
    }
}
