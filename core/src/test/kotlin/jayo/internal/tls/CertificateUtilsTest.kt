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

package jayo.internal.tls

import jayo.tls.certificatePem
import jayo.tls.decodeCertificatePem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CertificateUtilsTest {
    @Test
    fun testRoundtrip() {
        val certificateString =
            """
      -----BEGIN CERTIFICATE-----
      MIIBmjCCAQOgAwIBAgIBATANBgkqhkiG9w0BAQsFADATMREwDwYDVQQDEwhjYXNo
      LmFwcDAeFw03MDAxMDEwMDAwMDBaFw03MDAxMDEwMDAwMDFaMBMxETAPBgNVBAMT
      CGNhc2guYXBwMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCApFHhtrLan28q
      +oMolZuaTfWBA0V5aMIvq32BsloQu6LlvX1wJ4YEoUCjDlPOtpht7XLbUmBnbIzN
      89XK4UJVM6Sqp3K88Km8z7gMrdrfTom/274wL25fICR+yDEQ5fUVYBmJAKXZF1ao
      I0mIoEx0xFsQhIJ637v2MxJDupd61wIDAQABMA0GCSqGSIb3DQEBCwUAA4GBADam
      UVwKh5Ry7es3OxtY3IgQunPUoLc0Gw71gl9Z+7t2FJ5VkcI5gWfutmdxZ2bDXCI8
      8V0vxo1pHXnbBrnxhS/Z3TBerw8RyQqcaWOdp+pBXyIWmR+jHk9cHZCqQveTIBsY
      jaA9VEhgdaVhxBsT2qzUNDsXlOzGsliznDfoqETb
      -----END CERTIFICATE-----
      
      """.trimIndent()
        val certificate = certificateString.decodeCertificatePem()
        assertThat(certificate.certificatePem()).isEqualTo(certificateString)
    }
}
