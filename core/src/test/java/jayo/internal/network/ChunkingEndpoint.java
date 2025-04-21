/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.internal.network;

import jayo.*;
import org.jspecify.annotations.NonNull;

public class ChunkingEndpoint implements Endpoint {
    private final SocketChannelNetworkEndpoint wrapped;

    public ChunkingEndpoint(Endpoint _wrapped, int chunkSize) {
        this.wrapped = ((SocketChannelNetworkEndpoint) _wrapped);

        final var rawReader = wrapped.buildRawReader();
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
        wrapped.setReader(newRawReader);

        final var rawWriter = wrapped.buildRawWriter();
        final var newRawWriter = new RawWriter() {
            @Override
            public void write(final @NonNull Buffer reader, final long byteCount) {
                var remaining = byteCount;
                while (remaining > 0L) {
                    final var writeSize = Math.min(chunkSize, remaining);
                    rawWriter.write(reader, writeSize);
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
        wrapped.setWriter(newRawWriter);
    }

    @Override
    public @NonNull Reader getReader() {
        return wrapped.getReader();
    }

    @Override
    public @NonNull Writer getWriter() {
        return wrapped.getWriter();
    }

    @Override
    public void close() {
        wrapped.close();
    }

    @Override
    public boolean isOpen() {
        return wrapped.isOpen();
    }

    @Override
    public @NonNull Endpoint getUnderlying() {
        return wrapped;
    }
}
