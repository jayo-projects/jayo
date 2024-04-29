/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.Utf8ByteString;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import static jayo.internal.UnsafeUtils.*;
import static jayo.internal.Utf8Utils.buildUtf8StringBuilder;
import static jayo.internal.Utf8Utils.parseUtf8;

public final class RealUtf8ByteString extends RealByteString implements Utf8ByteString {
    private final transient boolean allowCompactString;
    private transient State state;
    private transient @NonNegative int length = 0; // Lazily computed.
    // CHARS state
    private transient char @Nullable [] chars = null; // Lazily computed.
    // CHARS state
    private transient @Nullable StringBuilder sb = null; // Lazily computed.

    public RealUtf8ByteString(final byte @NonNull [] data, final boolean isAscii) {
        this(data, isAscii, UNSAFE_AVAILABLE && SUPPORT_COMPACT_STRING);
    }

    public RealUtf8ByteString(final byte @NonNull [] data, final boolean isAscii, final boolean allowCompactString) {
        super(data);
        this.allowCompactString = allowCompactString;
        if (isAscii) {
            state = State.ASCII;
            length = data.length;
        } else {
            state = State.BYTES;
        }
    }

    public RealUtf8ByteString(final byte @NonNull [] data,
                              final @NonNegative int offset,
                              final @NonNegative int byteCount) {
        super(data, offset, byteCount);
        state = State.BYTES;
        this.allowCompactString = UNSAFE_AVAILABLE && SUPPORT_COMPACT_STRING;
    }

    /**
     * @param string a String that will be encoded in UTF-8
     */
    public RealUtf8ByteString(final @NonNull String string) {
        super(Objects.requireNonNull(string).getBytes(StandardCharsets.UTF_8));
        utf8 = string;
        length = string.length();
        state = State.STRING;
        this.allowCompactString = UNSAFE_AVAILABLE && SUPPORT_COMPACT_STRING;
    }

    @Override
    public @NonNull String decodeToUtf8() {
        if (state == State.STRING) {
            assert utf8 != null;
            return utf8;
        }
        // We don't care if we double-allocate in racy code.
        final var utf8String = switch (state) {
            case BYTES -> {
                final var string = new String(internalArray(), StandardCharsets.UTF_8);
                length = string.length();
                yield string;
            }
            case ASCII -> {
                if (allowCompactString) {
                    yield noCopyStringFromLatin1Bytes(internalArray());
                }
                yield new String(internalArray(), StandardCharsets.US_ASCII);
            }
            case CHARS -> {
                assert chars != null;
                yield new String(chars, 0, length);
            }
            case STRING_BUILDER -> {
                assert sb != null;
                yield sb.toString();
            }
            default -> throw new IllegalStateException("Unexpected state: " + state);
        };
        utf8 = utf8String;
        state = State.STRING;
        return utf8String;
    }

    /**
     * Scan all data
     * <ul>
     *     <li>If all bytes are ASCII, state becomes ASCII
     *     <li>Otherwise some of these bytes are non ASCII UTF-8 bytes, we build the char array from them
     * </ul>
     */
    private void scan() {
        var byteIndex = 0;
        final var endIndex = data.length;

        var isAscii = true;
        while (byteIndex < endIndex) {
            if ((data[byteIndex] & 0x80) != 0) {
                isAscii = false;
                break;
            }
            byteIndex++;
        }

        if (isAscii) {
            length = endIndex;
            state = State.ASCII;
            return;
        }

        // non ASCII, we must UTF-8 parse

        if (allowCompactString) {
            // Unsafe & compact string support, use unsafe operations
            final StringBuilder sb = buildUtf8StringBuilder(data, byteIndex);
            length = sb.length();
            this.sb = sb;
            state = State.STRING_BUILDER;
            return;
        }

        // No unsafe or no compact string support, fallback to char[] parse
        final var chars = new char[endIndex];
        length = parseUtf8(data, chars, byteIndex);
        this.chars = chars;
        state = State.CHARS;
    }

    @Override
    public int length() {
        if (state == State.BYTES) {
            scan();
        }
        return length;
    }

    @Override
    public char charAt(final @NonNegative int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("index < 0: " + index);
        }
        if (index >= length()) {
            throw new IndexOutOfBoundsException("index >= length(): " + index + " >= " + length());
        }
        return switch (state) {
            case ASCII -> (char) data[index];
            case CHARS -> {
                assert chars != null;
                yield chars[index];
            }
            case STRING_BUILDER -> {
                assert sb != null;
                yield sb.charAt(index);
            }
            case STRING -> {
                assert utf8 != null;
                yield utf8.charAt(index);
            }
            case BYTES -> throw new IllegalStateException("Unexpected state: " + state);
        };
    }

    @Override
    public @NonNull CharSequence subSequence(final @NonNegative int start, final @NonNegative int end) {
        if (start < 0 || end < 0 || start > end) {
            throw new IndexOutOfBoundsException("index < 0 || end < 0 || start > end: start=" + start + ", end=" + end);
        }
        if (start == end) {
            return Utf8ByteString.EMPTY;
        }
        if (end >= length()) {
            throw new IndexOutOfBoundsException("end >= length(): " + end + " >= " + length());
        }

        return switch (state) {
            case ASCII -> new RealUtf8ByteString(Arrays.copyOfRange(data, start, end), true, allowCompactString);
            case STRING_BUILDER -> {
                assert sb != null;
                yield sb.subSequence(start, end);
            }
            default -> toString().subSequence(start, end);
        };
    }

    @Override
    public @NonNull String toString() {
        return decodeToUtf8();
    }

    // region native-jvm-serialization

    @Serial
    private void readObject(final @NonNull ObjectInputStream in) throws IOException {
        final var dataLength = in.readInt();
        final var utf8ByteString = (RealUtf8ByteString) Utf8ByteString.readUtf8(in, dataLength);
        final Field dataField;
        final Field stateField;
        try {
            dataField = RealByteString.class.getDeclaredField("data");
            stateField = RealUtf8ByteString.class.getDeclaredField("state");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("RealByteString should contain a 'data' field and RealUtf8ByteString " +
                    "should contain a 'state' field", e);
        }
        dataField.setAccessible(true);
        stateField.setAccessible(true);
        try {
            dataField.set(this, utf8ByteString.data);
            stateField.set(this, State.BYTES);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("It should be possible to set RealByteString's 'data' field and " +
                    "RealUtf8ByteString's 'state' field", e);
        }
    }

    @Serial
    private void writeObject(final @NonNull ObjectOutputStream out) throws IOException {
        out.writeInt(data.length);
        out.write(data);
    }

    // endregion

    private enum State {
        BYTES,
        ASCII,
        CHARS,
        STRING_BUILDER,
        STRING
    }
}
