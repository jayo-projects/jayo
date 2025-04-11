/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
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

import jayo.internal.tls.RealHandshake;
import jayo.tls.CipherSuite;
import jayo.tls.Handshake;
import jayo.tls.Protocol;
import jayo.tls.TlsVersion;
import org.jspecify.annotations.NonNull;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.Certificate;
import java.util.List;
import java.util.function.Supplier;

public final class JayoUtils {
    // un-instantiable
    private JayoUtils() {
    }

    public static void checkOffsetAndCount(final long size, final long offset, final long byteCount) {
        if ((offset | byteCount) < 0 || offset > size || size - offset < byteCount) {
            throw new IndexOutOfBoundsException("size=" + size + " offset=" + offset + " byteCount=" + byteCount);
        }
    }

    public static @NonNull String socketPeerName(@NonNull final Socket socket) {
        assert socket != null;

        final var address = socket.getRemoteSocketAddress();
        return (address instanceof InetSocketAddress inetSocketAddress)
                ? inetSocketAddress.getHostName()
                : address.toString();
    }

    /**
     * If {@code string} starts with the given {@code prefix}, returns a copy of this string with the prefix removed.
     * Otherwise, returns this string.
     */
    public static @NonNull String removePrefix(final @NonNull String string, final @NonNull String prefix) {
        if (string.startsWith(prefix)) {
            return string.substring(prefix.length());
        }
        return string;
    }

    public static @NonNull Handshake createHandshake(
            final @NonNull Protocol protocol,
            final @NonNull TlsVersion tlsVersion,
            final @NonNull CipherSuite cipherSuite,
            final @NonNull List<Certificate> localCertificates,
            final @NonNull Supplier<@NonNull List<Certificate>> peerCertificatesFn) {
        assert protocol != null;
        assert tlsVersion != null;
        assert cipherSuite != null;
        assert localCertificates != null;
        assert peerCertificatesFn != null;

        return new RealHandshake(
                protocol,
                tlsVersion,
                cipherSuite,
                localCertificates,
                peerCertificatesFn
        );
    }
}
