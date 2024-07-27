/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.ByteString;
import jayo.RawReader;
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

    public static @NonNull ByteString hash(final @NonNull RawReader rawReader, final @NonNull Digest digest) {
        Objects.requireNonNull(rawReader);
        Objects.requireNonNull(digest);
        final MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(digest.algorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm is not available : " + digest.algorithm(), e);
        }
        // todo should we use ReaderSegmentQueue.Async here because hash is a slow operation ?
        try (rawReader; final var segmentQueue = new ReaderSegmentQueue(rawReader)) {
            var toProcess = segmentQueue.expectSize(1L);
            while (toProcess != 0L) {
                var head = segmentQueue.headVolatile();
                var finished = false;
                while (!finished) {
                    assert head != null;
                    final var currentLimit = head.limitVolatile();
                    final var toRead = (int) Math.min(toProcess, currentLimit - head.pos);
                    messageDigest.update(head.data, head.pos, toRead);
                    head.pos += toRead;
                    segmentQueue.decrementSize(toRead);
                    toProcess -= toRead;
                    finished = toProcess == 0L;
                    if (head.pos == currentLimit) {
                        final var oldHead = head;
                        if (finished) {
                            if (head.tryRemove() && head.validateRemove()) {
                                segmentQueue.removeHead(head);
                            }
                        } else {
                            if (!head.tryRemove()) {
                                throw new IllegalStateException("Non tail segment should be removable");
                            }
                            head = segmentQueue.removeHead(head);
                        }
                        SegmentPool.recycle(oldHead);
                    }
                }
                toProcess = segmentQueue.expectSize(1L);
            }
        }
        return new RealByteString(messageDigest.digest());
    }

    public static @NonNull ByteString hmac(final @NonNull RawReader rawReader,
                                           final @NonNull Hmac hMac,
                                           final @NonNull ByteString key) {
        Objects.requireNonNull(rawReader);
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
        // todo should we use ReaderSegmentQueue.Async here because hmac is a slow operation ?
        try (rawReader; final var segmentQueue = new ReaderSegmentQueue(rawReader)) {
            var toProcess = segmentQueue.expectSize(1L);
            while (toProcess != 0L) {
                var head = segmentQueue.headVolatile();
                var finished = false;
                while (!finished) {
                    assert head != null;
                    final var currentLimit = head.limitVolatile();
                    final var toRead = (int) Math.min(toProcess, currentLimit - head.pos);
                    javaMac.update(head.data, head.pos, toRead);
                    head.pos += toRead;
                    segmentQueue.decrementSize(toRead);
                    toProcess -= toRead;
                    finished = toProcess == 0L;
                    if (head.pos == currentLimit) {
                        final var oldHead = head;
                        if (finished) {
                            if (head.tryRemove() && head.validateRemove()) {
                                segmentQueue.removeHead(head);
                            }
                        } else {
                            if (!head.tryRemove()) {
                                throw new IllegalStateException("Non tail segment should be removable");
                            }
                            head = segmentQueue.removeHead(head);
                        }
                        SegmentPool.recycle(oldHead);
                    }
                }
                toProcess = segmentQueue.expectSize(1L);
            }
        }
        return new RealByteString(javaMac.doFinal());
    }
}
