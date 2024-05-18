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
import jayo.RawSource;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Random;

/**
 * Demonstrates use of the {@link Buffer.UnsafeCursor} class. While other
 * samples might demonstrate real use cases, this sample hopes to show the
 * basics of using an {@link Buffer.UnsafeCursor}:
 * <ul>
 *   <li>Efficient reuse of a single cursor instance.</li>
 *   <li>Guaranteed release of an attached cursor.</li>
 *   <li>Safe traversal of the data in a Buffer.</li>
 * </ul>
 *
 * <p>This sample implements a
 * <a href="https://en.wikipedia.org/wiki/Cipher_disk">circular cipher</a> by
 * creating a Source which will intercept all bytes written to the wire and
 * decrease their value by a specific amount. Then create a Sink which will
 * intercept all bytes read from the wire and increase their value by that same
 * specific amount. This creates an incredibly insecure way of encrypting data
 * written to the wire but demonstrates the power of the
 * {@link Buffer.UnsafeCursor} class for efficient operations on the bytes
 * being written and read.
 */
public final class Interceptors {
    public void run() {
        final byte cipher = (byte) (new Random().nextInt(256) - 128);
        System.out.println("Cipher   : " + cipher);

        Buffer wire = Buffer.create();

        // Create a Sink which will intercept and negatively rotate each byte by `cipher`
        RawSink sink = new InterceptingSink(wire) {
            @Override
            protected void intercept(byte[] data, int offset, int length) {
                for (int i = offset, end = offset + length; i < end; i++) {
                    data[i] -= cipher;
                }
            }
        };

        // Create a Source which will intercept and positively rotate each byte by `cipher`
        final var source = new InterceptingSource(wire) {
            @Override
            protected void intercept(byte[] data, int offset, int length) {
                for (int i = offset, end = offset + length; i < end; i++) {
                    data[i] += cipher;
                }
            }
        };

        Buffer transmit = Buffer.create();
        transmit.writeUtf8("This is not really a secure message");
        System.out.println("Transmit : " + transmit);

        sink.write(transmit, transmit.byteSize());
        System.out.println("Wire     : " + wire);

        Buffer receive = Buffer.create();
        source.readAtMostTo(receive, Long.MAX_VALUE);
        System.out.println("Receive  : " + receive);
    }

    abstract static class InterceptingSource implements RawSource {
        private final RawSource delegate;
        private final Buffer.UnsafeCursor cursor = Buffer.UnsafeCursor.create();

        InterceptingSource(RawSource delegate) {
            this.delegate = Objects.requireNonNull(delegate);
        }

        @Override
        public long readAtMostTo(final @NonNull Buffer sink, final long byteCount) {
            if (byteCount < 0L) {
                throw new IllegalArgumentException("byteCount < 0: " + byteCount);
            }
            if (byteCount == 0L) {
                return 0L;
            }

            long result = delegate.readAtMostTo(sink, byteCount);
            if (result == -1L) {
                return result;
            }

            sink.readUnsafe(cursor);
            try {
                long remaining = result;
                for (int length = cursor.seek(sink.byteSize() - result);
                     remaining > 0 && length > 0;
                     length = cursor.next()) {
                    int toIntercept = (int) Math.min(length, remaining);
                    intercept(cursor.data, cursor.pos, toIntercept);
                    remaining -= toIntercept;
                }
            } finally {
                cursor.close();
            }

            return result;
        }

        @Override
        public void close() {
            delegate.close();
        }

        protected abstract void intercept(byte[] data, int offset, int length);
    }


    abstract static class InterceptingSink implements RawSink {
        private final RawSink delegate;
        private final Buffer.UnsafeCursor cursor = Buffer.UnsafeCursor.create();

        InterceptingSink(RawSink delegate) {
            this.delegate = Objects.requireNonNull(delegate);
        }

        @Override
        public void write(@NonNull Buffer source, long byteCount) {
            if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
            if (source.byteSize() < byteCount) {
                throw new IllegalArgumentException("size=" + source.byteSize() + " byteCount=" + byteCount);
            }
            if (byteCount == 0) return;

            source.readUnsafe(cursor);
            try {
                long remaining = byteCount;
                for (int length = cursor.seek(0);
                     remaining > 0 && length > 0;
                     length = cursor.next()) {
                    int toIntercept = (int) Math.min(length, remaining);
                    intercept(cursor.data, cursor.pos, toIntercept);
                    remaining -= toIntercept;
                }
            } finally {
                cursor.close();
            }

            delegate.write(source, byteCount);
        }

        protected abstract void intercept(byte[] data, int offset, int length);

        @Override
        public void flush() {
            delegate.flush();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    public static void main(String... args) {
        new Interceptors().run();
    }
}
