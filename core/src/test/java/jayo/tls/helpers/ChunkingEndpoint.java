/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.tls.helpers;

import jayo.Buffer;
import jayo.RawReader;
import jayo.RawWriter;
import jayo.endpoints.Endpoint;
import org.jspecify.annotations.NonNull;

public class ChunkingEndpoint implements Endpoint {
    private final Endpoint wrapped;
    private final int chunkSize;

    public ChunkingEndpoint(Endpoint wrapped, int chunkSize) {
        this.wrapped = wrapped;
        this.chunkSize = chunkSize;
    }

    @Override
    public @NonNull RawReader getReader() {
        final var reader = wrapped.getReader();

        return new RawReader() {
            @Override
            public long readAtMostTo(@NonNull Buffer writer, long byteCount) {
                final var readSize = Math.min(chunkSize, byteCount);
                return reader.readAtMostTo(writer, readSize);
            }

            @Override
            public void close() {
                reader.close();
            }
        };
    }

    @Override
    public @NonNull RawWriter getWriter() {
        final var writer = wrapped.getWriter();

        return new RawWriter() {
            @Override
            public void write(@NonNull Buffer reader, long byteCount) {
                var remaining = byteCount;
                while (remaining > 0L) {
                    final var writeSize = Math.min(chunkSize, remaining);
                    writer.write(reader, writeSize);
                    remaining -= writeSize;
                }
            }

            @Override
            public void flush() {
                writer.flush();
            }

            @Override
            public void close() {
                writer.close();
            }
        };
    }

    @Override
    public void close() {
        wrapped.close();
    }

    @Override
    public @NonNull Endpoint getUnderlying() {
        return wrapped;
    }
}
