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
import jayo.ByteString;
import jayo.Jayo;
import jayo.crypto.Digests;
import jayo.crypto.Hmacs;

import java.nio.file.Path;

public final class Hashing {
    public void run() {
        Path path = Path.of("README.md");

        System.out.println("ByteString");
        ByteString byteString = readByteString(path);
        System.out.println("       md5: " + byteString.hash(Digests.MD5).hex());
        System.out.println("      sha1: " + byteString.hash(Digests.SHA_1).hex());
        System.out.println("    sha256: " + byteString.hash(Digests.SHA_256).hex());
        System.out.println("    sha512: " + byteString.hash(Digests.SHA_512).hex());
        System.out.println("  sha3_512: " + byteString.hash(Digests.SHA3_512).hex());
        System.out.println();

        System.out.println("Buffer");
        Buffer buffer = readBuffer(path);
        System.out.println("       md5: " + buffer.hash(Digests.MD5).hex());
        System.out.println("      sha1: " + buffer.hash(Digests.SHA_1).hex());
        System.out.println("    sha256: " + buffer.hash(Digests.SHA_256).hex());
        System.out.println("    sha512: " + buffer.hash(Digests.SHA_512).hex());
        System.out.println("  sha3_512: " + buffer.hash(Digests.SHA3_512).hex());
        System.out.println();

//        System.out.println("HashingSource");
//        try (HashingSource hashingSource = HashingSource.sha256(FileSystem.SYSTEM.source(path));
//             BufferedSource source = Okio.buffer(hashingSource)) {
//            source.readAll(Okio.blackhole());
//            System.out.println("    sha256: " + hashingSource.hash().hex());
//        }
//        System.out.println();
//
//        System.out.println("HashingSink");
//        try (HashingSink hashingSink = HashingSink.sha256(Okio.blackhole());
//             BufferedSink sink = Okio.buffer(hashingSink);
//             Source source = FileSystem.SYSTEM.source(path)) {
//            sink.writeAll(source);
//            sink.close(); // Emit anything buffered.
//            System.out.println("    sha256: " + hashingSink.hash().hex());
//        }
//        System.out.println();

        System.out.println("HMAC");
        ByteString secret = ByteString.decodeHex("7065616e7574627574746572");
        System.out.println("hmacSha256: " + byteString.hmac(Hmacs.HMAC_SHA_256, secret).hex());
        System.out.println();
    }

    public ByteString readByteString(Path path) {
        try (final var source = Jayo.buffer(Jayo.source(path))) {
            return source.readByteString();
        }
    }

    public Buffer readBuffer(Path path) {
        try (final var rawSource = Jayo.source(path)) {
            Buffer buffer = Buffer.create();
            buffer.transferFrom(rawSource);
            return buffer;
        }
    }

    public static void main(String[] args) {
        new Hashing().run();
    }
}
