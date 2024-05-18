/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.ByteString;
import jayo.RawSource;
import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import org.jspecify.annotations.NonNull;

import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public final class HashingUtils {
    // un-instantiable
    private HashingUtils() {
    }

    public static @NonNull ByteString hash(final @NonNull RawSource rawSource, final @NonNull Digest digest) {
        Objects.requireNonNull(rawSource);
        Objects.requireNonNull(digest);
        final MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(digest.algorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm is not available : " + digest.algorithm(), e);
        }
        try (rawSource; final var segmentQueue = new SyncSourceSegmentQueue(rawSource)) {
            while (segmentQueue.expectSize(1L) != 0L) {
                final var headNode = segmentQueue.lockedReadableHead();
                try {
                    final var head = headNode.segment();
                    final var toRead = head.limit() - head.pos();
                    messageDigest.update(head.data(), head.pos(), toRead);
                    segmentQueue.decrementSize(toRead);
                    SegmentPool.recycle(segmentQueue.removeHead());
                } finally {
                    headNode.unlock();
                }
            }
        }
        return new RealByteString(messageDigest.digest());
    }

    public static @NonNull ByteString hmac(final @NonNull RawSource rawSource,
                                           final @NonNull Hmac hMac,
                                           final @NonNull ByteString key) {
        Objects.requireNonNull(rawSource);
        Objects.requireNonNull(key);
        Objects.requireNonNull(hMac);
        final javax.crypto.Mac javaMac;
        try {
            javaMac = javax.crypto.Mac.getInstance(hMac.algorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm is not available : " + hMac.algorithm(), e);
        }
        if (!(key instanceof RealByteString _key)) {
            throw new IllegalArgumentException("key must be an instance of RealByteString");
        }
        try {
            javaMac.init(new SecretKeySpec(_key.internalArray(), hMac.algorithm()));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("InvalidKeyException was fired with the provided ByteString key", e);
        }
        try (rawSource; final var segmentQueue = new SyncSourceSegmentQueue(rawSource)) {
            while (segmentQueue.expectSize(1L) != 0L) {
                final var headNode = segmentQueue.lockedReadableHead();
                try {
                    final var head = headNode.segment();
                    final var toRead = head.limit() - head.pos();
                    javaMac.update(head.data(), head.pos(), toRead);
                    segmentQueue.decrementSize(toRead);
                    SegmentPool.recycle(segmentQueue.removeHead());
                } finally {
                    headNode.unlock();
                }
            }
        }
        return new RealByteString(javaMac.doFinal());
    }
}
