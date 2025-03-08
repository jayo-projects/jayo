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

import jayo.bytestring.ByteString;
import jayo.JayoProtocolException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;

/**
 * Built-in adapters for reading standard ASN.1 types.
 */
final class Adapters {
    // un-instantiable
    private Adapters() {
    }

    static final @NonNull DateTimeFormatter UTC_TIME_FORMATTER = new DateTimeFormatterBuilder()
            // cutoff of the 2-digit year is 1950-01-01T00:00:00Z
            .appendValueReduced(ChronoField.YEAR, 2, 2, 1950)
            .appendPattern("MMddHHmmss'Z'")
            .toFormatter()
            .withZone(UTC);

    static final @NonNull DateTimeFormatter GENERALIZED_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss'Z'")
            .withZone(UTC);

    static final @NonNull BasicDerAdapter<Boolean> BOOLEAN = new BasicDerAdapter<>(
            "BOOLEAN",
            DerHeader.TAG_CLASS_UNIVERSAL,
            1L,
            new BasicDerAdapter.Codec<>() {
                @Override
                public @NonNull Boolean decode(final @NonNull DerReader reader) {
                    assert reader != null;

                    return reader.readBoolean();
                }

                @Override
                public void encode(final @NonNull DerWriter writer, final @NonNull Boolean value) {
                    assert writer != null;
                    assert value != null;

                    writer.writeBoolean(value);
                }
            }
    );

    static final @NonNull BasicDerAdapter<Long> INTEGER_AS_LONG = new BasicDerAdapter<>(
            "INTEGER",
            DerHeader.TAG_CLASS_UNIVERSAL,
            2L,
            new BasicDerAdapter.Codec<>() {
                @Override
                public @NonNull Long decode(final @NonNull DerReader reader) {
                    assert reader != null;

                    return reader.readLong();
                }

                @Override
                public void encode(final @NonNull DerWriter writer, final @NonNull Long value) {
                    assert writer != null;
                    assert value != null;

                    writer.writeLong(value);
                }
            }
    );

    static final @NonNull BasicDerAdapter<BigInteger> INTEGER_AS_BIG_INTEGER = new BasicDerAdapter<>(
            "INTEGER",
            DerHeader.TAG_CLASS_UNIVERSAL,
            2L,
            new BasicDerAdapter.Codec<>() {
                @Override
                public @NonNull BigInteger decode(final @NonNull DerReader reader) {
                    assert reader != null;

                    return reader.readBigInteger();
                }

                @Override
                public void encode(final @NonNull DerWriter writer, final @NonNull BigInteger value) {
                    assert writer != null;
                    assert value != null;

                    writer.writeBigInteger(value);
                }
            }
    );

    static final @NonNull BasicDerAdapter<BitString> BIT_STRING = new BasicDerAdapter<>(
            "BIT STRING",
            DerHeader.TAG_CLASS_UNIVERSAL,
            3L,
            new BasicDerAdapter.Codec<>() {
                @Override
                public @NonNull BitString decode(final @NonNull DerReader reader) {
                    assert reader != null;

                    return reader.readBitString();
                }

                @Override
                public void encode(final @NonNull DerWriter writer, final @NonNull BitString value) {
                    assert writer != null;
                    assert value != null;

                    writer.writeBitString(value);
                }
            }
    );

    static final @NonNull BasicDerAdapter<ByteString> OCTET_STRING = new BasicDerAdapter<>(
            "OCTET STRING",
            DerHeader.TAG_CLASS_UNIVERSAL,
            4L,
            new BasicDerAdapter.Codec<>() {
                @Override
                public @NonNull ByteString decode(final @NonNull DerReader reader) {
                    assert reader != null;

                    return reader.readOctetString();
                }

                @Override
                public void encode(final @NonNull DerWriter writer, final @NonNull ByteString value) {
                    assert writer != null;
                    assert value != null;

                    writer.writeOctetString(value);
                }
            }
    );

    static final @NonNull BasicDerAdapter<Void> NULL = new BasicDerAdapter<>(
            "NULL",
            DerHeader.TAG_CLASS_UNIVERSAL,
            5L,
            new BasicDerAdapter.Codec<>() {
                @Override
                public Void decode(final @NonNull DerReader reader) {
                    assert reader != null;

                    return null;
                }

                @Override
                public void encode(final @NonNull DerWriter writer, final Void value) {
                    assert writer != null;
                }
            }
    );

    static final @NonNull BasicDerAdapter<String> OBJECT_IDENTIFIER = new BasicDerAdapter<>(
            "OBJECT IDENTIFIER",
            DerHeader.TAG_CLASS_UNIVERSAL,
            6L,
            new BasicDerAdapter.Codec<>() {
                @Override
                public @NonNull String decode(final @NonNull DerReader reader) {
                    assert reader != null;

                    return reader.readObjectIdentifier();
                }

                @Override
                public void encode(final @NonNull DerWriter writer, final @NonNull String value) {
                    assert writer != null;
                    assert value != null;

                    writer.writeObjectIdentifier(value);
                }
            }
    );

    static final @NonNull BasicDerAdapter<String> UTF8_STRING = new BasicDerAdapter<>(
            "UTF8",
            DerHeader.TAG_CLASS_UNIVERSAL,
            12L,
            new BasicDerAdapter.Codec<>() {
                @Override
                public @NonNull String decode(final @NonNull DerReader reader) {
                    assert reader != null;

                    return reader.readString();
                }

                @Override
                public void encode(final @NonNull DerWriter writer, final @NonNull String value) {
                    assert writer != null;
                    assert value != null;

                    writer.writeString(value);
                }
            }
    );

    /**
     * Permits alphanumerics, spaces, and these:
     * <pre>
     * {@code
     *   ' () + , - . / : = ?
     * }
     * </pre>
     * todo: constrain to printable string characters.
     */
    static final @NonNull BasicDerAdapter<String> PRINTABLE_STRING = new BasicDerAdapter<>(
            "PRINTABLE STRING",
            DerHeader.TAG_CLASS_UNIVERSAL,
            19L,
            new BasicDerAdapter.Codec<>() {
                @Override
                public @NonNull String decode(final @NonNull DerReader reader) {
                    assert reader != null;

                    return reader.readString();
                }

                @Override
                public void encode(final @NonNull DerWriter writer, final @NonNull String value) {
                    assert writer != null;
                    assert value != null;

                    writer.writeString(value);
                }
            }
    );

    /**
     * Based on International Alphabet No. 5. Note that there are bytes that IA5 and US-ASCII disagree on interpretation
     * todo: constrain to printable string characters.
     */
    static final @NonNull BasicDerAdapter<String> IA5_STRING = new BasicDerAdapter<>(
            "IA5 STRING",
            DerHeader.TAG_CLASS_UNIVERSAL,
            22L,
            new BasicDerAdapter.Codec<>() {
                @Override
                public @NonNull String decode(final @NonNull DerReader reader) {
                    assert reader != null;

                    return reader.readString();
                }

                @Override
                public void encode(final @NonNull DerWriter writer, final @NonNull String value) {
                    assert writer != null;
                    assert value != null;

                    writer.writeString(value);
                }
            }
    );

    /**
     * A timestamp like "191216030210Z" or "191215190210-0800" for 2019-12-15T19:02:10-08:00. The cutoff of the 2-digit
     * year is 1950-01-01T00:00:00Z.
     */
    static final @NonNull BasicDerAdapter<Long> UTC_TIME = new BasicDerAdapter<>(
            "UTC TIME",
            DerHeader.TAG_CLASS_UNIVERSAL,
            23L,
            new BasicDerAdapter.Codec<>() {
                @Override
                public @NonNull Long decode(final @NonNull DerReader reader) {
                    assert reader != null;

                    final var string = reader.readString();
                    return parseTime(string, UTC_TIME_FORMATTER);
                }

                @Override
                public void encode(final @NonNull DerWriter writer, final @NonNull Long value) {
                    assert writer != null;
                    assert value != null;

                    final var string = formatTime(value, UTC_TIME_FORMATTER);
                    writer.writeString(string);
                }
            }
    );

    /**
     * A timestamp like "191216030210Z" or "20191215190210-0800" for 2019-12-15T19:02:10-08:00. This is the same as
     * {@link #UTC_TIME} with the exception of the 4-digit year.
     */
    static final @NonNull BasicDerAdapter<Long> GENERALIZED_TIME = new BasicDerAdapter<>(
            "GENERALIZED TIME",
            DerHeader.TAG_CLASS_UNIVERSAL,
            24L,
            new BasicDerAdapter.Codec<>() {
                @Override
                public @NonNull Long decode(final @NonNull DerReader reader) {
                    assert reader != null;

                    final var string = reader.readString();
                    return parseTime(string, GENERALIZED_TIME_FORMATTER);
                }

                @Override
                public void encode(final @NonNull DerWriter writer, final @NonNull Long value) {
                    assert writer != null;
                    assert value != null;

                    final var string = formatTime(value, GENERALIZED_TIME_FORMATTER);
                    writer.writeString(string);
                }
            }
    );

    static final @NonNull DerAdapter<AnyValue> ANY_VALUE = new DerAdapter<>() {
        @Override
        public boolean matches(final @NonNull DerHeader header) {
            assert header != null;

            return true;
        }

        @Override
        public AnyValue fromDer(final @NonNull DerReader reader) {
            assert reader != null;

            return reader.read("ANY", header -> {
                final var bytes = reader.readUnknown();
                return new AnyValue(
                        header.tagClass(),
                        header.tag(),
                        header.constructed(),
                        header.length(),
                        bytes
                );
            });
        }

        @Override
        public void toDer(final @NonNull DerWriter writer, final @NonNull AnyValue value) {
            assert writer != null;
            assert value != null;

            writer.write("ANY", value.tagClass(), value.tag(), ignored -> {
                writer.writeOctetString(value.bytes());
                writer.constructed = value.constructed();
            });
        }
    };

    /**
     * @return a composite adapter for a struct or data class. This may be used for both SEQUENCE and SET types.
     * <p>
     * The fields are specified as a list of member adapters. When decoding, a value for each non-optional member but be
     * included in sequence.
     * <p>
     * todo: for sets, sort by tag when encoding.
     * todo: for set ofs, sort by encoded value when encoding.
     */
    static <T> @NonNull BasicDerAdapter<T> sequence(final @NonNull String name,
                                                    final @NonNull Function<T, List<?>> decompose,
                                                    final @NonNull Function<List<?>, T> construct,
                                                    final DerAdapter<?> @NonNull ... members) {
        assert name != null;
        assert decompose != null;
        assert construct != null;
        assert members != null;

        final var codec = new BasicDerAdapter.Codec<T>() {
            @Override
            public T decode(final @NonNull DerReader reader) {
                assert reader != null;

                return reader.withTypeHint(() -> {
                    final var list = new ArrayList<>();

                    while (list.size() < members.length) {
                        final var member = members[list.size()];
                        list.add(member.fromDer(reader));
                    }

                    if (reader.hasNext()) {
                        throw new JayoProtocolException("unexpected " + reader.peekHeader() + " at " + reader);
                    }

                    return construct.apply(list);
                });
            }

            @Override
            public void encode(final @NonNull DerWriter writer, final T value) {
                assert writer != null;

                final var list = decompose.apply(value);
                writer.withTypeHint(() -> {
                    for (var i = 0; i < list.size(); i++) {
                        @SuppressWarnings("unchecked") final var adapter = (DerAdapter<Object>) members[i];
                        adapter.toDer(writer, list.get(i));
                    }
                    return null;
                });
            }
        };

        return new BasicDerAdapter<>(
                name,
                DerHeader.TAG_CLASS_UNIVERSAL,
                16L,
                codec);
    }

    /**
     * @return an adapter that decodes as the first of a list of available types.
     */
    static @NonNull DerAdapter<DerAdapterValue> choice(final DerAdapter<?> @NonNull ... choices) {
        assert choices != null;

        return new DerAdapter<>() {
            @Override
            public boolean matches(final @NonNull DerHeader header) {
                assert header != null;

                return true;
            }

            @Override
            public @NonNull DerAdapterValue fromDer(final @NonNull DerReader reader) {
                assert reader != null;

                final var peekedHeader = reader.peekHeader();
                if (peekedHeader == null) {
                    throw new JayoProtocolException("expected a value at " + reader);
                }

                DerAdapter<?> matchingChoice = null;
                for (final var choice : choices) {
                    if (choice.matches(peekedHeader)) {
                        matchingChoice = choice;
                        break;
                    }
                }
                if (matchingChoice == null) {
                    throw new JayoProtocolException(
                            "expected a matching choice but was " + peekedHeader + " at " + reader);
                }

                return new DerAdapterValue(matchingChoice, matchingChoice.fromDer(reader));
            }

            @SuppressWarnings("unchecked")
            @Override
            public void toDer(final @NonNull DerWriter writer, final @NonNull DerAdapterValue value) {
                assert writer != null;
                assert value != null;

                ((DerAdapter<Object>) value.derAdapter).toDer(writer, value.value);
            }

            @Override
            public @NonNull String toString() {
                return Arrays.stream(choices)
                        .map(Object::toString)
                        .collect(Collectors.joining(" OR "));
            }
        };
    }

    /**
     * This decodes a value into its contents using a preceding member of the same SEQUENCE. For example, extensions
     * type IDs specify what types to use for the corresponding values.
     * <p>
     * If the hint is unknown {@code chooser} should return null which will cause the value to be decoded as an opaque
     * byte string.
     * <p>
     * This may optionally wrap the contents in a tag.
     */
    @SuppressWarnings("unchecked")
    static @NonNull DerAdapter<Object> usingTypeHint(final @NonNull Function<Object, DerAdapter<?>> chooser) {
        assert chooser != null;

        return new DerAdapter<>() {
            @Override
            public boolean matches(final @NonNull DerHeader header) {
                assert header != null;

                return true;
            }

            @Override
            public Object fromDer(final @NonNull DerReader reader) {
                assert reader != null;

                final var derAdapter = chooser.apply(reader.typeHint());
                if (derAdapter != null) {
                    return derAdapter.fromDer(reader);
                }
                return reader.readUnknown();
            }

            @Override
            public void toDer(final @NonNull DerWriter writer, final @Nullable Object value) {
                assert writer != null;

                // If we don't understand this hint, encode the body as a byte string. The byte string will include a
                // tag and length header as a prefix.
                final var derAdapter = chooser.apply(writer.typeHint());
                if (derAdapter != null) {
                    ((DerAdapter<Object>) derAdapter).toDer(writer, value);
                } else {
                    assert value != null;
                    writer.writeOctetString((ByteString) value);
                }
            }
        };
    }

//    private static final @NonNull List<ClassDerAdapter> DEFAULT_ANY_CHOICES = List.of(
//            new ClassDerAdapter(boolean.class, BOOLEAN),
//            new ClassDerAdapter(BigInteger.class, INTEGER_AS_BIG_INTEGER),
//            new ClassDerAdapter(BitString.class, BIT_STRING),
//            new ClassDerAdapter(ByteString.class, OCTET_STRING),
//            new ClassDerAdapter(Void.class, NULL),
//            new ClassDerAdapter(Nothing.class, OBJECT_IDENTIFIER),
//            new ClassDerAdapter(Nothing.class, UTF8_STRING),
//            new ClassDerAdapter(String.class, PRINTABLE_STRING),
//            new ClassDerAdapter(Nothing.class, IA5_STRING),
//            new ClassDerAdapter(Nothing.class, UTC_TIME),
//            new ClassDerAdapter(long.class, GENERALIZED_TIME),
//            new ClassDerAdapter(AnyValue.class, ANY_VALUE));

    static @NonNull DerAdapter<Object> any(final ClassDerAdapter @NonNull ... choices) {
        assert choices != null;

        return new DerAdapter<>() {
            @Override
            public boolean matches(@NonNull DerHeader header) {
                assert header != null;

                return true;
            }

            @Override
            public Object fromDer(final @NonNull DerReader reader) {
                assert reader != null;

                final var peekedHeader = reader.peekHeader();
                if (peekedHeader == null) {
                    throw new JayoProtocolException("expected a value at " + reader);
                }
                for (var choice : choices) {
                    if (choice.derAdapter.matches(peekedHeader)) {
                        return choice.derAdapter.fromDer(reader);
                    }
                }

                throw new JayoProtocolException("expected any but was " + peekedHeader + " at " + reader);
            }

            @SuppressWarnings("unchecked")
            @Override
            public void toDer(final @NonNull DerWriter writer, final @Nullable Object value) {
                assert writer != null;

                for (var choice : choices) {
                    if (choice.type.isInstance(value) || (value == null && choice.type == Void.class)) {
                        ((DerAdapter<Object>) choice.derAdapter).toDer(writer, value);
                        return;
                    }
                }
            }
        };
    }

    static long parseTime(final @NonNull String string, final @NonNull DateTimeFormatter formatter) {
        assert string != null;

        try {
            return Instant.from(formatter.parse(string)).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            throw new JayoProtocolException("Failed to parse time " + string);
        }
    }

    static @NonNull String formatTime(final long utcTime, final @NonNull DateTimeFormatter formatter) {
        return formatter.format(Instant.ofEpochMilli(utcTime));
    }

    /**
     * Java alternative for Kotlin's {@code Nothing} type
     */
    static final class Nothing {
    }

    record DerAdapterValue(
            @NonNull DerAdapter<?> derAdapter,
            @Nullable Object value
    ) {
    }

    record ClassDerAdapter(
            @NonNull Class<?> type,
            @NonNull DerAdapter<?> derAdapter
    ) {
    }
}
