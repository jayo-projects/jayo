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

import jayo.ByteString;
import jayo.JayoUnknownHostException;
import jayo.internal.tls.Adapters.DerAdapterValue;
import jayo.tls.HeldCertificate;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import static jayo.ByteString.decodeBase64;
import static jayo.internal.tls.Certificate.*;
import static jayo.internal.tls.CertificateAdapters.GENERAL_NAME_DNS_NAME;
import static jayo.internal.tls.CertificateAdapters.GENERAL_NAME_IP_ADDRESS;
import static jayo.internal.tls.ObjectIdentifiers.*;
import static jayo.internal.tls.RealCertificates.decodeCertificatePem;
import static jayo.internal.tls.RealCertificates.encodeBase64Lines;

public final class RealHeldCertificate implements HeldCertificate {
    private static final @NonNull Pattern PEM_PATTERN =
            Pattern.compile("-----BEGIN ([!-,.-~ ]*)-----([^-]*)-----END \\1-----");

    public static @NonNull RealHeldCertificate decode(final @NonNull String certificateAndPrivateKeyPem) {
        String certificatePem = null;
        String pkcs8Base64 = null;

        final var matcher = PEM_PATTERN.matcher(certificateAndPrivateKeyPem);

        for (var match : matcher.results().toList()) {
            final var label = match.group(1);
            switch (label) {
                case "CERTIFICATE" -> {
                    if (certificatePem != null) {
                        throw new IllegalArgumentException("string includes multiple certificates");
                    }
                    certificatePem = match.group(0); // Keep --BEGIN-- and --END-- for certificates.
                }
                case "PRIVATE KEY" -> {
                    if (pkcs8Base64 != null) {
                        throw new IllegalArgumentException("string includes multiple private keys");
                    }
                    pkcs8Base64 = match.group(2); // Include the contents only for PKCS8.
                }
                default -> throw new IllegalArgumentException("unexpected type: " + label);
            }
        }
        if (certificatePem == null) {
            throw new IllegalArgumentException("string does not include a certificate");
        }
        if (pkcs8Base64 == null) {
            throw new IllegalArgumentException("string does not include a private key");
        }

        return decode(certificatePem, pkcs8Base64);
    }

    private static @NonNull RealHeldCertificate decode(final @NonNull String certificatePem,
                                                       final @NonNull String pkcs8Base64Text) {
        final var certificate = decodeCertificatePem(certificatePem);

        final var pkcs8Bytes = decodeBase64(pkcs8Base64Text);
        if (pkcs8Bytes == null) {
            throw new IllegalArgumentException("failed to decode private key");
        }

        // The private key doesn't tell us its type but it's okay because the certificate knows!
        final var certificatePublicKey = certificate.getPublicKey();
        final String keyType;
        if (certificatePublicKey instanceof ECPublicKey) {
            keyType = "EC";
        } else if (certificatePublicKey instanceof RSAPublicKey) {
            keyType = "RSA";
        } else {
            throw new IllegalArgumentException("unexpected key type: " + certificatePublicKey);
        }

        final var privateKey = decodePkcs8(pkcs8Bytes, keyType);

        final var keyPair = new KeyPair(certificate.getPublicKey(), privateKey);
        return new RealHeldCertificate(keyPair, certificate);
    }

    private static @NonNull PrivateKey decodePkcs8(final @NonNull ByteString data, final @NonNull String keyAlgorithm) {
        try {
            final var keyFactory = KeyFactory.getInstance(keyAlgorithm);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(data.toByteArray()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("failed to decode private key", e);
        }
    }

    private static final long DEFAULT_DURATION_MILLIS = 1000L * 60 * 60 * 24; // 24 hours.

    private final @NonNull KeyPair keyPair;
    private final @NonNull X509Certificate certificate;

    private RealHeldCertificate(final @NonNull KeyPair keyPair, final @NonNull X509Certificate certificate) {
        assert keyPair != null;
        assert certificate != null;

        this.keyPair = keyPair;
        this.certificate = certificate;
    }

    @Override
    public @NonNull KeyPair getKeyPair() {
        return keyPair;
    }

    @Override
    public @NonNull X509Certificate getCertificate() {
        return certificate;
    }

    @Override
    public @NonNull String certificatePem() {
        return RealCertificates.certificatePem(certificate);
    }

    @Override
    public @NonNull String privateKeyPkcs8Pem() {
        final var sb = new StringBuilder();
        sb.append("-----BEGIN PRIVATE KEY-----\n");
        encodeBase64Lines(sb, ByteString.of(keyPair.getPrivate().getEncoded()));
        sb.append("-----END PRIVATE KEY-----\n");
        return sb.toString();
    }

    @Override
    public @NonNull String privateKeyPkcs1Pem() {
        final var sb = new StringBuilder();
        sb.append("-----BEGIN RSA PRIVATE KEY-----\n");
        encodeBase64Lines(sb, pkcs1Bytes());
        sb.append("-----END RSA PRIVATE KEY-----\n");
        return sb.toString();
    }

    private @NonNull ByteString pkcs1Bytes() {
        final var decoded =
                CertificateAdapters.PRIVATE_KEY_INFO.fromDer(ByteString.of(keyPair.getPrivate().getEncoded()));
        return decoded.privateKey();
    }

    public static final class Builder implements HeldCertificate.Builder {
        private long notBefore = -1L;
        private long notAfter = -1L;
        private @Nullable String commonName = null;
        private @Nullable String organizationalUnit = null;
        private final @NonNull List<String> altNames = new ArrayList<>();
        private @Nullable BigInteger serialNumber = null;
        private @Nullable KeyPair keyPair = null;
        private @Nullable HeldCertificate signedBy = null;
        private int maxIntermediateCas = -1;
        private @NonNull CertificateKeyFormat keyFormat = CertificateKeyFormat.ECDSA_256;

        @Override
        public @NonNull Builder validityInterval(final @NonNull Instant notBefore, final @NonNull Instant notAfter) {
            Objects.requireNonNull(notBefore);
            Objects.requireNonNull(notAfter);

            if (notBefore.compareTo(notAfter) >= 0) {
                throw new IllegalArgumentException("invalid interval: " + notBefore + " .. " + notAfter);
            }
            this.notBefore = notBefore.toEpochMilli();
            this.notAfter = notAfter.toEpochMilli();
            return this;
        }

        @Override
        public @NonNull Builder duration(final @NonNull Duration duration) {
            Objects.requireNonNull(duration);

            final var now = Instant.now();
            return validityInterval(now, now.plus(duration));
        }

        @Override
        public @NonNull Builder addSubjectAlternativeName(final @NonNull String altName) {
            Objects.requireNonNull(altName);

            altNames.add(altName);
            return this;
        }

        @Override
        public @NonNull Builder commonName(final @NonNull String cn) {
            Objects.requireNonNull(cn);

            commonName = cn;
            return this;
        }

        @Override
        public @NonNull Builder organizationalUnit(final @NonNull String ou) {
            Objects.requireNonNull(ou);

            organizationalUnit = ou;
            return this;
        }

        @Override
        public @NonNull Builder serialNumber(final long serialNumber) {
            return serialNumber(BigInteger.valueOf(serialNumber));
        }

        @Override
        public @NonNull Builder serialNumber(final @NonNull BigInteger serialNumber) {
            Objects.requireNonNull(serialNumber);

            this.serialNumber = serialNumber;
            return this;
        }

        @Override
        public @NonNull Builder keyPair(final @NonNull KeyPair keyPair) {
            Objects.requireNonNull(keyPair);

            this.keyPair = keyPair;
            return this;
        }

        @Override
        public @NonNull Builder keyPair(final @NonNull PublicKey publicKey, final @NonNull PrivateKey privateKey) {
            Objects.requireNonNull(publicKey);
            Objects.requireNonNull(privateKey);

            this.keyPair = new KeyPair(publicKey, privateKey);
            return this;
        }

        @Override
        public @NonNull Builder signedBy(final @Nullable HeldCertificate signedBy) {
            this.signedBy = signedBy;
            return this;
        }

        @Override
        public @NonNull Builder certificateAuthority(final int maxIntermediateCas) {
            if (maxIntermediateCas < 0) {
                throw new IllegalArgumentException("maxIntermediateCas < 0: " + maxIntermediateCas);
            }
            this.maxIntermediateCas = maxIntermediateCas;
            return this;
        }

        @Override
        public @NonNull Builder keyFormat(final @NonNull CertificateKeyFormat keyFormat) {
            Objects.requireNonNull(keyFormat);

            this.keyFormat = keyFormat;
            return this;
        }

        @Override
        public @NonNull HeldCertificate build() {
            // Subject keys & identity.
            final var subjectKeyPair = (keyPair != null) ? keyPair : generateKeyPair();
            final var subjectPublicKeyInfo =
                    CertificateAdapters.SUBJECT_PUBLIC_KEY_INFO.fromDer(
                            ByteString.of(subjectKeyPair.getPublic().getEncoded())
                    );
            final List<List<AttributeTypeAndValue>> subject = subject();

            // Issuer/signer keys & identity. It may be the subject if it is self-signed.
            final KeyPair issuerKeyPair;
            final List<List<AttributeTypeAndValue>> issuer;
            if (signedBy != null) {
                issuerKeyPair = signedBy.getKeyPair();
                issuer =
                        CertificateAdapters.RDN_SEQUENCE.fromDer(
                                ByteString.of(signedBy.getCertificate().getSubjectX500Principal().getEncoded())
                        );
            } else {
                issuerKeyPair = subjectKeyPair;
                issuer = subject;
            }
            final var signatureAlgorithm = signatureAlgorithm(issuerKeyPair);

            // Subset of certificate data that's covered by the signature.
            final var tbsCertificate =
                    new TbsCertificate(
                            // v3:
                            2L,
                            (serialNumber != null) ? serialNumber : BigInteger.ONE,
                            signatureAlgorithm,
                            issuer,
                            validity(),
                            subject,
                            subjectPublicKeyInfo,
                            null,
                            null,
                            extensions()
                    );

            // Signature.
            final Signature signature;
            try {
                signature = Signature.getInstance(tbsCertificate.signatureAlgorithmName());
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalArgumentException("Algorithm is not available : "
                        + tbsCertificate.signatureAlgorithmName(), e);
            }
            try {
                signature.initSign(issuerKeyPair.getPrivate());
            } catch (InvalidKeyException e) {
                throw new IllegalArgumentException(
                        "InvalidKeyException was fired with the provided KeyPair private key", e);
            }
            final ByteString signatureAsByteString;
            try {
                signature.update(CertificateAdapters.TBS_CERTIFICATE.toDer(tbsCertificate).toByteArray());
                signatureAsByteString = ByteString.of(signature.sign());
            } catch (SignatureException e) {
                throw new IllegalStateException("A signature exception occurred during update or verify", e);
            }

            // Complete signed certificate.
            final var certificate =
                    new jayo.internal.tls.Certificate(
                            tbsCertificate,
                            signatureAlgorithm,
                            new BitString(signatureAsByteString, (byte) 0));

            return new RealHeldCertificate(subjectKeyPair, certificate.toX509Certificate());
        }

        private @NonNull KeyPair generateKeyPair() {
            final KeyPairGenerator keyPairGenerator;
            try {
                keyPairGenerator = KeyPairGenerator.getInstance(keyFormat.getKeyAlgorithm());
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("No such algorithm: " + keyFormat.getKeyAlgorithm());
            }
            keyPairGenerator.initialize(keyFormat.getKeySize(), new SecureRandom());
            return keyPairGenerator.generateKeyPair();
        }

        private @NonNull List<List<AttributeTypeAndValue>> subject() {
            final List<List<AttributeTypeAndValue>> result = new ArrayList<>();

            if (organizationalUnit != null) {
                result.add(List.of(new AttributeTypeAndValue(ORGANIZATIONAL_UNIT_NAME, organizationalUnit)));
            }

            result.add(List.of(
                    new AttributeTypeAndValue(
                            COMMON_NAME,
                            (commonName != null) ? commonName : UUID.randomUUID().toString()
                    )
            ));

            return result;
        }

        private @NonNull AlgorithmIdentifier signatureAlgorithm(final @NonNull KeyPair signedByKeyPair) {
            if (signedByKeyPair.getPrivate() instanceof RSAPrivateKey) {
                return new AlgorithmIdentifier(SHA256_WITH_RSA_ENCRYPTION, null);
            }
            return new AlgorithmIdentifier(SHA256_WITH_ECDSA, ByteString.EMPTY);
        }

        private @NonNull Validity validity() {
            final var _notBefore = (notBefore != -1L) ? notBefore : System.currentTimeMillis();
            final var _notAfter = (notAfter != -1L) ? notAfter : _notBefore + DEFAULT_DURATION_MILLIS;
            return new Validity(_notBefore, _notAfter);
        }

        private List<Extension> extensions() {
            List<Extension> result = new ArrayList<>();

            if (maxIntermediateCas != -1) {
                result.add(new Extension(
                        BASIC_CONSTRAINTS,
                        true,
                        new BasicConstraints(true, (long) maxIntermediateCas)));
            }

            if (!altNames.isEmpty()) {
                final var extensionValue =
                        altNames.stream()
                                .map(altName -> {
                                    if (HostnameUtils.canParseAsIpAddress(altName)) {
                                        try {
                                            return new DerAdapterValue(
                                                    GENERAL_NAME_IP_ADDRESS,
                                                    ByteString.of(InetAddress.getByName(altName).getAddress()));
                                        } catch (UnknownHostException uhe) {
                                            throw new JayoUnknownHostException(uhe);
                                        }
                                    }
                                    return new DerAdapterValue(GENERAL_NAME_DNS_NAME, altName);
                                }).toList();
                result.add(new Extension(SUBJECT_ALTERNATIVE_NAME, true, extensionValue));
            }

            // Must return an immutable collection
            return List.copyOf(result);
        }
    }
}
