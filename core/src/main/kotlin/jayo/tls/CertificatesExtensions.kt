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

@file:JvmName("-Certificates") // A leading '-' hides this class from Java.

package jayo.tls

import jayo.tools.CertificateUtils
import java.security.cert.X509Certificate

/**
 * Decodes a multiline string that contains a [certificate][certificatePem] which is
 * [PEM-encoded][https://tools.ietf.org/html/rfc7468]. A typical input string looks like this:
 *
 * ```
 * -----BEGIN CERTIFICATE-----
 * MIIBYTCCAQegAwIBAgIBKjAKBggqhkjOPQQDAjApMRQwEgYDVQQLEwtlbmdpbmVl
 * cmluZzERMA8GA1UEAxMIY2FzaC5hcHAwHhcNNzAwMTAxMDAwMDA1WhcNNzAwMTAx
 * MDAwMDEwWjApMRQwEgYDVQQLEwtlbmdpbmVlcmluZzERMA8GA1UEAxMIY2FzaC5h
 * cHAwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASda8ChkQXxGELnrV/oBnIAx3dD
 * ocUOJfdz4pOJTP6dVQB9U3UBiW5uSX/MoOD0LL5zG3bVyL3Y6pDwKuYvfLNhoyAw
 * HjAcBgNVHREBAf8EEjAQhwQBAQEBgghjYXNoLmFwcDAKBggqhkjOPQQDAgNIADBF
 * AiAyHHg1N6YDDQiY920+cnI5XSZwEGhAtb9PYWO8bLmkcQIhAI2CfEZf3V/obmdT
 * yyaoEufLKVXhrTQhRfodTeigi4RX
 * -----END CERTIFICATE-----
 * ```
 */
public fun String.decodeCertificatePem(): X509Certificate = CertificateUtils.decodeCertificatePem(this)

/**
 * @return the certificate encoded in [PEM format][https://tools.ietf.org/html/rfc7468].
 */
public fun X509Certificate.certificatePem(): String = CertificateUtils.certificatePem(this)
