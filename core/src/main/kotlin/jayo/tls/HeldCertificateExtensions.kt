/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("-HeldCertificate") // Leading '-' hides this class from Java.

package jayo.tls

import jayo.JayoDslMarker
import jayo.tls.HeldCertificate.CertificateKeyFormat
import java.math.BigInteger
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Instant
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration
import kotlin.time.toJavaDuration

public fun HeldCertificate.Builder.build(config: HeldCertificateBuilderDsl.() -> Unit): HeldCertificate {
    contract { callsInPlace(config, InvocationKind.EXACTLY_ONCE) }

    config(HeldCertificateBuilderDsl(this))
    return build()
}

@JayoDslMarker
@JvmInline
public value class HeldCertificateBuilderDsl internal constructor(private val builder: HeldCertificate.Builder) {
    /**
     * Sets the certificate to be valid in ```[notBefore..notAfter]```.
     */
    public var validityInterval: Pair<Instant, Instant>
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.validityInterval(value.first, value.second)
        }

    /**
     * Sets the certificate to be valid immediately and until the specified duration has elapsed. The precision of
     * this field is milliseconds; further precision will be truncated.
     */
    public var duration: Duration
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.duration(value.toJavaDuration())
        }

    /**
     * Adds a subject alternative name (SAN) to the certificate. This is usually a literal hostname, a literal IP
     * address, or a hostname pattern. If no subject alternative names are added, that extension will be omitted.
     */
    public fun addSubjectAlternativeName(altName: String) {
        builder.addSubjectAlternativeName(altName)
    }

    /**
     * Set this certificate's common name (CN). If unset, a random string will be used.
     *
     * Historically this held the hostname of TLS certificate, but that practice was deprecated by
     * [RFC 2818][https://tools.ietf.org/html/rfc2818] and replaced with [addSubjectAlternativeName].
     */
    public var commonName: String
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.commonName(value)
        }

    /**
     * Sets the certificate's organizational unit (OU). If unset, this field will be omitted.
     */
    public var organizationalUnit: String
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.organizationalUnit(value)
        }

    /**
     * Sets this certificate's serial number. If unset, the serial number will be `1`.
     */
    public var serialNumber: Long
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.serialNumber(value)
        }

    /**
     * Sets this certificate's serial number. If unset, the serial number will be `1`.
     */
    public var serialNumberBigInteger: BigInteger
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.serialNumber(value)
        }

    /**
     * Sets the public/private key pair used for this certificate. If unset, a key pair will be generated.
     */
    public var keyPair: KeyPair
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.keyPair(value)
        }

    /**
     * Sets the public/private key pair used for this certificate. If unset, a key pair will be generated.
     */
    public var keyPairFromPublicPrivateKey: Pair<PublicKey, PrivateKey>
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.keyPair(value.first, value.second)
        }

    /**
     * Set the certificate that will issue this certificate. If unset, the certificate will be self-signed.
     */
    public var signedBy: HeldCertificate?
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.signedBy(value)
        }

    /**
     * Set this certificate to be a signing certificate, with up to `maxIntermediateCas` intermediate signing
     * certificates beneath it.
     *
     * By default, this certificate cannot sign other certificates. Set this to `0` so this certificate can sign other
     * certificates (but those certificates cannot themselves sign certificates). Set this to `1` so this certificate
     * can sign intermediate certificates that can themselves sign certificates. Add one for each additional layer of
     * intermediates to permit.
     */
    public fun certificateAuthority(maxIntermediateCas: Int) {
        builder.certificateAuthority(maxIntermediateCas)
    }

    /**
     * Set the certificate key format. If unset the certificate will use [ECDSA_256][CertificateKeyFormat.ECDSA_256].
     * Note that the default may change in future releases.
     */
    public var keyFormat: CertificateKeyFormat
        @Deprecated("Getter is unsupported.", level = DeprecationLevel.ERROR)
        get() = error("unsupported")
        set(value) {
            builder.keyFormat(value)
        }
}
