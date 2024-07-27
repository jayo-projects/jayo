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

import jayo.Utf8Utils;
import jayo.Utf8;

public final class ExploreCharsets {
    public void run() {
        dumpStringData("Café \uD83C\uDF69"); // NFC: é is one code point.
        dumpStringData("Café \uD83C\uDF69"); // NFD: e is one code point, its accent is another.
    }

    public void dumpStringData(String s) {
        System.out.println("                       " + s);
        System.out.println("        String.length: " + s.length());
        System.out.println("String.codePointCount: " + s.codePointCount(0, s.length()));
        System.out.println("            Utf8.size: " + Utf8Utils.size(s));
        System.out.println("          UTF-8 bytes: " + Utf8.encodeUtf8(s).hex());
        System.out.println();
    }

    public static void main(String... args) {
        new ExploreCharsets().run();
    }
}
