/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network

import jayo.network.Proxy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class HttpProxyTest {
    @Test
    fun `Valid HTTP Proxy without authentication`() {
        val proxy = Proxy.http(InetSocketAddress(0))
        val authentication = proxy.authentication
        assertThat(authentication).isNull()
    }

    @Test
    fun `Valid HTTP Proxy with authentication`() {
        val username = "Noël"
        val password = "Pâques"
        val proxy = Proxy.http(InetSocketAddress(0), username, password)
        val authentication = proxy.authentication
        assertThat(authentication).isNotNull()
        assertThat(authentication!!.username).isEqualTo(username)
        assertThat(authentication.password.concatToString()).isEqualTo(password)
        assertThat(authentication.charset).isEqualTo(Charsets.UTF_8)
    }

    @Test
    fun `Valid HTTP Proxy with authentication and charset`() {
        val username = "Noël"
        val password = "Pâques"
        val proxy = Proxy.http(InetSocketAddress(0), username, password, Charsets.ISO_8859_1)
        val authentication = proxy.authentication
        assertThat(authentication).isNotNull()
        assertThat(authentication!!.username).isEqualTo(username)
        assertThat(authentication.password.concatToString()).isEqualTo(password)
        assertThat(authentication.charset).isEqualTo(Charsets.ISO_8859_1)
    }

    @Test
    fun `HTTP Proxy equals tests`() {
        val address1 = InetSocketAddress("jayo.dev", 123)
        val proxy1 = Proxy.http(address1, "Noël", "Pâques")
        val address2 = InetSocketAddress("jayo.dev", 123)
        val proxy2 = Proxy.http(address2)
        assertThat(proxy1).isEqualTo(proxy2)

        val address3 = InetSocketAddress("jayo.dev", 123)
        val proxy3 = Proxy.socks4(address3)
        assertThat(proxy1).isNotEqualTo(proxy3)
    }

    @Test
    fun `HTTP Proxy hashCode tests`() {
        val address1 = InetSocketAddress("jayo.dev", 123)
        val proxy1 = Proxy.http(address1, "Noël", "Pâques")
        val address2 = InetSocketAddress("jayo.dev", 123)
        val proxy2 = Proxy.http(address2)
        assertThat(proxy1.hashCode()).isEqualTo(proxy2.hashCode())

        val address3 = InetSocketAddress("jayo.dev", 123)
        val proxy3 = Proxy.socks4(address3)
        assertThat(proxy1.hashCode()).isNotEqualTo(proxy3.hashCode())
    }

    @Test
    fun `HTTP Proxy toString test`() {
        val address1 = InetSocketAddress("127.0.0.1", 123)
        val proxy1 = Proxy.http(address1, "Noël", "Pâques")
        assertThat(proxy1.toString()).isEqualTo("HTTP @ 127.0.0.1:123")
    }
}
