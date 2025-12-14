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

import jayo.RawReader;
import jayo.RawWriter;
import jayo.bytestring.ByteString;
import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import jayo.internal.files.RealFile;
import org.jspecify.annotations.NonNull;

import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A Jayo's File is guaranteed to be a real existing file.
 */
public sealed interface File permits RealFile {
    /**
     * Opens this existing file, then returns it.
     *
     * @return the opened file
     * @throws JayoFileNotFoundException if the requested file does not exist.
     * @throws jayo.JayoException        if an I/O error occurs or the parent directory does not exist.
     */
    static @NonNull File open(final @NonNull Path path) {
        Objects.requireNonNull(path);
        return RealFile.open(path);
    }

    /**
     * Creates this non-existing file, then returns it.
     *
     * @return the created file
     * @throws JayoFileAlreadyExistsException if the requested file already exists.
     * @throws jayo.JayoException             if an I/O error occurs or the parent directory does not exist.
     */
    static @NonNull File create(final @NonNull Path path) {
        Objects.requireNonNull(path);
        return RealFile.create(path);
    }

    /**
     * Creates this file if it did not exist yet, else open it, then returns it.
     *
     * @return the created or opened file
     * @throws jayo.JayoException if an I/O error occurs or the parent directory does not exist.
     */
    static @NonNull File createIfNotExists(final @NonNull Path path) {
        Objects.requireNonNull(path);
        return RealFile.createIfNotExists(path);
    }

    static boolean exists(final @NonNull Path path) {
        Objects.requireNonNull(path);
        return Files.exists(path) && !Files.isDirectory(path);
    }

    /**
     * @return a RawWriter that writes to this file. {@code options} allow to specify how the file is opened.
     * @throws JayoFileNotFoundException if the file does not exist anymore.
     */
    @NonNull
    RawWriter writer(final @NonNull OpenOption @NonNull ... options);

    /**
     * @return a RawReader that reads from this file.
     * @throws JayoFileNotFoundException if the file does not exist anymore.
     */
    @NonNull
    RawReader reader();

    /**
     * In general, one may expect that for a path like {@code Path.of("home", "Downloads", "file.txt")} the name is
     * {@code file.txt}.
     *
     * @return the name of this file.
     */
    @NonNull
    String getName();

    /**
     * @return the number of readable bytes in this file. The amount of storage resources consumed by this file may be
     * larger (due to block size overhead, redundant copies for RAID, etc.), or smaller (due to file system compression,
     * shared inodes, etc.). The size of files that are not {@code regular} is unspecified, so this method returns
     * {@code -1L} for them.
     * @throws JayoFileNotFoundException if the file does not exist anymore.
     */
    long byteSize();

    /**
     * @return the {@code path} of this file.
     */
    @NonNull
    Path getPath();

    /**
     * @return the metadata of this file.
     * @throws JayoFileNotFoundException if the file does not exist anymore.
     * @throws jayo.JayoException        if this file cannot be accessed due to a connectivity problem, permissions
     *                                   problem, or other issue.
     */
    @NonNull
    FileMetadata metadata();

    /**
     * @param digest the chosen message digest algorithm to use for hashing.
     * @return the hash of this File.
     * @throws JayoFileNotFoundException if the file does not exist anymore.
     */
    @NonNull
    ByteString hash(final @NonNull Digest digest);

    /**
     * @param hMac the chosen "Message Authentication Code" (MAC) algorithm to use.
     * @param key  the key to use for this MAC operation.
     * @return the MAC result of this File.
     * @throws JayoFileNotFoundException if the file does not exist anymore.
     */
    @NonNull
    ByteString hmac(final @NonNull Hmac hMac, final @NonNull ByteString key);

    /**
     * Atomically moves or renames this file to {@code destination}, overriding {@code destination} if it already
     * exists.
     *
     * <h3>Only as atomic as the underlying File System supports</h3>
     * FAT and NTFS file systems cannot atomically move a file over an existing file. If the target file already exists,
     * the move is performed into two steps:
     * <ol>
     * <li>Atomically delete the target file.
     * <li>Atomically rename the source file to the target file.
     * </ol>
     * The delete step and move step are each atomic but not atomic in aggregate! If this process crashes, the host
     * operating system crashes, or the hardware fails, it is possible that the delete step will succeed and the rename
     * will not.
     *
     * <h3>Entire-file or nothing</h3>
     * These are the possible results of this operation:
     * <ul>
     * <li>This operation returns normally, the source file is absent, and the target file contains the data previously
     * held by the source file. This is the success case.
     * <li>The operation throws a {@link jayo.JayoException} and the file system is unchanged. For example, this occurs
     * if this process lacks permissions to perform the move.
     * <li>This operation throws a {@link jayo.JayoException}, the target file is deleted, but the source file is
     * unchanged. This is the partial failure case described above and is only possible on file systems like FAT and
     * NTFS that do not support atomic file replacement. Typically, in such cases this operation won't return at all
     * because the process or operating system has also crashed.
     * </ul>
     * There is no failure mode where the target file holds a subset of the bytes of the source file. If the rename step
     * cannot be performed atomically, this function will throw a {@link jayo.JayoException} before attempting a move.
     * Typically, this occurs if the source and target files are on different physical volumes.
     *
     * <h3>Non-Atomic Moves</h3>
     * If you need to move files across volumes, use {@link #copy(Path)} followed by {@link #delete()}, and change
     * your application logic to recover should the copy step suffer a partial failure.
     *
     * @throws JayoFileNotFoundException if the file does not exist anymore.
     * @throws jayo.JayoException        if the move failed.
     */
    void atomicMove(final @NonNull Path destination);

    /**
     * Copies all the bytes from this file to {@code destination}, overriding {@code destination} if it already exists.
     * This does not copy file metadata like "last modified", time, permissions, or extended attributes.
     * <p>
     * This function is not atomic; a failure may leave {@code destination} in an inconsistent state. For example,
     * {@code destination} may be empty or contain only a prefix of this file.
     *
     * @throws JayoFileNotFoundException if the file does not exist anymore.
     * @throws jayo.JayoException        if the copy failed.
     */
    void copy(final @NonNull Path destination);

    /**
     * Deletes this file
     *
     * @throws JayoFileNotFoundException if the file does not exist anymore.
     * @throws jayo.JayoException        if the deletion failed.
     */
    void delete();
}
