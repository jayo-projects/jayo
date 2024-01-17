/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.crypto;

import org.jspecify.annotations.NonNull;

/**
 * A message digest algorithm. Message digests are secure one-way hash functions that take arbitrary-sized data and
 * output a fixed-length hash value.
 *
 * @param algorithm the name of the requested algorithm.
 */
public record Digest(@NonNull String algorithm) {
}
