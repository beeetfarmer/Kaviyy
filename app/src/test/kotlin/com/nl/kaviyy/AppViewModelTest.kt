package com.nl.kaviyy

import app.cash.turbine.test
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
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

            override suspend fun signIn(
                url: ServerUrl,
                version: String?,
                username: String,
                password: String,
                staySignedIn: Boolean,
            ): AppResult<Server> = error("unused")

            override suspend fun signOut(serverId: ServerId) {
                active.value = null
            }

            override fun credentialSource(serverId: ServerId) = error("unused")
        }

    @Test
    fun followsSessionAndSignsOut() =
        runTest {
            val vm = AppViewModel(session)
            val server = Server(ServerId(1), "k", (ServerUrl.parse("https://k.lan") as ServerUrlResult.Valid).url)
            vm.state.test {
                assertEquals(AppState.SignedOut, awaitItem())
                active.value = server
                assertEquals(AppState.SignedIn(server), awaitItem())
                vm.signOut(server)
                assertEquals(AppState.SignedOut, awaitItem())
            }
        }
}
