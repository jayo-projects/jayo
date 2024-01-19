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
 *      http://www.apache.org/licenses/LICENSE-2.0
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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public final class GoldenValueSample {
    public void run() throws Exception {
        Point point = new Point(8.0, 15.0);
        ByteString pointBytes = serialize(point);
        System.out.println(pointBytes.base64());

        ByteString goldenBytes = ByteString.decodeBase64("rO0ABXNyAChvcmcudWZvc3MuZGluby5zYW1wbGVz" +
                "LkdvbGRlblZhbHVlJFBvaW50NwIhU3bWX1cCAAJEAAF4RAABeXhwQCAAAAAAAABALgAAAAAAAA==");
        Point decoded = (Point) deserialize(goldenBytes);
        assertEquals(point, decoded);
    }

    private static ByteString serialize(Object o) throws IOException {
        Buffer buffer = Buffer.create();
        try (ObjectOutputStream objectOut = new ObjectOutputStream(buffer.asOutputStream())) {
            objectOut.writeObject(o);
        }
        return buffer.readByteString();
    }

    private static Object deserialize(ByteString byteString) throws IOException, ClassNotFoundException {
        Buffer buffer = Buffer.create();
        buffer.write(byteString);
        try (ObjectInputStream objectIn = new ObjectInputStream(buffer.asInputStream())) {
            Object result = objectIn.readObject();
            if (objectIn.read() != -1) throw new IOException("Unconsumed bytes in stream");
            return result;
        }
    }

    static final class Point implements Serializable {
        double x;
        double y;

        Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private void assertEquals(Point a, Point b) {
        if (a.x != b.x || a.y != b.y) throw new AssertionError();
    }

    public static void main(String... args) throws Exception {
        new GoldenValueSample().run();
    }
}
