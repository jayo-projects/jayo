/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
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

package jayo.internal;

import jayo.exceptions.JayoException;
import jayo.endpoints.JayoSocketTimeoutException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.Socket;

import static java.lang.System.Logger.Level.WARNING;

// todo put in SocketEndpoint
final class SocketAsyncTimeout extends RealAsyncTimeout {
    private static final System.Logger LOGGER = System.getLogger("jayo.SocketAsyncTimeout");

    SocketAsyncTimeout(final @NonNull Socket socket) {
        super(() -> {
            try {
                socket.close();
            } catch (Exception e) {
                LOGGER.log(WARNING, "Failed to close timed out socket " + socket, e);
            }
        });
    }

    @Override
    @NonNull JayoSocketTimeoutException newTimeoutException(final @Nullable JayoException cause) {
        final var e = new JayoSocketTimeoutException("timeout");
        if (cause != null) {
            e.initCause(cause);
        }
        return e;
    }
}
