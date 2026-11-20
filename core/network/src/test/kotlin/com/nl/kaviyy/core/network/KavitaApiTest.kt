package com.nl.kaviyy.core.network

import com.nl.kaviyy.core.model.auth.Credentials
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KavitaApiTest {
    private val mock = MockWebServer().apply { start() }

    @After
    fun tearDown() = mock.close()

    private val source =
        object : CredentialSource {
            override fun current() = Credentials.AuthKey("key")

            override fun update(credentials: Credentials.Jwt) = Unit

            override fun signOut() = Unit
        }

    private fun api(): KavitaApi {
        val url = (ServerUrl.parse(mock.url("/kavita").toString()) as ServerUrlResult.Valid).url
        val server = Server(ServerId(1), "k", url, allowInsecure = true)
        return KavitaApis(ServerHttpClients(baseHttpClient())).forServer(server, source)
    }

    @Test
    fun seriesV2SendsTheLibraryFilterAndPaging() =
        runTest {
            val json = """[{"id":4,"libraryId":3,"name":"A","format":0,"pages":10,"unknownField":true}]"""
            mock.enqueue(MockResponse(body = json))
            val list = api().series(SeriesFilterV2Dto.library(3), 2, 50)
            assertEquals(listOf(4), list.map { it.id })
            val request = mock.takeRequest()
            assertEquals("/kavita/api/Series/v2", request.url.encodedPath)
            assertEquals("2", request.url.queryParameter("PageNumber"))
            assertEquals("50", request.url.queryParameter("PageSize"))
            assertEquals("key", request.headers[API_KEY])
            val body = request.body?.utf8().orEmpty()
            listOf(""""field":19""", """"value":"3"""", """"sortField":1""").forEach { assertTrue(it in body, body) }
        }

    @Test
    fun httpErrorsMapToAppErrors() =
        runTest {
            mock.enqueue(MockResponse(code = 404))
            assertEquals(AppResult.Failure(AppError.NotFound), apiCall { api().seriesDetail(1) })
            mock.enqueue(MockResponse(body = "not json"))
            assertEquals(AppResult.Failure(AppError.InvalidData), apiCall { api().libraries() })
        }

    @Test
    fun kavitaTimes() {
        assertEquals(1_767_323_045_000, kavitaTime("2026-01-02T03:04:05Z"))
        assertEquals(1_767_323_045_000, kavitaTime("2026-01-02T03:04:05"))
        assertEquals(1_767_323_045_500, kavitaTime("2026-01-02T03:04:05.5"))
        assertEquals(0, kavitaTime("0001-01-01T00:00:00"))
        assertEquals(0, kavitaTime(null))
        assertEquals(0, kavitaTime("garbage"))
    }
}
