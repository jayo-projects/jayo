/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio) and kotlinx-io (https://github.com/Kotlin/kotlinx-io), original
 * copyrights are below
 *
 * Copyright 2017-2023 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENCE file.
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

package jayo.internal

import jayo.RawReader
import jayo.Reader
import jayo.Writer
import jayo.buffered

interface ReaderFactory {
    class Pipe(
        var writer: Writer,
        var reader: Reader
    )

    fun pipe(): Pipe

    companion object {
        val BUFFER: ReaderFactory = object : ReaderFactory {
            override fun pipe(): Pipe {
                val buffer = RealBuffer()
                return Pipe(
                    buffer,
                    buffer
                )
            }
        }

        val REAL_SOURCE: ReaderFactory = object :
            ReaderFactory {
            override fun pipe(): Pipe {
                val buffer = RealBuffer()
                return Pipe(
                    buffer,
                    (buffer as RawReader).buffered()
                )
            }
        }

        val PEEK_BUFFER: ReaderFactory = object : ReaderFactory {
            override fun pipe(): Pipe {
                val buffer = RealBuffer()
                return Pipe(
                    buffer,
                    buffer.peek()
                )
            }
        }

        val PEEK_SOURCE: ReaderFactory = object :
            ReaderFactory {
            override fun pipe(): Pipe {
                val buffer = RealBuffer()
                val origin = (buffer as RawReader).buffered()
                return Pipe(
                    buffer,
                    origin.peek()
                )
            }
        }

        val BUFFERED_SOURCE: ReaderFactory = object :
            ReaderFactory {
            override fun pipe(): Pipe {
                val buffer = RealBuffer()
                val origin = (buffer as RawReader).buffered()
                return Pipe(
                    buffer,
                    origin.buffered()
                )
            }
        }
    }
}
