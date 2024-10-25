/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from OkHttp (https://github.com/square/okhttp), original copyright is below
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

package jayo.internal.tls.platform;

import jayo.tls.JssePlatform;
import org.jspecify.annotations.NonNull;

import java.security.Security;

import static java.lang.System.Logger.Level.INFO;

public final class JssePlatformUtils {
    private static final System.Logger LOGGER = System.getLogger("jayo.tls.JssePlatform");

    // un-instantiable
    private JssePlatformUtils() {
    }

    private static volatile @NonNull JssePlatform platform = findPlatform();

    public static @NonNull JssePlatform get() {
        return platform;
    }

    public static void resetForTests() {
        resetForTests(findPlatform());
    }

    public static void resetForTests(final @NonNull JssePlatform newPlatform) {
        assert newPlatform != null;

        platform = newPlatform;
    }

    /**
     * Attempt to match the host runtime to a capable Platform implementation.
     */
    private static @NonNull JssePlatform findPlatform() {
        final var preferredProvider = Security.getProviders()[0].getName();

        // 1) try Conscrypt
        if ("Conscrypt".equals(preferredProvider)) {
            final var conscrypt = ConscryptJssePlatform.buildIfSupported();

            if (conscrypt != null) {
                LOGGER.log(INFO, "Use Conscrypt JSSE");
                return conscrypt;
            }
        }

        // 2) try BouncyCastle
        final var isBcFips = "BCFIPS".equals(preferredProvider);
        if ("BC".equals(preferredProvider) || isBcFips) {
            final var bc = BouncyCastleJssePlatform.buildIfSupported(isBcFips);

            if (bc != null) {
                if (LOGGER.isLoggable(INFO)) {
                    LOGGER.log(INFO, isBcFips ? "Use BouncyCastle FIPS JSSE" : "Use BouncyCastle JSSE");
                }
                return bc;
            }
        }

        // 3) fallback to the builtin JDK JSSE
        LOGGER.log(INFO, "Use builtin JDK JSSE");
        return new JdkJssePlatform();
    }
}
