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

package jayo.samples

import jayo.buffered
import jayo.writer
import java.nio.file.Path
import java.nio.file.StandardOpenOption

fun writeEnv(path: Path) {
    path.writer(StandardOpenOption.CREATE).buffered().use { writer ->
        for ((key, value) in System.getenv()) {
            writer.write(key)
            writer.write("=")
            writer.write(value)
            writer.write("\n")
        }
    }
}

fun main() {
    writeEnv(Path.of("env.txt"))
}
