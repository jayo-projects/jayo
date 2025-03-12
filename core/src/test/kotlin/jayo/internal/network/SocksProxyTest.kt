/*
 * Copyright (c) 2025-present, pull-vert and Jayo contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package jayo.internal.network

import jayo.network.Proxy
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class ProxyTest {
    @Test
    fun `Valid Socks V5 Proxy`() {
        Proxy.socks5(InetSocketAddress(0), "Noël", charArrayOf('P', 'â', 'q', 'u', 'e', 's'))
    }

    @Test
    fun `Socks V5 Proxy with invalid username`() {
        assertThatThrownBy {
            Proxy.socks5(InetSocketAddress(0), "€", charArrayOf('a', 'b', 'c'))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Invalid username, must be ISO_8859_1 compatible")
    }

    @Test
    fun `Socks V5 Proxy with username too long`() {
        assertThatThrownBy {
            Proxy.socks5(InetSocketAddress(0), "a".repeat(256), charArrayOf('a', 'b', 'c'))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Username too long, must be less than 256 characters")
    }

    @Test
    fun `Socks V5 Proxy with invalid password`() {
        assertThatThrownBy {
            Proxy.socks5(InetSocketAddress(0), "abc", charArrayOf('€'))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Invalid password, must be ISO_8859_1 compatible")
    }

    @Test
    fun `Socks V5 Proxy with password too long`() {
        assertThatThrownBy {
            Proxy.socks5(InetSocketAddress(0), "abc", CharArray(256) { 'a' })
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Password too long, must be less than 256 characters")
    }

    @Test
    fun `Valid Socks V4 Proxy`() {
        Proxy.socks4(InetSocketAddress(0), "Noël")
    }

    @Test
    fun `Socks V4 Proxy with invalid username`() {
        assertThatThrownBy {
            Proxy.socks4(InetSocketAddress(0), "€")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Invalid username, must be ISO_8859_1 compatible")
    }

    @Test
    fun `Socks V4 Proxy with username too long`() {
        assertThatThrownBy {
            Proxy.socks4(InetSocketAddress(0), "a".repeat(256))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Username too long, must be less than 256 characters")
    }
}
