/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.exceptions;

import jayo.endpoints.JayoClosedEndpointException;
import jayo.endpoints.JayoEndpointException;
import org.jspecify.annotations.NonNull;

import java.io.*;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.Objects;

/**
 * Wraps an {@link IOException} with an unchecked exception.
 */
public class JayoException extends UncheckedIOException {
    private static final @NonNull String CLOSED_SOCKET_MESSAGE = "Socket is closed";

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
        super(Objects.requireNonNull(cause));
    }

    /**
     * Constructs a new {@link JayoException}, which type depends on the IOException class
     */
    public static JayoException buildJayoException(final @NonNull IOException ioException) {
        Objects.requireNonNull(ioException);
        return switch (ioException) {
            case EOFException eofException -> new JayoEOFException(eofException);
            case FileNotFoundException fileNotFoundException -> new JayoFileNotFoundException(fileNotFoundException);
            case NoSuchFileException noSuchFileException -> new JayoFileNotFoundException(noSuchFileException);
            case FileAlreadyExistsException faeException -> new JayoFileAlreadyExistsException(faeException);
            case ProtocolException protocolException -> new JayoProtocolException(protocolException);
            case SocketTimeoutException stoException -> new JayoTimeoutException(stoException);
            case InterruptedIOException interuptIOException -> new JayoInterruptedIOException(interuptIOException);

            // Endpoint related exceptions
            case ClosedChannelException closedChanException -> new JayoClosedEndpointException(closedChanException);
            case SocketException socketException -> {
                if (CLOSED_SOCKET_MESSAGE.equals(socketException.getMessage())) {
                    yield new JayoClosedEndpointException(socketException);
                }
                yield new JayoEndpointException(socketException);
            }

            default -> new JayoException(ioException);
        };
    }
}
