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

import jayo.Buffer;
import jayo.Reader;
import jayo.Writer;
import jayo.internal.JavaVersionUtils;
import jayo.internal.Utils;
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
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * A set of tools provided and used internally by Jayo and Jayo HTTP that can be useful to other libraries.
 */
@SuppressWarnings("unused")
public final class JayoUtils {
    // un-instantiable
    private JayoUtils() {
    }

    /**
     * Note: feel free to use this method to build an executor for your own purposes, but it has been optimized for our
     * IO-oriented subprojects like jayo-http. It may not be the best option for other uses.
     * @return a new executor for running async tasks.
     * @implNote New threads created by this executor will <b>not inherit thread-local variables</b> from the
     * constructing thread.
     */
    public static @NonNull ExecutorService executorService(final @NonNull String prefix, final boolean isDaemon) {
        Objects.requireNonNull(prefix);
        return JavaVersionUtils.executorService(prefix, isDaemon);
    }

    public static void close(final @NonNull ExecutorService executor) {
        Objects.requireNonNull(executor);
        JavaVersionUtils.close(executor);
    }

    public static void checkOffsetAndCount(final long size, final long offset, final long byteCount) {
        if ((offset | byteCount) < 0 || offset > size || size - offset < byteCount) {
            throw new IndexOutOfBoundsException("size=" + size + " offset=" + offset + " byteCount=" + byteCount);
        }
    }

    public static @NonNull String socketPeerName(@NonNull final Socket socket) {
        Objects.requireNonNull(socket);

        final var address = socket.getRemoteSocketAddress();
        return (address instanceof InetSocketAddress inetSocketAddress)
                ? inetSocketAddress.getHostName()
                : address.toString();
    }

    public static @NonNull Handshake createHandshake(
            final @NonNull Protocol protocol,
            final @NonNull TlsVersion tlsVersion,
            final @NonNull CipherSuite cipherSuite,
            final @NonNull List<Certificate> localCertificates,
            final @NonNull Supplier<@NonNull List<Certificate>> peerCertificatesFn) {
        Objects.requireNonNull(protocol);
        Objects.requireNonNull(tlsVersion);
        Objects.requireNonNull(cipherSuite);
        Objects.requireNonNull(localCertificates);
        Objects.requireNonNull(peerCertificatesFn);

        return new RealHandshake(
                protocol,
                tlsVersion,
                cipherSuite,
                localCertificates,
                peerCertificatesFn
        );
    }

    public static @NonNull Buffer buffer(final @NonNull Reader reader) {
        Objects.requireNonNull(reader);
        return Utils.internalBuffer(reader);
    }

    public static @NonNull Buffer buffer(final @NonNull Writer writer) {
        Objects.requireNonNull(writer);
        return Utils.internalBuffer(writer);
    }
}
