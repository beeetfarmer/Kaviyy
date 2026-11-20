package com.nl.kaviyy.core.network

import com.nl.kaviyy.core.model.server.PinnedCertificate
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.CipherSuite
import okhttp3.Handshake
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.TlsVersion
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Test
import java.net.HttpURLConnection
import javax.net.ssl.SSLHandshakeException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerHttpClientsTest {
    private val servers = mutableListOf<MockWebServer>()
    private val clients = ServerHttpClients(baseHttpClient())
    private val leaf = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()

    @After
    fun tearDown() = servers.forEach { it.close() }

    private fun mockServer(
        https: Boolean = false,
        vararg responses: MockResponse,
    ): MockWebServer =
        MockWebServer().also { s ->
            if (https) {
                s.useHttps(
                    HandshakeCertificates
                        .Builder()
                        .heldCertificate(leaf)
                        .build()
                        .sslSocketFactory(),
                )
            }
            responses.forEach(s::enqueue)
            s.start()
            servers += s
        }

    private fun server(
        mock: MockWebServer,
        allowInsecure: Boolean = false,
        pin: PinnedCertificate? = null,
    ) = Server(
        ServerId(1),
        "Test",
        (ServerUrl.parse(mock.url("/").toString()) as ServerUrlResult.Valid).url,
        pinnedCertificate = pin,
        allowInsecure = allowInsecure,
    )

    private fun OkHttpClient.get(url: String) = newCall(Request.Builder().url(url).build()).execute().use { it.code }

    @Test
    fun timeoutsAreSet() {
        val c = baseHttpClient()
        assertEquals(15_000, c.connectTimeoutMillis)
        assertEquals(30_000, c.readTimeoutMillis)
        assertEquals(30_000, c.writeTimeoutMillis)
    }

    @Test
    fun httpIsRefusedWithoutOptIn() {
        val mock = mockServer(responses = arrayOf(MockResponse(body = "ok")))
        val client = clients.create(server(mock))
        assertFailsWith<java.io.IOException> { client.get(mock.url("/").toString()) }
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun httpIsAllowedForOptedInPrivateHost() {
        val mock = mockServer(responses = arrayOf(MockResponse(body = "ok")))
        val client = clients.create(server(mock, allowInsecure = true))
        assertEquals(HttpURLConnection.HTTP_OK, client.get(mock.url("/").toString()))
    }

    @Test
    fun httpRedirectToAnotherHostIsBlocked() {
        val other = mockServer(responses = arrayOf(MockResponse(body = "leak")))
        val otherUrl = other.url("/").toString().replace("localhost", "127.0.0.1")
        val redirect = MockResponse(code = 302, headers = okhttp3.Headers.headersOf("Location", otherUrl))
        val mock = mockServer(responses = arrayOf(redirect))
        val client = clients.create(server(mock, allowInsecure = true))
        assertFailsWith<CleartextNotPermittedException> { client.get(mock.url("/").toString()) }
        assertEquals(0, other.requestCount)
    }

    @Test
    fun untrustedCertificateFailsWithoutPin() {
        val mock = mockServer(https = true, MockResponse(body = "ok"))
        val e = assertFailsWith<java.io.IOException> { clients.create(server(mock)).get(mock.url("/").toString()) }
        // localhost may resolve to ::1 first; OkHttp reports the last route and attaches earlier ones as suppressed.
        assertTrue((listOf(e) + e.suppressed).any { it is SSLHandshakeException }, e.toString())
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun pinnedSelfSignedCertificateIsTrusted() {
        val mock = mockServer(https = true, MockResponse(body = "ok"))
        val client = clients.create(server(mock, pin = PinnedCertificate.of(leaf.certificate.encoded)))
        assertEquals(HttpURLConnection.HTTP_OK, client.get(mock.url("/").toString()))
    }

    @Test
    fun pinCheckRequiresThePinnedCertificateInTheChain() {
        val other = HeldCertificate.Builder().build()

        fun handshake(vararg certs: HeldCertificate) =
            Handshake.get(
                TlsVersion.TLS_1_3,
                CipherSuite.TLS_AES_128_GCM_SHA256,
                certs.map { it.certificate },
                emptyList(),
            )
        val pin = PinnedCertificate.of(leaf.certificate.encoded)
        assertTrue(handshake(leaf).containsPin(pin))
        assertFalse(handshake(other).containsPin(pin))
        assertFalse(null.containsPin(pin))
    }
}
