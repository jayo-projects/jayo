/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from TLS Channel (https://github.com/marianobarrios/tls-channel), original copyright is below
 *
 * Copyright (c) [2015-2021] all contributors
 * Licensed under the MIT License
 */

package jayo.internal.tls;

import jayo.Reader;
import jayo.tls.JayoTlsHandshakeException;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.StandardConstants;
import java.util.HashMap;
import java.util.Map;

/**
 * Implement basic TLS parsing, just to read the SNI. Used by
 * {@linkplain RealServerTlsSocket ServerTlsSocket}.
 */
public final class TlsExplorer {
    // un-instantiable
    private TlsExplorer() {
    }

    /*
     * struct {
     *   uint8 major;
     *   uint8 minor;
     * } ProtocolVersion;
     *
     * enum {
     *   change_cipher_spec(20),
     *   alert(21),
     *   handshake(22),
     *   application_data(23),
     *   (255)
     * } ContentType;
     *
     * struct {
     *   ContentType type;
     *   ProtocolVersion version;
     *   uint16 length;
     *   opaque fragment[TLSPlaintext.length];
     * } TLSPlaintext;
     */

    /**
     * Explores a TLS record in search to the SNI. This method does not consume buffer.
     */
    public static Map<Integer, SNIServerName> exploreTlsRecord(final @NonNull Reader reader) {
        final var firstByte = reader.readByte();
        if (firstByte != 22) {
            // 22: handshake record
            throw new JayoTlsHandshakeException("Not a handshake record");
        }

        reader.skip(2); // ignore version

        final var recordLength = getInt16(reader);
        return exploreHandshake(reader, recordLength);
    }

    /*
     * enum {
     *   hello_request(0),
     *   client_hello(1),
     *   server_hello(2),
     *   certificate(11),
     *   server_key_exchange (12),
     *   certificate_request(13),
     *   server_hello_done(14),
     *   certificate_verify(15),
     *   client_key_exchange(16),
     *   finished(20),
     *   (255)
     * } HandshakeType;
     *
     * struct {
     *   HandshakeType msg_type;
     *   uint24 length;
     *   select (HandshakeType) {
     *     case hello_request: HelloRequest;
     *     case client_hello: ClientHello;
     *     case server_hello: ServerHello;
     *     case certificate: Certificate;
     *     case server_key_exchange: ServerKeyExchange;
     *     case certificate_request: CertificateRequest;
     *     case server_hello_done: ServerHelloDone;
     *     case certificate_verify: CertificateVerify;
     *     case client_key_exchange: ClientKeyExchange;
     *     case finished: Finished;
     *   } body;
     * } Handshake;
     */
    private static Map<Integer, SNIServerName> exploreHandshake(final @NonNull Reader reader, int recordLength) {
        final var handshakeType = reader.readByte();
        if (handshakeType != 0x01) {
            // 0x01: client_hello message
            throw new JayoTlsHandshakeException("Not an initial handshaking");
        }

        // What is the handshake body length?
        final var handshakeLength = getInt24(reader);

        // Theoretically, a single handshake message might span multiple records, but in practice this does not occur.
        if (handshakeLength > recordLength - 4) {
            // 4: handshake header size
            throw new JayoTlsHandshakeException("Handshake message spans multiple records");
        }

        return exploreClientHello(reader);
    }

    /*
     * struct {
     *   uint32 gmt_unix_time;
     *   opaque random_bytes[28];
     * } Random;
     *
     * opaque SessionID<0..32>;
     *
     * uint8 CipherSuite[2];
     *
     * enum {
     *   null(0),
     *   (255)
     * } CompressionMethod;
     *
     * struct {
     *   ProtocolVersion client_version;
     *   Random random;
     *   SessionID session_id;
     *   CipherSuite cipher_suites<2..2^16-2>;
     *   CompressionMethod compression_methods<1..2^8-1>;
     *   select (extensions_present) {
     *     case false: struct {};
     *     case true: Extension extensions<0..2^16-1>;
     *   };
     * } ClientHello;
     */
    private static Map<Integer, SNIServerName> exploreClientHello(final @NonNull Reader reader) {
        reader.skip(2); // ignore version
        reader.skip(32); // ignore random; 32: the length of Random
        ignoreByteVector8(reader); // ignore session id
        ignoreByteVector16(reader); // ignore cipher_suites
        ignoreByteVector8(reader); // ignore compression methods
        if (reader.exhausted()) {
            return new HashMap<>();
        }
        return exploreExtensions(reader);
    }

    /*
     * struct {
     *   ExtensionType extension_type;
     *   opaque extension_data<0..2^16-1>;
     * } Extension;
     *
     * enum {
     *   server_name(0),
     *   max_fragment_length(1),
     *   client_certificate_url(2),
     *   trusted_ca_keys(3),
     *   truncated_hmac(4),
     *   status_request(5),
     *   (65535)
     * }
     * ExtensionType;
     */
    private static Map<Integer, SNIServerName> exploreExtensions(final @NonNull Reader reader) {
        int length = getInt16(reader); // length of extensions
        while (length > 0) {
            int extType = getInt16(reader); // extension type
            int extLen = getInt16(reader); // length of extension data
            if (extType == 0x00) {
                // 0x00: type of server name indication
                return exploreSNIExt(reader, extLen);
            } else {
                // ignore other extensions
                reader.skip(extLen);
            }
            length -= extLen + 4;
        }
        return new HashMap<>();
    }

    /*
     * struct {
     *   NameType name_type;
     *   select (name_type) {
     *     case host_name: HostName;
     *   } name;
     * } ServerName;
     *
     * enum {
     *   host_name(0),
     *   (255)
     * } NameType;
     *
     * opaque HostName<1..2^16-1>;
     *
     * struct {
     *   ServerName server_name_list<1..2^16-1>
     * } ServerNameList;
     */
    private static Map<Integer, SNIServerName> exploreSNIExt(final @NonNull Reader reader, final int extLen) {
        final Map<Integer, SNIServerName> sniMap = new HashMap<>();
        var remains = extLen;
        if (extLen >= 2) {
            // "server_name" extension in ClientHello
            final var listLen = getInt16(reader); // length of server_name_list
            if (listLen == 0 || listLen + 2 != extLen) {
                throw new JayoTlsHandshakeException("Invalid server name indication extension");
            }
            remains -= 2; // 2: the length field of server_name_list
            while (remains > 0) {
                final var code = getInt8(reader); // name_type
                final var snLen = getInt16(reader); // length field of server name
                if (snLen > remains) {
                    throw new JayoTlsHandshakeException("Not enough data to fill declared vector size");
                }
                final var encoded = reader.readByteArray(snLen);
                final SNIServerName serverName;
                if (code == StandardConstants.SNI_HOST_NAME) {
                    if (encoded.length == 0) {
                        throw new JayoTlsHandshakeException("Empty HostName in server name indication");
                    }
                    serverName = new SNIHostName(encoded);
                } else {
                    serverName = new UnknownServerName(code, encoded);
                }
                // check for duplicated server name type
                if (sniMap.put(serverName.getType(), serverName) != null) {
                    throw new JayoTlsHandshakeException("Duplicated server name of type " + serverName.getType());
                }
                remains -= encoded.length + 3; // NameType: 1 byte; HostName;
                // length: 2 bytesProduced
            }
        } else if (extLen == 0) {
            // "server_name" extension in ServerHello
            throw new JayoTlsHandshakeException("Not server name indication extension in client");
        }
        if (remains != 0) {
            throw new JayoTlsHandshakeException("Invalid server name indication extension");
        }
        return sniMap;
    }

    private static int getInt8(final @NonNull Reader reader) {
        return reader.readByte();
    }

    private static int getInt16(final @NonNull Reader reader) {
        return ((reader.readByte() & 0xFF) << 8) | (reader.readByte() & 0xFF);
    }

    private static int getInt24(final @NonNull Reader reader) {
        return ((reader.readByte() & 0xFF) << 16) | ((reader.readByte() & 0xFF) << 8) | (reader.readByte() & 0xFF);
    }

    private static void ignoreByteVector8(final @NonNull Reader reader) {
        reader.skip(getInt8(reader));
    }

    private static void ignoreByteVector16(final @NonNull Reader reader) {
        reader.skip(getInt16(reader));
    }

    // For some reason, SNIServerName is abstract
    private static class UnknownServerName extends SNIServerName {
        UnknownServerName(final int code, final byte[] encoded) {
            super(code, encoded);
        }
    }
}
