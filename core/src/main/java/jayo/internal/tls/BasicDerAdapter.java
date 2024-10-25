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

import jayo.JayoProtocolException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Handles basic types that always use the same tag. This supports optional types and may set a type hint for further
 * adapters to process.
 * <p>
 * Types like ANY and CHOICE that don't have a consistent tag cannot use this.
 *
 * @param tagClass     The tag class this adapter expects, or -1 to match any tag class.
 * @param tag          The tag this adapter expects, or -1 to match any tag.
 * @param codec        Encode and decode the value once tags are handled.
 * @param isOptional   True if the default value should be used if this value is absent during decoding.
 * @param defaultValue The value to return if this value is absent. Undefined unless this is optional.
 * @param typeHint     True to set the encoded or decoded value as the type hint for the current SEQUENCE.
 */
record BasicDerAdapter<T>(
        @NonNull String name,
        @NonNegative int tagClass,
        @NonNegative long tag,
        @NonNull Codec<T> codec,
        boolean isOptional,
        @Nullable T defaultValue,
        boolean typeHint
) implements DerAdapter<T> {
    BasicDerAdapter(final @NonNull String name,
                    final @NonNegative int tagClass,
                    final @NonNegative long tag,
                    final @NonNull Codec<T> codec) {
        this(name, tagClass, tag, codec, false, null, false);
    }

    @Override
    public boolean matches(final @NonNull DerHeader header) {
        assert header != null;

        return header.tagClass() == tagClass && header.tag() == tag;
    }

    @Override
    public T fromDer(final @NonNull DerReader reader) {
        assert reader != null;

        final var peekedHeader = reader.peekHeader();
        if (peekedHeader == null || peekedHeader.tagClass() != tagClass || peekedHeader.tag() != tag) {
            if (isOptional) {
                return defaultValue;
            }
            throw new JayoProtocolException("expected " + this + " but was " + peekedHeader + " at " + reader);
        }

        final var result = reader.read(name, _ignored -> codec.decode(reader));

        if (typeHint) {
            reader.typeHint(result);
        }

        return result;
    }

    @Override
    public void toDer(@NonNull DerWriter writer, T value) {
        assert writer != null;

        if (typeHint) {
            writer.typeHint(value);
        }

        if (isOptional && value == defaultValue) {
            // Nothing to write!
            return;
        }

        writer.write(name, tagClass, tag, ignored -> codec.encode(writer, value));
    }

    /**
     * @return a copy with a context tag. This should be used when the type is ambiguous on its own.
     * For example, the tags in this schema are 0 and 1:
     * <pre>
     * {@code
     * Point ::= SEQUENCE {
     *   x [0] INTEGER OPTIONAL,
     *   y [1] INTEGER OPTIONAL
     * }
     * }
     * </pre>
     * You may also specify a tag class like {@link DerHeader#TAG_CLASS_APPLICATION}. The default tag class is
     * {@link DerHeader#TAG_CLASS_CONTEXT_SPECIFIC}.
     * <pre>
     * {@code
     * Point ::= SEQUENCE {
     *   x [APPLICATION 0] INTEGER OPTIONAL,
     *   y [APPLICATION 1] INTEGER OPTIONAL
     * }
     * }
     * </pre>
     */
    @NonNull
    BasicDerAdapter<T> withTag(final @NonNegative int tagClass, final @NonNegative long tag) {
        return new BasicDerAdapter<>(name, tagClass, tag, codec, isOptional, defaultValue, typeHint);
    }

    BasicDerAdapter<T> withTag(final @NonNegative long tag) {
        return withTag(DerHeader.TAG_CLASS_CONTEXT_SPECIFIC, tag);
    }

    /**
     * @return a copy of this adapter that doesn't encode values equal to {@code defaultValue}.
     */
    @NonNull
    BasicDerAdapter<T> optional(final @Nullable T defaultValue) {
        return new BasicDerAdapter<>(name, tagClass, tag, codec, true, defaultValue, typeHint);
    }

    /**
     * @return a copy of this adapter that sets the encoded or decoded value as the type hint for the other adapters on
     * this SEQUENCE to interrogate.
     */
    @NonNull
    BasicDerAdapter<T> asTypeHint() {
        return new BasicDerAdapter<>(name, tagClass, tag, codec, isOptional, defaultValue, true);
    }

    @Override
    public @NonNull String toString() {
        return name + " ["+ tagClass + "/" + tag + "]";
    }

    /**
     * Reads and writes values without knowledge of the enclosing tag, length, or defaults.
     */
    interface Codec<T> {
        T decode(final @NonNull DerReader reader);

        void encode(final @NonNull DerWriter writer, final T value);
    }
}
