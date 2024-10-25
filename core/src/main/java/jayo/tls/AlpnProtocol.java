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

import jayo.JayoProtocolException;
import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.net.URL;
import java.util.Objects;

/**
 * Protocols for <a href="https://tools.ietf.org/html/draft-ietf-tls-applayerprotoneg">ALPN</a> selection.
 * <h3>Protocol vs Scheme</h3>
 * Despite its name, {@link URL#getProtocol()} returns the {@linkplain URI#getScheme() scheme} (http,
 * https, etc.) of the URL, not the protocol (http/1.1, spdy/3.1, etc.). Jayo uses the word <b>protocol</b> to identify
 * how HTTP messages are framed.
 */
public enum AlpnProtocol {
    /**
     * An obsolete plaintext framing protocol that does not use persistent sockets by default.
     */
    HTTP_1_0("http/1.0"),

    /**
     * The IETF plaintext framing protocol that includes persistent socket connections.
     * <p>
     * See also <a href="https://tools.ietf.org/html/rfc7230">RFC 7230</a>
     */
    HTTP_1_1("http/1.1"),

    /**
     * The IETF's binary-framed protocol that includes header compression, multiplexing multiple requests on the same
     * socket, and server-push. HTTP/1.1 semantics are layered on HTTP/2.
     * <p>
     * HTTP/2 requires at least TLS 1.2 support {@code TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256}. Servers that enforce this
     * may send an exception message including the string {@code INADEQUATE_SECURITY}.
     */
    HTTP_2("h2"),

    /**
     * Cleartext HTTP/2 with no "upgrade" round trip. This option requires the client to have prior knowledge that the
     * server supports cleartext HTTP/2.
     * <p>
     * See also <a href="https://tools.ietf.org/html/rfc7540.section-3.4">RFC 7540: Starting HTTP/2 with Prior Knowledge</a>
     */
    H2_PRIOR_KNOWLEDGE("h2_prior_knowledge"),

    /**
     * QUIC (Quick UDP Internet Connection) is a new multiplexed and secure transport atop UDP, designed from the ground
     * up and optimized for HTTP/2 semantics.
     */
    QUIC("quic"),

    /**
     * HTTP/3 is the third major version of the Hypertext Transfer Protocol used to exchange information. HTTP/3 runs
     * over QUIC, which is published as <a href="https://tools.ietf.org/html/rfc9000">RFC 9000</a>.
     */
    HTTP_3("h3"),
    ;

    private final @NonNull String protocol;

    AlpnProtocol(final @NonNull String protocol) {
        this.protocol = protocol;
    }

    /**
     * @return the string used to identify this protocol for ALPN, like "http/1.1" or "h2".
     * <p>
     * See also <a href="https://www.iana.org/assignments/tls-extensiontype-values">IANA tls-extensiontype-values</a>
     */
    @Override
    public @NonNull String toString() {
        return protocol;
    }

    public static @NonNull AlpnProtocol get(final @NonNull String protocol) {
        Objects.requireNonNull(protocol);
        // Unroll the loop over values() to save an allocation.
        return switch (protocol) {
            case "http/1.0" -> HTTP_1_0;
            case "http/1.1" -> HTTP_1_1;
            case "h2" -> HTTP_2;
            case "h2_prior_knowledge" -> H2_PRIOR_KNOWLEDGE;
            case "quic" -> QUIC;
            case "h3" -> HTTP_3;
            default -> {
                // Support HTTP3 draft like h3-29
                if (protocol.startsWith("h3")) {
                    yield HTTP_3;
                } else {
                    throw new JayoProtocolException("Unexpected protocol: " + protocol);
                }
            }
        };
    }
}
