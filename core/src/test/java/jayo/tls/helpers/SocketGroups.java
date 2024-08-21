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

import jayo.tls.TlsEndpoint;

import javax.net.ssl.SSLSocket;
import java.net.Socket;

public class SocketGroups {

    public static class OldOldSocketPair {
        public final SSLSocket client;
        public final SSLSocket server;

        public OldOldSocketPair(SSLSocket client, SSLSocket server) {
            this.client = client;
            this.server = server;
        }
    }

    public static class OldIoSocketPair {
        public final SSLSocket client;
        public final SocketGroup server;

        public OldIoSocketPair(SSLSocket client, SocketGroup server) {
            this.client = client;
            this.server = server;
        }
    }

    public static class IoOldSocketPair {
        public final SocketGroup client;
        public final SSLSocket server;

        public IoOldSocketPair(SocketGroup client, SSLSocket server) {
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
        public final Socket plain;

        public SocketGroup(TlsEndpoint tls, Socket plain) {
            this.tls = tls;
            this.plain = plain;
        }
    }
}
