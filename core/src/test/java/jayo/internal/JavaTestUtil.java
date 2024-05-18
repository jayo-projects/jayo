/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal;

import java.util.ArrayList;
import java.util.List;

public final class JavaTestUtil {
    /** Remove all segments from the pool and return them as a list. */
    static List<Segment> takeAllPoolSegments() {
        final List<Segment> result = new ArrayList<>();
        while (SegmentPool.getByteCount() > 0) {
            result.add(SegmentPool.take());
        }
        return result;
    }
}
