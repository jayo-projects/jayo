/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.internal;

import jayo.*;
import jayo.external.NonNegative;
import jayo.tls.Handshake;
import jayo.tls.JayoTlsException;
import jayo.tls.JayoTlsHandshakeCallbackException;
import jayo.tls.TlsEndpoint;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.io.Serial;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static jayo.external.JayoUtils.checkOffsetAndCount;
import static jayo.internal.WriterSegmentQueue.newWriterSegmentQueue;

public final class RealTlsEndpoint {
    private static final System.Logger LOGGER = System.getLogger("jayo.tls.TlsEndpoint");

    static final int MAX_DATA_SIZE = 16 * 1024; // 2^14 bytes
    // @formatter:off
    static final int MAX_ENCRYPTED_PACKET_BYTE_SIZE =
            21            // header + iv
          + MAX_DATA_SIZE // data
          + 256           // max padding
          + 48;           // MAC or AEAD tag
    // @formatter:on
    private static final int SSL_V3_HEADER_SIZE = 5;    // SSLv3 record header

    /**
     * Handshake wrap() method calls need a buffer to read from, even when they actually do not read anything.
     * <p>
     * Note: standard SSLEngine is happy with no buffers, the empty buffer is here to make this work with Netty's
     * OpenSSL wrapper.
     */
    private final static @NonNull ByteBuffer @NonNull [] DUMMY_OUT = new ByteBuffer[]{ByteBuffer.allocate(0)};

    private final @NonNull RealReader encryptedReader;
    private final @NonNull WriterSegmentQueue encryptedWriterSegmentQueue;
    private final @NonNull SSLEngine engine;
    private final @NonNull Consumer<@NonNull SSLSession> sessionInitCallback;
    private final boolean waitForCloseConfirmation;

    private final Lock readLock = new ReentrantLock();
    private final Lock writeLock = new ReentrantLock();

    private Boolean isTls = null;
    /**
     * Whether an IOException was received from the underlying endpoint or from the {@link SSLEngine}.
     */
    volatile boolean invalid = false;

    /**
     * Whether a close_notify was already sent.
     */
    volatile boolean shutdownSent = false;

    /**
     * Whether a close_notify was already received.
     */
    volatile boolean shutdownReceived = false;

    /**
     * Decrypted data from encryptedReader
     */
    private final @NonNull RealBuffer decryptedBuffer = new RealBuffer();

    /**
     * Reference to the current read buffer supplied by the client. This field is only valid during a read operation.
     * This field is used instead of {@link #decryptedBuffer} in order to avoid any copy of the returned bytes when
     * possible.
     */
    private @Nullable RealBuffer suppliedDecryptedBuffer;

    /**
     * Bytes produced by the current read operation.
     */
    private int bytesToReturn;

    private int remainingBytesToRead;

    RealTlsEndpoint(
            final @NonNull Endpoint encryptedEndpoint,
            final @NonNull SSLEngine engine,
            final @Nullable RealReader encryptedReader,
            final @NonNull Consumer<@NonNull SSLSession> sessionInitCallback,
            boolean waitForCloseConfirmation) {
        assert encryptedEndpoint != null;
        assert engine != null;
        assert sessionInitCallback != null;

        this.encryptedReader = (encryptedReader != null)
                ? encryptedReader
                : new RealReader(encryptedEndpoint.getReader(), false);
        this.encryptedWriterSegmentQueue = newWriterSegmentQueue(encryptedEndpoint.getWriter(), false);
        this.engine = engine;
        this.sessionInitCallback = sessionInitCallback;
        this.waitForCloseConfirmation = waitForCloseConfirmation;
        handshake(); // THE initial handshake, this is an important step !
    }

    @NonNull
    Handshake getHandshake() {
        // on-demand Handshake creation, only if user calls it
        return Handshake.get(engine.getSession());
    }

    // read

    long readAtMostTo(final @NonNull Buffer writer, final @NonNegative long byteCount) {
        Objects.requireNonNull(writer);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0 : " + byteCount);
        }
        if (!(writer instanceof RealBuffer _writer)) {
            throw new IllegalArgumentException("writer must be an instance of RealBuffer");
        }
        if (byteCount == 0L) {
            return 0L;
        }

        readLock.lock();
        try {
            if (invalid || shutdownSent) {
                throw new JayoClosedResourceException();
            }

            // the decrypted buffer may already have available data
            suppliedDecryptedBuffer = _writer;
            bytesToReturn = (int) decryptedBuffer.bytesAvailable();

            while (true) {
                if (bytesToReturn > 0) {
                    if (decryptedBuffer.exhausted()) {
                        return bytesToReturn;
                    } else {
                        final var toTransfer = Math.min(bytesToReturn, byteCount);
                        writer.write(decryptedBuffer, toTransfer);
                        return toTransfer;
                    }
                }

                if (shutdownReceived) {
                    return -1L;
                }

                switch (engine.getHandshakeStatus()) {
                    case NEED_UNWRAP, NEED_WRAP -> writeAndHandshake();
                    case NOT_HANDSHAKING, FINISHED -> {
                        readAndUnwrap();
                        if (shutdownReceived) {
                            return -1L;
                        }
                    }
                    case NEED_TASK -> handleTask();
                    // Unsupported stage eg: NEED_UNWRAP_AGAIN
                    default -> {
                        return -1;
                    }
                }
            }
        } catch (TlsEOFException e) {
            return -1;
        } finally {
            suppliedDecryptedBuffer = null;
            readLock.unlock();
        }
    }

    private void readAndUnwrap() throws TlsEOFException {
        while (true) {
            if (remainingBytesToRead == 0) {
                remainingBytesToRead = readFromReader(); // maybe IO block
            }

            final var result = unwrap();

            /*
             * Note that data can be returned even in case of overflow, in that case, just return the data.
             */
            if (result.bytesProduced() > 0) {
                bytesToReturn += result.bytesProduced();
                if (remainingBytesToRead == 0) {
                    return;
                }
            }
            if (result.getStatus() == Status.CLOSED) {
                shutdownReceived = true;
                return;
            }
            if (result.getStatus() == Status.BUFFER_UNDERFLOW) {
                throw new IllegalStateException();
            }
            if (result.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                return;
            }
            final var status = engine.getHandshakeStatus();
            if (status == HandshakeStatus.NEED_TASK || status == HandshakeStatus.NEED_WRAP) {
                return;
            }
        }
    }

    private @NonNull SSLEngineResult unwrap() {
        final var destination = (suppliedDecryptedBuffer != null) ? suppliedDecryptedBuffer : decryptedBuffer;

        final var toRead = Math.min(remainingBytesToRead, Segment.SIZE);
        final var result = new Wrapper.SSLEngineResult();
        encryptedReader.segmentQueue.withCompactedHeadAsByteBuffer(toRead, source -> {
            result.value = callEngineUnwrap(source, destination);
            final var bytesConsumed = result.value.bytesConsumed();
            remainingBytesToRead -= bytesConsumed;
            return bytesConsumed;
        });

        return result.value;
    }

    private @NonNull SSLEngineResult callEngineUnwrap(final @NonNull ByteBuffer source,
                                                      final @NonNull RealBuffer destination) {
        var result = callEngineUnwrap(source, 1, destination);
        if (result == null) {
            result = callEngineUnwrap(source, MAX_DATA_SIZE, destination);
        }
        return Objects.requireNonNull(result,
                "BUFFER_OVERFLOW should not happen with destination size = " + MAX_DATA_SIZE);
    }

    private @Nullable SSLEngineResult callEngineUnwrap(final @NonNull ByteBuffer source,
                                                       final int minimumCapacity,
                                                       final @NonNull RealBuffer destination) {
        final var decryptedReaderQueue = destination.segmentQueue;
        return decryptedReaderQueue.withWritableTail(minimumCapacity, tail -> {
            final var dst = tail.asWriteByteBuffer(Segment.SIZE - tail.limit());
            try {
                final var result = engine.unwrap(source, dst);
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE,
                            "engine.unwrap() result [{0}]. engine status: {1}; source {2};{3}" +
                                    "decryptedReaderQueue {4}{5}",
                            Utils.resultToString(result), engine.getHandshakeStatus(), source, System.lineSeparator(),
                            decryptedReaderQueue, System.lineSeparator());
                }
                // Note that data can be returned (produced) even in case of overflow, in that case,
                // just return the data.
                final var written = result.bytesProduced();
                if (written > 0) {
                    tail.incrementLimitVolatile(written);
                } else if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                    assert result.bytesConsumed() == 0;
                    // must retry with bigger destination tail ByteBuffer
                    return null;
                }
                return result;
            } catch (SSLException e) {
                invalid = true;
                throw new JayoTlsException(e);
            }
        });
    }

    /**
     * @return either the packetSize if inside a TLS session or the full size of the encryptedReader
     */
    private @NonNegative int readFromReader() throws TlsEOFException {
        try {
            return callReadFromReader();
        } catch (JayoException e) {
            invalid = true;
            throw e;
        }
    }

    /**
     * @implNote We use {@link Buffer#getByte(long)} read in encryptedReader's buffer to not consume any data.
     */
    private @NonNegative int callReadFromReader() throws TlsEOFException {
        try {
            final var encryptedReaderBuffer = encryptedReader.segmentQueue.buffer;
            if (isTls == null) {
                if (!encryptedReader.request(1L)) {
                    throw new TlsEOFException();
                }
                // 22: TLS handshake record
                isTls = encryptedReaderBuffer.getByte(0L) == 22;
                if (!isTls) {
                    return (int) encryptedReaderBuffer.bytesAvailable();
                }
            }

            if (isTls) {
                // make sure TLS / SSL V3 header is present
                if (!encryptedReader.request(SSL_V3_HEADER_SIZE)) {
                    throw new TlsEOFException();
                }

                var contentByteSize = ((encryptedReaderBuffer.getByte(3L) & 0xFF) << 8);
                contentByteSize |= (encryptedReaderBuffer.getByte(4L) & 0xFF);
                final var packetByteSize = SSL_V3_HEADER_SIZE + contentByteSize;
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, "Reading from encryptedReader, packetByteSize {0}", packetByteSize);
                }
                if (!encryptedReader.request(packetByteSize)) {
                    throw new TlsEOFException();
                }

                return packetByteSize;
            }

            // Non TLS
            if (!encryptedReader.request((int) (encryptedReaderBuffer.bytesAvailable() + 1))) {
                throw new TlsEOFException();
            }

            return (int) encryptedReaderBuffer.bytesAvailable();
        } catch (JayoEOFException e) {
            throw new TlsEOFException();
        }
    }

    // write

    void write(final @NonNull Buffer reader, final @NonNegative long byteCount) {
        Objects.requireNonNull(reader);
        checkOffsetAndCount(reader.bytesAvailable(), 0L, byteCount);

        if (!(reader instanceof RealBuffer _reader)) {
            throw new IllegalArgumentException("reader must be an instance of RealBuffer");
        }

        /*
         * Note that we should enter the write loop even in the case that the reader buffer has no remaining bytes,
         * as it could be the case, that the user is forced to call write again, just to write pending encrypted bytes.
         */
        writeLock.lock();
        try {
            if (invalid || shutdownSent) {
                throw new JayoClosedResourceException();
            }

            wrapAndWrite(_reader, byteCount);
        } finally {
            writeLock.unlock();
        }
    }

    private void wrapAndWrite(final @NonNull RealBuffer source, final @NonNegative long byteCount) {
        var remaining = byteCount;
        final var closed = new Wrapper.Boolean();
        while (true) {
            if (closed.value) {
                throw new IllegalStateException("TLS endpoint is closed");
            }

            writeToWriter(); // IO block

            if (remaining == 0) {
                return;
            }

            final var toRead = (int) Math.min(MAX_DATA_SIZE, remaining);
            final var read = source.segmentQueue.withHeadsAsByteBuffers(toRead, sources -> {
                final var result = wrap(sources);
                if (result.getStatus() == Status.CLOSED) {
                    return -2;
                }
                return result.bytesConsumed();
            });
            if (read == -2) {
                closed.value = true;
            } else {
                remaining -= read;
            }
        }
    }

    private @NonNull SSLEngineResult wrap(final @NonNull ByteBuffer @NonNull [] sources) {
        // Force tail to be large enough to handle any valid record in the current SSL session, to avoid BUFFER_OVERFLOW
        return encryptedWriterSegmentQueue.withWritableTail(MAX_ENCRYPTED_PACKET_BYTE_SIZE, tail -> {
            final var destination = tail.asWriteByteBuffer(Segment.SIZE - tail.limit());
            try {
                final var result = engine.wrap(sources, destination);
                if (LOGGER.isLoggable(TRACE)) {
                    LOGGER.log(TRACE, "engine.wrap() result: [{0}]; engine status: {1}; sources: {2}, " +
                                    "encryptedWriterQueue: {3}", Utils.resultToString(result),
                            result.getHandshakeStatus(), Arrays.toString(sources), encryptedWriterSegmentQueue);
                }
                return switch (result.getStatus()) {
                    case OK, CLOSED -> {
                        final var written = result.bytesProduced();
                        if (written > 0) {
                            tail.incrementLimitVolatile(written);
                        }
                        yield result;
                    }
                    case BUFFER_OVERFLOW, BUFFER_UNDERFLOW -> throw new IllegalStateException();
                };
            } catch (SSLException e) {
                invalid = true;
                throw new JayoTlsException(e);
            }
        });
    }

    private void writeToWriter() {
        try {
            LOGGER.log(TRACE, "Writing to encryptedWriter");
            encryptedWriterSegmentQueue.emit(true); // maybe IO block
        } catch (JayoException e) {
            invalid = true;
            throw e;
        }
    }

    // handshake

    /**
     * Do a negotiation if this TLS connection is new, and it hasn't been done already.
     */
    private void handshake() {
        try {
            doHandshake();
        } catch (TlsEOFException e) {
            throw new JayoClosedResourceException();
        }
    }

    private void doHandshake() throws TlsEOFException {
        LOGGER.log(TRACE, "Calling SSLEngine.beginHandshake()");
        try {
            engine.beginHandshake();
        } catch (SSLException e) {
            throw new JayoTlsException(e);
        }

        writeAndHandshake();

        if (engine.getSession().getProtocol().startsWith("DTLS")) {
            throw new IllegalArgumentException("DTLS not supported");
        }

        // call client code
        try {
            sessionInitCallback.accept(engine.getSession());
        } catch (Exception e) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(DEBUG, "Client code threw exception in session initialization callback.", e);
            } else if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "Client code threw exception in session initialization callback: {0}.",
                        e.getMessage());
            }
            throw new JayoTlsHandshakeCallbackException("Session initialization callback failed", e);
        }
    }

    private void writeAndHandshake() throws TlsEOFException {
        readLock.lock();
        try {
            writeLock.lock();
            try {
                writeToWriter(); // IO block
                handshakeLoop();
            } finally {
                writeLock.unlock();
            }
        } finally {
            readLock.unlock();
        }
    }

    private void handshakeLoop() throws TlsEOFException {
        while (true) {
            switch (engine.getHandshakeStatus()) {
                case NEED_WRAP -> {
                    wrap(DUMMY_OUT);
                    writeToWriter(); // IO block
                }
                case NEED_UNWRAP -> {
                    readAndUnwrap();
                    if (bytesToReturn > 0) {
                        return;
                    }
                }
                case NOT_HANDSHAKING -> {
                    return;
                }
                case NEED_TASK -> handleTask();
                // FINISHED status is never returned by SSLEngine.getHandshakeStatus()
                case FINISHED -> throw new IllegalStateException();
                // Unsupported stage eg: NEED_UNWRAP_AGAIN
                default -> throw new IllegalStateException("unsupported stage: " + engine.getHandshakeStatus());
            }
        }
    }

    // close

    public void close() {
        tryShutdown();

        encryptedWriterSegmentQueue.close();
        encryptedReader.close();

        /*
         * After closing the underlying endpoint, locks should be taken fast.
         */
        readLock.lock();
        try {
            writeLock.lock();
            try {
                freeBuffer();
            } finally {
                writeLock.unlock();
            }
        } finally {
            readLock.unlock();
        }
    }

    private void tryShutdown() {
        if (!readLock.tryLock()) {
            return;
        }
        try {
            if (!writeLock.tryLock()) {
                return;
            }
            try {
                var closeConfirmed = false;
                if (!shutdownSent) {
                    closeConfirmed = shutdownSafe();
                }
                if (!closeConfirmed && waitForCloseConfirmation) {
                    shutdownSafe();
                }
            } finally {
                writeLock.unlock();
            }
        } finally {
            readLock.unlock();
        }
    }

    private boolean shutdownSafe() {
        try {
            return shutdown();
        } catch (Throwable e) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "Error doing TLS shutdown on close(), continuing.", e);
            } else if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "Error doing TLS shutdown on close(), continuing: {0}.", e.getMessage());
            }
            return true;
        }
    }

    public boolean shutdown() {
        readLock.lock();
        try {
            writeLock.lock();
            try {
                if (invalid) {
                    throw new JayoClosedResourceException();
                }

                if (!shutdownSent) {
                    shutdownSent = true;
                    writeToWriter(); // IO block
                    engine.closeOutbound();
                    wrap(DUMMY_OUT);
                    writeToWriter(); // IO block
                    /*
                     * If this side is the first to send close_notify, then, inbound is not done and false should be
                     * returned (so the client waits for the response). If this side is the second, then inbound was
                     * already done, and we can return true.
                     */
                    if (shutdownReceived) {
                        freeBuffer();
                    }
                    return shutdownReceived;
                }

                /*
                 * If we reach this point, then we just have to read the close notification from the client. Only try
                 * to do it if necessary, to make this method idempotent.
                 */
                if (!shutdownReceived) {
                    try {
                        // IO block
                        readAndUnwrap();
                        assert shutdownReceived;
                    } catch (TlsEOFException e) {
                        throw new JayoClosedResourceException();
                    }
                }
                freeBuffer();
                return true;
            } finally {
                writeLock.unlock();
            }
        } finally {
            readLock.unlock();
        }
    }

    private void freeBuffer() {
        decryptedBuffer.clear();
    }

    // other

    private void handleTask() {
        final var task = engine.getDelegatedTask();
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE,
                    "Async task needed, running it in the current thread immediately. Task: {0}", task);
        }
        task.run();
        // todo : should we provide a way to execute async task in a Virtual Thread Executor or by throwing an Exception
        //  like in the tls-channel library ? It would be useful for async NIO, but is there any use case for sync IO ?
    }

    /**
     * Used to signal EOF conditions from the reader.
     *
     * @implNote Not be a {@linkplain JayoException JayoException} to avoid being caught when catching
     * JayoException
     */
    private static final class TlsEOFException extends Exception {
        @Serial
        private static final long serialVersionUID = -3859156713994602991L;

        /**
         * For efficiency, override this method to do nothing.
         */
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    /**
     * The base class for builders of {@link TlsEndpoint}.
     */
    public static abstract sealed class Builder<T extends TlsEndpoint.Builder<T>> implements TlsEndpoint.Builder<T>
            permits ClientTlsEndpoint.Builder, ServerTlsEndpoint.Builder {
        final @NonNull Endpoint encryptedEndpoint;

        // @formatter:off
        @NonNull Consumer<@NonNull SSLSession> sessionInitCallback = session -> {};
        // @formatter:on
        boolean waitForCloseConfirmation = false;

        Builder(final @NonNull Endpoint encryptedEndpoint) {
            assert encryptedEndpoint != null;
            this.encryptedEndpoint = encryptedEndpoint;
        }

        abstract @NonNull T getThis();

        @Override
        public final @NonNull T sessionInitCallback(final @NonNull Consumer<@NonNull SSLSession> sessionInitCallback) {
            this.sessionInitCallback = Objects.requireNonNull(sessionInitCallback);
            return getThis();
        }

        @Override
        public final @NonNull T waitForCloseConfirmation(final boolean waitForCloseConfirmation) {
            this.waitForCloseConfirmation = waitForCloseConfirmation;
            return getThis();
        }
    }
}
