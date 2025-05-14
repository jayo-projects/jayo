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

package jayo.tls

import com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider
import com.amazon.corretto.crypto.provider.SelfTestStatus
import jayo.JayoTestUtil
import jayo.internal.tls.platform.JssePlatformUtils
import jayo.tls.HeldCertificate.CertificateKeyFormat.RSA_2048
import jayo.tools.JayoTlsUtils.localhost
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
import org.conscrypt.Conscrypt
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.opentest4j.TestAbortedException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.security.Security

/**
 * Marks a test class as JSSE multi-platform, each test runs on all platforms.
 *
 * Also allows individual tests to state general platform assumptions.
 *
 * **WARNING** the test class using this extension does not support `@BeforeEach` and `@AfterEach` anymore.
 */
open class JssePlatformRule() : InvocationInterceptor {
    private lateinit var currentProvider: String

    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        val args = invocationContext.arguments.toTypedArray()
        val targetInstance = invocationContext.getTarget().orElse(null)
        for (provider in ALL_PROVIDERS) {
            currentProvider = provider
            val providerNames = setupProvider(provider)
            JssePlatformUtils.resetForTests()
            try {
                invocationContext.executable.invoke(targetInstance, *args)
            } catch (ite: InvocationTargetException) {
                if (ite.cause !is TestAbortedException) {
                    throw ite
                }
            } finally {
                resetProvider(providerNames)
                JssePlatformUtils.resetForTests()
            }
        }
        invocation.skip()
    }

    private fun resetProvider(providerNames: Set<String>) {
        System.clearProperty("javax.net.debug")
        providerNames.forEach(Security::removeProvider)
    }

    private fun setupProvider(providerName: String) =
        when (providerName) {
            CONSCRYPT_PROVIDER -> {
                if (!Conscrypt.isAvailable()) {
                    System.err.println("Warning: Conscrypt not available")
                }

                val provider =
                    Conscrypt.newProviderBuilder()
                        .provideTrustManager(true)
                        .build()
                Security.insertProviderAt(provider, 1)
                setOf(provider.name)
            }

            BOUNCYCASTLE_PROVIDER -> {
                val provider1 = BouncyCastleProvider()
                val provider2 = BouncyCastleJsseProvider()
                Security.insertProviderAt(provider1, 1)
                Security.insertProviderAt(provider2, 2)
                setOf(provider1.name, provider2.name)
            }

            CORRETTO_PROVIDER -> {
                if (isCorrettoSupported) {
                    AmazonCorrettoCryptoProvider.install()
                    AmazonCorrettoCryptoProvider.INSTANCE.assertHealthy()
                    setOf(AmazonCorrettoCryptoProvider.PROVIDER_NAME)
                }
                setOf<String>()
            }

            JDK_PROVIDER -> {
                if (System.getProperty("javax.net.debug") == null) {
                    System.setProperty("javax.net.debug", "")
                }
                setOf<String>()
            }

            else -> throw IllegalArgumentException("Unexpected provider: $providerName")
        }

    fun isBouncyCastle() = BOUNCYCASTLE_PROVIDER == currentProvider

    fun assumeBouncyCastle() = assumeTrue(isBouncyCastle())

    fun isConscrypt() = CONSCRYPT_PROVIDER == currentProvider

    fun assumeConscrypt() = assumeTrue(isConscrypt())

    fun isGraalVMImage() = JayoTestUtil.isGraalVmImage

    fun assumeGraalVMImage() {
        assumeTrue(isGraalVMImage())
    }

    fun assumeNotGraalVMImage() {
        assumeFalse(isGraalVMImage())
    }

    fun assumeJdkVersion(majorVersion: Int) {
        assumeNotGraalVMImage()
        assumeTrue(PlatformVersion.majorVersion == majorVersion)
    }

    fun localhostHandshakeCertificates(): ClientHandshakeCertificates =
        if (isBouncyCastle()) localhostHandshakeCertificatesWithRsa2048 else localhost()

    companion object {
        const val JDK_PROVIDER = "jdk"
        const val BOUNCYCASTLE_PROVIDER = "bouncycastle"
        const val CONSCRYPT_PROVIDER = "conscrypt"
        const val CORRETTO_PROVIDER = "corretto"

        val ALL_PROVIDERS = listOf(JDK_PROVIDER, BOUNCYCASTLE_PROVIDER, CONSCRYPT_PROVIDER/*, CORRETTO_PROVIDER*/)

        val isCorrettoSupported: Boolean =
            try {
                // Trigger an early exception over a fatal error, prefer a RuntimeException over Error.
                Class.forName("com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider")

                AmazonCorrettoCryptoProvider.INSTANCE.loadingError == null &&
                        AmazonCorrettoCryptoProvider.INSTANCE.runSelfTests() == SelfTestStatus.PASSED
            } catch (_: ClassNotFoundException) {
                false
            }

        /**
         * For whatever reason, our BouncyCastle provider doesn't work with ECDSA keys. Configure it to use RSA-2048
         * instead.
         *
         * (We otherwise prefer ECDSA because it's faster.)
         */
        private val localhostHandshakeCertificatesWithRsa2048: ClientHandshakeCertificates by lazy {
            val heldCertificate =
                HeldCertificate.builder()
                    .commonName("localhost")
                    .addSubjectAlternativeName("localhost")
                    .keyFormat(RSA_2048)
                    .build()
            return@lazy ClientHandshakeCertificates.builder()
                .heldCertificate(heldCertificate)
                .addTrustedCertificate(heldCertificate.certificate)
                .build()
        }
    }
}
