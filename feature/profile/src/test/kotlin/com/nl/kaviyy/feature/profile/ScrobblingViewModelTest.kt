package com.nl.kaviyy.feature.profile

import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ScrobblingRepository
import com.nl.kaviyy.core.data.Tracker
import com.nl.kaviyy.core.data.TrackerAccounts
import com.nl.kaviyy.core.data.TrackerApis
import com.nl.kaviyy.core.datastore.TrackerFile
import com.nl.kaviyy.core.datastore.TrackerStore
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.network.baseHttpClient
import com.nl.kaviyy.core.network.tracker.AniListApi
import com.nl.kaviyy.core.network.tracker.KitsuApi
import com.nl.kaviyy.core.network.tracker.MalApi
import com.nl.kaviyy.core.testing.InMemoryDataStore
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URLDecoder
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ScrobblingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val mock = MockWebServer().apply { start() }
    private val http = baseHttpClient()
    private val io = Dispatchers.Unconfined
    private val store = TrackerStore(InMemoryDataStore(TrackerFile()))
    private val sync =
        TrackerAccounts(
            store,
            TrackerApis(
                AniListApi(http, io, mock.url("/")),
                MalApi(http, io, mock.url("/v2/"), mock.url("/oauth/")),
                KitsuApi(http, io, mock.url("/api/")),
            ),
        )
    private val noServer =
        object : AuthSession {
            override val servers = flowOf(emptyList<Server>())
            override val activeServer = flowOf<Server?>(null)

            override suspend fun switchTo(serverId: ServerId) = Unit

            override suspend fun remove(serverId: ServerId) = Unit

            override suspend fun deleteAllLocalData() = Unit

            override suspend fun knownServer(url: ServerUrl): Server? = null

            override suspend fun signIn(
                target: Server,
                method: LoginMethod,
                staySignedIn: Boolean,
            ): AppResult<Server> = error("unused")

            override suspend fun signOut(serverId: ServerId) = Unit

            override fun credentialSource(serverId: ServerId) = error("unused")
        }
    private val vm =
        ScrobblingViewModel(
            noServer,
            object : ScrobblingRepository {
                override suspend fun status(server: Server) = error("no server")
            },
            sync,
        )

    @After
    fun tearDown() {
        mock.close()
    }

    private fun reply(
        body: String,
        code: Int = 200,
    ) = mock.enqueue(MockResponse(code = code, headers = headersOf("Content-Type", "application/json"), body = body))

    @Test
    fun kitsuConnectsKeepsOnlyTokensAndCanDisconnect() =
        runTest {
            vm.actions.onEdit(Tracker.Kitsu)
            reply("""{"access_token":"a","refresh_token":"r","expires_in":100}""")
            reply("""{"data":[{"id":"77","attributes":{"name":"neo"}}]}""")
            vm.actions.onConnect(Tracker.Kitsu, "neo@example.com", "hunter2")
            advanceUntilIdle()
            assertEquals(mapOf(Tracker.Kitsu to "neo"), vm.state.value.trackers)
            assertNull(vm.state.value.editing)
            val saved = store.snapshot().accounts.getValue("Kitsu")
            assertEquals("77", saved.userId)
            assertTrue("hunter2" !in saved.toString())
            vm.actions.onDisconnect(Tracker.Kitsu)
            advanceUntilIdle()
            assertEquals(emptyMap(), vm.state.value.trackers)
        }

    @Test
    fun aRejectedPasteKeepsTheSheetOpenWithAnError() =
        runTest {
            vm.actions.onEdit(Tracker.AniList)
            reply("{}", code = 401)
            vm.actions.onConnect(Tracker.AniList, "123", "bad-token")
            advanceUntilIdle()
            assertEquals(Tracker.AniList, vm.state.value.editing)
            assertTrue(vm.state.value.connectFailed)
            assertEquals(emptyMap(), vm.state.value.trackers)
        }

    @Test
    fun malApprovalUrlCarriesAFreshVerifierThatTheExchangeReuses() =
        runTest {
            val url = vm.actions.authorizeUrl(Tracker.Mal, " cid ")
            val challenge =
                Regex("code_challenge=([^&]+)")
                    .find(url)
                    ?.groupValues
                    ?.get(1)
                    .orEmpty()
            assertTrue(challenge.length == 64 && "client_id=cid" in url, url)
            reply("""{"access_token":"a","refresh_token":"r","expires_in":100}""")
            reply("""{"id":3,"name":"neo"}""")
            vm.actions.onConnect(Tracker.Mal, "cid", "https://x/cb?code=C1")
            advanceUntilIdle()
            val form =
                URLDecoder.decode(
                    mock
                        .takeRequest()
                        .body
                        ?.utf8()
                        .orEmpty(),
                    "UTF-8",
                )
            assertTrue("code_verifier=${URLDecoder.decode(challenge, "UTF-8")}" in form, form)
        }
}
