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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jayo;

import org.jspecify.annotations.NonNull;
import jayo.exceptions.JayoException;
import jayo.external.NonNegative;
import jayo.internal.*;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.WARNING;

/**
 * Essential APIs for working with Jayo.
 */
public final class Jayo {
    private static final System.Logger LOGGER = System.getLogger("o.u.d.Jayo");
    private static final System.Logger LOGGER_SOCKET_ASYNC_TIMEOUT = System.getLogger("o.u.d.SocketAsyncTimeout");

    // un-instantiable
    private Jayo() {
    }

    /**
     * @return a new sink that buffers writes to {@code sink}. The returned sink will batch writes to {@code sink}. On
     * each write operation, the underlying buffer will automatically emit all the complete segment(s), if any.
     * <p>
     * Use this wherever you write to a sink to get an ergonomic and efficient access to data.
     */
    public static @NonNull Sink buffer(final @NonNull RawSink sink) {
        Objects.requireNonNull(sink);
        if (sink instanceof RealSink realSink) {
            return realSink;
        }
        return new RealSink(sink);
    }

    /**
     * @return a new source that buffers reads from {@code source}. The returned source will perform bulk reads into its
     * underlying buffer.
     * <p>
     * Use this wherever you read a source to get an ergonomic and efficient access to data.
     */
    public static @NonNull Source buffer(final @NonNull RawSource source) {
        Objects.requireNonNull(source);
        if (source instanceof RealSource realSource) {
            return realSource;
        }
        return new RealSource(source);
    }

    /**
     * @return a sink that writes to {@code out} stream.
     */
    public static @NonNull RawSink sink(final @NonNull OutputStream out) {
        Objects.requireNonNull(out);
        return new OutputStreamSink(out);
    }

    /**
     * @return a source that reads from {@code in} stream.
     */
    public static @NonNull RawSource source(final @NonNull InputStream in) {
        Objects.requireNonNull(in);
        return new InputStreamSource(in);
    }

    /**
     * @return a sink that writes to {@code socket}. Prefer this over {@link #sink(OutputStream)} because this
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
            return timeout.sink(new OutputStreamSink(socket.getOutputStream()));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * @return a source that reads from {@code socket}. Prefer this over {@link #source(InputStream)} because this
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
            return timeout.source(new InputStreamSource(socket.getInputStream()));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * @return a sink that writes to {@code path}. {@code options} allow to specify how the file is opened.
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
     * @return a source that reads from {@code path}. {@code options} allow to specify how the file is opened.
     * @implNote We always add the {@code StandardOpenOption.READ} option to the options Set, so we ensure we can read
     * from this {@code path}.
     */
    public static @NonNull RawSource source(@NonNull Path path, @NonNull OpenOption @NonNull ... options) {
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
     * @return a sink that writes to {@code file}. If you need specific options, please use
     * {@link #sink(Path, OpenOption...)} instead.
     */
    public static @NonNull RawSink sink(@NonNull File file) {
        Objects.requireNonNull(file);
        try {
            return new OutputStreamSink(new FileOutputStream(file));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    /**
     * @return a source that reads from {@code file}. If you need specific options, please use
     * {@link #source(Path, OpenOption...)} instead.
     */
    public static @NonNull RawSource source(final @NonNull File file) {
        Objects.requireNonNull(file);
        try {
            return new InputStreamSource(new FileInputStream(file));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
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
            return new OutputStreamSink(Files.newOutputStream(path, options.toArray(new OpenOption[0])));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    private static RawSource source(@NonNull Path path, @NonNull Set<OpenOption> options) {
        Objects.requireNonNull(path);
        try {
            return new InputStreamSource(Files.newInputStream(path, options.toArray(new OpenOption[0])));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    private static final class DiscardingSink implements RawSink {
        @Override
        public void write(@NonNull Buffer source, @NonNegative long byteCount) {
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
