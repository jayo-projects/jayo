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

import jayo.*;
import jayo.external.NonNegative;
import jayo.internal.Utils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Streaming decoder of data encoded following Abstract Syntax Notation One (ASN.1). There are multiple variants of
 * ASN.1, including:
 * <ul>
 *  <li>DER: Distinguished Encoding Rules. This further constrains ASN.1 for deterministic encoding.
 *  <li>BER: Basic Encoding Rules.
 * </ul>
 * This class was implemented according to the <a href="https://www.itu.int/rec/T-REC-X.690">X.690 spec</a>, and under
 * the advice of <a href="https://letsencrypt.org/docs/a-warm-welcome-to-asn1-and-der/">Lets Encrypt's ASN.1 and DER</a>
 * guide.
 */
final class DerReader {
    private final @NonNull CountingRawReader countingReader;
    private final @NonNull Reader reader;

    /**
     * How many bytes to read before {@link #peekHeader()} should return false, or -1L for no limit.
     */
    private long limit = -1L;
    /**
     * Type hints scoped to the call stack, manipulated with [withTypeHint].
     */
    private final @NonNull List<Object> typeHintStack = new ArrayList<>();
    /**
     * Names leading to the current location in the ASN.1 document.
     */
    private final @NonNull List<String> path = new ArrayList<>();
    private boolean constructed = false;
    private @Nullable DerHeader peekedHeader = null;

    DerReader(final @NonNull RawReader rawReader) {
        assert rawReader != null;

        this.countingReader = new CountingRawReader(rawReader);
        this.reader = Jayo.buffer(countingReader);
    }

    private @NonNegative long byteCount() {
        return countingReader.bytesRead - Utils.getBufferFromReader(reader).byteSize();
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

    private long bytesLeft() {
        return limit == -1L ? -1L : limit - byteCount();
    }

    boolean hasNext() {
        return peekHeader() != null;
    }

    /**
     * @return the next header to process unless this scope is exhausted.
     * <p>
     * This returns null if:
     * <ul>
     * <li>The stream is exhausted.
     * <li>We've read all of the bytes of an object whose length is known.
     * <li>We've reached the [DerHeader.TAG_END_OF_CONTENTS] of an object whose length is unknown.
     *  </ul>
     */
    @Nullable
    DerHeader peekHeader() {
        var result = peekedHeader;

        if (result == null) {
            result = readHeader();
            peekedHeader = result;
        }

        if (result.isEndOfData()) {
            return null;
        }

        return result;
    }

    /**
     * Consume the next header in the stream and return it. If there is no header to read because we have reached a
     * limit, this returns {@link #END_OF_DATA}.
     */
    @NonNull
    DerHeader readHeader() {
        if (peekedHeader != null) {
            throw new IllegalStateException("peeked header is already read");
        }

        // We've hit a local limit.
        if (byteCount() == limit) {
            return END_OF_DATA;
        }

        // We've exhausted the reader stream.
        if (limit == -1L && reader.exhausted()) {
            return END_OF_DATA;
        }

        // Read the tag.
        final var tagAndClass = reader.readByte() & 0xff;
        final var tagClass = tagAndClass & 0b1100_0000;
        final var constructed = (tagAndClass & 0b0010_0000) == 0b0010_0000;
        final var tag0 = tagAndClass & 0b0001_1111;
        final long tag;
        if (tag0 == 0b0001_1111) {
            tag = readVariableLengthLong();
        } else {
            tag = tag0;
        }

        // Read the length.
        final var length0 = reader.readByte() & 0xff;
        final long length;
        if (length0 == 0b1000_0000) {
            throw new JayoProtocolException("indefinite length not permitted for DER");
        } else if ((length0 & 0b1000_0000) == 0b1000_0000) {
            // Length specified over multiple bytes.
            final var lengthBytes = length0 & 0b0111_1111;
            if (lengthBytes > 8) {
                throw new JayoProtocolException("length encoded with more than 8 bytes is not supported");
            }

            long lengthBits = reader.readByte() & 0xff;
            if (lengthBits == 0L || lengthBytes == 1 && (lengthBits & 0b1000_0000) == 0L) {
                throw new JayoProtocolException("invalid encoding for length");
            }

            for (var i = 1; i < lengthBytes; i++) {
                lengthBits = lengthBits << 8;
                lengthBits += reader.readByte() & 0xff;
            }

            if (lengthBits < 0) {
                throw new JayoProtocolException("length > Long.MAX_VALUE");
            }

            length = lengthBits;
        } else {
            // Length is 127 or fewer bytes.
            length = length0 & 0b0111_1111;
        }

        // Note that this may be an encoded "end of data" header.
        return new DerHeader(tagClass, tag, constructed, length);
    }

    /**
     * Consume a header and execute {@code block}, which should consume the entire value described by the header. It is
     * an error to not consume a full value in {@code block}.
     */
    <T> T read(final @Nullable String name, final @NonNull Function<DerHeader, T> block) {
        assert block != null;

        if (!hasNext()) {
            throw new JayoProtocolException("expected a value");
        }

        final var header = peekedHeader;
        assert header != null;
        peekedHeader = null;

        final var pushedLimit = limit;
        final var pushedConstructed = constructed;

        final var newLimit = (header.length() != -1L) ? byteCount() + header.length() : -1L;
        if (pushedLimit != -1L && newLimit > pushedLimit) {
            throw new JayoProtocolException("enclosed object too large");
        }

        limit = newLimit;
        constructed = header.constructed();
        if (name != null) {
            path.add(name);
        }
        try {
            final var result = block.apply(header);

            // The object processed bytes beyond its range.
            if (newLimit != -1L && byteCount() > newLimit) {
                throw new JayoProtocolException("unexpected byte count at " + this);
            }

            return result;
        } finally {
            peekedHeader = null;
            limit = pushedLimit;
            constructed = pushedConstructed;
            if (name != null) {
                path.removeLast();
            }
        }
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

    boolean readBoolean() {
        if (bytesLeft() != 1L) {
            throw new JayoProtocolException("unexpected length: " + bytesLeft() + " at " + this);
        }
        return reader.readByte() != 0;
    }

    @NonNull
    BigInteger readBigInteger() {
        final var bytesLeft = bytesLeft();
        if (bytesLeft == 0L) {
            throw new JayoProtocolException("unexpected length: 0 at " + this);
        }
        final var byteArray = reader.readByteArray(bytesLeft);
        return new BigInteger(byteArray);
    }

    long readLong() {
        final var bytesLeft = bytesLeft();
        if (bytesLeft < 1L || bytesLeft > 8L) {
            throw new JayoProtocolException("unexpected length: " + bytesLeft() + " at " + this);
        }
        long result = reader.readByte(); // No "& 0xff" because this is a signed value!
        while (byteCount() < limit) {
            result = result << 8;
            result += reader.readByte() & 0xff;
        }
        return result;
    }

    @NonNull
    BitString readBitString() {
        if (bytesLeft() == -1L || constructed) {
            throw new JayoProtocolException("constructed bit strings not supported for DER");
        }
        if (bytesLeft() < 1L) {
            throw new JayoProtocolException("malformed bit string");
        }
        final var unusedBitCount = reader.readByte();
        final var byteString = reader.readByteString(bytesLeft());
        return new BitString(byteString, unusedBitCount);
    }

    @NonNull
    ByteString readOctetString() {
        final var bytesLeft = bytesLeft();
        if (bytesLeft == -1L || constructed) {
            throw new JayoProtocolException("constructed octet strings not supported for DER");
        }
        return reader.readByteString(bytesLeft);
    }

    /**
     * @return a UTF-8 encoded String from the reader
     */
    @NonNull
    String readString() {
        final var bytesLeft = bytesLeft();
        if (bytesLeft == -1L || constructed) {
            throw new JayoProtocolException("constructed strings not supported for DER");
        }
        return reader.readString(bytesLeft);
    }

    @NonNull
    String readObjectIdentifier() {
        final var result = Buffer.create();
        final var dot = (byte) ((int) '.');
        final var xy = readVariableLengthLong();
        if (xy >= 0L && xy < 40L) {
            result.writeDecimalLong(0L);
            result.writeByte(dot);
            result.writeDecimalLong(xy);
        } else if (xy >= 40L && xy < 80L) {
            result.writeDecimalLong(1L);
            result.writeByte(dot);
            result.writeDecimalLong(xy - 40L);
        } else {
            result.writeDecimalLong(2L);
            result.writeByte(dot);
            result.writeDecimalLong(xy - 80L);
        }
        while (byteCount() < limit) {
            result.writeByte(dot);
            result.writeDecimalLong(readVariableLengthLong());
        }
        return result.readString();
    }

    @NonNull
    String readRelativeObjectIdentifier() {
        final var result = Buffer.create();
        final var dot = (byte) ((int) '.');
        while (byteCount() < limit) {
            if (result.byteSize() > 0L) {
                result.writeByte(dot);
            }
            result.writeDecimalLong(readVariableLengthLong());
        }
        return result.readString();
    }

    /**
     * Read a value as bytes without interpretation of its contents.
     */
    @NonNull
    ByteString readUnknown() {
        return reader.readByteString(bytesLeft());
    }

    /**
     * Used for tags and sub-identifiers.
     */
    private long readVariableLengthLong() {
        var result = 0L;
        while (true) {
            final long byteN = reader.readByte() & 0xff;
            if ((byteN & 0b1000_0000L) == 0b1000_0000L) {
                result = (result + (byteN & 0b0111_1111)) << 7;
            } else {
                return result + byteN;
            }
        }
    }

    @Override
    public @NonNull String toString() {
        return String.join(" / ", path);
    }

    /**
     * A synthetic value that indicates there's no more bytes. Values with equivalent data may also show up in ASN.1
     * streams to also indicate the end of SEQUENCE, SET or other constructed value.
     */
    private static final @NonNull DerHeader END_OF_DATA =
            new DerHeader(DerHeader.TAG_CLASS_UNIVERSAL, DerHeader.TAG_END_OF_CONTENTS, false, -1L);

    /**
     * A raw reader that keeps track of how many bytes it consumed.
     */
    private final static class CountingRawReader implements RawReader {
        private final @NonNull RawReader delegate;
        private @NonNegative long bytesRead = 0L;

        private CountingRawReader(final @NonNull RawReader delegate) {
            assert delegate != null;

            this.delegate = delegate;
        }

        @Override
        public long readAtMostTo(final @NonNull Buffer writer, final @NonNegative long byteCount) {
            final var result = delegate.readAtMostTo(writer, byteCount);
            if (result == -1L) {
                return -1L;
            }
            bytesRead += result;
            return result;
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
