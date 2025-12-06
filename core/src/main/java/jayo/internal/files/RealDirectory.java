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

package jayo.internal.files;

import jayo.JayoException;
import jayo.files.Directory;
import jayo.files.File;
import jayo.files.JayoFileNotFoundException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final /*Valhalla 'value class'*/ class RealDirectory implements Directory {
    public static @NonNull Directory open(final @NonNull Path path) {
        assert path != null;

        if (!Files.exists(path)) {
            throw new JayoFileNotFoundException("Path does not exist: " + path);
        }
        return checkAndBuildDirectory(path);
    }

    public static @NonNull Directory create(final @NonNull Path path) {
        assert path != null;

        try {
            return checkAndBuildDirectory(Files.createDirectory(path));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    public static @NonNull Directory createIfNotExists(final @NonNull Path path) {
        assert path != null;

        if (Files.exists(path)) {
            return checkAndBuildDirectory(path);
        }
        try {
            return checkAndBuildDirectory(Files.createDirectory(path));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    private static @NonNull Directory checkAndBuildDirectory(final @NonNull Path path) {
        assert path != null;

        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("A Jayo's directory must be a directory.");
        }
        if (path.getFileName() == null) {
            throw new IllegalArgumentException("Jayo prevent zero element directories, meaning with no name.");
        }
        return new RealDirectory(path);
    }

    private final @NonNull Path path;

    public RealDirectory(final @NonNull Path path) {
        assert path != null;
        this.path = path;
    }

    @Override
    public @NonNull Path getPath() {
        return path;
    }

    @Override
    public void atomicMove(final @NonNull Path destination) {
        if (!Files.exists(path)) {
            throw new JayoFileNotFoundException("directory does not exist anymore");
        }
        try {
            Files.move(path, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public void copy(final @NonNull Path destination) {
        if (!Files.exists(path)) {
            throw new JayoFileNotFoundException("directory does not exist anymore");
        }
        try {
            Files.walkFileTree(path, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {
                        @Override
                        public @NonNull FileVisitResult visitFile(final @NonNull Path file,
                                                                  final @NonNull BasicFileAttributes attrs) {
                            File.open(file).copy(destination.resolve(path.relativize(file)));
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public @NonNull FileVisitResult preVisitDirectory(
                                final @NonNull Path dir,
                                final @NonNull BasicFileAttributes attrs
                        ) throws IOException {
                            final var targetDir = destination.resolve(path.relativize(dir));
                            try {
                                Files.copy(dir, targetDir);
                            } catch (FileAlreadyExistsException e) {
                                if (!Files.isDirectory(targetDir)) {
                                    throw e;
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public void delete() {
        if (!Files.exists(path)) {
            throw new JayoFileNotFoundException("directory does not exist anymore");
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public @NonNull FileVisitResult visitFile(final @NonNull Path file,
                                                          final @NonNull BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NonNull FileVisitResult postVisitDirectory(final @NonNull Path dir,
                                                                   final @Nullable IOException e) throws IOException {
                    if (e == null) {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    } else {
                        // directory iteration failed
                        throw e;
                    }
                }
            });
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public @NonNull List<@NonNull Path> listEntries() {
        if (!Files.exists(path)) {
            throw new JayoFileNotFoundException("directory does not exist anymore");
        }
        try (final var dirStream = Files.newDirectoryStream(path)) {
            final var result = new ArrayList<Path>();
            for (final var entry : dirStream) {
                result.add(entry);
            }
            return result;
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }
}
