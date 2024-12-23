/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static jayo.internal.UnsafeUtils.getBytes;
import static jayo.internal.UnsafeUtils.isLatin1;

public class UnsafeUtilsTest {
    private static final String ASCII = "cafe";
    private static final String LATIN1 = "café";
    private static final String UTF_16 = "𝙘afé";

    @Test
    void extractBytesAscii() {
        assertThat(getBytes(ASCII))
                .hasSize(4)
                .containsExactly((byte) 'c', (byte) 'a', (byte) 'f', (byte) 'e');
    }

    @Test
    void extractBytesUtf16() {
        assertThat(getBytes(UTF_16))
                .hasSize(10);
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
