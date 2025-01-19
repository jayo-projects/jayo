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

import jayo.internal.tls.platform.JdkJssePlatform;
import jayo.internal.tls.platform.JssePlatformUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509TrustManager;
import java.util.List;

/**
 * Access to platform-specific Java Secure Socket Extension (JSSE) features.
 * <p>
 * <b>Note:</b> Conscrypt adds Session Tickets support.
 */
public sealed interface JssePlatform permits JdkJssePlatform {
    static @NonNull JssePlatform get() {
        return JssePlatformUtils.get();
    }

    @NonNull
    SSLContext newSSLContext();

    @NonNull
    X509TrustManager platformTrustManager();

    /**
     * Configure TLS extensions on {@code sslEngine} for {@code hostname}.
     */
    void configureTlsExtensions(final @NonNull SSLEngine sslEngine,
                                final @Nullable String hostname,
                                final @NonNull List<Protocol> protocols);

    /**
     * Returns the negotiated protocol, or null if no protocol was negotiated.
     */
    @Nullable
    String getSelectedProtocol(final @NonNull SSLEngine sslEngine);
}
