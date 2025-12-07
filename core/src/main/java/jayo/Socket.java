/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo;

import org.jspecify.annotations.NonNull;

/**
 * A buffered {@link Socket}.
 *
 * @see Jayo#closeQuietly(RawSocket)
 */
public interface Socket extends RawSocket {
    @Override
    @NonNull
    Reader getReader();

    @Override
    @NonNull
    Writer getWriter();
}
