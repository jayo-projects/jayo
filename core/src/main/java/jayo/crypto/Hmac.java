/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.crypto;

import org.jspecify.annotations.NonNull;

/**
 * A "<b>M</b>essage <b>A</b>uthentication <b>C</b>ode" (MAC) algorithm. A MAC provides a way to check the integrity of
 * information transmitted over or stored in an unreliable medium, based on a secret key. Typically, message
 * authentication codes are used between two parties that share a secret key to validate information transmitted between
 * these parties. A MAC mechanism based on cryptographic hash functions is referred to as HMAC. HMAC can be used with
 * any cryptographic hash function, e.g., SHA256 or SHA384, in combination with a shared secret key.
 * @see JdkHmac
 */
public interface Hmac {
    /**
     * @return the string MAC algorithm used as parameter in
     * {@linkplain javax.crypto.Mac#getInstance(String) Mac.getInstance(algorithm)}
     */
    @Override
    @NonNull
    String toString();
}
