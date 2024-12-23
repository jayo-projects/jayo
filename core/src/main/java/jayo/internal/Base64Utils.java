/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package jayo.internal;

import jayo.ByteString;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author Alexander Y. Kleymenov
 */
public final class Base64Utils {
    // un-instantiable
    private Base64Utils() {
    }

    private static final byte @NonNull [] BASE64 = ((RealByteString) ByteString
            .encode("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"))
            .data;

    private static final byte @NonNull [] BASE64_URL_SAFE = ((RealByteString) ByteString
            .encode("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"))
            .data;

    public static byte @Nullable [] decodeBase64ToArray(final @NonNull CharSequence reader) {
        // Ignore trailing '=' padding and whitespace from the input.
        var limit = Objects.requireNonNull(reader).length();
        while (limit > 0) {
            final var c = reader.charAt(limit - 1);
            if (c != '=' && c != '\n' && c != '\r' && c != ' ' && c != '\t') {
                break;
            }
            limit--;
        }

        // If the input includes whitespace, this output array will be longer than necessary.
        final var out = new byte[(int) (limit * 6L / 8L)];
        var outCount = 0;
        var inCount = 0;

        var word = 0;
        for (var pos = 0; pos < limit; pos++) {
            final var c = reader.charAt(pos);

            final int bits;
            if (c >= 'A' && c <= 'Z') {
                // char ASCII value
                //  A    65    0
                //  Z    90    25 (ASCII - 65)
                bits = ((int) c) - 65;
            } else if (c >= 'a' && c <= 'z') {
                // char ASCII value
                //  a    97    26
                //  z    122   51 (ASCII - 71)
                bits = ((int) c) - 71;
            } else if (c >= '0' && c <= '9') {
                // char ASCII value
                //  0    48    52
                //  9    57    61 (ASCII + 4)
                bits = ((int) c) + 4;
            } else if (c == '+' || c == '-') {
                bits = 62;
            } else if (c == '/' || c == '_') {
                bits = 63;
            } else if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
                continue;
            } else {
                return null;
            }

            // Append this char's 6 bits to the word.
            word = word << 6 | bits;

            // For every 4 chars of input, we accumulate 24 bits of output. Emit 3 bytes.
            inCount++;
            if (inCount % 4 == 0) {
                out[outCount++] = (byte) (word >> 16);
                out[outCount++] = (byte) (word >> 8);
                out[outCount++] = (byte) word;
            }
        }

        final var lastWordChars = inCount % 4;
        switch (lastWordChars) {
            case 1 -> {
                // We read 1 char followed by "===". But 6 bits is a truncated byte! Fail.
                return null;
            }
            case 2 -> {
                // We read 2 chars followed by "==". Emit 1 byte with 8 of those 12 bits.
                word = word << 12;
                out[outCount++] = (byte) (word >> 16);
            }
            case 3 -> {
                // We read 3 chars, followed by "=". Emit 2 bytes for 16 of those 18 bits.
                word = word << 6;
                out[outCount++] = (byte) (word >> 16);
                out[outCount++] = (byte) (word >> 8);
            }
        }

        // If we sized our out array perfectly, we're done.
        if (outCount == out.length) {
            return out;
        }

        // Copy the decoded bytes to a new, right-sized array.
        return Arrays.copyOf(out, outCount);
    }

    static String encode(byte[] in) {
        return encode(in, BASE64);
    }

    static String encodeUrl(byte[] in) {
        return encode(in, BASE64_URL_SAFE);
    }

    private static String encode(byte[] in, byte[] map) {
        int length = (in.length + 2) / 3 * 4;
        byte[] out = new byte[length];
        int index = 0, end = in.length - in.length % 3;
        for (int i = 0; i < end; i += 3) {
            out[index++] = map[(in[i] & 0xff) >> 2];
            out[index++] = map[((in[i] & 0x03) << 4) | ((in[i + 1] & 0xff) >> 4)];
            out[index++] = map[((in[i + 1] & 0x0f) << 2) | ((in[i + 2] & 0xff) >> 6)];
            out[index++] = map[(in[i + 2] & 0x3f)];
        }
        switch (in.length % 3) {
            case 1:
                out[index++] = map[(in[end] & 0xff) >> 2];
                out[index++] = map[(in[end] & 0x03) << 4];
                out[index++] = '=';
                out[index] = '=';
                break;
            case 2:
                out[index++] = map[(in[end] & 0xff) >> 2];
                out[index++] = map[((in[end] & 0x03) << 4) | ((in[end + 1] & 0xff) >> 4)];
                out[index++] = map[((in[end + 1] & 0x0f) << 2)];
                out[index] = '=';
                break;
        }
        return new String(out, StandardCharsets.US_ASCII);
    }
}
