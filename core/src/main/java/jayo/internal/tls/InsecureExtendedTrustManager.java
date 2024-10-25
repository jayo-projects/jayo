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

import jayo.external.JayoUtils;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;

/**
 * This extends {@link X509ExtendedTrustManager} to disable verification for a set of hosts.
 */
final class InsecureExtendedTrustManager extends X509ExtendedTrustManager {
    private final @NonNull X509ExtendedTrustManager delegate;
    private final @NonNull List<String> insecureHosts;

    InsecureExtendedTrustManager(final @NonNull X509ExtendedTrustManager delegate,
                                 final @NonNull List<String> insecureHosts) {
        this.delegate = Objects.requireNonNull(delegate);
        this.insecureHosts = Objects.requireNonNull(insecureHosts);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        if (!insecureHosts.contains(JayoUtils.socketPeerName(socket))) {
            delegate.checkServerTrusted(chain, authType, socket);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        if (!insecureHosts.contains(engine.getPeerHost())) {
            delegate.checkServerTrusted(chain, authType, engine);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        throw new CertificateException("Unsupported operation");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        throw new CertificateException("Unsupported operation");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        throw new CertificateException("Unsupported operation");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        throw new CertificateException("Unsupported operation");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return delegate.getAcceptedIssuers();
    }
}
