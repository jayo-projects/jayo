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
import jayo.ByteString;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

import static jayo.internal.tls.RealCertificates.single;

record Certificate(
        @NonNull TbsCertificate tbsCertificate,
        @NonNull AlgorithmIdentifier signatureAlgorithm,
        @NonNull BitString signatureValue
) {
    @Nullable
    Object commonName() {
        return tbsCertificate.subject.stream()
                .flatMap(Collection::stream) // flatten
                .filter(atav -> atav.type.equals(ObjectIdentifiers.COMMON_NAME))
                .findFirst()
                .map(AttributeTypeAndValue::value)
                .orElse(null);
    }

    @Nullable
    Object organizationalUnitName() {
        return tbsCertificate.subject.stream()
                .flatMap(Collection::stream) // flatten
                .filter(atav -> atav.type.equals(ObjectIdentifiers.ORGANIZATIONAL_UNIT_NAME))
                .findFirst()
                .map(AttributeTypeAndValue::value)
                .orElse(null);
    }

    @Nullable
    Extension subjectAlternativeNames() {
        for (var extension : tbsCertificate.extensions) {
            if (extension.id.equals(ObjectIdentifiers.SUBJECT_ALTERNATIVE_NAME)) {
                return extension;
            }
        }
        return null;
    }

    @NonNull
    Extension basicConstraints() {
        for (var extension : tbsCertificate.extensions) {
            if (extension.id.equals(ObjectIdentifiers.BASIC_CONSTRAINTS)) {
                return extension;
            }
        }
        throw new NoSuchElementException("Certificate extensions contains no Basic constraint element.");
    }

    boolean checkSignature(final @NonNull PublicKey issuer) {
        assert issuer != null;

        final var signedData = CertificateAdapters.TBS_CERTIFICATE.toDer(tbsCertificate);

        final Signature signature;
        try {
            signature = Signature.getInstance(tbsCertificate.signatureAlgorithmName());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(
                    "Algorithm is not available : " + tbsCertificate.signatureAlgorithmName(), e);
        }
        try {
            signature.initVerify(issuer);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("The provided public key is invalid", e);
        }
        try {
            signature.update(signedData.toByteArray());
            return signature.verify(signatureValue.byteString().toByteArray());
        } catch (SignatureException e) {
            throw new IllegalStateException("A signature exception occurred during update or verify", e);
        }
    }

    @NonNull
    X509Certificate toX509Certificate() {
        final var data = CertificateAdapters.CERTIFICATE.toDer(this);
        try {
            final var certificateFactory = CertificateFactory.getInstance("X.509");
            final var certificates =
                    certificateFactory.generateCertificates(Buffer.create().write(data).asInputStream());
            return single(certificates);
        } catch (CertificateException e) {
            throw new IllegalArgumentException("failed to decode certificate", e);
        }
    }

    /**
     * @param version              This is a integer enum. Use 0L for v1, 1L for v2, and 2L for v3.
     * @param serialNumber
     * @param signature
     * @param issuer
     * @param validity
     * @param subject
     * @param subjectPublicKeyInfo
     * @param issuerUniqueID
     * @param subjectUniqueID
     * @param extensions
     */
    record TbsCertificate(
            long version,
            @NonNull BigInteger serialNumber,
            @NonNull AlgorithmIdentifier signature,
            @NonNull List<List<AttributeTypeAndValue>> issuer,
            @NonNull Validity validity,
            @NonNull List<List<AttributeTypeAndValue>> subject,
            @NonNull SubjectPublicKeyInfo subjectPublicKeyInfo,
            @Nullable BitString issuerUniqueID,
            @Nullable BitString subjectUniqueID,
            @NonNull List<Extension> extensions
    ) {
        /**
         * Returns the standard name of this certificate's signature algorithm as specified by
         * [Signature.getInstance]. Typical values are like "SHA256WithRSA".
         */
        @NonNull
        String signatureAlgorithmName() {
            return switch (signature.algorithm) {
                case ObjectIdentifiers.SHA256_WITH_RSA_ENCRYPTION -> "SHA256WithRSA";
                case ObjectIdentifiers.SHA256_WITH_ECDSA -> "SHA256withECDSA";
                default -> throw new IllegalStateException("unexpected signature algorithm: " + signature.algorithm);
            };
        }
    }

    /**
     * @param algorithm  An OID string like "1.2.840.113549.1.1.11" for sha256WithRSAEncryption.
     * @param parameters Parameters of a type implied by {@link #algorithm}.
     */
    record AlgorithmIdentifier(@NonNull String algorithm, @Nullable Object parameters) {
    }

    /**
     * @param type An OID string like "2.5.4.11" for organizationalUnitName.
     */
    record AttributeTypeAndValue(@NonNull String type, @Nullable Object value) {
    }

    record Validity(long notBefore, long notAfter) {
    }

    record SubjectPublicKeyInfo(@NonNull AlgorithmIdentifier algorithm, @NonNull BitString subjectPublicKey) {
    }

    record Extension(@NonNull String id, boolean critical, @Nullable Object value) {
    }

    /**
     * @param ca                 True if this certificate can be used as a Certificate Authority (CA).
     * @param maxIntermediateCas The maximum number of intermediate CAs between this and leaf certificates.
     */
    record BasicConstraints(boolean ca, @Nullable Long maxIntermediateCas) {
    }

    /**
     * A private key. Note that this class doesn't support attributes or an embedded public key.
     *
     * @param version v1(0), v2(1).
     */
    record PrivateKeyInfo(
            long version,
            @NonNull AlgorithmIdentifier algorithmIdentifier,
            @NonNull ByteString privateKey
    ) {
    }
}
