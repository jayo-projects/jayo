/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.samples.tls;

import jayo.tls.ServerHandshakeCertificates;

import javax.net.ssl.KeyManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

public class CertificateFactory {
    public static ServerHandshakeCertificates authenticatedCertificate() throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream keystoreFile =
                     TlsSocketClient.class.getClassLoader().getResourceAsStream("keystore.jks")) {
            char[] password = "password".toCharArray();
            ks.load(keystoreFile, password);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password);
            return ServerHandshakeCertificates.create(kmf);
        }
    }
}
