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

package jayo.internal;

import jayo.Buffer;
import jayo.RawReader;
import jayo.RawWriter;
import jayo.endpoints.JayoClosedEndpointException;
import jayo.endpoints.SocketEndpoint;
import jayo.exceptions.JayoException;
import jayo.exceptions.JayoInterruptedIOException;
import jayo.exceptions.JayoTimeoutException;
import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.Socket;
import java.util.Objects;

import static java.lang.System.Logger.Level.WARNING;
import static jayo.external.JayoUtils.checkOffsetAndCount;

public final class RealSocketEndpoint implements SocketEndpoint {
    private static final System.Logger LOGGER_TIMEOUT = System.getLogger("jayo.SocketEndpointTimeout");
    private final @NonNull Socket socket;
    private final @NonNull RealAsyncTimeout asyncTimeout;

    @SuppressWarnings("FieldMayBeFinal")
    private volatile RawReader reader = null;
    @SuppressWarnings("FieldMayBeFinal")
    private volatile RawWriter writer = null;

    // VarHandle mechanics
    private static final VarHandle READER;
    private static final VarHandle WRITER;

    static {
        try {
            final var l = MethodHandles.lookup();
            READER = l.findVarHandle(RealSocketEndpoint.class, "reader", RawReader.class);
            WRITER = l.findVarHandle(RealSocketEndpoint.class, "writer", RawWriter.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public RealSocketEndpoint(final @NonNull Socket socket) {
        this.socket = Objects.requireNonNull(socket);
        asyncTimeout = new RealAsyncTimeout(() -> {
            try {
                socket.close();
            } catch (Exception e) {
                LOGGER_TIMEOUT.log(WARNING, "Failed to close timed out socket " + socket, e);
            }
        });
    }

    @Override
    public @NonNull RawReader getReader() {
        var reader = this.reader;
        try {
            // always get the input stream from socket that does some checks
            final var in = socket.getInputStream();
            if (reader == null) {
                reader = new SocketEndpointRawReader(asyncTimeout.reader(new InputStreamRawReader(in)));
            }
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }

        if (!READER.compareAndSet(this, null, reader)) {
            reader = this.reader;
        }

        return reader;
    }

    @Override
    public @NonNull RawWriter getWriter() {
        var writer = this.writer;
        try {
            // always get the output stream from socket that does some checks
            final var out = socket.getOutputStream();
            if (writer == null) {
                writer = new SocketEndpointRawWriter(asyncTimeout.writer(new OutputStreamRawWriter(out)));
            }
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }

        if (!WRITER.compareAndSet(this, null, writer)) {
            writer = this.writer;
        }

        return writer;
    }

    @Override
    public @NonNull Socket getUnderlying() {
        return socket;
    }

    /**
     * This raw reader must respect Socket behavior regarding interrupted exceptions
     */
    private record SocketEndpointRawReader(@NonNull RawReader reader) implements RawReader {

        @Override
        public long readAtMostTo(final @NonNull Buffer writer, final @NonNegative long byteCount) {
            Objects.requireNonNull(writer);
            if (byteCount < 0L) {
                throw new IllegalArgumentException("byteCount < 0 : " + byteCount);
            }

            try {
                return reader.readAtMostTo(writer, byteCount);
            } catch (JayoTimeoutException e) {
                throw e;
            } catch (JayoInterruptedIOException e) {
                Thread thread = Thread.currentThread();
                if (thread.isVirtual() && thread.isInterrupted()) {
                    close();
                    throw new JayoClosedEndpointException();
                }
                throw e;
            }
        }

        @Override
        public void close() {
            reader.close();
        }
    }

    /**
     * This raw writer must respect Socket behavior regarding interrupted exceptions
     */
    private record SocketEndpointRawWriter(@NonNull RawWriter writer) implements RawWriter {

        @Override
        public void write(final @NonNull Buffer reader, final @NonNegative long byteCount) {
            Objects.requireNonNull(reader);
            checkOffsetAndCount(reader.byteSize(), 0, byteCount);

            try {
                writer.write(reader, byteCount);
            } catch (JayoInterruptedIOException e) {
                Thread thread = Thread.currentThread();
                if (thread.isVirtual() && thread.isInterrupted()) {
                    close();
                    throw new JayoClosedEndpointException();
                }
                throw e;
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            writer.close();
        }
    }
}
