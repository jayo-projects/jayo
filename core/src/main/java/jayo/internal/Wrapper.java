/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

final class Wrapper {
    // un-instantiable
    private Wrapper() {
    }

    static final class Int {
        int value;

        Int() {
            value = 0;
        }

        Int(final int value) {
            this.value = value;
        }
    }

    static final class Boolean {
        boolean value = false;
    }
}
