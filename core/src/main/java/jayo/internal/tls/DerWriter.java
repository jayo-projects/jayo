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

import jayo.Buffer;
import jayo.ByteString;
import jayo.Writer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class DerWriter {
    /** A stack of buffers that will be concatenated once we know the length of each. */
    private final @NonNull List<Writer> stack;
    /**
     * Type hints scoped to the call stack, manipulated with [pushTypeHint] and [popTypeHint].
     */
    private final @NonNull List<Object> typeHintStack = new ArrayList<>();
    /**
     * Names leading to the current location in the ASN.1 document.
     */
    private final @NonNull List<String> path = new ArrayList<>();
    /**
     * False unless we made a recursive call to [write] at the current stack frame. The explicit box adapter can clear
     * this to synthesize non-constructed values that are embedded in octet strings.
     */
    boolean constructed = false;

    DerWriter(final @NonNull Writer writer) {
        assert writer != null;

        stack = new ArrayList<>();
        stack.add(writer);
    }

    /**
     * The type hint for the current object. Used to pick adapters based on other fields, such as in extensions which
     * have different types depending on their extension ID.
     */
    @Nullable
    Object typeHint() {
        return typeHintStack.isEmpty() ? null : typeHintStack.getLast();
    }

    void typeHint(final @Nullable Object typeHint) {
        typeHintStack.set(typeHintStack.size() - 1, typeHint);
    }
    
    void write(final @NonNull String name, final int tagClass, final long tag, final @NonNull Consumer<Writer> block) {
        assert name != null;
        assert block != null;

        final int constructedBit;
        final var content = Buffer.create();

        stack.add(content);
        constructed = false; // The enclosed object written in block() is not constructed.
        path.add(name);
        try {
            block.accept(content);
            constructedBit = constructed ? 0b0010_0000 : 0;
            constructed = true; // The enclosing object is constructed.
        } finally {
            stack.removeLast();
            path.removeLast();
        }

        final var _writer = writer();

        // Write the tagClass, tag, and constructed bit. This takes 1 byte if tag is less than 31.
        if (tag < 31) {
            final var byte0 = (byte) (tagClass | constructedBit | ((int) tag));
            _writer.writeByte(byte0);
        } else {
            final var byte0 = (byte) (tagClass | constructedBit | 0b0001_1111);
            _writer.writeByte(byte0);
            writeVariableLengthLong(tag);
        }

        // Write the length. This takes 1 byte if length is less than 128.
        final var length = content.byteSize();
        if (length < 128L) {
            _writer.writeByte((byte) length);
        } else {
            // count how many bytes we'll need to express the length.
            final var lengthBitCount = 64 - Long.numberOfLeadingZeros(length);
            final var lengthByteCount = (lengthBitCount + 7) / 8;
            _writer.writeByte((byte) (0b1000_0000 | lengthByteCount));
            for (var shift = (lengthByteCount - 1) * 8; shift >= 0; shift -= 8) {
                _writer.writeByte((byte) (length >> shift));
            }
        }

        // Write the payload.
        _writer.transferFrom(content);
    }

    /**
     * Execute {@code block} with a new namespace for type hints. Type hints from the enclosing type are no longer
     * usable by the current type's members.
     */
    <T> T withTypeHint(final @NonNull Supplier<T> block) {
        assert block != null;

        typeHintStack.add(null);
        try {
            return block.get();
        } finally {
            typeHintStack.removeLast();
        }
    }

    void writeBoolean(final boolean b) {
        writer().writeByte((byte) (b ? -1 : 0));
    }

    void writeBigInteger(final @NonNull BigInteger bi) {
        assert bi != null;

        writer().write(bi.toByteArray());
    }

    void writeLong(final long l) {
        final var writer = writer();
        final var lengthBitCount = 65 - Long.numberOfLeadingZeros(l < 0L ? ~l : l);
        final var lengthByteCount = (lengthBitCount + 7) / 8;
        for (var shift = (lengthByteCount - 1) * 8; shift >= 0; shift -= 8) {
            writer.writeByte((byte) (l >> shift));
        }
    }

    void writeBitString(final @NonNull BitString bitString) {
        assert bitString != null;

        final var writer = writer();
        writer.writeByte(bitString.unusedBitsCount());
        writer.write(bitString.byteString());
    }

    void writeOctetString(final @NonNull ByteString byteString) {
        assert byteString != null;

        writer().write(byteString);
    }

    void writeString(final @NonNull String s) {
        assert s != null;

        writer().write(s);
    }

    void writeObjectIdentifier(final @NonNull String s) {
        assert s != null;

        final var utf8 = Buffer.create().write(s);
        final var v1 = utf8.readDecimalLong();
        if (utf8.readByte() != (byte) ((int) '.')) {
            throw new IllegalArgumentException("this string is not a valid object identifier");
        }
        final var v2 = utf8.readDecimalLong();
        writeVariableLengthLong(v1 * 40 + v2);

        while (!utf8.exhausted()) {
            if (utf8.readByte() != (byte) ((int) '.')) {
                throw new IllegalArgumentException("this string is not a valid object identifier");
            }
            final var vN = utf8.readDecimalLong();
            writeVariableLengthLong(vN);
        }
    }

    void writeRelativeObjectIdentifier(final @NonNull String s) {
        assert s != null;

        // Add a leading dot so each sub-identifier has a dot prefix.
        final var utf8 = Buffer.create()
                .writeByte((byte) ((int) '.'))
                .write(s);

        while (!utf8.exhausted()) {
            if (utf8.readByte() != (byte) ((int) '.')) {
                throw new IllegalArgumentException("this string is not a valid object identifier");
            }
            final var vN = utf8.readDecimalLong();
            writeVariableLengthLong(vN);
        }
    }

    private @NonNull Writer writer() {
        return stack.getLast();
    }

    /** Used for tags and sub-identifiers. */
    private void writeVariableLengthLong(final long v) {
        final var _writer = writer();
        final var bitCount = 64 - Long.numberOfLeadingZeros(v);
        final var byteCount = (bitCount + 6) / 7;
        for (var shift = (byteCount - 1) * 7; shift >= 0; shift -= 7) {
            final var lastBit = (shift == 0) ? 0 : 0b1000_0000;
            _writer.writeByte((byte) (((int) ((v >> shift) & 0b0111_1111)) | lastBit));
        }
    }

    @Override
    public @NonNull String toString() {
        return String.join(" / ", path);
    }
}
