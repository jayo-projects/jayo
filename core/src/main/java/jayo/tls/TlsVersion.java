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

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Versions of TLS that can be offered when negotiating a secure socket.
 *
 * @see javax.net.ssl.SSLSocket#setEnabledProtocols(String[])
 */
public enum TlsVersion {
    TLS_1_3("TLSv1.3"), // 2016.
    TLS_1_2("TLSv1.2"), // 2008.
    TLS_1_1("TLSv1.1"), // 2006 (obsolete).
    TLS_1_0("TLSv1"), // 1999 (obsolete).
    SSL_3_0("SSLv3"), // 1996 (obsolete).
    ;

    private final @NonNull String javaName;

    TlsVersion(@NonNull String javaName) {
        this.javaName = javaName;
    }

    public final @NonNull String getJavaName() {
        return javaName;
    }

    @Override
    public @NonNull String toString() {
        return javaName;
    }

    public static @NonNull TlsVersion fromJavaName(final @NonNull String javaName) {
        Objects.requireNonNull(javaName);
        return switch (javaName) {
            case "TLSv1.3" -> TLS_1_3;
            case "TLSv1.2" -> TLS_1_2;
            case "TLSv1.1" -> TLS_1_1;
            case "TLSv1" -> TLS_1_0;
            case "SSLv3" -> SSL_3_0;
            default -> throw new IllegalArgumentException("Unexpected TLS version: " + javaName);
        };
    }
}
