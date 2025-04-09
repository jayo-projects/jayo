/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo;

import jayo.network.JayoConnectException;
import jayo.network.JayoSocketException;
import jayo.network.JayoUnknownServiceException;
import jayo.tls.JayoTlsException;
import jayo.tls.JayoTlsHandshakeException;
import jayo.tls.JayoTlsPeerUnverifiedException;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.*;
import java.net.*;
import java.nio.channels.ClosedChannelException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.Objects;

/**
 * Wraps an {@link IOException} with an unchecked exception.
 */
public class JayoException extends UncheckedIOException {
    private static final @NonNull String CLOSED_SOCKET_MESSAGE = "Socket closed";
    private static final @NonNull String BROKEN_PIPE_SOCKET_MESSAGE = "Broken pipe";

    /**
     * Constructs a {@link JayoException} with the specified detail message.
     *
     * @param message The detail message (which is saved for later retrieval by the {@code message} value)
     */
    public JayoException(final @NonNull String message) {
        super(Objects.requireNonNull(message), new IOException(message));
    }

    /**
     * Constructs a {@link JayoException} with the specified detail message and cause.
     * <p>
     * Note that the detail message associated with {@code cause} is *not* automatically incorporated into this
     * exception's detail message.
     *
     * @param message The detail message (which is saved for later retrieval by the {@code message} value)
     * @param cause   The {@link IOException} (which is saved for later retrieval by the {@code cause} value).
     */
    public JayoException(final @NonNull String message, final @NonNull IOException cause) {
        super(Objects.requireNonNull(message), Objects.requireNonNull(cause));
    }

    /**
     * Constructs a {@link JayoException} with the specified {@code cause} and a detail message of `cause.toString()`
     * (which typically contains the class and detail message of {@code cause}).
     *
     * @param cause The {@link IOException} (which is saved for later retrieval by the {@code cause} value).
     */
    public JayoException(final @NonNull IOException cause) {
        super(Objects.requireNonNull(cause).getMessage(), cause);
    }

    /**
     * Constructs a new {@link JayoException}, its type depends on the IOException type
     */
    public static JayoException buildJayoException(final @NonNull IOException ioException) {
        Objects.requireNonNull(ioException);
        if (ioException instanceof EOFException eofException) {
            return new JayoEOFException(eofException);
        }
        if (ioException instanceof FileNotFoundException fileNotFoundException) {
            return new JayoFileNotFoundException(fileNotFoundException);
        }
        if (ioException instanceof NoSuchFileException noSuchFileException) {
            return new JayoFileNotFoundException(noSuchFileException);
        }
        if (ioException instanceof FileAlreadyExistsException faeException) {
            return new JayoFileAlreadyExistsException(faeException);
        }
        if (ioException instanceof ProtocolException protocolException) {
            return new JayoProtocolException(protocolException);
        }
        if (ioException instanceof SocketTimeoutException stoException) {
            return new JayoTimeoutException(stoException);
        }
        if (ioException instanceof InterruptedIOException interuptIOException) {
            return new JayoInterruptedIOException(interuptIOException);
        }
        if (ioException instanceof UnknownHostException unknownHostException) {
            return new JayoUnknownHostException(unknownHostException);
        }

        // Endpoint related exceptions
        if (ioException instanceof ClosedChannelException closedChanException) {
            return new JayoClosedResourceException(closedChanException);
        }
        if (ioException instanceof ConnectException connectException) {
            return new JayoConnectException(connectException);
        }
        if (ioException instanceof SocketException socketException) {
            if (CLOSED_SOCKET_MESSAGE.equals(socketException.getMessage())
                    || BROKEN_PIPE_SOCKET_MESSAGE.equals(socketException.getMessage())) {
                return new JayoClosedResourceException(socketException);
            }
            return new JayoSocketException(socketException);
        }
        if (ioException instanceof UnknownServiceException unknownServiceException) {
            return new JayoUnknownServiceException(unknownServiceException);
        }

        // TLS/SSL related exceptions
        if (ioException instanceof SSLHandshakeException sslHandshakeException) {
            return new JayoTlsHandshakeException(sslHandshakeException);
        }
        if (ioException instanceof SSLPeerUnverifiedException sslPeerUnverifiedException) {
            return new JayoTlsPeerUnverifiedException(sslPeerUnverifiedException);
        }
        if (ioException instanceof SSLException sslException) {
            return new JayoTlsException(sslException);
        }

        // default
        if (BROKEN_PIPE_SOCKET_MESSAGE.equals(ioException.getMessage())) {
            return new JayoClosedResourceException(ioException);
        }
        return new JayoException(ioException);
    }
}
