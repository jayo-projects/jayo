/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.internal;

import jayo.*;
import org.jspecify.annotations.NonNull;

public class ChunkingSocket implements Socket {
    private final SocketChannelNetworkSocket wrapped;
    private final Reader reader;
    private final Writer writer;

    public ChunkingSocket(Socket _wrapped, int chunkSize) {
        this.wrapped = ((SocketChannelNetworkSocket) _wrapped);

        final var rawReader = wrapped.reader.reader;
        final var newRawReader = new RawReader() {
            @Override
            public long readAtMostTo(final @NonNull Buffer writer, final long byteCount) {
                final var readSize = Math.min(chunkSize, byteCount);
                return rawReader.readAtMostTo(writer, readSize);
            }

            @Override
            public void close() {
                rawReader.close();
            }
        };
        reader = Jayo.buffer(newRawReader);

        final var rawWriter = wrapped.writer.writer;
        final var newRawWriter = new RawWriter() {
            @Override
            public void writeFrom(final @NonNull Buffer source, final long byteCount) {
                var remaining = byteCount;
                while (remaining > 0L) {
                    final var writeSize = Math.min(chunkSize, remaining);
                    rawWriter.writeFrom(source, writeSize);
                    remaining -= writeSize;
                }
            }

            @Override
            public void flush() {
                rawWriter.flush();
            }

            @Override
            public void close() {
                rawWriter.close();
            }
        };
        writer = Jayo.buffer(newRawWriter);
    }

    @Override
    public @NonNull Reader getReader() {
        return reader;
    }

    @Override
    public @NonNull Writer getWriter() {
        return writer;
    }

    @Override
    public void cancel() {
        wrapped.cancel();
    }

    @Override
    public boolean isOpen() {
        return wrapped.isOpen();
    }

    @Override
    public @NonNull Socket getUnderlying() {
        return wrapped;
    }
}
