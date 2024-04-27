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
import java.net.Socket;
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
import static java.lang.System.Logger.Level.WARNING;

/**
 * Essential APIs for working with Jayo.
 */
public final class Jayo {
    private static final System.Logger LOGGER = System.getLogger("jayo.Jayo");
    private static final System.Logger LOGGER_SOCKET_ASYNC_TIMEOUT = System.getLogger("jayo.SocketAsyncTimeout");

    // un-instantiable
    private Jayo() {
    }

    /**
     * @return a new sink that buffers writes to the raw {@code sink}. The returned sink will batch writes to
     * {@code sink}. On each write operation, the underlying buffer will automatically emit all the complete segment(s),
     * if any.
     * <p>
     * Use this wherever you write to a raw sink to get an ergonomic and efficient access to data.
     */
    public static @NonNull Sink buffer(final @NonNull RawSink sink) {
        Objects.requireNonNull(sink);
        if (sink instanceof RealSink realSink) {
            return realSink;
        }
        return new RealSink(sink);
    }

    /**
     * @return a new source that buffers reads from the raw {@code source}. The returned source will perform bulk reads
     * into its underlying buffer.
     * <p>
     * Use this wherever you read from a raw source to get an ergonomic and efficient access to data.
     */
    public static @NonNull Source buffer(final @NonNull RawSource source) {
        Objects.requireNonNull(source);
        if (source instanceof RealSource realSource) {
            return realSource;
        }
        return new RealSource(source);
    }

    /**
     * @return a raw sink that writes to {@code out} stream.
     */
    public static @NonNull RawSink sink(final @NonNull OutputStream out) {
        Objects.requireNonNull(out);
        return new OutputStreamRawSink(out);
    }

    /**
     * @return a raw source that reads from {@code in} stream.
     */
    public static @NonNull RawSource source(final @NonNull InputStream in) {
        Objects.requireNonNull(in);
        return new InputStreamRawSource(in);
    }

    /**
     * @return a raw sink that writes to {@code socket}. Prefer this over {@link #sink(OutputStream)} because this
     * method honors timeouts. When the socket write times out, the socket is asynchronously closed by a watchdog
     * thread.
     */
    public static @NonNull RawSink sink(final @NonNull Socket socket) {
        Objects.requireNonNull(socket);
        final var timeout = new RealAsyncTimeout(() -> {
            try {
                socket.close();
            } catch (Exception e) {
                LOGGER_SOCKET_ASYNC_TIMEOUT.log(WARNING, "Failed to close timed out socket " + socket, e);
            }
        });
        try {
            return timeout.sink(new OutputStreamRawSink(socket.getOutputStream()));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * @return a raw source that reads from {@code socket}. Prefer this over {@link #source(InputStream)} because this
     * method honors timeouts. When the socket read times out, the socket is asynchronously closed by a watchdog
     * thread.
     */
    public static @NonNull RawSource source(final @NonNull Socket socket) {
        Objects.requireNonNull(socket);
        final var timeout = new RealAsyncTimeout(() -> {
            try {
                socket.close();
            } catch (Exception e) {
                LOGGER_SOCKET_ASYNC_TIMEOUT.log(WARNING, "Failed to close timed out socket " + socket, e);
            }
        });
        try {
            return timeout.source(new InputStreamRawSource(socket.getInputStream()));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * @return a raw sink that writes to {@code path}. {@code options} allow to specify how the file is opened.
     * @implNote We always add the {@code StandardOpenOption.WRITE} option to the options Set, so we ensure we can write
     * in this {@code path}.
     */
    public static @NonNull RawSink sink(final @NonNull Path path, final @NonNull OpenOption @NonNull ... options) {
        Objects.requireNonNull(path);
        final Set<OpenOption> optionsSet = new HashSet<>();
        for (final var option : options) {
            if (option == StandardOpenOption.READ) {
                LOGGER.log(DEBUG, "Ignoring StandardOpenOption.READ. A sink does not read.");
                continue;
            }
            optionsSet.add(option);
        }
        optionsSet.add(StandardOpenOption.WRITE); // a sink needs the WRITE option
        return sink(path, optionsSet);
    }

    /**
     * @return a raw source that reads from {@code path}. {@code options} allow to specify how the file is opened.
     * @implNote We always add the {@code StandardOpenOption.READ} option to the options Set, so we ensure we can read
     * from this {@code path}.
     */
    public static @NonNull RawSource source(final @NonNull Path path, final @NonNull OpenOption @NonNull ... options) {
        Objects.requireNonNull(path);
        final Set<OpenOption> optionsSet = new HashSet<>();
        for (final var option : options) {
            if (option == StandardOpenOption.WRITE
                    || option == StandardOpenOption.APPEND
                    || option == StandardOpenOption.TRUNCATE_EXISTING
                    || option == StandardOpenOption.SYNC
                    || option == StandardOpenOption.DSYNC) {
                LOGGER.log(DEBUG, "Ignoring all write related options : WRITE, APPEND, TRUNCATE_EXISTING, " +
                        "SYNC, DSYNC. A source does not write.");
                continue;
            }
            optionsSet.add(option);
        }
        optionsSet.add(StandardOpenOption.READ); // a source needs the READ option
        return source(path, optionsSet);
    }

    /**
     * @return a raw sink that writes to {@code file}.
     * <p>
     * If you need specific options, please use {@link #sink(Path, OpenOption...)} instead.
     */
    public static @NonNull RawSink sink(final @NonNull File file) {
        Objects.requireNonNull(file);
        try {
            return new OutputStreamRawSink(new FileOutputStream(file));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * @return a raw source that reads from {@code file}.
     * <p>
     * If you need specific options, please use {@link #source(Path, OpenOption...)} instead.
     */
    public static @NonNull RawSource source(final @NonNull File file) {
        Objects.requireNonNull(file);
        try {
            return new InputStreamRawSource(new FileInputStream(file));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * Consumes all this source and return its hash.
     * 
     * @param rawSource the source
     * @param digest the chosen message digest algorithm to use for hashing.
     * @return the hash of this source.
     */
    public static @NonNull ByteString hash(final @NonNull RawSource rawSource, final @NonNull Digest digest) {
        return HashingUtils.hash(rawSource, digest);
    }

    /**
     * Consumes all this source and return its MAC result.
     *
     * @param rawSource the source
     * @param hMac the chosen "Message Authentication Code" (MAC) algorithm to use.
     * @param key the key to use for this MAC operation.
     * @return the MAC result of this source.
     */
    public static @NonNull ByteString hmac(final @NonNull RawSource rawSource,
                                           final @NonNull Hmac hMac,
                                           final @NonNull ByteString key) {
        return HashingUtils.hmac(rawSource, hMac, key);
    }

    /**
     * @return a {@link RawSink} that DEFLATE-compresses data to this {@code sink} while writing.
     */
    public static @NonNull RawSink deflate(final @NonNull RawSink sink) {
        return deflate(sink, new Deflater());
    }

    /**
     * @return a {@link RawSink} that DEFLATE-compresses data to this {@code sink} while writing.
     */
    public static @NonNull RawSink deflate(final @NonNull RawSink sink, final @NonNull Deflater deflater) {
        return new DeflaterRawSink(sink, deflater);
    }

    /**
     * @return an {@link InflaterRawSource} that DEFLATE-decompresses this {@code source} while reading.
     *
     * @see InflaterRawSource
     */
    public static @NonNull InflaterRawSource inflate(final @NonNull RawSource source) {
        return inflate(source, new Inflater());
    }

    /**
     * @return an {@link InflaterRawSource} that DEFLATE-decompresses this {@code source} while reading.
     *
     * @see InflaterRawSource
     */
    public static @NonNull InflaterRawSource inflate(final @NonNull RawSource source,
                                                     final @NonNull Inflater inflater) {
        return new RealInflaterRawSource(source, inflater);
    }

    /**
     * @return a {@link RawSource} that gzip-decompresses this {@code source} while reading.
     *
     * @see GzipRawSource
     */
    public static @NonNull RawSource gzip(final @NonNull RawSource source) {
        return new GzipRawSource(source);
    }

    /**
     * @return a sink that discards all data written to it.
     */
    public static @NonNull RawSink discardingSink() {
        return new DiscardingSink();
    }

    private static @NonNull RawSink sink(final @NonNull Path path, final @NonNull Set<OpenOption> options) {
        Objects.requireNonNull(path);
        try {
            final var bc = Files.newByteChannel(path, options.toArray(new OpenOption[0]));
            return new OutputStreamRawSink(Channels.newOutputStream(bc), bc);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    private static RawSource source(final @NonNull Path path, final @NonNull Set<OpenOption> options) {
        Objects.requireNonNull(path);
        try {
            return new InputStreamRawSource(Files.newInputStream(path, options.toArray(new OpenOption[0])));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    private static final class DiscardingSink implements RawSink {
        @Override
        public void write(final @NonNull Buffer source, final @NonNegative long byteCount) {
            Objects.requireNonNull(source);
            source.skip(byteCount);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
