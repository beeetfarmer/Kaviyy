package com.nl.kaviyy.core.model.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class ServerUrlTest {
    private fun valid(input: String): ServerUrl =
        when (val r = ServerUrl.parse(input)) {
            is ServerUrlResult.Valid -> r.url
            is ServerUrlResult.Invalid -> fail("$input rejected: ${r.error}")
        }

    private fun error(input: String): ServerUrlError = assertIs<ServerUrlResult.Invalid>(ServerUrl.parse(input)).error

    @Test
    fun normalisesCommonInputs() {
        assertEquals("https://kavita.home.lan:5000", valid("  kavita.home.lan:5000/ ").value)
        assertEquals("https://kavita.example.com", valid("HTTPS://Kavita.Example.COM:443/").value)
        assertEquals("http://192.168.1.20:5000", valid("http://192.168.1.20:5000/api/").value)
        assertEquals("https://example.com/kavita", valid("https://example.com/kavita/api").value)
        assertEquals("http://example.com", valid("http://example.com:80").value)
        assertEquals("https://[fd00::1]:5000", valid("https://[FD00::1]:5000").value)
    }

    @Test
    fun nonAsciiHostsAreRejected() {
        assertIs<ServerUrlResult.Invalid>(ServerUrl.parse("https://bücher.example"))
    }

    @Test
    fun resolveJoinsPaths() {
        assertEquals("https://h/kavita/api/Server/server-info", valid("h/kavita").resolve("/api/Server/server-info"))
    }

    @Test
    fun rejectsBadInput() {
        assertEquals(ServerUrlError.Empty, error("   "))
        assertEquals(ServerUrlError.UnsupportedScheme, error("ftp://host"))
        assertEquals(ServerUrlError.UnsupportedScheme, error("javascript://host"))
        assertEquals(ServerUrlError.UnsupportedScheme, error("file:///etc/passwd"))
        assertEquals(ServerUrlError.ContainsCredentials, error("https://user:pass@host"))
        assertEquals(ServerUrlError.ContainsCredentials, error("https://user@host"))
        assertEquals(ServerUrlError.ContainsQueryOrFragment, error("https://host/?apiKey=abc"))
        assertEquals(ServerUrlError.ContainsQueryOrFragment, error("https://host/#x"))
        assertEquals(ServerUrlError.InvalidPort, error("https://host:70000"))
        assertEquals(ServerUrlError.Malformed, error("https://ho st"))
        assertEquals(ServerUrlError.InvalidHost, error("https://:5000"))
        assertEquals(ServerUrlError.Malformed, error("https://[zz::1]"))
    }

    @Test
    fun privateHostDetection() {
        listOf(
            "10.0.0.5",
            "172.16.0.1",
            "172.31.255.255",
            "192.168.1.20",
            "169.254.3.4",
            "100.64.0.1",
            "100.127.1.1",
            "127.0.0.1",
            "nas.local",
            "kavita.home.lan",
            "[fd12::1]",
            "[fe80::1]",
            "[::1]",
        ).forEach { assertTrue(valid("http://$it").isPrivateHost, it) }
        listOf("8.8.8.8", "172.32.0.1", "100.128.0.1", "192.169.0.1", "example.com", "lan.example.com", "[2001:db8::1]")
            .forEach { assertFalse(valid("http://$it").isPrivateHost, it) }
    }

    @Test
    fun cleartextPolicy() {
        assertTrue(valid("https://example.com").cleartextPermitted(allowInsecure = false))
        assertFalse(valid("http://192.168.1.2").cleartextPermitted(allowInsecure = false))
        assertTrue(valid("http://192.168.1.2").cleartextPermitted(allowInsecure = true))
        assertFalse(valid("http://example.com").cleartextPermitted(allowInsecure = true))
    }
}
