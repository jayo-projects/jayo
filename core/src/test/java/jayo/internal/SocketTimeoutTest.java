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

import jayo.Cancellable;
import jayo.Jayo;
import jayo.JayoInterruptedIOException;
import jayo.JayoTimeoutException;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class SocketTimeoutTest {
    // The size of the socket buffers to use. Less than half the data transferred during tests to ensure send and
    // receive buffers are flooded and any necessary blocking behavior takes place.
    private static final int SOCKET_BUFFER_SIZE = 256 * 1024;
    private static final int ONE_MB = 1024 * 1024;

    @Test
    public void readWithoutTimeout() throws Exception {
        try (var socket = socket(ONE_MB, 0);
             var reader = Jayo.buffer(Jayo.reader(socket))) {
            Cancellable.run(Duration.ofMillis(500), _scope -> reader.require(ONE_MB));
        }
    }

    @Test
    public void readWithTimeout() throws Exception {
        try (var socket = socket(0, 0);
             var reader = Jayo.buffer(Jayo.reader(socket))) {
            Cancellable.run(Duration.ofMillis(25), _scope ->
                    assertThatThrownBy(() -> reader.require(ONE_MB))
                            // we may fail when expecting 1MB and socket is reading, or after the read, exception is not
                            // the same
                            .isInstanceOf(JayoTimeoutException.class));
        }
    }

    @Test
    public void readWitManualCancellation() throws Exception {
        try (var socket = socket(ONE_MB, 0);
             var reader = Jayo.buffer(Jayo.reader(socket))) {
            Cancellable.run(scope -> {
                scope.cancel();
                assertThatThrownBy(() -> reader.require(ONE_MB))
                        .isInstanceOf(JayoInterruptedIOException.class)
                        .isNotInstanceOf(JayoTimeoutException.class);
            });
        }
    }

    @Test
    public void writeWithoutTimeout() throws Exception {
        try (var socket = socket(0, ONE_MB);
             var writer = Jayo.buffer(Jayo.writer(socket))) {
            Cancellable.run(Duration.ofMillis(50), _scope -> {
                byte[] data = new byte[ONE_MB];
                writer.write(new RealBuffer().write(data), data.length);
                writer.flush();
            });
        }
    }

    @Test
    public void writeWithTimeout() throws Exception {
        try (var socket = socket(ONE_MB, 0)) {
            long start = System.nanoTime();
            assertThatThrownBy(() -> Cancellable.run(Duration.ofMillis(1), _scope -> {
                        try (var writer = Jayo.buffer(Jayo.writer(socket))) {
                            byte[] data = new byte[ONE_MB];
                            writer.write(new RealBuffer().write(data), data.length);
                            writer.flush();
                        }
                    })
            ).isInstanceOf(JayoTimeoutException.class);
            long elapsed = System.nanoTime() - start;
            assertThat(TimeUnit.NANOSECONDS.toMillis(elapsed) >= 1).isTrue();
            assertThat(TimeUnit.NANOSECONDS.toMillis(elapsed) <= 500).isTrue();
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
