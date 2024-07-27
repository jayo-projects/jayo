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

import jayo.Jayo;
import jayo.Writer;

import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public final class WriteFile {
  public void run() {
    writeEnv(Path.of("env.txt"));
  }

  public void writeEnv(Path path) {
    try (Writer fileWriter = Jayo.buffer(Jayo.writer(path, StandardOpenOption.CREATE))) {

      for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
        fileWriter.writeUtf8(entry.getKey());
        fileWriter.writeUtf8("=");
        fileWriter.writeUtf8(entry.getValue());
        fileWriter.writeUtf8("\n");
      }

    }
  }

  public static void main(String... args) throws Exception {
    new WriteFile().run();
  }
}
