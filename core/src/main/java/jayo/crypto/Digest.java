/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.crypto;

import org.jspecify.annotations.NonNull;

/**
 * A message digest algorithm. Message digests are secure one-way cryptographic hash functions, e.g., SHA256 or SHA384,
 * that take arbitrary-sized data and output a fixed-length hash value.
 * @see JdkDigest
 */
public interface Digest {
    /**
     * @return the string digest algorithm used as parameter in
     * {@linkplain java.security.MessageDigest#getInstance(String) MessageDigest.getInstance(algorithm)}
     */
    @Override
    @NonNull
    String toString();
}
