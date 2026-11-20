package com.nl.kaviyy.feature.home

import app.cash.turbine.test
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.Home
import com.nl.kaviyy.core.data.HomeRepository
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun server(id: Long) =
        Server(ServerId(id), "s$id", (ServerUrl.parse("https://s$id.lan") as ServerUrlResult.Valid).url)

    private val active = MutableStateFlow<Server?>(server(1))
    private val refreshed = mutableListOf<Long>()
    private var result: AppResult<Unit> = AppResult.Success(Unit)
    private val homes = MutableStateFlow(Home(null, emptyList()))

    private val session =
        object : AuthSession {
            override val servers = active.map { listOfNotNull(it) }
            override val activeServer = active

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

    private val repo =
        object : HomeRepository {
            override fun home(server: Server): Flow<Home> = homes

            override suspend fun refresh(server: Server): AppResult<Unit> {
                refreshed += server.id.value
                return result
            }
        }

    @Test
    fun refreshesOnOpenAndOnServerSwitch() =
        runTest {
            val vm = HomeViewModel(session, repo)
            vm.state.test {
                skipItems(1)
                runCurrent()
                assertEquals(listOf(1L), refreshed)
                active.value = server(2)
                runCurrent()
                assertEquals(listOf(1L, 2L), refreshed)
                assertEquals(ServerId(2), expectMostRecentItem().server?.id)
            }
        }

    @Test
    fun failedRefreshKeepsContentAndExposesTheError() =
        runTest {
            result = AppResult.Failure(AppError.Network)
            val vm = HomeViewModel(session, repo)
            vm.state.test {
                runCurrent()
                val s = expectMostRecentItem()
                assertEquals(AppError.Network, s.error)
                assertEquals(homes.value, s.home)
                result = AppResult.Success(Unit)
                vm.refresh()
                runCurrent()
                assertNull(expectMostRecentItem().error)
            }
        }
}
