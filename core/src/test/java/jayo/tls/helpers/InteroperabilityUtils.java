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

import jayo.Endpoint;
import jayo.Jayo;
import jayo.tls.TlsEndpoint;

import java.io.IOException;

public class InteroperabilityUtils {

    public interface Reader {
        int read(byte[] array, int offset, int length) throws IOException;

        void close() throws IOException;
    }

    public interface Writer {
        void renegotiate() throws IOException;

        void write(byte[] array, int offset, int length) throws IOException;

        void close() throws IOException;
    }

    public static class EndpointReader implements Reader {
        private final Endpoint endpoint;
        private final jayo.Reader jayoReader;

        public EndpointReader(Endpoint endpoint) {
            this.endpoint = endpoint;
            this.jayoReader = Jayo.buffer(endpoint.getReader());
        }

        @Override
        public int read(byte[] array, int offset, int length) {
            return jayoReader.readAtMostTo(array, offset, length);
        }

        @Override
        public void close() {
            endpoint.close();
        }
    }

    public static class TlsEndpointWriter implements Writer {
        private final TlsEndpoint tlsEndpoint;
        private final jayo.Writer jayoWriter;

        public TlsEndpointWriter(TlsEndpoint tlsEndpoint) {
            this.tlsEndpoint = tlsEndpoint;
            this.jayoWriter = Jayo.buffer(tlsEndpoint.getWriter());
        }

        @Override
        public void write(byte[] array, int offset, int length) {
            jayoWriter.write(array, offset, length)
                    .flush();
        }

        @Override
        public void renegotiate() {
            tlsEndpoint.renegotiate();
        }

        @Override
        public void close() {
            tlsEndpoint.close();
        }
    }
}
