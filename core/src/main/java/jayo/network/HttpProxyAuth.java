/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.network;

import jayo.internal.network.RealHttpProxy;
import org.jspecify.annotations.NonNull;

import java.nio.charset.Charset;

/**
 * A data holder for a username, a password and the charset to use to encode these credentials.
 */
public sealed interface HttpProxyAuth permits RealHttpProxy.Auth {
    /**
     * @return the username.
     */
    @NonNull
    String getUsername();

    /**
     * @return the user password.
     * <p>
     * Note: this method returns a reference to the password. It is the caller's responsibility to zero out the password
     * information after it is no longer needed.
     */
    char @NonNull [] getPassword();

    /**
     * @return the charset to use to encode the credentials that consist of the {@link #getUsername() username} and the
     * {@link #getPassword() password}.
     */
    @NonNull
    Charset getCharset();
}
