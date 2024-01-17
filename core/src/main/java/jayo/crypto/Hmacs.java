/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.crypto;

import org.jspecify.annotations.NonNull;

/**
 * JVM included macs. There is no guaranty that all JVM provides all these algorithms.
 */
public final class Hmacs {
    // un-instantiable
    private Hmacs() {
    }

    /**
     * 128-bit "Message Authentication Code" (MAC) algorithm.
     * <p>
     * MD5 has been vulnerable to collisions since 2004. <b>It should not be used in new code.</b>
     * <p>
     * MD5 is both insecure and obsolete because it is inexpensive to reverse! This hash is offered because it is
     * popular and convenient for use in legacy systems that are not security-sensitive.
     */
    public static final @NonNull Hmac HMAC_MD5 = new Hmac("HmacMD5");

    /**
     * 160-bit SHA-1 "Message Authentication Code" (MAC) algorithm.
     * <p>
     * SHA-1 has been vulnerable to collisions since 2017. <b>It should not be used in new code.</b>
     * <p>
     * Consider upgrading from SHA-1 to SHA-256 ! This hash is offered because it is popular and convenient for use in
     * legacy systems that are not security-sensitive.
     */
    public static final @NonNull Hmac HMAC_SHA_1 = new Hmac("HmacSHA1");

    /**
     * 224-bit SHA-224 "Message Authentication Code" (MAC) algorithm.
     */
    public static final @NonNull Hmac HMAC_SHA_224 = new Hmac("HmacSHA224");

    /**
     * 256-bit SHA-256 "Message Authentication Code" (MAC) algorithm.
     */
    public static final @NonNull Hmac HMAC_SHA_256 = new Hmac("HmacSHA256");

    /**
     * 384-bit SHA-384 "Message Authentication Code" (MAC) algorithm.
     */
    public static final @NonNull Hmac HMAC_SHA_384 = new Hmac("HmacSHA384");

    /**
     * 512-bit SHA-512 "Message Authentication Code" (MAC) algorithm.
     */
    public static final @NonNull Hmac HMAC_SHA_512 = new Hmac("HmacSHA512");

    /**
     * 224-bit SHA-512/224 "Message Authentication Code" (MAC) algorithm, a truncated variant of SHA-512.
     */
    public static final @NonNull Hmac HMAC_SHA_512_224 = new Hmac("HmacSHA512/224");

    /**
     * 256-bit SHA-512/256 "Message Authentication Code" (MAC) algorithm, a truncated variant of SHA-512.
     */
    public static final @NonNull Hmac HMAC_SHA_512_256 = new Hmac("HmacSHA512/256");

    /**
     * 224-bit SHA3-224 "Message Authentication Code" (MAC) algorithm.
     */
    public static final @NonNull Hmac HMAC_SHA3_224 = new Hmac("HmacSHA3-224");

    /**
     * 256-bit SHA3-256 "Message Authentication Code" (MAC) algorithm.
     */
    public static final @NonNull Hmac HMAC_SHA3_256 = new Hmac("HmacSHA3-256");

    /**
     * 384-bit SHA3-384 "Message Authentication Code" (MAC) algorithm.
     */
    public static final @NonNull Hmac HMAC_SHA3_384 = new Hmac("HmacSHA3-384");

    /**
     * 512-bit SHA3-512 "Message Authentication Code" (MAC) algorithm.
     */
    public static final @NonNull Hmac HMAC_SHA3_512 = new Hmac("HmacSHA3-512");
}
