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

import jayo.files.File;
import jayo.files.FileMetadata;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

public final class RealFileMetadata implements FileMetadata {
    private final @NonNull BasicFileAttributes attributes;
    private final @Nullable Path symlinkTarget;

    public RealFileMetadata(final @NonNull BasicFileAttributes attributes, final @Nullable Path symlinkTarget) {
        assert attributes != null;

        this.attributes = attributes;
        this.symlinkTarget = symlinkTarget;
    }

    @Override
    public boolean isRegularFile() {
        return attributes.isRegularFile();
    }

    @Override
    public @Nullable File getSymlinkTarget() {
        return (symlinkTarget != null) ? File.open(symlinkTarget) : null;
    }

    @Override
    public @Nullable Instant getCreatedAt() {
        return instantFromFileTime(attributes.creationTime());
    }

    @Override
    public @Nullable Instant getLastModifiedAt() {
        return instantFromFileTime(attributes.lastModifiedTime());
    }

    @Override
    public @Nullable Instant getLastAccessedAt() {
        return instantFromFileTime(attributes.lastAccessTime());
    }

    private static @Nullable Instant instantFromFileTime(final @NonNull FileTime fileTime) {
        assert fileTime != null;

        final var instant = fileTime.toInstant();
        return Instant.EPOCH.equals(instant) ? null : instant;
    }
}
