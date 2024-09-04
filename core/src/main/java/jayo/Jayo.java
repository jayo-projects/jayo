/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
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

package jayo;

import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import jayo.exceptions.JayoException;
import jayo.external.NonNegative;
import jayo.internal.*;
import org.jspecify.annotations.NonNull;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * Essential APIs for working with Jayo.
 */
public final class Jayo {
    private static final System.Logger LOGGER = System.getLogger("jayo.Jayo");

    // un-instantiable
    private Jayo() {
    }

    /**
     * @return a new writer that buffers writes to the raw {@code writer}. The returned writer will batch writes to
     * {@code writer}. On each write operation, the underlying buffer will automatically emit all the complete
     * segment(s), if any.
     * <p>
     * Actual write operations to the raw {@code writer} are processed <b>synchronously</b>.
     * <p>
     * Use this wherever you synchronously write to a raw writer to get an ergonomic and efficient access to data.
     */
    public static @NonNull Writer buffer(final @NonNull RawWriter writer) {
        return new RealWriter(writer, false);
    }

    /**
     * @return a new writer that buffers writes to the raw {@code writer}. The returned writer will batch writes to
     * {@code writer}. On each write operation, the underlying buffer will automatically emit all the complete
     * segment(s), if any.
     * <p>
     * Actual write operations to the raw {@code writer} are seamlessly processed <b>asynchronously</b> by a virtual
     * thread.
     * <p>
     * Use this wherever you asynchronously write to a raw writer to get an ergonomic and efficient access to data.
     */
    public static @NonNull Writer bufferAsync(final @NonNull RawWriter writer) {
        return new RealWriter(writer, true);
    }

    /**
     * @return a new reader that buffers reads from the raw {@code reader}. The returned reader will perform bulk reads
     * into its underlying buffer.
     * <p>
     * Actual read operations from the raw {@code reader} are processed <b>synchronously</b>.
     * <p>
     * Use this wherever you synchronously read from a raw reader to get an ergonomic and efficient access to data.
     */
    public static @NonNull Reader buffer(final @NonNull RawReader reader) {
        return new RealReader(reader, false);
    }

    /**
     * @return a new reader that buffers reads from the raw {@code reader}. The returned reader will perform bulk reads
     * into its underlying buffer.
     * <p>
     * Actual read operations from the raw {@code reader} are seamlessly processed <b>asynchronously</b> by a virtual
     * thread.
     * <p>
     * Use this wherever you asynchronously read from a raw reader to get an ergonomic and efficient access to data.
     */
    public static @NonNull Reader bufferAsync(final @NonNull RawReader reader) {
        return new RealReader(reader, true);
    }

    /**
     * @return a raw writer that writes to {@code out} stream.
     */
    public static @NonNull RawWriter writer(final @NonNull OutputStream out) {
        Objects.requireNonNull(out);
        return new OutputStreamRawWriter(out);
    }

    /**
     * @return a raw reader that reads from {@code in} stream.
     */
    public static @NonNull RawReader reader(final @NonNull InputStream in) {
        Objects.requireNonNull(in);
        return new InputStreamRawReader(in);
    }

    /**
     * @return a raw writer that writes to {@code path}. {@code options} allow to specify how the file is opened.
     * @implNote We always add the {@code StandardOpenOption.WRITE} option to the options Set, so we ensure we can write
     * in this {@code path}.
     */
    public static @NonNull RawWriter writer(final @NonNull Path path, final @NonNull OpenOption @NonNull ... options) {
        Objects.requireNonNull(path);
        final Set<OpenOption> optionsSet = new HashSet<>();
        for (final var option : options) {
            if (option == StandardOpenOption.READ) {
                LOGGER.log(DEBUG, "Ignoring StandardOpenOption.READ. A writer does not read.");
                continue;
            }
            optionsSet.add(option);
        }
        optionsSet.add(StandardOpenOption.WRITE); // a writer needs the WRITE option
        return writer(path, optionsSet);
    }

    private static @NonNull RawWriter writer(final @NonNull Path path, final @NonNull Set<OpenOption> options) {
        Objects.requireNonNull(path);
        try {
            final var bc = Files.newByteChannel(path, options.toArray(new OpenOption[0]));
            return new OutputStreamRawWriter(Channels.newOutputStream(bc), bc);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * @return a raw reader that reads from {@code path}. {@code options} allow to specify how the file is opened.
     * @implNote We always add the {@code StandardOpenOption.READ} option to the options Set, so we ensure we can read
     * from this {@code path}.
     */
    public static @NonNull RawReader reader(final @NonNull Path path, final @NonNull OpenOption @NonNull ... options) {
        Objects.requireNonNull(path);
        final Set<OpenOption> optionsSet = new HashSet<>();
        for (final var option : options) {
            if (option == StandardOpenOption.WRITE
                    || option == StandardOpenOption.APPEND
                    || option == StandardOpenOption.TRUNCATE_EXISTING
                    || option == StandardOpenOption.SYNC
                    || option == StandardOpenOption.DSYNC) {
                LOGGER.log(DEBUG, "Ignoring all write related options : WRITE, APPEND, TRUNCATE_EXISTING, " +
                        "SYNC, DSYNC. A reader does not write.");
                continue;
            }
            optionsSet.add(option);
        }
        optionsSet.add(StandardOpenOption.READ); // a reader needs the READ option
        return reader(path, optionsSet);
    }

    private static RawReader reader(final @NonNull Path path, final @NonNull Set<OpenOption> options) {
        Objects.requireNonNull(path);
        try {
            return new InputStreamRawReader(Files.newInputStream(path, options.toArray(new OpenOption[0])));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * @return a raw writer that writes to {@code file}.
     * <p>
     * If you need specific options, please use {@link #writer(Path, OpenOption...)} instead.
     */
    public static @NonNull RawWriter writer(final @NonNull File file) {
        Objects.requireNonNull(file);
        try {
            return new OutputStreamRawWriter(new FileOutputStream(file));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * @return a raw reader that reads from {@code file}.
     * <p>
     * If you need specific options, please use {@link #reader(Path, OpenOption...)} instead.
     */
    public static @NonNull RawReader reader(final @NonNull File file) {
        Objects.requireNonNull(file);
        try {
            return new InputStreamRawReader(new FileInputStream(file));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * Consumes all this reader and return its hash.
     *
     * @param reader the reader
     * @param digest the chosen message digest algorithm to use for hashing.
     * @return the hash of this reader.
     */
    public static @NonNull ByteString hash(final @NonNull RawReader reader, final @NonNull Digest digest) {
        return HashingUtils.hash(reader, digest);
    }

    /**
     * Consumes all this reader and return its MAC result.
     *
     * @param reader the reader
     * @param hMac   the chosen "Message Authentication Code" (MAC) algorithm to use.
     * @param key    the key to use for this MAC operation.
     * @return the MAC result of this reader.
     */
    public static @NonNull ByteString hmac(final @NonNull RawReader reader,
                                           final @NonNull Hmac hMac,
                                           final @NonNull ByteString key) {
        return HashingUtils.hmac(reader, hMac, key);
    }

    /**
     * @return a {@link RawWriter} that DEFLATE-compresses data to this {@code writer} while writing.
     */
    public static @NonNull RawWriter deflate(final @NonNull RawWriter writer) {
        return deflate(writer, new Deflater());
    }

    /**
     * @return a {@link RawWriter} that DEFLATE-compresses data to this {@code writer} while writing.
     */
    public static @NonNull RawWriter deflate(final @NonNull RawWriter writer, final @NonNull Deflater deflater) {
        return new DeflaterRawWriter(writer, deflater);
    }

    /**
     * @return an {@link InflaterRawReader} that DEFLATE-decompresses this {@code reader} while reading.
     */
    public static @NonNull InflaterRawReader inflate(final @NonNull RawReader reader) {
        return inflate(reader, new Inflater());
    }

    /**
     * @return an {@link InflaterRawReader} that DEFLATE-decompresses this {@code reader} while reading.
     */
    public static @NonNull InflaterRawReader inflate(final @NonNull RawReader reader,
                                                     final @NonNull Inflater inflater) {
        return new RealInflaterRawReader(reader, inflater);
    }

    /**
     * @return a {@link RawReader} that gzip-decompresses this {@code reader} while reading.
     */
    public static @NonNull RawReader gzip(final @NonNull RawReader reader) {
        return new GzipRawReader(reader);
    }

    /**
     * @return a writer that discards all data written to it.
     */
    public static @NonNull RawWriter discardingWriter() {
        return new DiscardingWriter();
    }

    private static final class DiscardingWriter implements RawWriter {
        @Override
        public void write(final @NonNull Buffer reader, final @NonNegative long byteCount) {
            Objects.requireNonNull(reader);
            try {
                reader.skip(byteCount);
            } catch (IllegalStateException ignored) {
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
