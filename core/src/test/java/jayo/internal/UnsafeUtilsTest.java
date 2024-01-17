/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static jayo.internal.UnsafeUtils.bytes;
import static jayo.internal.UnsafeUtils.isLatin1;
import static jayo.internal.UtilKt.LATIN1;

public class UnsafeUtilsTest {
    private static final String ASCII = "abc";
    private static final String UTF_16 = "ab𝙘";

    @Test
    void extractBytesAscii() {
        assertThat(bytes(ASCII))
                .hasSize(3)
                .containsExactly((byte) 'a', (byte) 'b', (byte) 'c');
    }

    @Test
    void extractBytesUtf16() {
        assertThat(bytes(UTF_16))
                .hasSize(8);
    }

    @Test
    void stringCoderAscii() {
        assertThat(isLatin1(ASCII)).isTrue();
    }

    @Test
    void stringCoderLatin1() {
        assertThat(isLatin1(LATIN1)).isTrue();
    }

    @Test
    void stringCoderUtf16() {
        assertThat(isLatin1(UTF_16)).isFalse();
    }
}
