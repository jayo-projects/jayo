/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from Okio (https://github.com/square/okio), original copyright is below
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

package jayo.internal;

import org.junit.jupiter.api.Test;
import jayo.Cancellable;
import jayo.Jayo;
import jayo.Sink;
import jayo.Source;
import jayo.exceptions.JayoCancelledException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class SocketTimeoutTest {
    // The size of the socket buffers to use. Less than half the data transferred during tests to
    // ensure send and receive buffers are flooded and any necessary blocking behavior takes place.
    private static final int SOCKET_BUFFER_SIZE = 256 * 1024;
    private static final int ONE_MB = 1024 * 1024;

    @Test
    public void readWithoutTimeout() throws Exception {
        try (Socket socket = socket(ONE_MB, 0);
             Source source = Jayo.buffer(Jayo.source(socket))) {
            Cancellable.withTimeout(500, TimeUnit.MILLISECONDS, _scope -> {
                source.require(ONE_MB);
            });
        }
    }

    @Test
    public void readWithTimeout() throws Exception {
        try (Socket socket = socket(0, 0)) {
            Cancellable.withTimeout(25, TimeUnit.MILLISECONDS, _scope -> {
                try (Source source = Jayo.buffer(Jayo.source(socket))) {
                    assertThatThrownBy(() -> source.require(ONE_MB))
                            // we may fail when expecting 1MB and socket is reading, or after the read, exception is not
                            // the same
                            .isInstanceOf(JayoCancelledException.class);
                }
            });
        }
    }

    @Test
    public void writeWithoutTimeout() throws Exception {
        try (Socket socket = socket(0, ONE_MB)) {
            Cancellable.withTimeout(50, TimeUnit.MILLISECONDS, _scope -> {
                try (Sink sink = Jayo.buffer(Jayo.sink(socket))) {
                    byte[] data = new byte[ONE_MB];
                    sink.write(new RealBuffer().write(data), data.length);
                    sink.flush();
                }
            });
        }
    }

    @Test
    public void writeWithTimeout() throws Exception {
        try (Socket socket = socket(0, 0)) {
            Cancellable.withTimeout(50, TimeUnit.MILLISECONDS, _scope -> {
                try (Sink sink = Jayo.buffer(Jayo.sink(socket))) {
                    byte[] data = new byte[ONE_MB];
                    long start = System.nanoTime();
                    assertThatThrownBy(() -> {
                        sink.write(new RealBuffer().write(data), data.length);
                        sink.flush();
                    }).isInstanceOf(JayoCancelledException.class);
                    long elapsed = System.nanoTime() - start;
                    assertThat(TimeUnit.NANOSECONDS.toMillis(elapsed) >= 50).isTrue();
                    assertThat(TimeUnit.NANOSECONDS.toMillis(elapsed) <= 500).isTrue();
                }
            });
        }
    }

    /**
     * Returns a socket that can read {@code readableByteCount} incoming bytes and
     * will accept {@code writableByteCount} written bytes. The socket will idle
     * for 5 seconds when the required data has been read and written.
     */
    static Socket socket(final int readableByteCount, final int writableByteCount) throws IOException {
        InetAddress inetAddress = InetAddress.getByName("localhost");
        final ServerSocket serverSocket = new ServerSocket(0, 50, inetAddress);
        serverSocket.setReuseAddress(true);
        serverSocket.setReceiveBufferSize(SOCKET_BUFFER_SIZE);

        Thread peer = new Thread("peer") {
            @Override
            public void run() {
                try (Socket socket = serverSocket.accept()) {
                    socket.setSendBufferSize(SOCKET_BUFFER_SIZE);
                    writeFully(socket.getOutputStream(), readableByteCount);
                    readFully(socket.getInputStream(), writableByteCount);
                    Thread.sleep(500); // Sleep 500 ms so the peer can close the connection.
                } catch (Exception ignored) {
                }
            }
        };
        peer.start();

        Socket socket = new Socket(serverSocket.getInetAddress(), serverSocket.getLocalPort());
        socket.setReceiveBufferSize(SOCKET_BUFFER_SIZE);
        socket.setSendBufferSize(SOCKET_BUFFER_SIZE);
        return socket;
    }

    private static void writeFully(OutputStream out, final int byteCount) throws IOException {
        out.write(new byte[byteCount]);
        out.flush();
    }

    private static void readFully(InputStream in, final int byteCount) throws IOException {
        int count = 0;
        byte[] result = new byte[byteCount];
        while (count < byteCount) {
            int read = in.read(result, count, result.length - count);
            if (read == -1) throw new EOFException();
            count += read;
        }
    }
}
