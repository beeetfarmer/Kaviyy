package com.nl.kaviyy.core.network.tracker

import com.nl.kaviyy.core.network.baseHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plain HTTP to a local mock; the production client ([trackerHttpClient]) is HTTPS-only. */
class TrackersTest {
    private val mock = MockWebServer().apply { start() }
    private val http = baseHttpClient()
    private val io = Dispatchers.IO
    private val login = TrackerLogin("tok", userId = "77")

    @After
    fun tearDown() = mock.close()

    private fun reply(
        body: String,
        code: Int = 200,
    ) = mock.enqueue(MockResponse(code = code, headers = headersOf("Content-Type", "application/json"), body = body))

    private fun sent() = mock.takeRequest()

    @Test
    fun aniListSearchesAndSavesWithGraphQlVariables(): Unit =
        runBlocking {
            val api = AniListApi(http, io, mock.url("/"))
            reply(
                """{"data":{"Page":{"media":[""" +
                    """{"id":5,"title":{"romaji":"Ember Road","english":null},"synonyms":["ER"]}]}}}""",
            )
            assertEquals(listOf(TrackerMedia(5, listOf("Ember Road", "ER"))), api.search(login, "Ember \"Road\""))
            val search = sent()
            assertEquals("Bearer tok", search.headers["Authorization"])
            assertTrue(
                "\"s\":\"Ember \\\"Road\\\"\"" in search.body?.utf8().orEmpty(),
                "title is a variable, never spliced",
            )
            reply("""{"data":{"Media":{"mediaListEntry":null}}}""")
            assertNull(api.progress(login, 5))
            sent()
            reply("""{"data":{"SaveMediaListEntry":{"id":1}}}""")
            api.save(login, 5, 12)
            assertTrue("\"p\":12" in sent().body?.utf8().orEmpty())
        }

    @Test
    fun malExchangesThePastedCodeAndPatchesProgress(): Unit =
        runBlocking {
            val api = MalApi(http, io, mock.url("/v2/"), mock.url("/oauth/"), now = { 1_000 })
            reply("""{"access_token":"a","refresh_token":"r","expires_in":10}""")
            reply("""{"id":3,"name":"neo"}""")
            val got = api.exchange("cid", "https://example.com/cb?code=XYZ&state=1", "ver")
            assertEquals(TrackerLogin("a", "r", 11_000, "neo", "3"), got)
            val token = sent().body?.utf8().orEmpty()
            assertTrue("code=XYZ" in token && "code_verifier=ver" in token && "client_id=cid" in token)
            assertEquals("/v2/users/@me", sent().url.encodedPath)
            reply("""{"status":"reading"}""")
            api.save(login, 9, 4)
            val patch = sent()
            assertEquals("PATCH", patch.method)
            assertEquals("/v2/manga/9/my_list_status", patch.url.encodedPath)
            assertTrue("num_chapters_read=4" in patch.body?.utf8().orEmpty())
            assertEquals("XYZ", MalApi.codeFrom(" XYZ "))
        }

    @Test
    fun kitsuCreatesThenUpdatesTheLibraryEntry(): Unit =
        runBlocking {
            val api = KitsuApi(http, io, mock.url("/api/"))
            reply("""{"data":[]}""") // no entry yet
            reply("""{"data":{"id":"500"}}""")
            api.save(login, 42, 3)
            sent()
            val create = sent()
            assertEquals("POST", create.method)
            val body = create.body?.utf8().orEmpty()
            assertTrue("\"progress\":3" in body && "\"id\":\"77\"" in body && "\"id\":\"42\"" in body)
            reply("""{"data":[{"id":"500","attributes":{"progress":3}}]}""")
            assertEquals(3, api.progress(login, 42))
            sent()
        }

    @Test
    fun errorsSurfaceTheStatus() {
        reply("{}", code = 401)
        val e =
            assertFailsWith<TrackerHttpException> { runBlocking { AniListApi(http, io, mock.url("/")).viewer("bad") } }
        assertEquals(401, e.code)
    }
}
