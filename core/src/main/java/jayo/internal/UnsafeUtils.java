/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Inspired by Netty (https://github.com/netty/netty) and Chronicle-Bytes (https://github.com/OpenHFT/Chronicle-Bytes)
 */

package jayo.internal;

import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import sun.misc.Unsafe;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Objects;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

/**
 * This class exposes unsafe operations
 */
final class UnsafeUtils {
    // uninstantiable
    private UnsafeUtils() {
    }

    static final boolean UNSAFE_AVAILABLE;
    static final boolean SUPPORT_COMPACT_STRING;

    private static final System.Logger LOGGER = System.getLogger("jayo.UnsafeUtils");

    private static final @Nullable Unsafe UNSAFE;
    private static final String VALUE_FIELD_NAME = "value";
    private static final String CODER_FIELD_NAME = "coder";
    private static final String COUNT_FIELD_NAME = "count";

    // Manipulate String
    private static final Field S_VALUE;
    private static final Field S_CODER;
    private static final long S_VALUE_OFFSET;
    private static final long S_CODER_OFFSET;

    // Manipulate StringBuilder
    private static final Field SB_VALUE;
    private static final Field SB_CODER;
    private static final Field SB_COUNT;
    private static final long SB_VALUE_OFFSET;
    private static final long SB_CODER_OFFSET;
    private static final long SB_COUNT_OFFSET;

    static {
        // attempt to access field Unsafe#theUnsafe
        final var maybeUnsafe = maybeUnsafe();

        // the conditional check here can not be replaced with checking that maybeUnsafe
        // is an instanceof Unsafe and reversing the if and else blocks; this is because an
        // instanceof check against Unsafe will trigger a class load, and we might not have
        // the runtime permission accessClassInPackage.sun.misc
        if (maybeUnsafe instanceof Throwable) {
            UNSAFE = null;
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(DEBUG, "sun.misc.Unsafe.theUnsafe: unavailable", (Throwable) maybeUnsafe);
            } else {
                LOGGER.log(DEBUG, "sun.misc.Unsafe.theUnsafe: unavailable: {0}",
                        ((Throwable) maybeUnsafe).getMessage());
            }
            UNSAFE_AVAILABLE = false;
        } else {
            UNSAFE = (Unsafe) maybeUnsafe;
            LOGGER.log(DEBUG, "sun.misc.Unsafe.theUnsafe: available");
            UNSAFE_AVAILABLE = true;
        }
        if (UNSAFE_AVAILABLE) {
            try {
                S_VALUE = String.class.getDeclaredField(VALUE_FIELD_NAME);
                S_VALUE_OFFSET = getFieldOffset(S_VALUE);
                S_CODER = String.class.getDeclaredField(CODER_FIELD_NAME);
                S_CODER_OFFSET = getFieldOffset(S_CODER);
                final var a = getObject("A", S_VALUE_OFFSET);
                SUPPORT_COMPACT_STRING = Array.getLength(a) == 1;

                final var abstractStringBuilderClass = StringBuilder.class.getSuperclass();
                SB_VALUE = abstractStringBuilderClass.getDeclaredField(VALUE_FIELD_NAME);
                SB_VALUE_OFFSET = getFieldOffset(SB_VALUE);
                SB_CODER = abstractStringBuilderClass.getDeclaredField(CODER_FIELD_NAME);
                SB_CODER_OFFSET = getFieldOffset(SB_CODER);
                SB_COUNT = abstractStringBuilderClass.getDeclaredField(COUNT_FIELD_NAME);
                SB_COUNT_OFFSET = getFieldOffset(SB_COUNT);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        } else {
            S_VALUE = null;
            S_VALUE_OFFSET = -1L;
            S_CODER = null;
            S_CODER_OFFSET = -1L;
            SUPPORT_COMPACT_STRING = false;

            SB_VALUE = null;
            SB_VALUE_OFFSET = -1L;
            SB_CODER = null;
            SB_CODER_OFFSET = -1L;
            SB_COUNT = null;
            SB_COUNT_OFFSET = -1L;
        }
    }

    /**
     * Attempt to access field Unsafe#theUnsafe
     */
    private static Object maybeUnsafe() {
        try {
            final var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            // We always want to try using Unsafe as the access still works on java9 as well and
            // we need it for out native-transports and many optimizations.
            unsafeField.setAccessible(true);
            // the unsafe instance
            return unsafeField.get(null);
        } catch (NoSuchFieldException | SecurityException | IllegalAccessException
                 // Also catch NoClassDefFoundError in case someone uses for example OSGI and it made Unsafe unloadable.
                 | NoClassDefFoundError e) {
            return e;
        }
    }

    static byte @NonNull [] getBytes(final @NonNull String string) {
        return getObject(string, S_VALUE_OFFSET);
    }

    static boolean isLatin1(final @NonNull String string) {
        return SUPPORT_COMPACT_STRING && getByte(string, S_CODER_OFFSET) == ((byte) 0);
    }

    static @NonNull String noCopyStringFromLatin1Bytes(final byte @NonNull [] bytes) {
        @SuppressWarnings("StringOperationCanBeSimplified") final var string = new String();
        assert UNSAFE != null;
        UNSAFE.putObject(string, S_VALUE_OFFSET, bytes);
        UNSAFE.putByte(string, S_CODER_OFFSET, (byte) 0);
        return string;
    }

    static void noCopyStringBuilderAppendLatin1Bytes(final @NonNull StringBuilder sb,
                                                     final byte @NonNull [] bytes,
                                                     final @NonNegative int offset,
                                                     final @NonNegative int byteCount) {
        final var sbBytes = getBytes(sb);
        final var count = getCount(sb);
        if (isLatin1(sb)) {
            System.arraycopy(bytes, offset, sbBytes, count, byteCount);
        } else {
            var sbIndex = count << 1;
            final var endIndex = offset + byteCount;
            for (int i = offset; i < endIndex; i++) {
                sbBytes[sbIndex++] = bytes[i];
                sbBytes[sbIndex++] = (byte) 0;
            }
        }
        setCount(sb, count + byteCount);
    }

    private static byte @NonNull [] getBytes(final @NonNull StringBuilder sb) {
        return getObject(sb, SB_VALUE_OFFSET);
    }

    private static boolean isLatin1(final @NonNull StringBuilder sb) {
        return getByte(sb, SB_CODER_OFFSET) == ((byte) 0);
    }

    private static @NonNegative int getCount(final @NonNull StringBuilder sb) {
        return getInt(sb, SB_COUNT_OFFSET);
    }

    private static void setCount(final @NonNull StringBuilder sb, final @NonNegative int count) {
        assert UNSAFE != null;
        UNSAFE.putInt(sb, SB_COUNT_OFFSET, count);
    }

    /**
     * Retrieves the offset of the provided field within its class or interface.
     *
     * @param field the field whose offset should be fetched.
     * @return the offset of the field.
     * @implNote This Unsafe method is deprecated, but VarHandle does not integrate well with JPMS modules, forcing ugly
     * {@code --add-opens java.base/*some_jvm_package*=ALL-UNNAMED}
     */
    private static long getFieldOffset(final @NonNull Field field) {
        Objects.requireNonNull(field);
        assert UNSAFE != null;
        return UNSAFE.objectFieldOffset(field);
    }

    private static byte getByte(final @NonNull Object object, long offset) {
        Objects.requireNonNull(object);
        assert UNSAFE != null;
        return UNSAFE.getByte(object, offset);
    }

    private static int getInt(final @NonNull Object object, long offset) {
        Objects.requireNonNull(object);
        assert UNSAFE != null;
        return UNSAFE.getInt(object, offset);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getObject(final @NonNull Object object, long offset) {
        Objects.requireNonNull(object);
        assert UNSAFE != null;
        return (T) UNSAFE.getObject(object, offset);
    }
}
