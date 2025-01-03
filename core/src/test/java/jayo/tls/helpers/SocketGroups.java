/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.tls.helpers;

import jayo.network.NetworkEndpoint;
import jayo.tls.TlsEndpoint;

public class SocketGroups {

    public static class OldOldSocketPair {
        public final TlsEndpoint client;
        public final TlsEndpoint server;

        public OldOldSocketPair(TlsEndpoint client, TlsEndpoint server) {
            this.client = client;
            this.server = server;
        }
    }

    public static class OldIoSocketPair {
        public final TlsEndpoint client;
        public final SocketGroup server;

        public OldIoSocketPair(TlsEndpoint client, SocketGroup server) {
            this.client = client;
            this.server = server;
        }
    }

    public static class IoOldSocketPair {
        public final SocketGroup client;
        public final TlsEndpoint server;

        public IoOldSocketPair(SocketGroup client, TlsEndpoint server) {
            this.client = client;
            this.server = server;
        }
    }

    public static class SocketPair {
        public final SocketGroup client;
        public final SocketGroup server;

        public SocketPair(SocketGroup client, SocketGroup server) {
            this.client = client;
            this.server = server;
        }
    }

    public static class SocketGroup {
        public final TlsEndpoint tls;
        public final NetworkEndpoint plain;

        public SocketGroup(TlsEndpoint tls, NetworkEndpoint plain) {
            this.tls = tls;
            this.plain = plain;
        }
    }
}
