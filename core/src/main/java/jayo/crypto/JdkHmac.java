/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.crypto;

import org.jspecify.annotations.NonNull;

/**
 * JVM included macs. There is no guaranty that all JVM provides all these algorithms.
 */
public enum JdkHmac implements Hmac {
    /**
     * 128-bit "Message Authentication Code" (MAC) algorithm.
     * <p>
     * MD5 has been vulnerable to collisions since 2004. <b>It should not be used in new code.</b>
     * <p>
     * MD5 is both insecure and obsolete because it is inexpensive to reverse! This hash is offered because it is
     * popular and convenient for use in legacy systems that are not security-sensitive.
     */
    HMAC_MD5("HmacMD5"),

    /**
     * 160-bit SHA-1 "Message Authentication Code" (MAC) algorithm.
     * <p>
     * SHA-1 has been vulnerable to collisions since 2017. <b>It should not be used in new code.</b>
     * <p>
     * Consider upgrading from SHA-1 to SHA-256 ! This hash is offered because it is popular and convenient for use in
     * legacy systems that are not security-sensitive.
     */
    HMAC_SHA_1("HmacSHA1"),

    /**
     * 224-bit SHA-224 "Message Authentication Code" (MAC) algorithm.
     */
    HMAC_SHA_224("HmacSHA224"),

    /**
     * 256-bit SHA-256 "Message Authentication Code" (MAC) algorithm.
     */
    HMAC_SHA_256("HmacSHA256"),

    /**
     * 384-bit SHA-384 "Message Authentication Code" (MAC) algorithm.
     */
    HMAC_SHA_384("HmacSHA384"),

    /**
     * 512-bit SHA-512 "Message Authentication Code" (MAC) algorithm.
     */
    HMAC_SHA_512("HmacSHA512"),

    /**
     * 224-bit SHA-512/224 "Message Authentication Code" (MAC) algorithm, a truncated variant of SHA-512.
     */
    HMAC_SHA_512_224("HmacSHA512/224"),

    /**
     * 256-bit SHA-512/256 "Message Authentication Code" (MAC) algorithm, a truncated variant of SHA-512.
     */
    HMAC_SHA_512_256("HmacSHA512/256"),

    /**
     * 224-bit SHA3-224 "Message Authentication Code" (MAC) algorithm.
     */
    HMAC_SHA3_224("HmacSHA3-224"),

    /**
     * 256-bit SHA3-256 "Message Authentication Code" (MAC) algorithm.
     */
    HMAC_SHA3_256("HmacSHA3-256"),

    /**
     * 384-bit SHA3-384 "Message Authentication Code" (MAC) algorithm.
     */
    HMAC_SHA3_384("HmacSHA3-384"),

    /**
     * 512-bit SHA3-512 "Message Authentication Code" (MAC) algorithm.
     */
    HMAC_SHA3_512("HmacSHA3-512"),
    ;

    private final @NonNull String algorithm;

    JdkHmac(final @NonNull String algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public @NonNull String toString() {
        return algorithm;
    }
}
