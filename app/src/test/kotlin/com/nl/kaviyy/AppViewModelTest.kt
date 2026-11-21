package com.nl.kaviyy

import app.cash.turbine.test
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.testing.InMemoryDataStore
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AppViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val active = MutableStateFlow<Server?>(null)
    private val session =
        object : AuthSession {
            override val activeServer = active
            override val servers = active.map { listOfNotNull(it) }

            override suspend fun switchTo(serverId: ServerId) {
                calls += "switch ${serverId.value}"
            }

            override suspend fun deleteAllLocalData() {
                calls += "wipe"
                active.value = null
            }

            override suspend fun remove(serverId: ServerId) {
                calls += "remove ${serverId.value}"
                active.value = null
            }

            override suspend fun knownServer(url: ServerUrl): Server? = null

            override suspend fun signIn(
                target: Server,
                method: LoginMethod,
                staySignedIn: Boolean,
            ): AppResult<Server> = error("unused")

            override suspend fun signOut(serverId: ServerId) {
                active.value = null
            }

            override fun credentialSource(serverId: ServerId) = error("unused")
        }

    private val calls = mutableListOf<String>()

    @Test
    fun switchAndRemoveGoThroughTheSession() =
        runTest {
            val vm = AppViewModel(session, AppSettingsStore(InMemoryDataStore(AppSettings())), clock)
            vm.switchTo(ServerId(2))
            vm.remove(ServerId(3))
            vm.deleteAllLocalData()
            runCurrent()
            assertEquals(listOf("switch 2", "remove 3", "wipe"), calls)
        }

    @Test
    fun followsSessionAndSignsOut() =
        runTest {
            val vm = AppViewModel(session, AppSettingsStore(InMemoryDataStore(AppSettings())), clock)
            val server = Server(ServerId(1), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)
            vm.state.test {
                assertEquals(AppState.SignedOut, awaitItem())
                active.value = server
                assertEquals(AppState.SignedIn(server), awaitItem())
                vm.signOut(server)
                assertEquals(AppState.SignedOut, awaitItem())
            }
        }

    private var now = 0L
    private val clock =
        object : java.time.Clock() {
            override fun getZone() = java.time.ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId?) = this

            override fun instant(): java.time.Instant = java.time.Instant.ofEpochMilli(now)
        }

    @Test
    fun appLockLocksOnColdStartAndAfterTheChosenTimeAway() =
        kotlinx.coroutines.test.runTest {
            val store = AppSettingsStore(InMemoryDataStore(AppSettings(appLock = true, lockAfterMinutes = 1)))
            val vm = AppViewModel(session, store, clock)
            runCurrent()
            kotlin.test.assertEquals(true, vm.locked.value)
            vm.onBackground() // the unlock prompt covering the app doesn't count
            vm.unlocked()
            vm.onForeground()
            kotlin.test.assertEquals(false, vm.locked.value)
            vm.onBackground()
            now += 59_000
            vm.onForeground()
            kotlin.test.assertEquals(false, vm.locked.value)
            vm.onBackground()
            now += 60_000
            vm.onForeground()
            kotlin.test.assertEquals(true, vm.locked.value)
        }

    @Test
    fun withoutAppLockNothingLocks() =
        kotlinx.coroutines.test.runTest {
            val vm = AppViewModel(session, AppSettingsStore(InMemoryDataStore(AppSettings())), clock)
            runCurrent()
            vm.onBackground()
            now += 3_600_000
            vm.onForeground()
            kotlin.test.assertEquals(false, vm.locked.value)
        }
}
