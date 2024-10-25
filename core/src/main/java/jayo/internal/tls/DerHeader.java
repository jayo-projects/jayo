/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from OkHttp (https://github.com/square/okhttp), original copyright is below
 *
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo.internal.tls;

import org.jspecify.annotations.NonNull;

/**
 * The first two bytes of each value is a header that includes its tag (field ID) and length.
 *
 * @param tagClass    Namespace of the tag.
 *                    <p>
 *                    This value is encoded in bits 7 and 8 of the first byte of each value.
 *                    <pre>
 *                    {@code
 *                    0b00xxxxxx Universal
 *                    0b01xxxxxx Application
 *                    0b10xxxxxx Context-Specific
 *                    0b11xxxxxx Private
 *                    }
 *                    </pre>
 * @param tag         Identifies which member in the ASN.1 schema the field holds.
 * @param constructed If the constructed bit is set it indicates that the value is composed of other values that have their own
 *                    headers.
 *                    <p>
 *                    This value is encoded in bit 6 of the first byte of each value.
 *                    <pre>
 *                    {@code
 *                    0bxx0xxxxx Primitive
 *                    0bxx1xxxxx Constructed
 *                    }
 *                    </pre>
 * @param length      Length of the message in bytes, or -1L if its length is unknown at the time of encoding.
 */
record DerHeader(int tagClass, long tag, boolean constructed, long length) {

    boolean isEndOfData() {
        return tagClass == TAG_CLASS_UNIVERSAL && tag == TAG_END_OF_CONTENTS;
    }

    @Override
    public @NonNull String toString() {
        return tagClass + "/" + tag;
    }

    static final int TAG_CLASS_UNIVERSAL = 0b0000_0000;
    static final int TAG_CLASS_APPLICATION = 0b0100_0000;
    static final int TAG_CLASS_CONTEXT_SPECIFIC = 0b1000_0000;
    static final int TAG_CLASS_PRIVATE = 0b1100_0000;

    static final long TAG_END_OF_CONTENTS = 0L;
}
