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

import jayo.bytestring.ByteString;
import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import jayo.internal.*;
import jayo.network.NetworkSocket;
import org.jspecify.annotations.NonNull;

import java.io.*;
import java.nio.channels.*;
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
     * @return a new writer that buffers writes to the {@code rawWriter}. The returned writer will batch writes to
     * {@code writer}. On each write operation, the underlying buffer will automatically emit all the complete
     * segment(s), if any.
     * <p>
     * Use this wherever you write to a raw writer to get ergonomic and efficient access to data.
     */
    public static @NonNull Writer buffer(final @NonNull RawWriter rawWriter) {
        Objects.requireNonNull(rawWriter);
        return new RealWriter(rawWriter);
    }

    /**
     * @return a new reader that buffers reads from the raw {@code rawReader}. The returned reader will perform bulk reads
     * into its underlying buffer.
     * <p>
     * Use this wherever you read from a raw reader to get ergonomic and efficient access to data.
     */
    public static @NonNull Reader buffer(final @NonNull RawReader rawReader) {
        Objects.requireNonNull(rawReader);
        return new RealReader(rawReader);
    }

    /**
     * @return a new socket that buffers read and writes from the {@code rawSocket}.
     */
    public static @NonNull Socket buffer(final @NonNull RawSocket rawSocket) {
        Objects.requireNonNull(rawSocket);
        return new Socket() {
            private boolean isCanceled = false;

            @Override
            public @NonNull Reader getReader() {
                return buffer(rawSocket.getReader());
            }

            @Override
            public @NonNull Writer getWriter() {
                return buffer(rawSocket.getWriter());
            }

            @Override
            public void cancel() {
                isCanceled = true;
            }

            @Override
            public boolean isOpen() {
                return !isCanceled;
            }

            @Override
            public @NonNull Object getUnderlying() {
                return rawSocket;
            }
        };
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
     * @return a raw writer that writes to {@code out} gathering byte channel.
     */
    public static @NonNull RawWriter writer(final @NonNull GatheringByteChannel out) {
        Objects.requireNonNull(out);
        return new GatheringByteChannelRawWriter(out);
    }

    /**
     * @return a raw writer that writes to {@code out} writable byte channel.
     */
    public static @NonNull RawWriter writer(final @NonNull WritableByteChannel out) {
        Objects.requireNonNull(out);
        return new WritableByteChannelRawWriter(out);
    }

    /**
     * @return a raw reader that reads from {@code in} readable byte channel.
     */
    public static @NonNull RawReader reader(final @NonNull ReadableByteChannel in) {
        Objects.requireNonNull(in);
        return new ReadableByteChannelRawReader(in);
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
            return new GatheringByteChannelRawWriter(FileChannel.open(path, options));
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
            return new ReadableByteChannelRawReader(FileChannel.open(path, options));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * @return a raw writer that writes to {@code file}.
     * <p>
     * If you need specific open options, please use {@link #writer(Path, OpenOption...)} instead.
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
     * If you need specific open options, please use {@link #reader(Path, OpenOption...)} instead.
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
     * @return a {@linkplain NetworkSocket Jayo network socket} based on {@code ioSocket}. Prefer this over
     * {@linkplain #writer(OutputStream) Jayo.writer(ioSocket.getOutputStream())} and
     * {@linkplain #reader(InputStream) Jayo.reader(ioSocket.getInputStream())} because this socket honors timeouts.
     * When a socket operation times out, this socket is asynchronously closed by a watchdog thread.
     */
    public static @NonNull NetworkSocket socket(final java.net.@NonNull Socket ioSocket) {
        Objects.requireNonNull(ioSocket);
        return new IoSocketNetworkSocket(ioSocket);
    }

    /**
     * @return a {@linkplain NetworkSocket Jayo network socket} based on {@code nioSocketChannel}. Prefer this over
     * {@linkplain #writer(GatheringByteChannel) Jayo.writer(nioSocketChannel)} and
     * {@linkplain #reader(ReadableByteChannel) Jayo.reader(nioSocketChannel)} because this socket honors timeouts.
     * When a socket operation times out, this socket is asynchronously closed by a watchdog thread.
     */
    public static @NonNull NetworkSocket socket(final @NonNull SocketChannel nioSocketChannel) {
        Objects.requireNonNull(nioSocketChannel);
        return new SocketChannelNetworkSocket(nioSocketChannel);
    }

    /**
     * @return an array of two symmetric sockets, <i>A</i> (element 0) and <i>B</i> (element 1) that are mutually
     * connected:
     * <ul>
     * <li>Pipe AB connects <i>A</i>’s writer to <i>B</i>’s reader.
     * <li>Pipe BA connects <i>B</i>’s writer to <i>A</i>’s reader.
     * </ul>
     * Each pipe uses a buffer to decouple reader and writer. This buffer has a user-specified maximum size. When a
     * socket writer outruns its corresponding reader, the buffer fills up and eventually writes to the writer will
     * block until the reader has caught up. Symmetrically, if a reader outruns its writer, reads block until there is
     * data to be read.
     * <p>
     * There is a buffer for Pipe AB and another for Pipe BA. The maximum amount of memory that could be held by the two
     * sockets together is {@code maxBufferSize * 2}.
     * <p>
     * Limits on the amount of time spent waiting for the other party can be configured by using
     * {@linkplain Cancellable#call(java.time.Duration, java.util.function.Function) call with timeout} or
     * {@linkplain Cancellable#run(java.time.Duration, java.util.function.Consumer) run with timeout}.
     * <p>
     * When the writer is closed, reader reads will continue to complete normally until the buffer is exhausted. At that
     * point reads will return -1, indicating the end of the stream. But if the reader is closed first, writes to the
     * writer will immediately fail with a {@link JayoException}.
     * <p>
     * Canceling either socket immediately fails all reads and writes on both sockets.
     */
    public static @NonNull RawSocket @NonNull [] inMemorySocketPair(final long maxBufferSize) {
        final var ab = Pipe.create(maxBufferSize);
        final var ba = Pipe.create(maxBufferSize);
        return new RawSocket[]{new PipeRawSocket(ab, ba), new PipeRawSocket(ba, ab)};
    }

    /**
     * Closes this {@code socket}, ignoring any {@link JayoException}.
     */
    public static void closeQuietly(final @NonNull RawSocket socket) {
        Objects.requireNonNull(socket);
        try (final var ignored1 = socket.getWriter(); final var ignored2 = socket.getReader()) {
            socket.cancel();
        } catch (JayoException ignored) {
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
     * @return a {@link RawWriter} that DEFLATE-compresses data to this {@code writer} while writing, using the provided
     * {@code deflater}.
     */
    public static @NonNull RawWriter deflate(final @NonNull RawWriter writer, final @NonNull Deflater deflater) {
        Objects.requireNonNull(writer);
        Objects.requireNonNull(deflater);
        return new DeflaterRawWriter(writer, deflater);
    }

    /**
     * @return an {@link InflaterRawReader} that DEFLATE-decompresses data of this {@code reader} while reading.
     */
    public static @NonNull InflaterRawReader inflate(final @NonNull RawReader reader) {
        return inflate(reader, new Inflater());
    }

    /**
     * @return an {@link InflaterRawReader} that DEFLATE-decompresses data of this {@code reader} while reading, using
     * the provided {@code inflater}.
     */
    public static @NonNull InflaterRawReader inflate(final @NonNull RawReader reader,
                                                     final @NonNull Inflater inflater) {
        Objects.requireNonNull(reader);
        Objects.requireNonNull(inflater);
        return new RealInflaterRawReader(reader, inflater);
    }

    /**
     * @return a {@link RawWriter} that gzip-compresses data to this {@code writer} while writing.
     */
    public static @NonNull RawWriter gzip(final @NonNull RawWriter writer) {
        Objects.requireNonNull(writer);
        return new GzipRawWriter(writer);
    }

    /**
     * @return a {@link RawReader} that gzip-decompresses data of this {@code reader} while reading.
     */
    public static @NonNull RawReader gzip(final @NonNull RawReader reader) {
        Objects.requireNonNull(reader);
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
        public void writeFrom(final @NonNull Buffer source, final long byteCount) {
            Objects.requireNonNull(source);

            try {
                source.skip(byteCount);
            } catch (IllegalStateException ignored) {
            }
        }

        @Override
        public void flush() {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
