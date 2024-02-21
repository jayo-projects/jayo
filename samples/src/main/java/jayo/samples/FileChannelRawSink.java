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

package jayo.samples;

import jayo.Buffer;
import jayo.RawSink;
import jayo.exceptions.JayoException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

/**
 * Special Sink for a FileChannel to take advantage of the
 * {@link FileChannel#transferFrom(ReadableByteChannel, long, long) transfer} method available.
 */
final class FileChannelRawSink implements RawSink {
    private final FileChannel channel;

    private long position;

    FileChannelRawSink(FileChannel channel) throws IOException {
        this.channel = channel;
        this.position = channel.position();
    }

    @Override
    public void write(Buffer source, long byteCount) {
        if (!channel.isOpen()) throw new IllegalStateException("closed");
        if (byteCount == 0) return;

        final var sourceAsByteChannel = source.asReadableByteChannel();
        try {
            long remaining = byteCount;
            while (remaining > 0) {
                long written = channel.transferFrom(sourceAsByteChannel, position, remaining);
                position += written;
                remaining -= written;
            }
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public void flush() {
        try {
            // Cannot alter meta data through this Sink
            channel.force(false);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }
}
