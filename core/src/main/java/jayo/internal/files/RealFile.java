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

import jayo.Jayo;
import jayo.JayoException;
import jayo.RawReader;
import jayo.RawWriter;
import jayo.bytestring.ByteString;
import jayo.crypto.Digest;
import jayo.crypto.Hmac;
import jayo.files.File;
import jayo.files.FileMetadata;
import jayo.files.JayoFileNotFoundException;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Objects;

import static java.lang.System.Logger.Level.DEBUG;

public final /*Valhalla 'value class'*/ class RealFile implements File {
    private static final System.Logger LOGGER = System.getLogger("jayo.files.File");

    public static @NonNull File open(final @NonNull Path path) {
        assert path != null;

        if (!Files.exists(path)) {
            throw new JayoFileNotFoundException("Path does not exist: " + path);
        }
        return checkAndBuildFile(path);
    }

    public static @NonNull File create(final @NonNull Path path) {
        assert path != null;

        try {
            return checkAndBuildFile(Files.createFile(path));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    public static @NonNull File createIfNotExists(final @NonNull Path path) {
        assert path != null;

        if (Files.exists(path)) {
            return checkAndBuildFile(path);
        }
        try {
            return checkAndBuildFile(Files.createFile(path));
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    private static @NonNull File checkAndBuildFile(final @NonNull Path path) {
        assert path != null;

        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException("A Jayo's file cannot be a directory.");
        }
        if (path.getFileName() == null) {
            throw new IllegalArgumentException("Jayo prevent zero element files, meaning with no name.");
        }
        return new RealFile(path);
    }

    private final @NonNull Path path;

    private RealFile(final @NonNull Path path) {
        assert path != null;
        this.path = path;
    }

    @Override
    public @NonNull RawWriter writer(final @NonNull OpenOption @NonNull ... options) {
        if (!Files.exists(path)) { // todo should we also call Files.isWritable(path) ?
            throw new JayoFileNotFoundException("file does not exist anymore");
        }
        final var optionsSet = new HashSet<OpenOption>();
        for (final var option : options) {
            if (option == StandardOpenOption.CREATE || option == StandardOpenOption.CREATE_NEW) {
                LOGGER.log(DEBUG, "Ignoring CREATE and CREATE_NEW options. " +
                        "A Jayo file is always already existing.");
                continue;
            }
            optionsSet.add(option);
        }
        return Jayo.writer(path, optionsSet.toArray(new OpenOption[0]));
    }

    @Override
    public @NonNull RawReader reader() {
        if (!Files.exists(path)) { // todo should we also call Files.isReadable(path) ?
            throw new JayoFileNotFoundException("file does not exist anymore");
        }
        return Jayo.reader(path);
    }

    @Override
    public @NonNull String getName() {
        final var fileNamePath = path.getFileName();
        if (fileNamePath == null) {
            throw new IllegalStateException("Jayo prevent zero element files, meaning with no file name.");
        }
        return fileNamePath.toString();
    }

    @Override
    public long byteSize() {
        if (!Files.exists(path)) {
            throw new JayoFileNotFoundException("file does not exist anymore");
        }
        try {
            return (Files.isRegularFile(path)) ? Files.size(path) : -1L;
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public @NonNull Path getPath() {
        return path;
    }

    @Override
    public @NonNull FileMetadata metadata() {
        if (!Files.exists(path)) {
            throw new JayoFileNotFoundException("file does not exist anymore");
        }
        try {
            final var attributes = Files.readAttributes(
                    path,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            );
            final var symlinkTarget = (attributes.isSymbolicLink()) ? Files.readSymbolicLink(path) : null;
            return new RealFileMetadata(attributes, symlinkTarget);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }

    @Override
    public @NonNull ByteString hash(@NonNull Digest digest) {
        Objects.requireNonNull(digest);
        return Jayo.hash(reader(), digest);
    }

    @Override
    public @NonNull ByteString hmac(@NonNull Hmac hMac, @NonNull ByteString key) {
        Objects.requireNonNull(hMac);
        Objects.requireNonNull(key);
        return Jayo.hmac(reader(), hMac, key);
    }

    // shared with Directory

    @Override
    public void atomicMove(final @NonNull Path destination) {
        if (!Files.exists(path)) {
            throw new JayoFileNotFoundException("file does not exist anymore");
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
            throw new JayoFileNotFoundException("file does not exist anymore");
        }
        if (Files.isDirectory(destination)) {
            throw new JayoException("destination is a directory");
        }
        final var destFile = createIfNotExists(destination);
        try (final var bytesIn = Jayo.reader(path); final var bytesOut = Jayo.buffer(destFile.writer())) {
            bytesOut.writeAllFrom(bytesIn);
        }
    }

    @Override
    public void delete() {
        if (!Files.exists(path)) {
            throw new JayoFileNotFoundException("file does not exist anymore");
        }
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw JayoException.buildJayoException(e);
        }
    }
}
