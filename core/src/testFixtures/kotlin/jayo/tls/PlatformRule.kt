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
import jayo.internal.tls.platform.BouncyCastleJssePlatform
import jayo.internal.tls.platform.ConscryptJssePlatform
import jayo.internal.tls.platform.JdkJssePlatform
import jayo.internal.tls.platform.JssePlatformUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
import org.conscrypt.Conscrypt
import org.hamcrest.*
import org.hamcrest.CoreMatchers.anything
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.*
import org.opentest4j.TestAbortedException
import java.lang.reflect.Method
import java.security.Security

/**
 * Marks a test as Platform aware, before the test runs a consistent Platform will be established e.g., SecurityProvider
 * for Conscrypt installed.
 *
 * Also allows a test file to state general platform assumptions or for individual test.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
open class PlatformRule
@JvmOverloads
constructor(
    val requiredPlatformName: String? = null,
    val jssePlatform: JssePlatform? = null,
) : BeforeEachCallback, AfterEachCallback, InvocationInterceptor {
    private val versionChecks = mutableListOf<Pair<Matcher<out Any>, Matcher<out Any>>>()

    override fun beforeEach(context: ExtensionContext) {
        setupPlatform()
    }

    override fun afterEach(context: ExtensionContext) {
        resetPlatform()
    }

    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        var failed = false
        try {
            invocation.proceed()
        } catch (e: TestAbortedException) {
            throw e
        } catch (e: Throwable) {
            failed = true
            rethrowIfNotExpected(e)
        } finally {
            resetPlatform()
        }
        if (!failed) {
            failIfExpected()
        }
    }

    fun setupPlatform() {
        if (requiredPlatformName != null) {
            assumeTrue(getPlatformSystemProperty() == requiredPlatformName)
        }

        if (jssePlatform != null) {
            JssePlatformUtils.resetForTests(jssePlatform)
        } else {
            JssePlatformUtils.resetForTests()
        }

        if (requiredPlatformName != null) {
            System.err.println("Running with ${JssePlatform.get().javaClass.simpleName}")
        }
    }

    fun resetPlatform() {
        if (jssePlatform != null) {
            JssePlatformUtils.resetForTests()
        }
    }

    fun expectFailureOnConscryptPlatform() {
        expectFailure(platformMatches(CONSCRYPT_PROPERTY))
    }

    fun expectFailureOnCorrettoPlatform() {
        expectFailure(platformMatches(CORRETTO_PROPERTY))
    }

    fun expectFailureFromJdkVersion(majorVersion: Int) {
        if (!JayoTestUtil.isGraalVmImage) {
            expectFailure(fromMajor(majorVersion))
        }
    }

    fun expectFailureOnJdkVersion(majorVersion: Int) {
        if (!JayoTestUtil.isGraalVmImage) {
            expectFailure(onMajor(majorVersion))
        }
    }

    private fun expectFailure(
        versionMatcher: Matcher<out Any>,
        failureMatcher: Matcher<out Any> = anything(),
    ) {
        versionChecks.add(Pair(versionMatcher, failureMatcher))
    }

    fun platformMatches(platform: String): Matcher<Any> =
        object : BaseMatcher<Any>() {
            override fun describeTo(description: Description) {
                description.appendText(platform)
            }

            override fun matches(item: Any?): Boolean {
                return getPlatformSystemProperty() == platform
            }
        }

    fun fromMajor(version: Int): Matcher<PlatformVersion> {
        return object : TypeSafeMatcher<PlatformVersion>() {
            override fun describeTo(description: Description) {
                description.appendText("JDK with version from $version")
            }

            override fun matchesSafely(item: PlatformVersion): Boolean {
                return item.majorVersion >= version
            }
        }
    }

    fun onMajor(version: Int): Matcher<PlatformVersion> {
        return object : TypeSafeMatcher<PlatformVersion>() {
            override fun describeTo(description: Description) {
                description.appendText("JDK with version $version")
            }

            override fun matchesSafely(item: PlatformVersion): Boolean {
                return item.majorVersion == version
            }
        }
    }

    fun rethrowIfNotExpected(e: Throwable) {
        versionChecks.forEach { (versionMatcher, failureMatcher) ->
            if (versionMatcher.matches(PlatformVersion) && failureMatcher.matches(e)) {
                return
            }
        }

        throw e
    }

    fun failIfExpected() {
        versionChecks.forEach { (versionMatcher, failureMatcher) ->
            if (versionMatcher.matches(PlatformVersion)) {
                val description = StringDescription()
                versionMatcher.describeTo(description)
                description.appendText(" expected to fail with exception that ")
                failureMatcher.describeTo(description)

                fail<Any>(description.toString())
            }
        }
    }

    fun isConscrypt() = getPlatformSystemProperty() == CONSCRYPT_PROPERTY

    fun isBouncyCastle() = getPlatformSystemProperty() == BOUNCYCASTLE_PROPERTY

    fun isGraalVMImage() = JayoTestUtil.isGraalVmImage

    fun assumeConscrypt() {
        assumeTrue(getPlatformSystemProperty() == CONSCRYPT_PROPERTY)
    }

    fun assumeCorretto() {
        assumeTrue(getPlatformSystemProperty() == CORRETTO_PROPERTY)
    }

    fun assumeBouncyCastle() {
        assumeTrue(getPlatformSystemProperty() == BOUNCYCASTLE_PROPERTY)
    }

    fun assumeGraalVMImage() {
        assumeTrue(isGraalVMImage())
    }

    fun assumeNotConscrypt() {
        assumeTrue(getPlatformSystemProperty() != CONSCRYPT_PROPERTY)
    }

    fun assumeNotCorretto() {
        assumeTrue(getPlatformSystemProperty() != CORRETTO_PROPERTY)
    }

    fun assumeNotBouncyCastle() {
        // Most failures are with MockWebServer
        // org.bouncycastle.tls.TlsFatalAlertReceived: handshake_failure(40)
        //        at org.bouncycastle.tls.TlsProtocol.handleAlertMessage(TlsProtocol.java:241)
        assumeTrue(getPlatformSystemProperty() != BOUNCYCASTLE_PROPERTY)
    }

    fun assumeNotGraalVMImage() {
        assumeFalse(isGraalVMImage())
    }

    fun assumeJdkVersion(majorVersion: Int) {
        assumeNotGraalVMImage()
        assumeTrue(PlatformVersion.majorVersion == majorVersion)
    }

    /*fun localhostHandshakeCertificates(): HandshakeCertificates {
      return when {
        isBouncyCastle() -> localhostHandshakeCertificatesWithRsa2048
        else -> localhost()
      }
    }*/

    companion object {
        const val PROPERTY_NAME = "jayo.platform"
        const val JDK_PROPERTY = "jdk21(+) builtin"
        const val CONSCRYPT_PROPERTY = "conscrypt"
        const val CORRETTO_PROPERTY = "corretto"
        const val BOUNCYCASTLE_PROPERTY = "bouncycastle"

        /**
         * For whatever reason our BouncyCastle provider doesn't work with ECDSA keys. Just configure it to use RSA-2048
         * instead.
         *
         * (We otherwise prefer ECDSA because it's faster.)
         */
        /*private val localhostHandshakeCertificatesWithRsa2048: HandshakeCertificates by lazy {
          val heldCertificate =
            HeldCertificate.Builder()
              .commonName("localhost")
              .addSubjectAlternativeName("localhost")
              .rsa2048()
              .build()
          return@lazy HandshakeCertificates.Builder()
            .heldCertificate(heldCertificate)
            .addTrustedCertificate(heldCertificate.certificate)
            .build()
        }*/

        init {
            val platformSystemProperty = getPlatformSystemProperty()

            if (platformSystemProperty == JDK_PROPERTY) {
                if (System.getProperty("javax.net.debug") == null) {
                    System.setProperty("javax.net.debug", "")
                }
            } else if (platformSystemProperty == CONSCRYPT_PROPERTY && Security.getProviders()[0].name != "Conscrypt") {
                if (!Conscrypt.isAvailable()) {
                    System.err.println("Warning: Conscrypt not available")
                }

                val provider =
                    Conscrypt.newProviderBuilder()
                        .provideTrustManager(true)
                        .build()
                Security.insertProviderAt(provider, 1)
            } else if (platformSystemProperty == BOUNCYCASTLE_PROPERTY && Security.getProviders()[0].name != "BC") {
                Security.insertProviderAt(BouncyCastleProvider(), 1)
                Security.insertProviderAt(BouncyCastleJsseProvider(), 2)
            } else if (platformSystemProperty == CORRETTO_PROPERTY) {
                AmazonCorrettoCryptoProvider.install()

                AmazonCorrettoCryptoProvider.INSTANCE.assertHealthy()
            }

            JssePlatformUtils.resetForTests()

            System.err.println("Running Tests with ${JssePlatform.get().javaClass.simpleName}")
        }

        @JvmStatic
        fun getPlatformSystemProperty(): String {
            var property: String? = System.getProperty(PROPERTY_NAME)

            if (property == null) {
                property =
                    when (JssePlatform.get()) {
                        is ConscryptJssePlatform -> CONSCRYPT_PROPERTY
                        is BouncyCastleJssePlatform -> BOUNCYCASTLE_PROPERTY
                        is JdkJssePlatform -> if (isCorrettoInstalled) CORRETTO_PROPERTY else JDK_PROPERTY
                    }
            }

            return property
        }

        @JvmStatic
        fun conscrypt() = PlatformRule(CONSCRYPT_PROPERTY)

        @JvmStatic
        fun bouncycastle() = PlatformRule(BOUNCYCASTLE_PROPERTY)

        val isCorrettoSupported: Boolean =
            try {
                // Trigger an early exception over a fatal error, prefer a RuntimeException over Error.
                Class.forName("com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider")

                AmazonCorrettoCryptoProvider.INSTANCE.loadingError == null &&
                        AmazonCorrettoCryptoProvider.INSTANCE.runSelfTests() == SelfTestStatus.PASSED
            } catch (e: ClassNotFoundException) {
                false
            }

        val isCorrettoInstalled: Boolean =
            isCorrettoSupported && Security.getProviders().first().name == AmazonCorrettoCryptoProvider.PROVIDER_NAME
    }
}
