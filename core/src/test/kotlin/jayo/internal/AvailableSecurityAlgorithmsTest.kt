/*
 * Copyright (c) 2024-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.security.Provider
import java.security.Security
import java.util.*
import java.util.stream.Collectors
import javax.crypto.Mac


class AvailableSecurityAlgorithmsTest {
    @Disabled
    @Test
    fun getAvailableMessageDigestAlgorithms() {
        val digestClassName = MessageDigest::class.java.getSimpleName()
        val aliasPrefix = "Alg.Alias.$digestClassName."
        Arrays.stream(Security.getProviders())
            .flatMap { prov ->
                val algorithms: Set<String> = HashSet(0)
                prov.getServices().stream()
                    .filter { s -> digestClassName.equals(s.type, ignoreCase = true) }
                    .map(Provider.Service::getAlgorithm)
                    .collect(Collectors.toCollection { algorithms })
                prov.keys.stream()
                    .map { obj: Any -> obj.toString() }
                    .filter { k -> k.startsWith(aliasPrefix) }
                    .map { k ->
                        java.lang.String.format(
                            "\"%s\" -> \"%s\"",
                            k.substring(aliasPrefix.length),
                            prov[k].toString()
                        )
                    }
                    .collect(Collectors.toCollection { algorithms })
                algorithms.stream()
            }
            .sorted(String::compareTo)
            .forEach(::println)
    }

    /*
    Temurin 17 / Azul Zulu 17 / OpenJDK 21
MD2
MD5
SHA-1
SHA-224
SHA-256
SHA-384
SHA-512
SHA-512/224
SHA-512/256
SHA3-224
SHA3-256
SHA3-384
SHA3-512
     */

    @Disabled
    @Test
    fun getAvailableMacAlgorithms() {
        val digestClassName = Mac::class.java.getSimpleName()
        val aliasPrefix = "Alg.Alias.$digestClassName."
        Arrays.stream(Security.getProviders())
            .flatMap { prov ->
                val algorithms: Set<String> = HashSet(0)
                prov.getServices().stream()
                    .filter { s -> digestClassName.equals(s.type, ignoreCase = true) }
                    .map(Provider.Service::getAlgorithm)
                    .collect(Collectors.toCollection { algorithms })
                prov.keys.stream()
                    .map { obj: Any -> obj.toString() }
                    .filter { k -> k.startsWith(aliasPrefix) }
                    .map { k ->
                        java.lang.String.format(
                            "\"%s\" -> \"%s\"",
                            k.substring(aliasPrefix.length),
                            prov[k].toString()
                        )
                    }
                    .collect(Collectors.toCollection { algorithms })
                algorithms.stream()
            }
            .sorted(String::compareTo)
            .forEach(::println)
    }
    
    /*
    HmacMD5
HmacPBESHA1
HmacPBESHA224
HmacPBESHA256
HmacPBESHA384
HmacPBESHA512
HmacPBESHA512/224
HmacPBESHA512/256
HmacSHA1
HmacSHA224
HmacSHA256
HmacSHA3-224
HmacSHA3-256
HmacSHA3-384
HmacSHA3-512
HmacSHA384
HmacSHA512
HmacSHA512/224
HmacSHA512/256
PBEWithHmacSHA1
PBEWithHmacSHA224
PBEWithHmacSHA256
PBEWithHmacSHA384
PBEWithHmacSHA512
SslMacMD5
SslMacSHA1
     */
}