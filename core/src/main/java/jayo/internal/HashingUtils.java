/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.RawReader;
import jayo.Reader;
import jayo.bytestring.ByteString;
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

    /**
     * Consume the whole {@code rawReader} and hash its content
     */
    public static @NonNull ByteString hash(final @NonNull RawReader rawReader, final @NonNull Digest digest) {
        Objects.requireNonNull(rawReader);
        Objects.requireNonNull(digest);

        final MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(digest.toString());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm is not available : " + digest, e);
        }
        try (final var reader = (rawReader instanceof Reader _reader) ? _reader : new RealReader(rawReader)) {
            // exhaust the Reader
            reader.request(Long.MAX_VALUE);
            final var buffer = Utils.internalBuffer(reader);

            var segment = buffer.head;
            while (segment != null) {
                messageDigest.update(segment.data, segment.pos, segment.limit - segment.pos);
                final var removed = segment;
                segment = segment.pop();
                SegmentPool.recycle(removed);
            }

            buffer.byteSize = 0L;
            buffer.head = null;
        }
        return new RealByteString(messageDigest.digest());
    }

    /**
     * Consume the whole {@code rawReader} and hmac its content
     */
    public static @NonNull ByteString hmac(final @NonNull RawReader rawReader,
                                           final @NonNull Hmac hMac,
                                           final @NonNull ByteString key) {
        Objects.requireNonNull(rawReader);
        Objects.requireNonNull(hMac);
        Objects.requireNonNull(key);

        final javax.crypto.Mac javaMac;
        try {
            javaMac = javax.crypto.Mac.getInstance(hMac.toString());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm is not available : " + hMac, e);
        }
        try {
            javaMac.init(new SecretKeySpec(Utils.internalArray(key), hMac.toString()));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("InvalidKeyException was fired with the provided ByteString key", e);
        }
        try (final var reader = (rawReader instanceof Reader _reader) ? _reader : new RealReader(rawReader)) {
            // exhaust the Reader
            reader.request(Long.MAX_VALUE);
            final var buffer = Utils.internalBuffer(reader);

            var segment = buffer.head;
            while (segment != null) {
                assert segment != null;
                javaMac.update(segment.data, segment.pos, segment.limit - segment.pos);
                final var removed = segment;
                segment = segment.pop();
                SegmentPool.recycle(removed);
            }

            buffer.byteSize = 0L;
            buffer.head = null;
        }
        return new RealByteString(javaMac.doFinal());
    }
}
