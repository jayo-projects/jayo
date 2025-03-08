/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 *
 * Forked from OkHttp (https://github.com/square/okhttp), original copyright is below
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

package jayo.internal.tls;

import jayo.bytestring.ByteString;
import jayo.JayoProtocolException;
import org.jspecify.annotations.NonNull;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static jayo.internal.tls.Adapters.*;
import static jayo.internal.tls.Certificate.*;

/**
 * ASN.1 adapters adapted from the specifications in <a href="https://tools.ietf.org/html/rfc5280">RFC 5280</a>.
 */
final class CertificateAdapters {
    // un-instantiable
    private CertificateAdapters() {
    }

    /**
     * <pre>
     * {@code
     * Time ::= CHOICE {
     *   utcTime        UTCTime,
     *   generalTime    GeneralizedTime
     * }
     * }
     * </pre>
     * RFC 5280, section 4.1.2.5:
     * <blockquote>
     * CAs conforming to this profile MUST always encode certificate validity dates through the year 2049 as UTCTime;
     * certificate validity dates in 2050 or later MUST be encoded as GeneralizedTime.
     * </blockquote>
     */
    static final @NonNull DerAdapter<Long> TIME = new DerAdapter<>() {
        @Override
        public boolean matches(final @NonNull DerHeader header) {
            assert header != null;

            return Adapters.UTC_TIME.matches(header) || Adapters.GENERALIZED_TIME.matches(header);
        }

        @Override
        public @NonNull Long fromDer(final @NonNull DerReader reader) {
            assert reader != null;

            final var peekHeader = reader.peekHeader();
            if (peekHeader == null) {
                throw new JayoProtocolException("expected time but was exhausted at " + reader);
            }

            if (peekHeader.tagClass() == Adapters.UTC_TIME.tagClass() &&
                    peekHeader.tag() == Adapters.UTC_TIME.tag()) {
                return Adapters.UTC_TIME.fromDer(reader);
            }
            if (peekHeader.tagClass() == Adapters.GENERALIZED_TIME.tagClass() &&
                    peekHeader.tag() == Adapters.GENERALIZED_TIME.tag()) {
                return Adapters.GENERALIZED_TIME.fromDer(reader);
            }
            throw new JayoProtocolException("expected time but was " + peekHeader + " at " + reader);
        }

        @Override
        public void toDer(final @NonNull DerWriter writer, final @NonNull Long value) {
            assert writer != null;
            assert value != null;

            // [1950-01-01T00:00:00 .. 2050-01-01T00:00:00Z)
            if (value >= -631_152_000_000L && value < 2_524_608_000_000L) {
                Adapters.UTC_TIME.toDer(writer, value);
            } else {
                Adapters.GENERALIZED_TIME.toDer(writer, value);
            }
        }
    };

    /**
     * <pre>
     * {@code
     * Validity ::= SEQUENCE {
     * notBefore      Time,
     * notAfter       Time
     * }
     * }
     * </pre>
     */
    private static final @NonNull BasicDerAdapter<Validity> VALIDITY =
            Adapters.sequence(
                    "Validity",
                    validity -> List.of(validity.notBefore(), validity.notAfter()),
                    list -> new Validity((long) list.get(0), (long) list.get(1)),
                    TIME,
                    TIME
            );

    /**
     * The type of the parameters depends on the algorithm that precedes it.
     */
    private static final @NonNull DerAdapter<Object> ALGORITHM_PARAMETERS =
            Adapters.usingTypeHint(typeHint -> {
                // This type is pretty strange. The spec says that for certain algorithms we must encode null when it is
                // present, and for others we must omit it! https://tools.ietf.org/html/rfc4055#section-2.1
                if (typeHint.equals(ObjectIdentifiers.SHA256_WITH_RSA_ENCRYPTION) ||
                        typeHint.equals(ObjectIdentifiers.RSA_ENCRYPTION)) {
                    return Adapters.NULL;
                }
                if (typeHint.equals(ObjectIdentifiers.EC_PUBLIC_KEY)) {
                    return Adapters.OBJECT_IDENTIFIER;
                }
                return null;
            });

    /**
     * <pre>
     * {@code
     * AlgorithmIdentifier ::= SEQUENCE  {
     *   algorithm      OBJECT IDENTIFIER,
     *   parameters     ANY DEFINED BY algorithm OPTIONAL
     * }
     * }
     * </pre>
     */
    static final @NonNull BasicDerAdapter<AlgorithmIdentifier> ALGORITHM_IDENTIFIER =
            Adapters.sequence(
                    "AlgorithmIdentifier",
                    // must use Arrays.asList to accept nullable elements
                    ai -> Collections.unmodifiableList(Arrays.asList(ai.algorithm(), ai.parameters())),
                    list -> new AlgorithmIdentifier((String) list.get(0), list.get(1)),
                    Adapters.OBJECT_IDENTIFIER.asTypeHint(),
                    ALGORITHM_PARAMETERS
            );

    /**
     * <pre>
     * {@code
     * BasicConstraints ::= SEQUENCE {
     *   cA                      BOOLEAN DEFAULT FALSE,
     *   pathLenConstraint       INTEGER (0..MAX) OPTIONAL
     * }
     * }
     * </pre>
     */
    private static final @NonNull BasicDerAdapter<BasicConstraints> BASIC_CONSTRAINTS =
            Adapters.sequence(
                    "BasicConstraints",
                    // must use Arrays.asList to accept nullable elements
                    bc -> Collections.unmodifiableList(Arrays.asList(bc.ca(), bc.maxIntermediateCas())),
                    list -> new BasicConstraints((boolean) list.get(0), (Long) list.get(1)),
                    Adapters.BOOLEAN.optional(false),
                    Adapters.INTEGER_AS_LONG.optional(null)
            );

    /**
     * Note that only a subset of available choices are implemented.
     * <pre>
     * {@code
     * GeneralName ::= CHOICE {
     *   otherName                       [0]     OtherName,
     *   rfc822Name                      [1]     IA5String,
     *   dNSName                         [2]     IA5String,
     *   x400Address                     [3]     ORAddress,
     *   directoryName                   [4]     Name,
     *   ediPartyName                    [5]     EDIPartyName,
     *   uniformResourceIdentifier       [6]     IA5String,
     *   iPAddress                       [7]     OCTET STRING,
     *   registeredID                    [8]     OBJECT IDENTIFIER
     * }
     * }
     * </pre>
     * The first property of the pair is the adapter that was used, the second property is the value.
     */
    static final @NonNull BasicDerAdapter<String> GENERAL_NAME_DNS_NAME = Adapters.IA5_STRING.withTag(2L);
    static final @NonNull BasicDerAdapter<ByteString> GENERAL_NAME_IP_ADDRESS = Adapters.OCTET_STRING.withTag(7L);
    static final @NonNull DerAdapter<DerAdapterValue> GENERAL_NAME =
            Adapters.choice(
                    GENERAL_NAME_DNS_NAME,
                    GENERAL_NAME_IP_ADDRESS,
                    Adapters.ANY_VALUE
            );

    /**
     * <pre>
     * {@code
     * SubjectAltName ::= GeneralNames
     *
     * GeneralNames ::= SEQUENCE SIZE (1..MAX) OF GeneralName
     * }
     * </pre>
     */
    private static final @NonNull BasicDerAdapter<List<DerAdapterValue>> SUBJECT_ALTERNATIVE_NAME = GENERAL_NAME.asSequenceOf();

    /**
     * This uses the preceding extension ID to select which adapter to use for the extension value
     * that follows.
     */
    private static final @NonNull BasicDerAdapter<Object> EXTENSION_VALUE =
            Adapters.usingTypeHint(typeHint -> {
                        if (typeHint.equals(ObjectIdentifiers.SUBJECT_ALTERNATIVE_NAME)) {
                            return SUBJECT_ALTERNATIVE_NAME;
                        }
                        if (typeHint.equals(ObjectIdentifiers.BASIC_CONSTRAINTS)) {
                            return BASIC_CONSTRAINTS;
                        }
                        return null;
                    })
                    .withExplicitBox(
                            Adapters.OCTET_STRING.tagClass(),
                            Adapters.OCTET_STRING.tag(),
                            false
                    );

    /**
     * <pre>
     * {@code
     * Extension ::= SEQUENCE  {
     *   extnID      OBJECT IDENTIFIER,
     *   critical    BOOLEAN DEFAULT FALSE,
     *   extnValue   OCTET STRING
     *     -- contains the DER encoding of an ASN.1 value
     *     -- corresponding to the extension type identified
     *     -- by extnID
     * }
     * }
     * </pre>
     */
    static final @NonNull BasicDerAdapter<Extension> EXTENSION =
            Adapters.sequence(
                    "Extension",
                    // must use Arrays.asList to accept nullable elements
                    extension -> Collections.unmodifiableList(Arrays.asList(extension.id(), extension.critical(), extension.value())),
                    list -> new Extension((String) list.get(0), (boolean) list.get(1), list.get(2)),
                    Adapters.OBJECT_IDENTIFIER.asTypeHint(),
                    Adapters.BOOLEAN.optional(false),
                    EXTENSION_VALUE
            );

    /**
     * <pre>
     * {@code
     * AttributeTypeAndValue ::= SEQUENCE {
     *   type     AttributeType,
     *   value    AttributeValue
     * }
     *
     * AttributeType ::= OBJECT IDENTIFIER
     *
     * AttributeValue ::= ANY -- DEFINED BY AttributeType
     * }
     * </pre>
     */
    private static final @NonNull BasicDerAdapter<AttributeTypeAndValue> ATTRIBUTE_TYPE_AND_VALUE =
            Adapters.sequence(
                    "AttributeTypeAndValue",
                    // must use Arrays.asList to accept nullable elements
                    atav -> Collections.unmodifiableList(Arrays.asList(atav.type(), atav.value())),
                    list -> new AttributeTypeAndValue((String) list.get(0), list.get(1)),
                    Adapters.OBJECT_IDENTIFIER,
                    Adapters.any(
                            new ClassDerAdapter(String.class, Adapters.UTF8_STRING),
                            new ClassDerAdapter(Nothing.class, Adapters.PRINTABLE_STRING),
                            new ClassDerAdapter(AnyValue.class, Adapters.ANY_VALUE)
                    )
            );

    /**
     * <pre>
     * {@code
     * RDNSequence ::= SEQUENCE OF RelativeDistinguishedName
     *
     * RelativeDistinguishedName ::= SET SIZE (1..MAX) OF AttributeTypeAndValue
     * }
     * </pre>
     */
    static final @NonNull BasicDerAdapter<List<List<AttributeTypeAndValue>>> RDN_SEQUENCE =
            ATTRIBUTE_TYPE_AND_VALUE.asSetOf().asSequenceOf();

    /**
     * <pre>
     * {@code
     * Name ::= CHOICE {
     *   -- only one possibility for now --
     *   rdnSequence  RDNSequence
     * }
     * }
     * </pre>
     */
    static final @NonNull DerAdapter<DerAdapterValue> NAME = Adapters.choice(RDN_SEQUENCE);

    /**
     * <pre>
     * {@code
     * SubjectPublicKeyInfo ::= SEQUENCE  {
     *   algorithm            AlgorithmIdentifier,
     *   subjectPublicKey     BIT STRING
     * }
     * }
     * </pre>
     */
    static final @NonNull BasicDerAdapter<SubjectPublicKeyInfo> SUBJECT_PUBLIC_KEY_INFO =
            Adapters.sequence(
                    "SubjectPublicKeyInfo",
                    spki -> List.of(spki.algorithm(), spki.subjectPublicKey()),
                    list -> new SubjectPublicKeyInfo((AlgorithmIdentifier) list.get(0), (BitString) list.get(1)),
                    ALGORITHM_IDENTIFIER,
                    Adapters.BIT_STRING
            );

    /**
     * <pre>
     * {@code
     * TBSCertificate ::= SEQUENCE  {
     *   version         [0]  EXPLICIT Version DEFAULT v1,
     *   serialNumber         CertificateSerialNumber,
     *   signature            AlgorithmIdentifier,
     *   issuer               Name,
     *   validity             Validity,
     *   subject              Name,
     *   subjectPublicKeyInfo SubjectPublicKeyInfo,
     *   issuerUniqueID  [1]  IMPLICIT UniqueIdentifier OPTIONAL, -- If present, version MUST be v2 or v3
     *   subjectUniqueID [2]  IMPLICIT UniqueIdentifier OPTIONAL, -- If present, version MUST be v2 or v3
     *   extensions      [3]  EXPLICIT Extensions OPTIONAL -- If present, version MUST be v3
     * }
     * }
     * </pre>
     */
    @SuppressWarnings("unchecked")
    static final @NonNull BasicDerAdapter<TbsCertificate> TBS_CERTIFICATE =
            Adapters.sequence(
                    "TBSCertificate",
                    // must use Arrays.asList to accept nullable elements
                    tbsCertificate -> Collections.unmodifiableList(Arrays.asList(
                            tbsCertificate.version(),
                            tbsCertificate.serialNumber(),
                            tbsCertificate.signature(),
                            new DerAdapterValue(RDN_SEQUENCE, tbsCertificate.issuer()),
                            tbsCertificate.validity(),
                            new DerAdapterValue(RDN_SEQUENCE, tbsCertificate.subject()),
                            tbsCertificate.subjectPublicKeyInfo(),
                            tbsCertificate.issuerUniqueID(),
                            tbsCertificate.subjectUniqueID(),
                            tbsCertificate.extensions()
                    )),
                    list -> {
                        final var issuer = (List<List<AttributeTypeAndValue>>) ((DerAdapterValue) list.get(3)).value();
                        assert issuer != null;
                        final var subject = (List<List<AttributeTypeAndValue>>) ((DerAdapterValue) list.get(5)).value();
                        assert subject != null;
                        return new TbsCertificate(
                                (long) list.get(0),
                                (BigInteger) list.get(1),
                                (AlgorithmIdentifier) list.get(2),
                                issuer,
                                (Validity) list.get(4),
                                subject,
                                (SubjectPublicKeyInfo) list.get(6),
                                (BitString) list.get(7),
                                (BitString) list.get(8),
                                (List<Extension>) list.get(9)
                        );
                    },
                    Adapters.INTEGER_AS_LONG
                            .withExplicitBox(0L)
                            // v1 == 0.
                            .optional(0L),
                    Adapters.INTEGER_AS_BIG_INTEGER,
                    ALGORITHM_IDENTIFIER,
                    NAME,
                    VALIDITY,
                    NAME,
                    SUBJECT_PUBLIC_KEY_INFO,
                    Adapters.BIT_STRING.withTag(1L).optional(null),
                    Adapters.BIT_STRING.withTag(2L).optional(null),
                    EXTENSION.asSequenceOf().withExplicitBox(3L).optional(List.of())
            );

    /**
     * <pre>
     * {@code
     * Certificate ::= SEQUENCE  {
     *   tbsCertificate       TBSCertificate,
     *   signatureAlgorithm   AlgorithmIdentifier,
     *   signatureValue       BIT STRING
     * }
     * }
     * </pre>
     */
    static final @NonNull BasicDerAdapter<Certificate> CERTIFICATE =
            Adapters.sequence(
                    "Certificate",
                    certificate -> List.of(
                            certificate.tbsCertificate(),
                            certificate.signatureAlgorithm(),
                            certificate.signatureValue()
                    ),
                    list -> new Certificate(
                            (TbsCertificate) list.get(0),
                            (AlgorithmIdentifier) list.get(1),
                            (BitString) list.get(2)),
                    TBS_CERTIFICATE,
                    ALGORITHM_IDENTIFIER,
                    Adapters.BIT_STRING
            );

    /**
     * <pre>
     * {@code
     * Version ::= INTEGER { v1(0), v2(1) } (v1, ..., v2)
     *
     * PrivateKeyAlgorithmIdentifier ::= AlgorithmIdentifier
     *
     * PrivateKey ::= OCTET STRING
     *
     * OneAsymmetricKey ::= SEQUENCE {
     *   version                   Version,
     *   privateKeyAlgorithm       PrivateKeyAlgorithmIdentifier,
     *   privateKey                PrivateKey,
     *   attributes            [0] Attributes OPTIONAL,
     *   ...,
     *   [[2: publicKey        [1] PublicKey OPTIONAL ]],
     *   ...
     * }
     *
     * PrivateKeyInfo ::= OneAsymmetricKey
     * }
     * </pre>
     */
    static final @NonNull BasicDerAdapter<PrivateKeyInfo> PRIVATE_KEY_INFO =
            Adapters.sequence(
                    "PrivateKeyInfo",
                    pki -> List.of(pki.version(), pki.algorithmIdentifier(), pki.privateKey()),
                    list -> new PrivateKeyInfo(
                            (long) list.get(0),
                            (AlgorithmIdentifier) list.get(1),
                            (ByteString) list.get(2)),
                    Adapters.INTEGER_AS_LONG,
                    ALGORITHM_IDENTIFIER,
                    Adapters.OCTET_STRING
            );
}
