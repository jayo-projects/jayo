/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.tls.helpers;

import jayo.tls.ClientHandshakeCertificates;
import jayo.tls.ServerHandshakeCertificates;
import jayo.tls.TlsVersion;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class CertificateFactory {
    public static final int TLS_MAX_DATA_SIZE = (int) Math.pow(2, 14);
    public static final String CERTIFICATE_COMMON_NAME = "name"; // must match what's in the certificates

    private final TlsVersion version;
    private final SSLContext defaultContext;
    private final TrustManagerFactory tmf;
    private final KeyManagerFactory kmf;
    private final List<String> allCiphers;

    public CertificateFactory(TlsVersion version) {
        this.version = version;
        try {
            SSLContext sslContext = SSLContext.getInstance(version.getJavaName());
            KeyStore ks = KeyStore.getInstance("JKS");
            try (InputStream keystoreFile = getClass().getClassLoader().getResourceAsStream("keystore.jks")) {
                char[] password = "password".toCharArray();
                ks.load(keystoreFile, password);
                tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ks);
                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(ks, password);

                sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            }
            this.defaultContext = sslContext;
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
        this.allCiphers = ciphers(defaultContext).stream().sorted().toList();
    }

    public ServerHandshakeCertificates getServerHandshakeCertificates() {
        return ServerHandshakeCertificates.create(kmf, null, version);
    }

    public ClientHandshakeCertificates getClientHandshakeCertificates() {
        return ClientHandshakeCertificates.create(tmf, null, version);
    }

    public CertificateFactory() {
        this(TlsVersion.TLS_1_2);
    }

    private List<String> ciphers(SSLContext ctx) {
        return Arrays.stream(ctx.createSSLEngine().getSupportedCipherSuites())
                // this is not a real cipher, but a hack actually
                .filter(c -> !Objects.equals(c, "TLS_EMPTY_RENEGOTIATION_INFO_SCSV"))
                // disable problematic ciphers
                .filter(c -> !Arrays.asList(
                                "TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5",
                                "TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA",
                                "TLS_KRB5_EXPORT_WITH_RC4_40_MD5",
                                "TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
                                "TLS_KRB5_WITH_3DES_EDE_CBC_MD5",
                                "TLS_KRB5_WITH_3DES_EDE_CBC_SHA",
                                "TLS_KRB5_WITH_DES_CBC_MD5",
                                "TLS_KRB5_WITH_DES_CBC_SHA",
                                "TLS_KRB5_WITH_RC4_128_MD5",
                                "TLS_KRB5_WITH_RC4_128_SHA",
                                "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                                "SSL_RSA_EXPORT_WITH_RC4_40_MD5")
                        .contains(c))
                // No SHA-2 with TLS < 1.2
                .filter(c -> Arrays.asList("TLSv1.2", "TLSv1.3").contains(version.getJavaName())
                        || !c.endsWith("_SHA256") && !c.endsWith("_SHA384"))
                // Disable cipher only supported in TLS >= 1.3
                .filter(c -> version.getJavaName().compareTo("TLSv1.3") > 0
                        || !Arrays.asList("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384")
                        .contains(c))
                // https://bugs.openjdk.java.net/browse/JDK-8224997
                .filter(c -> !c.endsWith("_CHACHA20_POLY1305_SHA256"))
                // Anonymous ciphers are problematic because they are disabled in some VMs
                .filter(c -> !c.contains("_anon_"))
                .toList();
    }

    public SSLContext getDefaultContext() {
        return defaultContext;
    }

    public List<String> getAllCiphers() {
        return allCiphers;
    }
}
