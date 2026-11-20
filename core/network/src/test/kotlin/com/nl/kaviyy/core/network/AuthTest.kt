package com.nl.kaviyy.core.network

import com.nl.kaviyy.core.model.auth.Credentials
import com.nl.kaviyy.core.model.server.PinnedCertificate
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthTest {
    private class FakeSource(
        var credentials: Credentials?,
    ) : CredentialSource {
        var signedOut = false

        override fun current() = credentials

        override fun update(credentials: Credentials.Jwt) {
            this.credentials = credentials
        }

        override fun signOut() {
            signedOut = true
            credentials = null
        }
    }

    private val mock = MockWebServer().apply { start() }
    private val server =
        Server(
            ServerId(1),
            "Test",
            (ServerUrl.parse(mock.url("/").toString()) as ServerUrlResult.Valid).url,
            allowInsecure = true,
        )
    private val jwt = Credentials.Jwt("aarav", "old-token", "old-refresh")

    @After
    fun tearDown() = mock.close()

    private fun call(
        source: CredentialSource,
        path: String = "/api/Library",
    ): Int {
        val client = ServerHttpClients(baseHttpClient()).create(server, source)
        return client.newCall(Request.Builder().url(mock.url(path)).build()).execute().use { it.code }
    }

    private fun ok(body: String = "{}") =
        MockResponse(headers = headersOf("Content-Type", "application/json"), body = body)

    @Test
    fun attachesBearerTokenOrApiKey() {
        mock.enqueue(ok())
        mock.enqueue(ok())
        call(FakeSource(jwt))
        call(FakeSource(Credentials.AuthKey("the-key")))
        assertEquals("Bearer old-token", mock.takeRequest().headers[AUTHORIZATION])
        val second = mock.takeRequest()
        assertEquals("the-key", second.headers[API_KEY])
        assertNull(second.headers[AUTHORIZATION])
    }

    @Test
    fun imageEndpointsGetApiKeyOnTheWireOnly() {
        repeat(3) { mock.enqueue(ok()) }
        call(FakeSource(jwt.copy(apiKey = "user-key")), "/api/Image/series-cover?seriesId=4")
        call(FakeSource(Credentials.AuthKey("auth-key")), "/api/Reader/image?chapterId=1&page=0")
        call(FakeSource(jwt.copy(apiKey = "user-key")), "/api/Library")
        assertEquals("user-key", mock.takeRequest().url.queryParameter(API_KEY_PARAM))
        assertEquals("auth-key", mock.takeRequest().url.queryParameter(API_KEY_PARAM))
        assertNull(mock.takeRequest().url.queryParameter(API_KEY_PARAM))
    }

    @Test
    fun refreshesOnceOn401AndRetriesWithNewToken() {
        mock.enqueue(MockResponse(code = 401))
        mock.enqueue(ok("""{"token":"new-token","refreshToken":"new-refresh"}"""))
        mock.enqueue(ok())
        val source = FakeSource(jwt)
        assertEquals(200, call(source))
        mock.takeRequest()
        val refresh = mock.takeRequest()
        assertEquals("/api/Account/refresh-token", refresh.url.encodedPath)
        assertNull(refresh.headers[AUTHORIZATION])
        val body = refresh.body?.utf8().orEmpty()
        assertTrue("old-token" in body && "old-refresh" in body, body)
        assertEquals("Bearer new-token", mock.takeRequest().headers[AUTHORIZATION])
        assertEquals(Credentials.Jwt("aarav", "new-token", "new-refresh"), source.credentials)
    }

    @Test
    fun tokenAlreadyRefreshedByAnotherCallIsReusedWithoutRefreshing() {
        val source = FakeSource(jwt)
        mock.enqueue(MockResponse(code = 401))
        mock.enqueue(ok())
        // Simulate a concurrent refresh landing between the request and its 401.
        val client =
            ServerHttpClients(baseHttpClient())
                .create(server, source)
                .newBuilder()
                .addNetworkInterceptor { chain ->
                    chain.proceed(chain.request()).also { source.credentials = jwt.copy(token = "fresh") }
                }.build()
        client.newCall(Request.Builder().url(mock.url("/api/Library")).build()).execute().close()
        mock.takeRequest()
        assertEquals("Bearer fresh", mock.takeRequest().headers[AUTHORIZATION])
        assertEquals(2, mock.requestCount)
    }

    @Test
    fun rejectedRefreshSignsOut() {
        mock.enqueue(MockResponse(code = 401))
        mock.enqueue(MockResponse(code = 400))
        val source = FakeSource(jwt)
        assertEquals(401, call(source))
        assertTrue(source.signedOut)
    }

    @Test
    fun serverErrorDuringRefreshKeepsUserSignedIn() {
        mock.enqueue(MockResponse(code = 401))
        mock.enqueue(MockResponse(code = 503))
        val source = FakeSource(jwt)
        assertEquals(401, call(source))
        assertEquals(jwt, source.credentials)
    }

    @Test
    fun unauthorizedAfterRefreshSignsOut() {
        mock.enqueue(MockResponse(code = 401))
        mock.enqueue(ok("""{"token":"new-token","refreshToken":"new-refresh"}"""))
        mock.enqueue(MockResponse(code = 401))
        val source = FakeSource(jwt)
        assertEquals(401, call(source))
        assertTrue(source.signedOut)
    }

    @Test
    fun rejectedAuthKeySignsOut() {
        mock.enqueue(MockResponse(code = 401))
        val source = FakeSource(Credentials.AuthKey("bad"))
        assertEquals(401, call(source))
        assertTrue(source.signedOut)
        assertEquals(1, mock.requestCount)
    }

    private val tlsCert =
        HeldCertificate
            .Builder()
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
    private val origin = tlsServer()
    private val elsewhere = tlsServer()

    private fun tlsServer() =
        MockWebServer().apply {
            useHttps(
                HandshakeCertificates
                    .Builder()
                    .heldCertificate(tlsCert)
                    .build()
                    .sslSocketFactory(),
            )
            start()
        }

    @After
    fun closeTlsServers() {
        origin.close()
        elsewhere.close()
    }

    @Test
    fun credentialsAreStrippedOnRedirectToAnotherHost() {
        val pinned =
            Server(
                ServerId(2),
                "TLS",
                (ServerUrl.parse(origin.url("/").toString()) as ServerUrlResult.Valid).url,
                pinnedCertificate = PinnedCertificate.of(tlsCert.certificate.encoded),
            )
        val target = elsewhere.url("/").toString().replace("localhost", "127.0.0.1")
        listOf(jwt, Credentials.AuthKey("the-key")).forEach { creds ->
            // A hostile Location that echoes the key back must not carry it to the other host.
            origin.enqueue(MockResponse(code = 302, headers = headersOf("Location", "$target?apiKey=echoed")))
            elsewhere.enqueue(ok())
            val client = ServerHttpClients(baseHttpClient()).create(pinned, FakeSource(creds))
            client
                .newCall(
                    Request.Builder().url(origin.url("/api/Image/series-cover?seriesId=1")).build(),
                ).execute()
                .close()
            val sent = origin.takeRequest().headers
            assertTrue(sent[AUTHORIZATION] != null || sent[API_KEY] != null)
            val leaked = elsewhere.takeRequest()
            assertNull(leaked.headers[AUTHORIZATION])
            assertNull(leaked.headers[API_KEY])
            assertNull(leaked.url.queryParameter(API_KEY_PARAM))
        }
    }
}
