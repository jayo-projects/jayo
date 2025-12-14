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

import jayo.internal.files.RealDirectory;
import org.jspecify.annotations.NonNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A Jayo's Directory is guaranteed to be a real existing directory.
 */
public sealed interface Directory permits RealDirectory {
    /**
     * Opens this existing directory, then returns it.
     *
     * @return the opened directory
     * @throws JayoFileNotFoundException if the requested directory does not exist.
     * @throws jayo.JayoException        if an I/O error occurs or the parent directory does not exist.
     */
    static @NonNull Directory open(final @NonNull Path path) {
        Objects.requireNonNull(path);
        return RealDirectory.open(path);
    }

    /**
     * Creates this non-existing directory, then returns it.
     *
     * @return the created directory
     * @throws JayoFileAlreadyExistsException if the requested directory already exists.
     * @throws jayo.JayoException             if an I/O error occurs or the parent directory does not exist.
     */
    static @NonNull Directory create(final @NonNull Path path) {
        Objects.requireNonNull(path);
        return RealDirectory.create(path);
    }

    /**
     * Creates this directory if it did not exist yet, else open it, then returns it.
     *
     * @return the created or opened directory
     * @throws jayo.JayoException if an I/O error occurs or the parent directory does not exist.
     */
    static @NonNull Directory createIfNotExists(final @NonNull Path path) {
        Objects.requireNonNull(path);
        return RealDirectory.createIfNotExists(path);
    }

    static boolean exists(final @NonNull Path path) {
        Objects.requireNonNull(path);
        return Files.exists(path) && Files.isDirectory(path);
    }

    /**
     * @return the {@code path} of this directory.
     */
    @NonNull
    Path getPath();

    /**
     * In general, one may expect that for a path like {@code Path.of("home", "Downloads")} the name is
     * {@code Downloads}.
     *
     * @return the name of this directory.
     */
    @NonNull
    String getName();

    /**
     * Atomically moves this directory to {@code destination} if the underlying file system supports it. All
     * subdirectories and files contained in this directory are also moved to {@code destination}.
     * <p>
     * If {@code destination} directory was preexistent, all the subdirectories and files it contained are kept and
     * merged with the ones contained in this directory. If some file names clash, the files in this directory
     * take precedence and override the previous ones.
     * <p>
     * See {@link File#atomicMove(Path)} for more details about how each file contained in this directory is moved.
     *
     * <h3>Non-Atomic Moves</h3>
     * If you need to move directories across volumes, use {@link #copy(Path)} followed by {@link #delete()}, and change
     * your application logic to recover should the copy step suffer a partial failure.
     *
     * @throws JayoFileNotFoundException if the directory does not exist anymore.
     * @throws jayo.JayoException        if the move failed.
     */
    void atomicMove(final @NonNull Path destination);

    void copy(final @NonNull Path destination);

    /**
     * Deletes this directory and all its content.
     *
     * @throws JayoFileNotFoundException if the directory does not exist anymore.
     * @throws jayo.JayoException        if the deletion failed.
     */
    void delete();

    /**
     * Lists the entries in this directory. This method does not recurse into subdirectories.
     *
     * @return a list of the entries in this directory.
     * @throws JayoFileNotFoundException if the directory does not exist anymore.
     * @throws jayo.JayoException        if an I/O error occurred.
     */
    @NonNull List<@NonNull Path> listEntries();
}
