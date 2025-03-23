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

package jayo.tools;

import org.jspecify.annotations.NonNull;

import java.util.regex.Pattern;

public final class HostnameUtils {
    // un-instantiable
    private HostnameUtils() {
    }

    /**
     * Quick and dirty pattern to differentiate IP addresses from hostnames. This is an approximation of Android's
     * private InetAddress#isNumeric API.
     * <p>
     * This matches IPv6 addresses as a hex string containing at least one colon, and possibly including dots after the
     * first colon. It matches IPv4 addresses as strings containing only decimal digits and dots. This pattern matches
     * strings like "a:.23" and "54" that are neither IP addresses nor hostnames; they will be verified as IP addresses
     * (which is a stricter verification).
     */
    private final static Pattern VERIFY_AS_IP_ADDRESS = Pattern.compile(
            "([0-9a-fA-F]*:[0-9a-fA-F:.]*)|([\\d.]+)");

    /**
     * @return true if this string is not a host name and might be an IP address.
     */
    public static boolean canParseAsIpAddress(final @NonNull String maybeIpAddress) {
        assert maybeIpAddress != null;

        return VERIFY_AS_IP_ADDRESS.matcher(maybeIpAddress).matches();
    }
}
