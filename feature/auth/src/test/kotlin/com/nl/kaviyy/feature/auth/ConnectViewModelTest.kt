package com.nl.kaviyy.feature.auth

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.KavitaVersion
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlError
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.network.ServerInfo
import com.nl.kaviyy.core.network.SsoInfo
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConnectViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val probed = mutableListOf<Server>()
    private var answer: AppResult<ServerInfo> = AppResult.Success(ServerInfo(KavitaVersion.parse("0.9.0.2")))

    private var loginResult: AppResult<Server> = AppResult.Failure(AppError.Unauthorized)
    private val signIns = mutableListOf<List<Any?>>()
    private val session =
        object : AuthSession {
            override val activeServer = emptyFlow<Server?>()

            override suspend fun signIn(
                url: ServerUrl,
                version: String?,
                method: LoginMethod,
                staySignedIn: Boolean,
            ): AppResult<Server> {
                signIns += listOf(url.value, version, method, staySignedIn)
                return loginResult
            }

            override suspend fun signOut(serverId: ServerId) = Unit

            override fun credentialSource(serverId: ServerId) = error("unused")
        }

    private fun viewModel() =
        ConnectViewModel(
            { server ->
                probed += server
                answer
            },
            session,
        )

    private fun ConnectViewModel.type(text: String) {
        address.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun probesOnlyAfterTypingPauses() =
        runTest {
            val vm = viewModel()
            runCurrent()
            vm.type("kavita.home.la")
            advanceTimeBy(300)
            vm.type("kavita.home.lan")
            advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
            runCurrent()
            assertEquals(1, probed.size)
            assertEquals("https://kavita.home.lan", probed.single().url.value)
            assertFalse(probed.single().allowInsecure)
            val found = assertIs<Reachability.Found>(vm.state.value.reachability)
            assertEquals("0.9.0.2", found.version)
            assertTrue(found.supported)
        }

    @Test
    fun invalidAndHttpAddressesAreNotProbed() =
        runTest {
            val vm = viewModel()
            vm.type("ftp://x")
            advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
            runCurrent()
            assertEquals(Reachability.Invalid(ServerUrlError.UnsupportedScheme), vm.state.value.reachability)
            vm.type("http://192.168.1.2:5000")
            advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
            runCurrent()
            assertEquals(Reachability.InsecureHttp, vm.state.value.reachability)
            vm.type("")
            advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
            runCurrent()
            assertEquals(Reachability.Idle, vm.state.value.reachability)
            assertTrue(probed.isEmpty())
        }

    @Test
    fun failuresAndOutdatedServersAreReported() =
        runTest {
            answer = AppResult.Failure(AppError.Tls)
            val vm = viewModel()
            vm.type("k.example")
            advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
            runCurrent()
            assertEquals(Reachability.Failed(AppError.Tls), vm.state.value.reachability)
            answer = AppResult.Success(ServerInfo(KavitaVersion.parse("0.8.6")))
            vm.type("k2.example")
            advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
            runCurrent()
            assertFalse(assertIs<Reachability.Found>(vm.state.value.reachability).supported)
        }

    @Test
    fun signInNeedsAReachableServerAndCredentials() =
        runTest {
            val vm = viewModel()
            vm.type("k.example")
            advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
            runCurrent()
            assertFalse(vm.state.value.canSignIn)
            vm.username.setTextAndPlaceCursorAtEnd("aarav")
            vm.password.setTextAndPlaceCursorAtEnd("hunter2")
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertTrue(vm.state.value.canSignIn)
            vm.setStaySignedIn(false)
            assertFalse(vm.state.value.staySignedIn)
        }

    private suspend fun kotlinx.coroutines.test.TestScope.readyToSignIn(): ConnectViewModel {
        val vm = viewModel()
        vm.type("k.example")
        advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
        vm.username.setTextAndPlaceCursorAtEnd(" aarav ")
        vm.password.setTextAndPlaceCursorAtEnd("hunter2")
        Snapshot.sendApplyNotifications()
        runCurrent()
        return vm
    }

    @Test
    fun wrongPasswordShowsErrorAndKeepsInput() =
        runTest {
            val vm = readyToSignIn()
            vm.setStaySignedIn(false)
            vm.signIn()
            runCurrent()
            assertEquals(
                listOf<Any?>("https://k.example", "0.9.0.2", LoginMethod.Password("aarav", "hunter2"), false),
                signIns.single(),
            )
            assertEquals(AppError.Unauthorized, vm.state.value.signInError)
            assertFalse(vm.state.value.signingIn)
            assertEquals("hunter2", vm.password.text.toString())
        }

    @Test
    fun successClearsThePassword() =
        runTest {
            val vm = readyToSignIn()
            val url = (ServerUrl.parse("https://k.example") as ServerUrlResult.Valid).url
            loginResult = AppResult.Success(Server(ServerId(1), "k", url))
            vm.signIn()
            runCurrent()
            assertEquals("", vm.password.text.toString())
            assertEquals(null, vm.state.value.signInError)
        }

    @Test
    fun authKeySheetSignsInWithTrimmedKeyAndCloses() =
        runTest {
            val vm = readyToSignIn()
            val url = (ServerUrl.parse("https://k.example") as ServerUrlResult.Valid).url
            loginResult = AppResult.Success(Server(ServerId(1), "k", url))
            vm.openAuthKey()
            assertTrue(vm.state.value.authKeySheetOpen)
            vm.authKey.setTextAndPlaceCursorAtEnd("  pasted-key\n")
            vm.signInWithAuthKey()
            runCurrent()
            assertEquals(LoginMethod.AuthKey("pasted-key"), signIns.single()[2])
            assertFalse(vm.state.value.authKeySheetOpen)
            assertEquals("", vm.authKey.text.toString())
        }

    @Test
    fun authKeyNeedsAFoundServerAndANonEmptyKey() =
        runTest {
            val vm = viewModel()
            vm.openAuthKey()
            assertFalse(vm.state.value.authKeySheetOpen)
            val ready = readyToSignIn()
            ready.openAuthKey()
            ready.signInWithAuthKey()
            runCurrent()
            assertTrue(signIns.isEmpty())
        }

    @Test
    fun ssoServerWithPasswordDisabledOnlyOffersTheKeyFlow() =
        runTest {
            answer =
                AppResult.Success(
                    ServerInfo(KavitaVersion.parse("0.9.0.2"), SsoInfo("Authelia", passwordLoginDisabled = true)),
                )
            val vm = readyToSignIn()
            assertFalse(vm.state.value.passwordLogin)
            assertFalse(vm.state.value.canSignIn)
            vm.signIn()
            vm.openAuthKey(viaSso = true)
            runCurrent()
            assertTrue(signIns.isEmpty())
            assertTrue(vm.state.value.authKeySheetOpen && vm.state.value.authKeyViaSso)
            vm.closeAuthKey()
            assertFalse(vm.state.value.authKeyViaSso)
        }

    @Test
    fun signInIsIgnoredUntilReady() =
        runTest {
            val vm = viewModel()
            vm.signIn()
            runCurrent()
            assertTrue(signIns.isEmpty())
        }
}
