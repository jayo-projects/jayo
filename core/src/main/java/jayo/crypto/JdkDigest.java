/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.crypto;

import org.jspecify.annotations.NonNull;

/**
 * JDK included digests. There is no guaranty that all JVM provides all these algorithms.
 */
public enum JdkDigest implements Digest {
    /**
     * 128-bit MD5 hash algorithm.
     * <p>
     * MD5 has been vulnerable to collisions since 2004. <b>It should not be used in new code.</b>
     * <p>
     * MD5 is both insecure and obsolete because it is inexpensive to reverse! This hash is offered because it is
     * popular and convenient for use in legacy systems that are not security-sensitive.
     */
    MD5("MD5"),

    /**
     * 160-bit SHA-1 hash algorithm.
     * <p>
     * SHA-1 has been vulnerable to collisions since 2017. <b>It should not be used in new code.</b>
     * <p>
     * Consider upgrading from SHA-1 to SHA-256 ! This hash is offered because it is popular and convenient for use in
     * legacy systems that are not security-sensitive.
     */
    SHA_1("SHA-1"),

    /**
     * 224-bit SHA-224 hash algorithm.
     */
    SHA_224("SHA-224"),

    /**
     * 256-bit SHA-256 hash algorithm.
     */
    SHA_256("SHA-256"),

    /**
     * 384-bit SHA-384 hash algorithm.
     */
    SHA_384("SHA-384"),

    /**
     * 512-bit SHA-512 hash algorithm.
     */
    SHA_512("SHA-512"),

    /**
     * 224-bit SHA-512/224 hash algorithm, a truncated variant of SHA-512.
     */
    SHA_512_224("SHA-512/224"),

    /**
     * 256-bit SHA-512/256 hash algorithm, a truncated variant of SHA-512.
     */
    SHA_512_256("SHA-512/256"),

    /**
     * 224-bit SHA3-224 hash algorithm.
     */
    SHA3_224("SHA3-224"),

    /**
     * 256-bit SHA3-256 hash algorithm.
     */
    SHA3_256("SHA3-256"),

    /**
     * 384-bit SHA3-384 hash algorithm.
     */
    SHA3_384("SHA3-384"),

    /**
     * 512-bit SHA3-512 hash algorithm.
     */
    SHA3_512("SHA3-512"),
    ;

    private final @NonNull String algorithm;

    JdkDigest(final @NonNull String algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public @NonNull String toString() {
        return algorithm;
    }
}
