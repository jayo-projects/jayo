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
import jayo.RawReader;
import jayo.JayoException;
import jayo.external.CancelToken;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Creates a Reader around a ReadableByteChannel and efficiently reads data using an UnsafeCursor.
 *
 * <p>This is a basic example showing another use for the UnsafeCursor. Using the
 * {@link ByteBuffer#wrap(byte[], int, int) ByteBuffer.wrap()} along with access to Buffer segments,
 * a ReadableByteChannel can be given direct access to Buffer data without having to copy the data.
 */
final class ByteChannelRawReader implements RawReader {
    private final ReadableByteChannel channel;

    private final Buffer.UnsafeCursor cursor = Buffer.UnsafeCursor.create();

    ByteChannelRawReader(ReadableByteChannel channel) {
        this.channel = channel;
    }

    @Override
    public long readAtMostTo(final @NonNull Buffer writer, final long byteCount) {
        if (!channel.isOpen()) throw new IllegalStateException("closed");

        final var cancelToken = CancelToken.getCancelToken();

        try (Buffer.UnsafeCursor ignored = writer.readAndWriteUnsafe(cursor)) {
            CancelToken.throwIfReached(cancelToken);
            long oldSize = writer.byteSize();
            int length = (int) Math.min(8192, byteCount);

            cursor.expandBuffer(length);
            int read = channel.read(ByteBuffer.wrap(cursor.data, cursor.pos, length));
            if (read == -1) {
                cursor.resizeBuffer(oldSize);
            } else {
                cursor.resizeBuffer(oldSize + read);
            }
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
