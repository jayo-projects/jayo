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
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Encode and decode a model object like a {@code long} or {@link Certificate} as DER bytes.
 */
interface DerAdapter<T> {
    /**
     * @return true if this adapter can read {@code header} in a choice.
     */
    boolean matches(final @NonNull DerHeader header);

    /**
     * @return a value from this adapter.
     * <p>
     * This must always return a value, though it doesn't necessarily need to consume data from {@code reader}. For
     * example, if the reader's peeked tag isn't readable by this adapter, it may return a default value.
     * <p>
     * If this does read a value, it starts with the tag and length, and reads an entire value, including any potential
     * composed values.
     * <p>
     * If there's nothing to read and no default value, this will throw an exception.
     */
    T fromDer(final @NonNull DerReader reader);

    default T fromDer(final @NonNull ByteString byteString) {
        assert byteString != null;

        final var buffer = Buffer.create().write(byteString);
        final var reader = new DerReader(buffer);
        return fromDer(reader);
    }

    /**
     * Writes {@code value} to this adapter, unless it is the default value and can be safely omitted.
     * <p>
     * If this does write a value, it will write a tag and a length and a full value.
     */
    void toDer(final @NonNull DerWriter writer, final T value);

    default @NonNull ByteString toDer(final T value) {
        final var buffer = Buffer.create();
        final var writer = new DerWriter(buffer);
        toDer(writer, value);
        return buffer.readByteString();
    }

    /**
     * @param forceConstructed non-null to set the constructed bit to the specified value, even if the writing process
     *                         sets something else. This is used to encode SEQUENCES in values that are declared to have
     *                         non-constructed values, like OCTET STRING values.
     * @return an adapter that expects this value wrapped by another value. Typically, this occurs when a value has both
     * a context or application tag and a universal tag.
     * <p>
     * Use this for EXPLICIT tag types:
     * <p>
     * <pre>
     * {@code
     * [5] EXPLICIT UTF8String
     * }
     * </pre>
     */
    default @NonNull BasicDerAdapter<T> withExplicitBox(final @NonNegative int tagClass,
                                                        final @NonNegative long tag,
                                                        final @Nullable Boolean forceConstructed) {
        final var codec = new BasicDerAdapter.Codec<T>() {
            @Override
            public T decode(final @NonNull DerReader reader) {
                return fromDer(reader);
            }

            @Override
            public void encode(final @NonNull DerWriter writer, final T value) {
                toDer(writer, value);
                if (forceConstructed != null) {
                    writer.constructed = forceConstructed;
                }
            }
        };

        return new BasicDerAdapter<>("EXPLICIT", tagClass, tag, codec);
    }

    default @NonNull BasicDerAdapter<T> withExplicitBox(final @NonNegative long tag) {
        return withExplicitBox(DerHeader.TAG_CLASS_CONTEXT_SPECIFIC, tag, null);
    }

    /**
     * Returns an adapter that returns a list of values of this type.
     */
    default BasicDerAdapter<List<T>> asSequenceOf(final @NonNull String name,
                                                  final @NonNegative int tagClass,
                                                  final @NonNegative long tag) {
        final var codec = new BasicDerAdapter.Codec<List<T>>() {
            @Override
            public List<T> decode(final @NonNull DerReader reader) {
                final var result = new ArrayList<T>();
                while (reader.hasNext()) {
                    result.add(fromDer(reader));
                }
                return result;
            }

            @Override
            public void encode(final @NonNull DerWriter writer, final List<T> value) {
                for (var v : value) {
                    toDer(writer, v);
                }
            }
        };

        return new BasicDerAdapter<>(name, tagClass, tag, codec);
    }

    default BasicDerAdapter<List<T>> asSequenceOf() {
        return asSequenceOf("SEQUENCE OF", DerHeader.TAG_CLASS_UNIVERSAL, 16L);
    }

    /**
     * Returns an adapter that returns a set of values of this type.
     */
    default BasicDerAdapter<List<T>> asSetOf() {
        return asSequenceOf("SET OF", DerHeader.TAG_CLASS_UNIVERSAL, 17L);
    }
}
