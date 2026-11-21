package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.ScrobbleHoldDto
import com.nl.kaviyy.core.testing.FakeKavitaApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ScrobblingRepositoryTest {
    private val server = Server(ServerId(1), "s", (ServerUrl.parse("https://s.lan") as ServerUrlResult.Valid).url)
    private val api = FakeKavitaApi()
    private val repo = KavitaScrobblingRepository { api }

    @Test
    fun unlicensedStopsAfterTheLicenseCheck() =
        runTest {
            api.licensed = false
            assertEquals(AppResult.Success(ScrobbleStatus(false)), repo.status(server))
            assertEquals(1, api.scrobbleCalls)
        }

    @Test
    fun linksAreConnectedExpiredOrMissing() =
        runTest {
            api.aniList = "eyJ0"
            api.expired = setOf(1)
            api.holds = listOf(ScrobbleHoldDto(4, "Berserk"))
            val s = (repo.status(server) as AppResult.Success).value
            assertEquals(
                mapOf(ScrobbleProvider.AniList to LinkState.Expired, ScrobbleProvider.Mal to LinkState.NotConnected),
                s.links,
            )
            api.expired = emptySet()
            api.malUser = "neo"
            val t = (repo.status(server) as AppResult.Success).value
            assertEquals(LinkState.Connected, t.links[ScrobbleProvider.AniList])
            assertEquals(LinkState.Connected, t.links[ScrobbleProvider.Mal])
            assertEquals("Berserk", t.holds.single().seriesName)
        }
}
