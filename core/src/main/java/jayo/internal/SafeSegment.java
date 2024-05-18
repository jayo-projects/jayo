/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import jayo.external.NonNegative;
import org.jspecify.annotations.NonNull;

abstract sealed class SafeSegment permits Segment {
    /**
     * The binary data.
     */
    abstract byte @NonNull [] data();

    /**
     * The next byte of application data byte to read in this segment.
     */
    abstract @NonNegative int pos();

    /**
     * The first byte of available data ready to be written to.
     */
    abstract @NonNegative int limit();

    /**
     * True if other buffer segments or byte strings use the same byte array.
     */
    abstract boolean shared();

    /**
     * Returns a new segment that shares the underlying byte array with this one. Adjusting pos and limit are safe but
     * writes are forbidden. This also marks the current segment as shared, which prevents it from being pooled.
     */
    abstract @NonNull Segment sharedCopy();
}
