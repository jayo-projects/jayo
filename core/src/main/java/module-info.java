/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

module jayo {
    requires jdk.unsupported; // required for unsafe access
    
    requires static kotlin.stdlib;
    requires static org.jspecify;

    exports jayo;
    exports jayo.crypto;
    exports jayo.exceptions;
    exports jayo.external;
    exports jayo.endpoints;
}
