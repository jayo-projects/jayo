/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
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

package jayo.files;

import jayo.internal.files.RealFileMetadata;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Description of a file referenced by a path.
 * <p>
 * In simple use a file system is a mechanism for organizing files and directories on a local storage device. In
 * practice file systems are more capable and their contents more varied. For example, a path may refer to:
 * <ul>
 * <li>An operating system process that consumes data, produces data, or both. For example, reading from the
 * {@code /dev/urandom} file on Linux returns a unique sequence of pseudorandom bytes to each reader.
 * <li>A stream that connects a pair of programs together. A pipe is a special file that a producing program writes to
 * and a consuming program reads from. Both programs operate concurrently. The size of a pipe is not well-defined: the
 * writer can write as much data as the reader is able to read.
 * <li>A file on a remote file system. The performance and availability of remote files may be quite different from that
 * of local files!
 * <li>A symbolic link (symlink) to another file. When attempting to access this path the file system will follow the
 * link and return data from the target path.
 * <li>The same content as another path without a symlink. On UNIX file systems an inode is an anonymous handle to a
 * file's content, and multiple paths may target the same inode without any other relationship to one another. A
 * consequence of this design is that a directory with three 1 GiB files may only need 1 GiB on the storage device.
 * </ul>
 * This class does not attempt to model these rich file system features! It exposes a limited view useful for programs
 * with only basic file system needs. Be cautious of the potential consequences of special files when writing programs
 * that operate on a file system.
 * <p>
 * File metadata is subject to change, and code that operates on file systems should defend against changes to the file
 * that occur between reading metadata and subsequent operations.
 */
public sealed interface FileMetadata permits RealFileMetadata {
    /**
     * @return true if this file is a container of bytes. If this is true, then {@link File#getSize()} has a
     * non-negative value.
     */
    boolean isRegularFile();

    /**
     * @return the file that this file is a symlink to, or null if this file is not a symlink.
     */
    @Nullable
    File getSymlinkTarget();

    /**
     * @return the system time of the host computer when this file was created, if the host file system supports this
     * feature.
     * <p>
     * Note: this is typically available on Windows NTFS file systems and not available on UNIX or Windows FAT file
     * systems.
     */
    @Nullable
    Instant getCreatedAt();

    /**
     * @return the system time of the host computer when this file was most recently written.
     * <p>
     * Note: the accuracy of the returned time may be much more coarse than its precision. In particular, this value
     * is expressed with millisecond precision but may be accessed at second- or day-accuracy only.
     */
    @Nullable
    Instant getLastModifiedAt();

    /**
     * @return the system time of the host computer when this file was most recently read or written.
     * <p>
     * Note: the accuracy of the returned time may be much more coarse than its precision. In particular, this value is
     * expressed with millisecond precision but may be accessed at second- or day-accuracy only.
     */
    @Nullable
    Instant getLastAccessedAt();
}
