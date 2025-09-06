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

import java.util.Objects;

import static jayo.internal.Utils.mac;
import static jayo.internal.Utils.messageDigest;

public final class HashingUtils {
    // un-instantiable
    private HashingUtils() {
    }

    /**
     * Consume the whole {@code rawReader} and hash its content
     */
    public static @NonNull ByteString hash(final @NonNull RawReader rawReader, final @NonNull Digest digest) {
        Objects.requireNonNull(rawReader);

        final var messageDigest = messageDigest(digest);
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

        final var javaMac = mac(hMac, key);
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
