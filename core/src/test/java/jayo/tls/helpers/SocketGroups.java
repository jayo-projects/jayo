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

import jayo.network.NetworkSocket;
import jayo.tls.TlsSocket;

public class SocketGroups {

    public static class OldOldSocketPair {
        public final TlsSocket client;
        public final TlsSocket server;

        public OldOldSocketPair(TlsSocket client, TlsSocket server) {
            this.client = client;
            this.server = server;
        }
    }

    public static class OldIoSocketPair {
        public final TlsSocket client;
        public final SocketGroup server;

        public OldIoSocketPair(TlsSocket client, SocketGroup server) {
            this.client = client;
            this.server = server;
        }
    }

    public static class IoOldSocketPair {
        public final SocketGroup client;
        public final TlsSocket server;

        public IoOldSocketPair(SocketGroup client, TlsSocket server) {
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
        public final TlsSocket tls;
        public final NetworkSocket plain;

        public SocketGroup(TlsSocket tls, NetworkSocket plain) {
            this.tls = tls;
            this.plain = plain;
        }
    }
}
