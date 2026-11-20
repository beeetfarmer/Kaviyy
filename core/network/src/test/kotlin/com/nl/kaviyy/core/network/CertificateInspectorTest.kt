package com.nl.kaviyy.core.network

import com.nl.kaviyy.core.model.server.PinnedCertificate
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CertificateInspectorTest {
    private fun cert(vararg names: String) =
        HeldCertificate
            .Builder()
            .commonName("kavita")
            .apply { names.forEach { addSubjectAlternativeName(it) } }
            .build()

    @Test
    fun readsTheLeafWithoutSendingARequest() {
        val held = cert("localhost", "127.0.0.1")
        MockWebServer().use { mock ->
            mock.useHttps(
                HandshakeCertificates
                    .Builder()
                    .heldCertificate(held)
                    .build()
                    .sslSocketFactory(),
            )
            mock.enqueue(MockResponse(body = "never"))
            mock.start()
            val url =
                (
                    ServerUrl.parse(
                        mock.url("/").toString().replace("localhost", "127.0.0.1"),
                    ) as ServerUrlResult.Valid
                ).url
            val presented = checkNotNull(runBlocking { CertificateInspector(Dispatchers.IO).fetch(url) })
            assertEquals(PinnedCertificate.of(held.certificate.encoded), presented.pin)
            assertTrue(presented.matchesHost)
            assertTrue("CN=kavita" in presented.subject)
            assertEquals(95, presented.fingerprint.length) // 32 bytes as AB: pairs
            assertEquals(0, mock.requestCount)
        }
    }

    @Test
    fun unreachableHostGivesNull() {
        val url = (ServerUrl.parse("https://127.0.0.1:1") as ServerUrlResult.Valid).url
        assertNull(runBlocking { CertificateInspector(Dispatchers.IO).fetch(url) })
    }

    @Test
    fun hostMatching() {
        val c = cert("kavita.home.lan", "*.example.com", "192.168.1.20", "fd00::1").certificate
        assertTrue(certificateMatchesHost(c, "kavita.home.lan"))
        assertTrue(certificateMatchesHost(c, "books.example.com"))
        assertFalse(certificateMatchesHost(c, "a.b.example.com"))
        assertFalse(certificateMatchesHost(c, "example.com"))
        assertTrue(certificateMatchesHost(c, "192.168.1.20"))
        assertTrue(certificateMatchesHost(c, "fd00:0:0:0:0:0:0:1"))
        assertFalse(certificateMatchesHost(c, "192.168.1.21"))
        assertFalse(certificateMatchesHost(cert().certificate, "kavita"))
    }
}
