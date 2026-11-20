package com.nl.kaviyy.feature.auth

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.model.result.AppError
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.KavitaVersion
import com.nl.kaviyy.core.model.server.PinnedCertificate
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlError
import com.nl.kaviyy.core.model.server.ServerUrlResult
import com.nl.kaviyy.core.network.LoginMethod
import com.nl.kaviyy.core.network.PresentedCertificate
import com.nl.kaviyy.core.network.ServerInfo
import com.nl.kaviyy.core.network.SsoInfo
import com.nl.kaviyy.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    private val ok: AppResult<ServerInfo> = AppResult.Success(ServerInfo(KavitaVersion.parse("0.9.0.2")))
    private val probed = mutableListOf<Server>()
    private var answer: (Server) -> AppResult<ServerInfo> = { ok }
    private var presented: PresentedCertificate? = null
    private val known = mutableMapOf<String, Server>()
    private var loginResult: AppResult<Server> = AppResult.Failure(AppError.Unauthorized)
    private val signIns = mutableListOf<Triple<Server, LoginMethod, Boolean>>()

    private val session =
        object : AuthSession {
            override val activeServer = emptyFlow<Server?>()
            override val servers = emptyFlow<List<Server>>()

            override suspend fun switchTo(serverId: ServerId) = Unit

            override suspend fun deleteAllLocalData() = Unit

            override suspend fun remove(serverId: ServerId) = Unit

            override suspend fun knownServer(url: ServerUrl) = known[url.value]

            override suspend fun signIn(
                target: Server,
                method: LoginMethod,
                staySignedIn: Boolean,
            ): AppResult<Server> {
                signIns += Triple(target, method, staySignedIn)
                return loginResult
            }

            override suspend fun signOut(serverId: ServerId) = Unit

            override fun credentialSource(serverId: ServerId) = error("unused")
        }

    private fun viewModel() =
        ConnectViewModel(
            { server ->
                probed += server
                answer(server)
            },
            { presented },
            session,
        )

    private fun url(s: String) = (ServerUrl.parse(s) as ServerUrlResult.Valid).url

    private fun cert(
        bytes: Int,
        matchesHost: Boolean = true,
    ) = PresentedCertificate(
        PinnedCertificate.of(byteArrayOf(bytes.toByte())),
        "CN=k",
        "CN=k",
        Date(0),
        Date(1),
        matchesHost,
    )

    private fun TestScope.type(
        vm: ConnectViewModel,
        text: String,
    ) {
        vm.address.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        advanceTimeBy(ADDRESS_DEBOUNCE_MS + 1)
        runCurrent()
    }

    private fun TestScope.readyToSignIn(): ConnectViewModel {
        val vm = viewModel()
        type(vm, "k.example")
        vm.username.setTextAndPlaceCursorAtEnd(" aarav ")
        vm.password.setTextAndPlaceCursorAtEnd("hunter2")
        Snapshot.sendApplyNotifications()
        runCurrent()
        return vm
    }

    @Test
    fun probesOnlyAfterTypingPauses() =
        runTest {
            val vm = viewModel()
            runCurrent()
            vm.address.setTextAndPlaceCursorAtEnd("kavita.home.la")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(300)
            type(vm, "kavita.home.lan")
            assertEquals(listOf("https://kavita.home.lan"), probed.map { it.url.value })
            assertFalse(probed.single().allowInsecure)
            val found = assertIs<Reachability.Found>(vm.state.value.reachability)
            assertEquals("0.9.0.2", found.version)
            assertTrue(found.supported)
        }

    @Test
    fun invalidAddressesAreNotProbed() =
        runTest {
            val vm = viewModel()
            type(vm, "ftp://x")
            assertEquals(Reachability.Invalid(ServerUrlError.UnsupportedScheme), vm.state.value.reachability)
            type(vm, "")
            assertEquals(Reachability.Idle, vm.state.value.reachability)
            assertTrue(probed.isEmpty())
        }

    @Test
    fun publicHttpIsRefusedAndPrivateHttpNeedsOptIn() =
        runTest {
            val vm = viewModel()
            type(vm, "http://example.com")
            assertEquals(Reachability.HttpNotAllowed, vm.state.value.reachability)
            vm.showSheet(ConnectSheet.InsecureHttp)
            assertNull(vm.state.value.sheet)
            type(vm, "http://192.168.1.2:5000")
            assertIs<Reachability.InsecureHttp>(vm.state.value.reachability)
            assertTrue(probed.isEmpty())
            vm.showSheet(ConnectSheet.InsecureHttp)
            vm.confirmSheet()
            runCurrent()
            assertTrue(probed.single().allowInsecure)
            assertTrue(assertIs<Reachability.Found>(vm.state.value.reachability).server.allowInsecure)
            assertNull(vm.state.value.sheet)
        }

    @Test
    fun savedHttpOptInIsReused() =
        runTest {
            known["http://192.168.1.2:5000"] =
                Server(ServerId(3), "nas", url("http://192.168.1.2:5000"), allowInsecure = true)
            val vm = viewModel()
            type(vm, "http://192.168.1.2:5000")
            assertIs<Reachability.Found>(vm.state.value.reachability)
        }

    @Test
    fun untrustedCertificateCanBeTrustedOnFirstUse() =
        runTest {
            val presentedCert = cert(1)
            presented = presentedCert
            answer = { s -> if (s.pinnedCertificate == presentedCert.pin) ok else AppResult.Failure(AppError.Tls) }
            val vm = viewModel()
            type(vm, "k.lan")
            assertIs<Reachability.UntrustedCertificate>(vm.state.value.reachability)
            vm.showSheet(ConnectSheet.Certificate)
            vm.confirmSheet()
            runCurrent()
            val found = assertIs<Reachability.Found>(vm.state.value.reachability)
            assertEquals(presentedCert.pin, found.server.pinnedCertificate)
        }

    @Test
    fun certificateForAnotherNameIsNeverPinned() =
        runTest {
            presented = cert(1, matchesHost = false)
            answer = { AppResult.Failure(AppError.Tls) }
            val vm = viewModel()
            type(vm, "k.lan")
            vm.showSheet(ConnectSheet.Certificate)
            vm.confirmSheet()
            runCurrent()
            assertTrue(probed.none { it.pinnedCertificate != null })
        }

    @Test
    fun changedCertificateOnASavedServerIsAHardFail() =
        runTest {
            known["https://k.lan"] = Server(ServerId(1), "k", url("https://k.lan"), pinnedCertificate = cert(1).pin)
            presented = cert(2)
            answer = { AppResult.Failure(AppError.Tls) }
            val vm = viewModel()
            type(vm, "k.lan")
            assertIs<Reachability.PinMismatch>(vm.state.value.reachability)
            vm.showSheet(ConnectSheet.Certificate)
            assertNull(vm.state.value.sheet)
        }

    @Test
    fun failuresAndOutdatedServersAreReported() =
        runTest {
            answer = { AppResult.Failure(AppError.Network) }
            val vm = viewModel()
            type(vm, "k.example")
            assertEquals(Reachability.Failed(AppError.Network), vm.state.value.reachability)
            answer = { AppResult.Success(ServerInfo(KavitaVersion.parse("0.8.6"))) }
            type(vm, "k2.example")
            assertFalse(assertIs<Reachability.Found>(vm.state.value.reachability).supported)
        }

    @Test
    fun wrongPasswordShowsErrorAndKeepsInput() =
        runTest {
            val vm = readyToSignIn()
            assertTrue(vm.state.value.canSignIn)
            vm.setStaySignedIn(false)
            vm.signIn()
            runCurrent()
            val (target, method, stay) = signIns.single()
            assertEquals("https://k.example", target.url.value)
            assertEquals(LoginMethod.Password("aarav", "hunter2"), method)
            assertFalse(stay)
            assertEquals(AppError.Unauthorized, vm.state.value.signInError)
            assertEquals("hunter2", vm.password.text.toString())
        }

    @Test
    fun successClearsThePassword() =
        runTest {
            val vm = readyToSignIn()
            loginResult = AppResult.Success(Server(ServerId(1), "k", url("https://k.example")))
            vm.signIn()
            runCurrent()
            assertEquals("", vm.password.text.toString())
        }

    @Test
    fun successfulSignInFiresOneEvent() =
        runTest {
            val vm = readyToSignIn()
            loginResult = AppResult.Success(Server(ServerId(1), "k", url("https://k.example")))
            val events = mutableListOf<Unit>()
            backgroundScope.launch { vm.signedIn.collect { events += it } }
            runCurrent()
            vm.signIn()
            runCurrent()
            assertEquals(1, events.size)
        }

    @Test
    fun authKeySheetSignsInWithTrimmedKeyAndCloses() =
        runTest {
            val vm = readyToSignIn()
            loginResult = AppResult.Success(Server(ServerId(1), "k", url("https://k.example")))
            vm.showSheet(ConnectSheet.AuthKey)
            assertEquals(ConnectSheet.AuthKey, vm.state.value.sheet)
            vm.authKey.setTextAndPlaceCursorAtEnd("  pasted-key\n")
            vm.confirmSheet()
            runCurrent()
            assertEquals(LoginMethod.AuthKey("pasted-key"), signIns.single().second)
            assertNull(vm.state.value.sheet)
            assertEquals("", vm.authKey.text.toString())
        }

    @Test
    fun sheetsNeedTheRightState() =
        runTest {
            val vm = viewModel()
            vm.showSheet(ConnectSheet.AuthKey)
            vm.showSheet(ConnectSheet.Certificate)
            assertNull(vm.state.value.sheet)
            vm.signIn()
            runCurrent()
            assertTrue(signIns.isEmpty())
        }

    @Test
    fun ssoServerWithPasswordDisabledOnlyOffersTheKeyFlow() =
        runTest {
            answer =
                {
                    AppResult.Success(
                        ServerInfo(KavitaVersion.parse("0.9.0.2"), SsoInfo("Authelia", passwordLoginDisabled = true)),
                    )
                }
            val vm = readyToSignIn()
            assertFalse(vm.state.value.passwordLogin)
            assertFalse(vm.state.value.canSignIn)
            vm.showSheet(ConnectSheet.Sso)
            assertEquals(ConnectSheet.Sso, vm.state.value.sheet)
        }
}
