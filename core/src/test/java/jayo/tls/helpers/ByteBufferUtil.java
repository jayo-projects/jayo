/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.tls.helpers;

import java.nio.ByteBuffer;

public class ByteBufferUtil {

    public static void copy(ByteBuffer src, ByteBuffer dst, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("negative length");
        }
        if (src.remaining() < length) {
            throw new IllegalArgumentException(String.format(
                    "source buffer does not have enough remaining capacity (%d < %d)", src.remaining(), length));
        }
        if (dst.remaining() < length) {
            throw new IllegalArgumentException(String.format(
                    "destination buffer does not have enough remaining capacity (%d < %d)", dst.remaining(), length));
        }
        if (length == 0) {
            return;
        }
        ByteBuffer tmp = src.duplicate();
        tmp.limit(src.position() + length);
        dst.put(tmp);
        src.position(src.position() + length);
    }
}
