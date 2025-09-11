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

import jayo.Socket;
import jayo.Jayo;
import jayo.tls.TlsSocket;

import java.io.IOException;

public class InteroperabilityUtils {

    public interface Reader {
        int read(byte[] array, int offset, int length) throws IOException;

        void close() throws IOException;
    }

    public interface Writer {
        void write(byte[] array, int offset, int length) throws IOException;

        void close() throws IOException;
    }

    public static class EndpointReader implements Reader {
        private final Socket socket;
        private final jayo.Reader jayoReader;

        public EndpointReader(Socket socket) {
            this.socket = socket;
            this.jayoReader = Jayo.buffer(socket.getReader());
        }

        @Override
        public int read(byte[] array, int offset, int length) {
            return jayoReader.readAtMostTo(array, offset, length);
        }

        @Override
        public void close() {
            socket.cancel();
        }
    }

    public static class TlsSocketWriter implements Writer {
        private final TlsSocket tlsSocket;
        private final jayo.Writer jayoWriter;

        public TlsSocketWriter(TlsSocket tlsSocket) {
            this.tlsSocket = tlsSocket;
            this.jayoWriter = Jayo.buffer(tlsSocket.getWriter());
        }

        @Override
        public void write(byte[] array, int offset, int length) {
            jayoWriter.write(array, offset, length)
                    .flush();
        }

        @Override
        public void close() {
            tlsSocket.cancel();
        }
    }
}
