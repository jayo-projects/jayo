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

package jayo.internal.tls;

import org.jspecify.annotations.NonNull;

/**
 * ASN.1 object identifiers used internally by this implementation.
 */
final class ObjectIdentifiers {
    // un-instantiable
    private ObjectIdentifiers() {
    }

    static final @NonNull String EC_PUBLIC_KEY = "1.2.840.10045.2.1";
    static final @NonNull String SHA256_WITH_ECDSA = "1.2.840.10045.4.3.2";
    static final @NonNull String RSA_ENCRYPTION = "1.2.840.113549.1.1.1";
    static final @NonNull String SHA256_WITH_RSA_ENCRYPTION = "1.2.840.113549.1.1.11";
    static final @NonNull String SUBJECT_ALTERNATIVE_NAME = "2.5.29.17";
    static final @NonNull String BASIC_CONSTRAINTS = "2.5.29.19";
    static final @NonNull String COMMON_NAME = "2.5.4.3";
    static final @NonNull String ORGANIZATIONAL_UNIT_NAME = "2.5.4.11";
}
