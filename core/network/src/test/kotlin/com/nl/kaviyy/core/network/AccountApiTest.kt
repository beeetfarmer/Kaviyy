package com.nl.kaviyy.core.network

import com.nl.kaviyy.core.model.auth.Credentials
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountApiTest {
    private val mock = MockWebServer().apply { start() }
    private val url = (ServerUrl.parse(mock.url("/").toString()) as ServerUrlResult.Valid).url
    private val server = Server(ServerId(1), "S", url, allowInsecure = true)
    private val api = KavitaAccountApi(ServerHttpClients(baseHttpClient()), Dispatchers.IO)

    @After
    fun tearDown() = mock.close()

    private fun login() = runBlocking { api.login(server, "aarav", "hunter2") }

    @Test
    fun successReturnsJwtApiKeyAndRoles() {
        mock.enqueue(
            MockResponse(
                headers = headersOf("Content-Type", "application/json"),
                body = """{"username":"Aarav","token":"t","refreshToken":"r","apiKey":"k","roles":["Download"]}""",
            ),
        )
        val result = (login() as AppResult.Success).value
        assertEquals(Credentials.Jwt("Aarav", "t", "r", "k"), result.credentials)
        assertEquals(setOf("Download"), result.roles)
        val sent = mock.takeRequest()
        assertEquals("/api/Account/login", sent.url.encodedPath)
        assertEquals("POST", sent.method)
        assertTrue("hunter2" in sent.body?.utf8().orEmpty())
        assertEquals(null, sent.url.query, "password never in the URL")
    }

    @Test
    fun wrongPasswordIsUnauthorized() {
        mock.enqueue(MockResponse(code = 401, body = "Your account is locked"))
        assertEquals(AppResult.Failure(AppError.Unauthorized), login())
    }

    @Test
    fun responseWithoutTokenIsInvalid() {
        mock.enqueue(MockResponse(body = """{"username":"x"}"""))
        assertEquals(AppResult.Failure(AppError.InvalidData), login())
    }
}
