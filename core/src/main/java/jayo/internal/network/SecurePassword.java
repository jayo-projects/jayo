/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network;

import org.jspecify.annotations.NonNull;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

final class SecurePassword {
    private static final @NonNull String ALGORITHM = "AES";
    private static final @NonNull String TRANSFORMATION = "AES/CFB/PKCS5Padding";
    private static final @NonNull SecureRandom SECURE_RANDOM = new SecureRandom();

    private final @NonNull SecretKey secretKey;
    private final byte @NonNull [] iv;
    private final byte @NonNull [] encrypted;

    SecurePassword(final @NonNull String toEncrypt, final @NonNull Charset charset) {
        assert toEncrypt != null;
        assert charset != null;

        try {
            secretKey = generateKey();
            iv = generateIv();

            // encrypt
            final var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));

            final var toEncryptBytes = toEncrypt.getBytes(charset);

            encrypted = cipher.doFinal(toEncryptBytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalStateException(e);
        }
    }

    public byte @NonNull [] decrypt() {
        try {
            final var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
            return cipher.doFinal(encrypted);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static @NonNull SecretKey generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(256); // AES supports key sizes of 128, 192, and 256 bits
        return keyGen.generateKey();
    }

    private static byte @NonNull [] generateIv() {
        final var iv = new byte[16];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }
}
