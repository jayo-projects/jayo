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
import jayo.internal.tls.RealClientTlsEndpoint;
import jayo.internal.tls.RealServerTlsEndpoint;
import jayo.tls.*;
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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;
import static jayo.internal.tls.platform.JdkJssePlatform.alpnProtocolNames;
import static jayo.tools.JayoUtils.checkOffsetAndCount;

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
    private static final @NonNull ByteBuffer @NonNull [] DUMMY_OUT = new ByteBuffer[]{ByteBuffer.allocate(0)};

    private final @NonNull RealReader encryptedReader;
    private final @NonNull WriterSegmentQueue encryptedWriterSegmentQueue;
    private final @NonNull SSLEngine engine;
    private final boolean waitForCloseConfirmation;
    private final @NonNull SSLSession tlsSession;

    private final @NonNull Lock readLock = new ReentrantLock();
    private final @NonNull Lock writeLock = new ReentrantLock();

    private @Nullable Boolean isTls = null;
    /**
     * Whether an IOException was received from the underlying endpoint or from the {@link SSLEngine}.
     */
    private volatile boolean invalid = false;

    /**
     * Whether a close_notify was already sent.
     */
    private volatile boolean shutdownSent = false;

    /**
     * Whether a close_notify was already received.
     */
    private volatile boolean shutdownReceived = false;

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

    public RealTlsEndpoint(
            final @NonNull Endpoint encryptedEndpoint,
            final @NonNull SSLEngine engine,
            boolean waitForCloseConfirmation) {
        assert encryptedEndpoint != null;
        assert engine != null;

        if (!(encryptedEndpoint.getReader() instanceof RealReader reader)) {
            throw new IllegalArgumentException("encryptedEndpoint.reader must be an instance of RealReader");
        }
        this.encryptedReader = reader;
        if (!(encryptedEndpoint.getWriter() instanceof RealWriter writer)) {
            throw new IllegalArgumentException("encryptedEndpoint.writer must be an instance of RealWriter");
        }
        this.encryptedWriterSegmentQueue = writer.segmentQueue;

        JssePlatform.get().adaptSslEngine(engine);

        this.engine = engine;
        this.waitForCloseConfirmation = waitForCloseConfirmation;

        // THE initial handshake, this is an important step !
        handshake();

        tlsSession = engine.getSession();

        if (tlsSession.getProtocol().startsWith("DTLS")) {
            throw new IllegalArgumentException("DTLS not supported");
        }
    }

    public @NonNull SSLSession getSession() {
        return tlsSession;
    }

    public @NonNull Handshake getHandshake() {
        // on-demand Handshake creation, only if user calls it
        final var applicationProtocol = engine.getApplicationProtocol();
        return Handshake.get(tlsSession, Protocol.get(applicationProtocol));
    }

    // read

    public long readAtMostTo(final @NonNull Buffer writer, final long byteCount) {
        Objects.requireNonNull(writer);
        if (byteCount < 0L) {
            throw new IllegalArgumentException("byteCount < 0: " + byteCount);
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
            final var dst = tail.asWriteByteBuffer(Segment.SIZE - tail.limit);
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
                    tail.limit += written;
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
    private int readFromReader() throws TlsEOFException {
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
    private int callReadFromReader() throws TlsEOFException {
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

    public void write(final @NonNull Buffer reader, final long byteCount) {
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

    private void wrapAndWrite(final @NonNull RealBuffer source, final long byteCount) {
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
            final var destination = tail.asWriteByteBuffer(Segment.SIZE - tail.limit);
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
                            tail.limit += written;
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

    public boolean shutdownReceived() {
        return shutdownReceived;
    }

    public boolean shutdownSent() {
        return shutdownSent;
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
    public static sealed abstract class Builder<T extends TlsEndpoint.Builder<T, U>, U extends TlsEndpoint.Parameterizer>
            implements TlsEndpoint.Builder<T, U> permits RealClientTlsEndpoint.Builder, RealServerTlsEndpoint.Builder {
        protected boolean waitForCloseConfirmation = false;

        protected abstract @NonNull T getThis();

        @Override
        public final @NonNull T waitForCloseConfirmation(final boolean waitForCloseConfirmation) {
            this.waitForCloseConfirmation = waitForCloseConfirmation;
            return getThis();
        }

        @Override
        public abstract @NonNull T clone();
    }

    /**
     * The base class for parameterize {@link TlsEndpoint}.
     */
    public static sealed abstract class Parameterizer implements TlsEndpoint.Parameterizer
            permits RealClientTlsEndpoint.Builder.Parameterizer, RealServerTlsEndpoint.Builder.Parameterizer {
        protected final @NonNull SSLEngine engine;

        protected Parameterizer(final @NonNull SSLEngine engine) {
            assert engine != null;
            this.engine = engine;
        }

        @Override
        public final @NonNull List<@NonNull Protocol> getEnabledProtocols() {
            return Arrays.stream(engine.getSSLParameters().getApplicationProtocols())
                    .map(Protocol::get)
                    .filter(Objects::nonNull)
                    .toList();
        }

        @Override
        public final void setEnabledProtocols(@NonNull List<@NonNull Protocol> protocols) {
            Objects.requireNonNull(protocols);

            final var sslParameters = engine.getSSLParameters();
            // Enable ALPN.
            final var names = alpnProtocolNames(protocols);
            sslParameters.setApplicationProtocols(names.toArray(String[]::new));
            engine.setSSLParameters(sslParameters);
        }

        @Override
        public final @NonNull List<@NonNull TlsVersion> getEnabledTlsVersions() {
            return Arrays.stream(engine.getEnabledProtocols())
                    .map(TlsVersion::fromJavaName)
                    .toList();
        }

        @Override
        public final void setEnabledTlsVersions(final @NonNull List<@NonNull TlsVersion> tlsVersions) {
            Objects.requireNonNull(tlsVersions);

            final var tlsVersionsAsStrings = tlsVersions.stream()
                    .map(TlsVersion::getJavaName)
                    .toArray(String[]::new);
            engine.setEnabledProtocols(tlsVersionsAsStrings);
        }

        @Override
        public final @NonNull List<@NonNull CipherSuite> getSupportedCipherSuites() {
            return Arrays.stream(engine.getSupportedCipherSuites())
                    .map(CipherSuite::fromJavaName)
                    .toList();
        }

        @Override
        public final @NonNull List<@NonNull CipherSuite> getEnabledCipherSuites() {
            return Arrays.stream(engine.getEnabledCipherSuites())
                    .map(CipherSuite::fromJavaName)
                    .toList();
        }

        @Override
        public final void setEnabledCipherSuites(@NonNull List<@NonNull CipherSuite> cipherSuites) {
            Objects.requireNonNull(cipherSuites);

            final var cipherSuitesAsStrings = cipherSuites.stream()
                    .map(CipherSuite::getJavaName)
                    .toArray(String[]::new);
            engine.setEnabledCipherSuites(cipherSuitesAsStrings);
        }
    }
}
