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
import jayo.RawSource;
import jayo.exceptions.JayoException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Special Source for a FileChannel to take advantage of the
 * {@link FileChannel#transferTo(long, long, WritableByteChannel) transfer} method available.
 */
final class FileChannelRawSource implements RawSource {
    private final FileChannel channel;

    private long position;

    FileChannelRawSource(FileChannel channel) throws IOException {
        this.channel = channel;
        this.position = channel.position();
    }

    @Override
    public long readAtMostTo(Buffer sink, long byteCount) {
        if (!channel.isOpen()) throw new IllegalStateException("closed");
        try {
            if (position == channel.size()) {
                return -1L;
            }

            long read = channel.transferTo(position, byteCount, sink.asWritableByteChannel());
            position += read;
            return read;
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
