/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.endpoints;

import jayo.internal.RealSocketEndpoint;
import org.jspecify.annotations.NonNull;

import java.net.Socket;

/**
 * An endpoint bound to an underlying {@link Socket}.
 * <p>
 * This socket must be {@linkplain Socket#isConnected() connected} and not {@linkplain Socket#isClosed() closed} on
 * {@link SocketEndpoint} creation.
 * <p>
 * Please read {@link Endpoint} javadoc for endpoint rationale.
 */
public sealed interface SocketEndpoint extends Endpoint permits RealSocketEndpoint {
    /**
     * @return the underlying {@link Socket}.
     */
    @NonNull
    Socket getUnderlying();
}
