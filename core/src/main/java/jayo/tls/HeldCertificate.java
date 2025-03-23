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

import jayo.internal.tls.RealHeldCertificate;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;

/**
 * A certificate and its private key. These are some properties of certificates that are used with TLS:
 * <ul>
 * <li><b>A common name.</b> This is a string identifier for the certificate. It usually describes the purpose of the
 * certificate like "Entrust Root Certification Authority - G2" or "www.jayo.dev".
 * <li><b>A set of hostnames.</b> These are in the certificate's subject alternative name (SAN) extension. A subject
 * alternative name is either a literal hostname ("jayo.dev"), a literal IP address ("74.122.190.80"), or a hostname
 * pattern ("*.api.jayo.dev").
 * <li><b>A validity interval.</b> A certificate should not be used before its validity interval starts or after it
 * ends.
 * <li><b>A public key.</b> This cryptographic key is used for asymmetric encryption digital signatures. Note that the
 * private key is not a part of the certificate!
 * <li><b>A signature issued by another certificate's private key.</b> This mechanism allows a trusted third-party to
 * endorse a certificate. Third parties should only endorse certificates once they've confirmed that the owner of the
 * private key is also the owner of the certificate's other properties.
 * </ul>
 * Certificates are signed by other certificates and a sequence of them is called a certificate chain. The chain
 * terminates in a self-signed "root" certificate. Signing certificates in the middle of the chain are called
 * "intermediates". Organizations that offer certificate signing are called certificate authorities (CAs).
 * <p>
 * Browsers and other HTTP clients need a set of trusted root certificates to authenticate their peers. Sets of root
 * certificates are managed by either the HTTP client (like Firefox), or the host platform (like Android). In July 2018
 * Android had 134 trusted root certificates for its HTTP clients to trust.
 * <p>
 * For example, in order to establish a secure connection to {@code https://www.squareup.com/}, these three certificates
 * are used.
 * <pre>
 * {@code
 * www.squareup.com certificate:
 *
 * Common Name: www.squareup.com
 * Subject Alternative Names: www.squareup.com, squareup.com, account.squareup.com...
 * Validity: 2018-07-03T20:18:17Z – 2019-08-01T20:48:15Z
 * Public Key: d107beecc17325f55da976bcbab207ba4df68bd3f8fce7c3b5850311128264fd53e1baa342f58d93...
 * Signature: 1fb0e66fac05322721fe3a3917f7c98dee1729af39c99eab415f22d8347b508acdf0bab91781c3720...
 *
 * signed by intermediate certificate:
 *
 * Common Name: Entrust Certification Authority - L1M
 * Subject Alternative Names: none
 * Validity: 2014-12-15T15:25:03Z – 2030-10-15T15:55:03Z
 * Public Key: d081c13923c2b1d1ecf757dd55243691202248f7fcca520ab0ab3f33b5b08407f6df4e7ab0fb9822...
 * Signature: b487c784221a29c0a478ecf54f1bb484976f77eed4cf59afa843962f1d58dea6f3155b2ed9439c4c4...
 *
 * signed by root certificate:
 *
 * Common Name: Entrust Root Certification Authority - G2
 * Subject Alternative Names: none
 * Validity: 2009-07-07T17:25:54Z – 2030-12-07T17:55:54Z
 * Public Key: ba84b672db9e0c6be299e93001a776ea32b895411ac9da614e5872cffef68279bf7361060aa527d8...
 * Self-signed Signature: 799f1d96c6b6793f228d87d3870304606a6b9a2e59897311ac43d1f513ff8d392bc0f...
 * }
 * </pre>
 * In this example the HTTP client already knows and trusts the last certificate, "Entrust Root Certification Authority
 * - G2". That certificate is used to verify the signature of the intermediate certificate, "Entrust Certification
 * Authority - L1M". The intermediate certificate is used to verify the signature of the "www.squareup.com" certificate.
 * <p>
 * These roles are reversed for client authentication. In that case the client has a private key and a chain of
 * certificates. The server uses a set of trusted root certificates to authenticate the client. Subject alternative
 * names are not used for client authentication.
 */
public sealed interface HeldCertificate permits RealHeldCertificate {
    /**
     * Build a held certificate with reasonable defaults.
     */
    static @NonNull Builder builder() {
        return new RealHeldCertificate.Builder();
    }

    /**
     * Decodes a multiline string that contains both a {@linkplain #certificatePem() certificate} and a
     * {@linkplain #privateKeyPkcs8Pem() private key}, both
     * <a href="https://tools.ietf.org/html/rfc7468">PEM-encoded</a>. A typical input string looks like this:
     * <pre>
     * {@code
     * -----BEGIN CERTIFICATE-----
     * MIIBYTCCAQegAwIBAgIBKjAKBggqhkjOPQQDAjApMRQwEgYDVQQLEwtlbmdpbmVl
     * cmluZzERMA8GA1UEAxMIY2FzaC5hcHAwHhcNNzAwMTAxMDAwMDA1WhcNNzAwMTAx
     * MDAwMDEwWjApMRQwEgYDVQQLEwtlbmdpbmVlcmluZzERMA8GA1UEAxMIY2FzaC5h
     * cHAwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASda8ChkQXxGELnrV/oBnIAx3dD
     * ocUOJfdz4pOJTP6dVQB9U3UBiW5uSX/MoOD0LL5zG3bVyL3Y6pDwKuYvfLNhoyAw
     * HjAcBgNVHREBAf8EEjAQhwQBAQEBgghjYXNoLmFwcDAKBggqhkjOPQQDAgNIADBF
     * AiAyHHg1N6YDDQiY920+cnI5XSZwEGhAtb9PYWO8bLmkcQIhAI2CfEZf3V/obmdT
     * yyaoEufLKVXhrTQhRfodTeigi4RX
     * -----END CERTIFICATE-----
     * -----BEGIN PRIVATE KEY-----
     * MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCA7ODT0xhGSNn4ESj6J
     * lu/GJQZoU9lDrCPeUcQ28tzOWw==
     * -----END PRIVATE KEY-----
     * }
     * </pre>
     * The string should contain exactly one certificate and one private key in
     * <a href="https://tools.ietf.org/html/rfc5208">PKCS #8</a> format. It should not contain any other PEM-encoded
     * blocks, but it may contain other text which will be ignored.
     * <p>
     * One can encode a held certificate into this format by concatenating the results of
     * {@linkplain #certificatePem() certificatePem} and {@linkplain #privateKeyPkcs8Pem() privateKeyPkcs8Pem}.
     */
    static @NonNull HeldCertificate decode(final @NonNull String certificateAndPrivateKeyPem) {
        return RealHeldCertificate.decode(certificateAndPrivateKeyPem);
    }

    @NonNull
    KeyPair getKeyPair();

    @NonNull
    X509Certificate getCertificate();

    /**
     * @return the certificate encoded in <a href="https://tools.ietf.org/html/rfc7468">PEM format</a>.
     */
    @NonNull
    String certificatePem();

    /**
     * @return the private key encoded in <a href="https://tools.ietf.org/html/rfc5208">PKCS #8</a>
     * <a href="https://tools.ietf.org/html/rfc7468">PEM format</a>.
     */
    @NonNull
    String privateKeyPkcs8Pem();

    /**
     * @return the RSA private key encoded in <a href="https://tools.ietf.org/html/rfc8017">PKCS #1</a>
     * <a href="https://tools.ietf.org/html/rfc7468">PEM format</a>.
     */
    @NonNull
    String privateKeyPkcs1Pem();

    /**
     * The builder used to create a {@link HeldCertificate} with reasonable defaults.
     */
    sealed interface Builder permits RealHeldCertificate.Builder {
        /**
         * Sets the certificate to be valid in {@code [notBefore..notAfter]}.
         */
        @NonNull
        Builder validityInterval(final @NonNull Instant notBefore, final @NonNull Instant notAfter);

        /**
         * Sets the certificate to be valid immediately and until the specified duration has elapsed. The precision of
         * this field is milliseconds; further precision will be truncated.
         */
        @NonNull
        Builder duration(final @NonNull Duration duration);

        /**
         * Adds a subject alternative name (SAN) to the certificate. This is usually a literal hostname, a literal IP
         * address, or a hostname pattern. If no subject alternative names are added, that extension will be omitted.
         */
        @NonNull
        Builder addSubjectAlternativeName(final @NonNull String altName);

        /**
         * Set this certificate's common name (CN). If unset a random string will be used.
         * <p>
         * Historically this held the hostname of TLS certificate, but that practice was deprecated by
         * <a href="https://tools.ietf.org/html/rfc2818">RFC 2818</a> and replaced with
         * {@link #addSubjectAlternativeName(String)}.
         */
        @NonNull
        Builder commonName(final @NonNull String cn);

        /**
         * Sets the certificate's organizational unit (OU). If unset this field will be omitted.
         */
        @NonNull
        Builder organizationalUnit(final @NonNull String ou);

        /**
         * Sets this certificate's serial number. If unset the serial number will be 1.
         */
        @NonNull
        Builder serialNumber(final long serialNumber);

        /**
         * Sets this certificate's serial number. If unset the serial number will be 1.
         */
        @NonNull
        Builder serialNumber(final @NonNull BigInteger serialNumber);

        /**
         * Sets the public/private key pair used for this certificate. If unset a key pair will be generated.
         */
        @NonNull
        Builder keyPair(final @NonNull KeyPair keyPair);

        /**
         * Sets the public/private key pair used for this certificate. If unset a key pair will be generated.
         */
        @NonNull
        Builder keyPair(final @NonNull PublicKey publicKey, final @NonNull PrivateKey privateKey);

        /**
         * Set the certificate that will issue this certificate. If unset the certificate will be self-signed.
         */
        @NonNull
        Builder signedBy(final @Nullable HeldCertificate signedBy);

        /**
         * Set this certificate to be a signing certificate, with up to {@code maxIntermediateCas} intermediate signing
         * certificates beneath it.
         * <p>
         * By default, this certificate cannot not sign other certificates. Set this to 0 so this certificate can sign
         * other certificates (but those certificates cannot themselves sign certificates). Set this to 1 so this
         * certificate can sign intermediate certificates that can themselves sign certificates. Add one for each
         * additional layer of intermediates to permit.
         */
        @NonNull
        Builder certificateAuthority(final int maxIntermediateCas);

        /**
         * Set the certificate key format. If unset the certificate will use
         * {@linkplain CertificateKeyFormat#ECDSA_256 ECDSA_256}. Note that the default may change in future releases.
         */
        @NonNull
        Builder keyFormat(final @NonNull CertificateKeyFormat keyFormat);

        @NonNull
        HeldCertificate build();
    }

    enum CertificateKeyFormat {
        /**
         * Configure the certificate to generate a 256-bit ECDSA key, which provides about 128 bits of security. ECDSA
         * keys are noticeably faster than RSA keys.
         * <p>
         * This is the default configuration. Note that the default may change in future releases.
         */
        ECDSA_256("EC", 256),

        /**
         * Configure the certificate to generate a 2048-bit RSA key, which provides about 112 bits of security. RSA keys
         * are interoperable with very old clients that don't support ECDSA.
         */
        RSA_2048("RSA", 2048);

        private final @NonNull String keyAlgorithm;
        private final int keySize;

        CertificateKeyFormat(final @NonNull String keyAlgorithm, final int keySize) {
            assert keyAlgorithm != null;

            this.keyAlgorithm = keyAlgorithm;
            this.keySize = keySize;
        }

        public @NonNull String getKeyAlgorithm() {
            return keyAlgorithm;
        }

        public int getKeySize() {
            return keySize;
        }
    }
}
