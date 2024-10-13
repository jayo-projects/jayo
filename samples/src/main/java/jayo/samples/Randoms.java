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
import jayo.Jayo;
import jayo.RawReader;
import jayo.Reader;
import org.jspecify.annotations.NonNull;

import java.util.Random;

public final class Randoms {
    public void run() {
        Random random = new Random(3782615686L);
        Reader reader = Jayo.buffer(new RandomReader(random, 5));
        System.out.println("Secret random is: " + reader.readString());
    }

    static final class RandomReader implements RawReader {
        private final Random random;
        private long bytesLeft;

        RandomReader(Random random, long bytesLeft) {
            this.random = random;
            this.bytesLeft = bytesLeft;
        }

        @Override
        public long readAtMostTo(final @NonNull Buffer writer, final long byteCount) {
            if (bytesLeft == -1L) {
                throw new IllegalStateException("closed");
            }
            if (bytesLeft == 0L) {
                return -1L;
            }
            long resolvedByteCount = byteCount;
            if (byteCount > Integer.MAX_VALUE) {
                resolvedByteCount = Integer.MAX_VALUE;
            }
            if (byteCount > bytesLeft) {
                resolvedByteCount = bytesLeft;
            }

            // Random is most efficient when computing 32 bits of randomness. Start with that.
            int ints = (int) (resolvedByteCount / 4);
            for (int i = 0; i < ints; i++) {
                writer.writeInt(random.nextInt());
            }

            // If we need 1, 2, or 3 bytes more, keep going. We'll discard 24, 16 or 8 random bits!
            int bytes = (int) (resolvedByteCount - ints * 4);
            if (bytes > 0) {
                int bits = random.nextInt();
                for (int i = 0; i < bytes; i++) {
                    writer.writeByte((byte) (bits & 0xff));
                    bits >>>= 8;
                }
            }

            bytesLeft -= resolvedByteCount;
            return resolvedByteCount;
        }

        @Override
        public void close() {
            bytesLeft = -1L;
        }
    }

    public static void main(String... args) {
        new Randoms().run();
    }
}
