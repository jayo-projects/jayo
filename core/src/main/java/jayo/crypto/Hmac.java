/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.crypto;

import org.jspecify.annotations.NonNull;

/**
 * "Message Authentication Code" (MAC) algorithm.
 * A MAC provides a way to check the integrity of information transmitted over or stored in an unreliable medium, based
 * on a secret key. Typically, message authentication codes are used between two parties that share a secret key in
 * order to validate information transmitted between these parties. A MAC mechanism that is based on cryptographic hash
 * functions is referred to as HMAC. HMAC can be used with any cryptographic hash function, e.g., SHA256 or SHA384,
 * in combination with a secret shared key.
 *
 * @param algorithm the name of the requested algorithm.
 */
public record Hmac(@NonNull String algorithm) {
}
